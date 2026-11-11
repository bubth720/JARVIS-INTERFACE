package fr.jarvis.llm.a02

/**
 * Kotlin face of libjarvis_llm_a02 (bubth720/JARVIS-LLM a02/native, pinned by SHA in sources.json).
 * There is deliberately no pathname loader: weights can only enter through loadSealedFd.
 */
interface A02Native {
    fun create(): Long
    fun begin(handle: Long)
    fun cancel(handle: Long)
    fun loadSealedFd(handle: Long, sourceFd: Int, size: Long, sha256: ByteArray, budget: Long, context: Int, threads: Int, small: Boolean): ByteArray
    fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray
    /** Protected engine token stream; returns the terminal kind (2 completed, 3 cancelled, 4 error). */
    fun generateStream(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int, binding: ByteArray, callback: NativeStreamSink): Int
    fun unload(handle: Long)
    fun destroy(handle: Long)
}

/**
 * Exact JNI shape required by the protected SynchronousStream. Called synchronously on the native
 * worker thread; it never touches UI and only forwards bounded UTF-8 pieces to [onText].
 * Return codes: 0 accepted, 2 cancel requested.
 */
class NativeStreamSink(private val expectedQuestion: ByteArray, private val onText: (String) -> Unit, private val cancelled: () -> Boolean) {
    @Volatile var terminal = -1; private set
    @Volatile var chunks = 0; private set
    @Volatile var acknowledged = false; private set
    @Volatile var firstTokenNanos = -1L; private set
    val text = StringBuilder()

    /** The rendered prompt must contain this request's question bytes before any prefill. */
    fun onNativePrepared(rendered: ByteArray, template: ByteArray, inputTokens: Int): Boolean =
        inputTokens > 0 && template.isNotEmpty() && String(rendered, Charsets.UTF_8).contains(String(expectedQuestion, Charsets.UTF_8))

    fun onNativeEvent(binding: ByteArray, kind: Int, sequence: Int, bytes: ByteArray, sampledTokens: Int, nativeFirstTokenNanos: Long, nativeClockDomain: Int): Int {
        when (kind) {
            0 -> acknowledged = true
            1 -> {
                if (cancelled()) return 2
                val piece = String(bytes, Charsets.UTF_8)
                chunks++; text.append(piece); firstTokenNanos = nativeFirstTokenNanos
                onText(piece)
            }
            else -> terminal = kind
        }
        return 0
    }
}

class SealedNativeBridge private constructor() : A02Native {
    external override fun create(): Long
    external override fun begin(handle: Long)
    external override fun cancel(handle: Long)
    external override fun loadSealedFd(handle: Long, sourceFd: Int, size: Long, sha256: ByteArray, budget: Long, context: Int, threads: Int, small: Boolean): ByteArray
    external override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray
    external override fun generateStream(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int, binding: ByteArray, callback: NativeStreamSink): Int
    external override fun unload(handle: Long)
    external override fun destroy(handle: Long)

    companion object {
        @Volatile private var instance: SealedNativeBridge? = null
        /** Loads the native engine once per process. */
        fun get(): SealedNativeBridge = instance ?: synchronized(this) {
            instance ?: SealedNativeBridge().also { System.loadLibrary("jarvis_llm_a02"); instance = it }
        }
    }
}
