package com.stark.jarvislocal.v1a

import com.stark.jarvislocal.EngineCompletion
import com.stark.jarvislocal.InferenceCapabilities
import com.stark.jarvislocal.JarvisSettings
import com.stark.jarvislocal.LocalLanguageModel
import com.stark.jarvislocal.StopResult
import com.stark.jarvislocal.UnloadResult
import fr.jarvis.llm.a02.A02Native
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

/**
 * CORE LocalLanguageModel implemented over the JARVIS-LLM engine through A02 only.
 * One native worker thread serializes load/generate/unload (the engine pins handles);
 * cancel() is the only cross-thread call, exactly as in the protected engine contract.
 */
class A02LanguageModel(
    private val native: A02Native,
    private val model: VerifiedModel,
    private val memoryBudgetBytes: () -> Long,
    private val threads: Int = 2,
) : LocalLanguageModel {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "jarvis-a02-engine") }.asCoroutineDispatcher()
    private val handle: Long by lazy { native.create().also { require(it > 0) { "A02_ENGINE_CREATE_REFUSED" } } }
    private val generating = AtomicBoolean(false)
    @Volatile private var loaded = false
    @Volatile var lastTrace: String? = null
        private set

    override val capabilities = InferenceCapabilities(maxContextSize = MAX_CONTEXT, maxGenerationTokens = MAX_OUTPUT)

    override suspend fun warmUp(settings: JarvisSettings) = withContext(worker) {
        if (loaded) return@withContext
        native.begin(handle)
        model.sourceFd().use { source ->
            lastTrace = String(native.loadSealedFd(handle, source.fd, model.sizeBytes, model.sha256.toByteArray(Charsets.US_ASCII),
                memoryBudgetBytes(), settings.contextSize.coerceIn(256, MAX_CONTEXT), threads, true), Charsets.UTF_8)
        }
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
        const val MAX_CONTEXT = 2048
        const val MAX_OUTPUT = 256
    }
}
