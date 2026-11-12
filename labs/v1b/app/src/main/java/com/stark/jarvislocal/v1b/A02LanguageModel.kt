package com.stark.jarvislocal.v1b

import android.os.SystemClock
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

/**
 * V1-B conversation policy. Engine configuration is the V1-A reference, unchanged: context 768,
 * exactly one thread (protected native_configuration.h). Only the output ceiling moves, bounded by
 * JARVIS-LLM a02/native/a02_output_policy.h (hard ceiling 256, context 768 only).
 */
object V1bPolicy {
    const val CONTEXT = 768
    const val THREADS = 1
    const val DEFAULT_OUTPUT = 128
    const val EXPERIMENTAL_OUTPUT = 256
    const val HARD_CEILING = 256
    /** Protected P2 limit: at most 8 ChatML entries = 3 previous exchanges + the question. */
    const val MAX_HISTORY_PAIRS = 3
    /** Protected generate(): prompt + maxTokens + 8 <= context. */
    const val CONTEXT_MARGIN = 8
    /**
     * Subordinate instructions appended by the protected engine after its fixed system prompt
     * ("Tu es JARVIS, un assistant local. Réponds en français, brièvement. …"). Identical to the
     * Linux host harness (JARVIS-LLM a02/tests/jni/SealedNativeBridge.java V1B_SYSTEM).
     */
    const val SYSTEM_PROMPT = "Tu converses avec l'utilisateur. Retiens ce qu'il dit de lui (prénom, goûts) et réutilise-le quand il le demande. Toi, tu es JARVIS : ne prends jamais son prénom. /no_think"
    /** Host session memory: the last first name the user stated about himself (same rule as the Linux harness). */
    private val NAME = Regex("(?i:je m'appelle|je m’appelle|mon prénom est|mon prenom est|mon nom est|appelle-moi)\\s+(\\p{L}[\\p{L}-]{1,30})")
    const val MEMORY_ENABLED = true
    fun userName(history: List<ConversationHistory.Turn>, question: String): String? =
        (history.map { it.user } + question).flatMap { NAME.findAll(it).map { m -> m.groupValues[1] }.toList() }
            .lastOrNull()?.replaceFirstChar { it.uppercaseChar() }
    /** Subordinate instructions actually sent for this turn (memory fact appended when a name is known). */
    fun systemFor(history: List<ConversationHistory.Turn>, question: String): String {
        val name = if (MEMORY_ENABLED) userName(history, question) else null
        return if (name == null) SYSTEM_PROMPT else "$SYSTEM_PROMPT Fait connu : l'utilisateur s'appelle $name (ce n'est pas ton nom)."
    }
    private val FORBIDDEN = listOf("<|", "<think>", "</think>", "/think", "[INST]", "[/INST]", "<<SYS>>", "/no_think", "\u0000")
    /** A stored turn is re-sent only if the protected P2 validator would accept its texts. */
    fun admissible(text: String) = text.isNotBlank() && FORBIDDEN.none { text.contains(it) }
}

data class TurnMetrics(
    val maxTokens: Int, val promptTokens: Int, val generatedTokens: Int, val historyPairsSent: Int,
    val firstTokenMs: Long, val totalMs: Long, val decodeTokensPerSecond: Double,
) { val stoppedNaturally: Boolean get() = generatedTokens < maxTokens }
data class TurnResult(val text: String, val metrics: TurnMetrics)

/** Optional streaming extension of CORE's LocalLanguageModel (CORE contract itself unchanged). */
interface ConversationalLanguageModel : LocalLanguageModel {
    val outputCeiling: Int
    /** Streams real engine pieces of the answer to [question] given the stored [history]. */
    suspend fun generateTurns(history: List<ConversationHistory.Turn>, question: String, maxTokens: Int, onText: (String) -> Unit): TurnResult
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
    override val outputCeiling: Int = V1bPolicy.DEFAULT_OUTPUT,
    private val nanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) : ConversationalLanguageModel {
    init { require(outputCeiling in 1..V1bPolicy.HARD_CEILING) { "A02_OUTPUT_POLICY_REFUSED" } }
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "jarvis-a02-engine") }
    private val worker = executor.asCoroutineDispatcher()
    private var created = false
    private val handle: Long by lazy { native.create().also { require(it > 0) { "A02_ENGINE_CREATE_REFUSED" }; created = true } }
    private val generating = AtomicBoolean(false)
    @Volatile private var loaded = false
    @Volatile private var destroyed = false
    @Volatile private var loadedContext = 0
    @Volatile var lastTrace: String? = null
        private set

    override val capabilities = InferenceCapabilities(maxContextSize = V1bPolicy.CONTEXT, maxGenerationTokens = outputCeiling)

    override suspend fun warmUp(settings: JarvisSettings) {
        check(!destroyed) { "ENGINE_DESTROYED" } // the worker is shut down after destroy()
        withContext(worker) { load(settings) }
    }
    private fun load(settings: JarvisSettings) {
        if (loaded) return
        native.begin(handle)
        val context = contextFor(settings)
        model.sourceFd().use { source ->
            lastTrace = String(native.loadSealedFd(handle, source.fd, model.sizeBytes, model.sha256.toByteArray(Charsets.US_ASCII),
                memoryBudgetBytes(), context, V1bPolicy.THREADS, true), Charsets.UTF_8)
        }
        // Every protected load resets the ceiling to 96: opt in to the V1-B ceiling, or fail closed.
        val admitted = runCatching { native.setOutputCeiling(handle, outputCeiling) }
        if (admitted.getOrNull() != outputCeiling) {
            runCatching { native.unload(handle) }
            throw IllegalStateException(admitted.exceptionOrNull()?.message ?: "A02_OUTPUT_POLICY_REFUSED")
        }
        loadedContext = context
        loaded = true
    }

    /** CORE single-prompt contract (kept for completeness; V1-B conversations use [generateTurns]). */
    override suspend fun generate(prompt: String, systemPrompt: String, settings: JarvisSettings): String = withContext(worker) {
        check(loaded && !destroyed) { "MODEL_NOT_LOADED" }
        native.begin(handle)
        generating.set(true)
        try {
            String(native.generateP2(handle, arrayOf("user".toByteArray()), arrayOf(prompt.toByteArray(Charsets.UTF_8)),
                settings.maxTokens.coerceIn(1, outputCeiling), systemPrompt.toByteArray(Charsets.UTF_8)), Charsets.UTF_8).trim()
        } finally { generating.set(false) }
    }

    /** Newest admissible exchanges that fit, measured with the engine's own tokenizer. */
    internal fun window(history: List<ConversationHistory.Turn>, question: String, maxTokens: Int): Window {
        val eligible = history.filter { V1bPolicy.admissible(it.user) && V1bPolicy.admissible(it.assistant) }
        val system = V1bPolicy.systemFor(history, question).toByteArray(Charsets.UTF_8)
        for (keep in minOf(V1bPolicy.MAX_HISTORY_PAIRS, eligible.size) downTo 0) {
            val kept = eligible.takeLast(keep)
            val roles = ArrayList<ByteArray>(); val texts = ArrayList<ByteArray>()
            for (turn in kept) {
                roles += "user".toByteArray(); texts += turn.user.toByteArray(Charsets.UTF_8)
                roles += "assistant".toByteArray(); texts += turn.assistant.toByteArray(Charsets.UTF_8)
            }
            roles += "user".toByteArray(); texts += question.toByteArray(Charsets.UTF_8)
            val tokens = native.countTurnsTokens(handle, system, roles.toTypedArray(), texts.toTypedArray())
            if (tokens > 0 && tokens + maxTokens + V1bPolicy.CONTEXT_MARGIN <= loadedContext)
                return Window(roles.toTypedArray(), texts.toTypedArray(), keep, tokens, system)
        }
        throw IllegalStateException("Question trop longue pour le contexte : raccourcis-la")
    }
    internal class Window(val roles: Array<ByteArray>, val texts: Array<ByteArray>, val pairs: Int, val promptTokens: Int, val system: ByteArray)

    override suspend fun generateTurns(history: List<ConversationHistory.Turn>, question: String, maxTokens: Int, onText: (String) -> Unit): TurnResult {
        check(loaded && !destroyed) { "MODEL_NOT_LOADED" }
        return withContext(worker) {
            check(loaded) { "MODEL_NOT_LOADED" }
            val tokens = maxTokens.coerceIn(1, outputCeiling)
            native.begin(handle)
            val window = window(history, question, tokens)
            val sink = NativeStreamSink(question.toByteArray(Charsets.UTF_8), onText) { false }
            generating.set(true)
            val start = nanos()
            val terminal = try {
                native.generateStreamTurns(handle, window.system, window.roles, window.texts,
                    tokens, loadedContext, binding(question), sink)
            } finally { generating.set(false) }
            val end = nanos()
            when (terminal) {
                2 -> {
                    val first = if (sink.firstTokenNanos > 0) sink.firstTokenNanos else end
                    val decodeSeconds = (end - first) / 1e9
                    val rate = if (sink.sampledTokens > 1 && decodeSeconds > 0) (sink.sampledTokens - 1) / decodeSeconds else 0.0
                    TurnResult(sink.text.toString().trim(), TurnMetrics(tokens, sink.inputTokens.takeIf { it > 0 } ?: window.promptTokens,
                        sink.sampledTokens, window.pairs, (first - start) / 1_000_000, (end - start) / 1_000_000, Math.round(rate * 10) / 10.0))
                }
                3 -> throw IllegalStateException("Calcul natif arrêté")
                else -> throw IllegalStateException("STREAM_ERROR")
            }
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
    override suspend fun unload(): UnloadResult {
        if (!loaded || destroyed) return UnloadResult.CONFIRMED
        return withContext(worker) { unloadOnWorker() }
    }
    private fun unloadOnWorker(): UnloadResult {
        if (!loaded) return UnloadResult.CONFIRMED
        return try { native.unload(handle); loaded = false; UnloadResult.CONFIRMED } catch (e: Exception) { UnloadResult.FAILED }
    }

    /** End of session: unload, free the native handle and stop the worker thread (reload = new instance). */
    suspend fun destroy(): UnloadResult {
        if (destroyed) return UnloadResult.CONFIRMED
        val unloaded = unload()
        if (unloaded != UnloadResult.CONFIRMED) return unloaded
        withContext(worker) { if (created) native.destroy(handle); destroyed = true }
        executor.shutdown()
        return UnloadResult.CONFIRMED
    }

    companion object {
        fun contextFor(settings: JarvisSettings) = if (settings.contextSize >= V1bPolicy.CONTEXT) V1bPolicy.CONTEXT else 512
        /** 64-hex binding the protected stream echoes on every event (request identity, not a secret). */
        fun binding(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }.toByteArray(Charsets.US_ASCII)
    }
}
