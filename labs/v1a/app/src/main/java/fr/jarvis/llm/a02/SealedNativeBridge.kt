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
    fun unload(handle: Long)
    fun destroy(handle: Long)
}

class SealedNativeBridge private constructor() : A02Native {
    external override fun create(): Long
    external override fun begin(handle: Long)
    external override fun cancel(handle: Long)
    external override fun loadSealedFd(handle: Long, sourceFd: Int, size: Long, sha256: ByteArray, budget: Long, context: Int, threads: Int, small: Boolean): ByteArray
    external override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray
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
