package fr.jarvis.v1.stt

/** JNI surface of libjarvis_stt (whisper.cpp v1.8.4). Handles are opaque; one transcription at a time. */
object WhisperNative {
    @Volatile private var loaded = false
    fun ensureLoaded() { if (!loaded) synchronized(this) { if (!loaded) { System.loadLibrary("jarvis_stt"); loaded = true } } }
    @JvmStatic external fun load(path: String): Long
    @JvmStatic external fun transcribe(handle: Long, pcm16k: FloatArray, threads: Int): String
    @JvmStatic external fun cancel(handle: Long)
    @JvmStatic external fun free(handle: Long)
}

/**
 * Local French speech recognition. The model file must already be verified (SHA-256) by the host;
 * this class never downloads anything. close() waits for a running transcription to stop.
 */
class LocalSpeechRecognizer(modelPath: String, private val threads: Int = 2) : AutoCloseable {
    private val handle: Long
    init {
        WhisperNative.ensureLoaded()
        handle = WhisperNative.load(modelPath)
        check(handle != 0L) { "STT_MODEL_LOAD_REFUSED" }
    }
    @Volatile private var closed = false
    /** [pcm16k]: mono float PCM at 16 kHz in [-1, 1]. Returns the trimmed French transcript. */
    fun transcribe(pcm16k: FloatArray): String { check(!closed) { "STT_CLOSED" }; return WhisperNative.transcribe(handle, pcm16k, threads).trim() }
    fun cancel() { if (!closed) WhisperNative.cancel(handle) }
    override fun close() { if (!closed) { closed = true; WhisperNative.free(handle) } }

    companion object {
        /** whisper.cpp reference model, pinned (resolved read-only, see labs/v1/resources.json). */
        const val SAMPLE_RATE = 16_000
    }
}
