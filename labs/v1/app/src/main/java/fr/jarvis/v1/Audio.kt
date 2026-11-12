package fr.jarvis.v1

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.jarvis.voice.pocket.CancellationToken
import com.jarvis.voice.pocket.PocketEngine
import com.jarvis.voice.pocketprobe.PcmPlayer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Source of one spoken utterance as 16 kHz mono float PCM. */
interface SpeechCapture {
    /** Blocks until the utterance ends (voice-activity end, [stop] or max duration). */
    fun capture(onLevel: (Float) -> Unit, stop: () -> Boolean): FloatArray
}

/** Pure energy-based end-of-utterance detector (testable on the JVM). */
class EndpointDetector(private val sampleRate: Int = 16_000, private val threshold: Float = 0.012f,
                       private val silenceMs: Int = 1_000, private val maxMs: Int = 15_000, private val noSpeechMs: Int = 6_000) {
    private var total = 0; private var spoke = false; private var silent = 0
    /** Returns true when capture should stop after this block. */
    fun accept(block: FloatArray, n: Int): Boolean {
        var e = 0.0; for (i in 0 until n) e += block[i] * block[i]
        val rms = sqrt(e / maxOf(1, n)).toFloat()
        total += n
        if (rms >= threshold) { spoke = true; silent = 0 } else silent += n
        val ms = { s: Int -> s * 1000 / sampleRate }
        return ms(total) >= maxMs || (spoke && ms(silent) >= silenceMs) || (!spoke && ms(total) >= noSpeechMs)
    }
    val speechDetected get() = spoke
    companion object { fun rms(b: FloatArray, n: Int): Float { var e = 0.0; for (i in 0 until n) e += b[i] * b[i]; return sqrt(e / maxOf(1, n)).toFloat() } }
}

/** Real Android microphone (RECORD_AUDIO granted by the user), 16 kHz mono, voice recognition source. */
class MicCapture : SpeechCapture {
    @SuppressLint("MissingPermission") // checked by the caller before any capture
    override fun capture(onLevel: (Float) -> Unit, stop: () -> Boolean): FloatArray {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT, maxOf(min, rate / 2 * 4))
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone indisponible" }
        val out = ArrayList<FloatArray>(); val block = FloatArray(rate / 10); val endpoint = EndpointDetector(rate)
        try {
            rec.startRecording()
            while (!stop()) {
                val n = rec.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                check(n >= 0) { "Lecture micro impossible : $n" }
                if (n == 0) continue
                out += block.copyOf(n); onLevel((EndpointDetector.rms(block, n) * 8f).coerceIn(0f, 1f))
                if (endpoint.accept(block, n)) break
            }
        } finally { runCatching { rec.stop() }; rec.release() }
        val pcm = FloatArray(out.sumOf { it.size }); var o = 0
        for (b in out) { b.copyInto(pcm, o); o += b.size }
        return pcm
    }
}

/**
 * Pocket TTS on the speaker: the pinned VOICE engine (alba preset, 24 kHz) streams PCM into the probe's
 * PcmPlayer (AudioTrack). stop() cancels the ONNX run AND flushes playback immediately.
 */
class PocketSpeech(private val engine: PocketEngine) : AutoCloseable {
    private val speaking = AtomicBoolean(false)
    @Volatile private var token: CancellationToken? = null
    @Volatile private var player: PcmPlayer? = null
    data class Spoken(val firstPcmMs: Double, val audioSeconds: Double, val samplesWritten: Long, val interrupted: Boolean)

    fun speak(text: String, onLevel: (Float) -> Unit): Spoken {
        val t = CancellationToken(); val p = PcmPlayer(t)
        token = t; player = p; speaking.set(true)
        var written = 0L
        try {
            val metrics = engine.generate(clean(text), t) { pcm ->
                written += pcm.size; onLevel((EndpointDetector.rms(pcm, pcm.size) * 6f).coerceIn(0f, 1f)); p.write(pcm)
            }
            p.drain()
            return Spoken(metrics.firstPcmMs, metrics.audioSeconds, written, false)
        } catch (e: Exception) {
            // Interruption may surface as CancellationException (queue/player) or OrtException (terminated run).
            if (t.isCancelled()) return Spoken(-1.0, written / 24_000.0, written, true)
            throw e
        } finally { speaking.set(false); p.close(); token = null; player = null }
    }
    fun stop() { token?.cancel(); player?.stop() }
    val isSpeaking get() = speaking.get()
    override fun close() { stop(); engine.close() }

    companion object {
        /** Speakable French text: no emoji/markup, bounded length (the conversation keeps the full text). */
        fun clean(text: String): String = text.replace(Regex("[\\p{So}\\p{Cs}*_#`~>|]"), " ").replace(Regex("\\s+"), " ").replace(Regex(" ([.,])"), "$1").trim().take(600).ifBlank { "D'accord." }
    }
}
