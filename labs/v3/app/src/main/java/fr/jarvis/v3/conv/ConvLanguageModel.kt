package fr.jarvis.v3.conv

import android.os.SystemClock
import com.stark.jarvislocal.EngineCompletion
import com.stark.jarvislocal.InferenceCapabilities
import com.stark.jarvislocal.JarvisSettings
import com.stark.jarvislocal.StopResult
import com.stark.jarvislocal.UnloadResult
import com.stark.jarvislocal.v1b.ConversationHistory
import com.stark.jarvislocal.v1b.ConversationalLanguageModel
import com.stark.jarvislocal.v1b.TurnMetrics
import com.stark.jarvislocal.v1b.TurnResult
import com.stark.jarvislocal.v1b.V1bPolicy
import com.stark.jarvislocal.v1b.VerifiedModel
import fr.jarvis.llm.a02.A02Native
import fr.jarvis.llm.a02.NativeStreamSink
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * CONVERSATION-01 derivative of V1-B [com.stark.jarvislocal.v1b.A02LanguageModel] (that file is unchanged and
 * still used by V1). ONE difference: the subordinate instruction of each turn comes from [instruction]
 * (persona + working memory + confirmed memories) instead of the fixed V1bPolicy routing. Everything the
 * protected engine enforces is identical: sealed load, context 768, one thread, ceiling <= 256, at most
 * 3 previous exchanges, exact token budget, cancellation. Default [instruction] = V1bPolicy (V1 behaviour).
 *
 * Original description: CORE LocalLanguageModel implemented over the JARVIS-LLM engine through A02 only.
 * One native worker thread serializes load/generate/unload (the engine pins handles);
 * cancel() is the only cross-thread call, exactly as in the protected engine contract.
 */
class ConvLanguageModel(
    private val native: A02Native,
    private val model: VerifiedModel,
    private val memoryBudgetBytes: () -> Long,
    override val outputCeiling: Int = V1bPolicy.DEFAULT_OUTPUT,
    private val nanos: () -> Long = { SystemClock.elapsedRealtimeNanos() },
    private val instruction: (List<ConversationHistory.Turn>, String) -> String = V1bPolicy::systemFor,
    /** Protected engine profile: true = Qwen3 0.6B (production); false = the engine's own 1.7B profile (study test APK only). */
    private val small: Boolean = true,
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
    /** Instruction actually sent on the last turn (local diagnostics and tests; never logged or exported). */
    @Volatile var lastInstruction: String? = null
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
                memoryBudgetBytes(), context, V1bPolicy.THREADS, small), Charsets.UTF_8)
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
        val system = instruction(history, question).also { lastInstruction = it }.toByteArray(Charsets.UTF_8)
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
