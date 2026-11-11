package com.stark.jarvislocal.v1a

import com.stark.jarvislocal.EngineCompletion
import com.stark.jarvislocal.InferenceCapabilities
import com.stark.jarvislocal.JarvisSettings
import com.stark.jarvislocal.LocalLanguageModel
import com.stark.jarvislocal.StopResult
import com.stark.jarvislocal.UnloadResult
import fr.jarvis.llm.a02.A02Native
import fr.jarvis.llm.a02.NativeStreamSink
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** The verified model the host is allowed to load: private file + trusted digest. */
data class VerifiedModel(val sourceFd: () -> SourceDescriptor, val sizeBytes: Long, val sha256: String) {
    init { require(sha256.matches(Regex("[a-f0-9]{64}")) && sizeBytes > 0) }
}
/** A read-only descriptor owned by the host; closed by the host after the native snapshot. */
interface SourceDescriptor : AutoCloseable { val fd: Int }

/** Optional streaming extension of CORE's LocalLanguageModel (CORE contract itself unchanged). */
interface StreamingLanguageModel : LocalLanguageModel {
    /** Streams real engine pieces to [onText]; returns the full text or throws on cancel/error. */
    suspend fun generateStreaming(prompt: String, systemPrompt: String, settings: JarvisSettings, onText: (String) -> Unit): String
}

/**
 * CORE LocalLanguageModel implemented over the JARVIS-LLM engine through A02 only.
 * One native worker thread serializes load/generate/unload (the engine pins handles);
 * cancel() is the only cross-thread call, exactly as in the protected engine contract.
 */
class A02LanguageModel(
    private val native: A02Native,
    private val model: VerifiedModel,
    private val memoryBudgetBytes: () -> Long,
    /** Protected engine policy for Qwen3 0.6B: exactly 1 thread, context 512 or 768. */
    private val threads: Int = 1,
) : StreamingLanguageModel {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "jarvis-a02-engine") }.asCoroutineDispatcher()
    private val handle: Long by lazy { native.create().also { require(it > 0) { "A02_ENGINE_CREATE_REFUSED" } } }
    private val generating = AtomicBoolean(false)
    @Volatile private var loaded = false
    @Volatile private var loadedContext = 0
    @Volatile var lastTrace: String? = null
        private set

    override val capabilities = InferenceCapabilities(maxContextSize = MAX_CONTEXT, maxGenerationTokens = MAX_OUTPUT)

    override suspend fun warmUp(settings: JarvisSettings) = withContext(worker) {
        if (loaded) return@withContext
        native.begin(handle)
        model.sourceFd().use { source ->
            lastTrace = String(native.loadSealedFd(handle, source.fd, model.sizeBytes, model.sha256.toByteArray(Charsets.US_ASCII),
                memoryBudgetBytes(), contextFor(settings), threads, true), Charsets.UTF_8)
        }
        loadedContext = contextFor(settings)
        loaded = true
    }

    override suspend fun generate(prompt: String, systemPrompt: String, settings: JarvisSettings): String = withContext(worker) {
        check(loaded) { "MODEL_NOT_LOADED" }
        native.begin(handle)
        generating.set(true)
        try {
            String(native.generateP2(handle, arrayOf("user".toByteArray()), arrayOf(prompt.toByteArray(Charsets.UTF_8)),
                settings.maxTokens.coerceIn(1, MAX_OUTPUT), systemPrompt.toByteArray(Charsets.UTF_8)), Charsets.UTF_8).trim()
        } finally { generating.set(false) }
    }

    override suspend fun generateStreaming(prompt: String, systemPrompt: String, settings: JarvisSettings, onText: (String) -> Unit): String =
        withContext(worker) {
            check(loaded) { "MODEL_NOT_LOADED" }
            native.begin(handle)
            val question = prompt.toByteArray(Charsets.UTF_8)
            val sink = NativeStreamSink(question, onText) { false }
            generating.set(true)
            val terminal = try {
                native.generateStream(handle, systemPrompt.toByteArray(Charsets.UTF_8), question,
                    settings.maxTokens.coerceIn(1, MAX_OUTPUT), loadedContext, binding(prompt), sink)
            } finally { generating.set(false) }
            when (terminal) {
                2 -> sink.text.toString().trim()
                3 -> throw IllegalStateException("Calcul natif arrêté")
                else -> throw IllegalStateException("STREAM_ERROR")
            }
        }

    override fun isLoaded(): Boolean = loaded

    override suspend fun prepare(settings: JarvisSettings): EngineCompletion {
        warmUp(settings)
        return EngineCompletion(nativeDone = true, modelUnloaded = false)
    }

    override fun requestStop(): StopResult {
        if (!loaded) return StopResult.NOT_RUNNING
        native.cancel(handle)
        return if (generating.get()) StopResult.REQUESTED else StopResult.NOT_RUNNING
    }

    /** Synchronous native unload on the same worker: CONFIRMED means model and context freed. */
    override suspend fun unload(): UnloadResult = withContext(worker) {
        if (!loaded) return@withContext UnloadResult.CONFIRMED
        try { native.unload(handle); loaded = false; UnloadResult.CONFIRMED } catch (e: Exception) { UnloadResult.FAILED }
    }

    companion object {
        /** Protected engine limits for Qwen3 0.6B (native_configuration.h): context 512|768, output <= 96. */
        const val MAX_CONTEXT = 768
        const val MAX_OUTPUT = 96
        fun contextFor(settings: JarvisSettings) = if (settings.contextSize >= 768) 768 else 512
        /** 64-hex binding the protected stream echoes on every event (request identity, not a secret). */
        fun binding(prompt: String) = MessageDigest.getInstance("SHA-256").digest(prompt.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }.toByteArray(Charsets.US_ASCII)
    }
}
