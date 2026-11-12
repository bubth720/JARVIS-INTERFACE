package fr.jarvis.v1

import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jarvis.interfaceapp.ui.chat.core.CoreConversationClientAdapter
import com.jarvis.interfaceapp.ui.chat.core.CoreTextLink
import com.jarvis.voice.pocket.PocketEngine
import com.stark.jarvislocal.UnloadResult
import com.stark.jarvislocal.v1b.A02LanguageModel
import com.stark.jarvislocal.v1b.V1bCoreTransport
import fr.jarvis.m55d.CockpitEvent
import fr.jarvis.m55d.CockpitState
import fr.jarvis.m55d.CockpitViewModel
import fr.jarvis.m55d.EventSource
import fr.jarvis.v1.stt.LocalSpeechRecognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** One spoken or typed exchange, with what was actually measured. */
data class Exchange(
    val user: String, val answer: String, val viaVoice: Boolean,
    val captureMs: Long = 0, val sttMs: Long = 0, val llmMs: Long = 0, val firstTextMs: Long = -1,
    val ttsFirstPcmMs: Double = -1.0, val ttsAudioSeconds: Double = 0.0, val ttsSamples: Long = 0,
    val interrupted: Boolean = false, val error: String? = null,
)

enum class Phase { LOADING, READY, LISTENING, TRANSCRIBING, THINKING, SPEAKING, ERROR, CLOSED }

/**
 * JARVIS V1 session: Microphone → STT (whisper.cpp) → CORE (SessionCoordinator via the V1-B transport)
 * → LLM (A02 sealed engine) → Pocket TTS → speaker. Every component is the qualified one, reused as is.
 * There is no synthetic answer anywhere: a failure is shown, never replaced by text.
 */
class V1Session(
    private val scope: CoroutineScope,
    val model: A02LanguageModel,
    private val sttModel: File,
    private val ttsDir: File,
    private val thermal: () -> Int,
    private val lowMemory: () -> Boolean,
) {
    val cockpit = CockpitViewModel(thermal)
    val transport = V1bCoreTransport(scope, model)
    val client = CoreConversationClientAdapter(transport, scope)
    val exchanges = mutableStateListOf<Exchange>()
    var phase by mutableStateOf(Phase.LOADING); private set
    var status by mutableStateOf("Chargement local : modèle de langage, reconnaissance vocale, voix…"); private set
    var partial by mutableStateOf(""); private set
    private val sessionId = "v1-" + SystemClock.elapsedRealtimeNanos()
    private val audioDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-mic") }.asCoroutineDispatcher()
    private val sttDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-stt") }.asCoroutineDispatcher()
    private val ttsDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-tts") }.asCoroutineDispatcher()
    private var stt: LocalSpeechRecognizer? = null
    private var tts: PocketSpeech? = null
    private val stopRequested = AtomicBoolean(false)
    private var turn: Job? = null
    var closed = false; private set

    private fun event(state: CockpitState, source: EventSource, level: Float = 0f, error: String? = null) =
        cockpit.accept(CockpitEvent(sessionId, state, source, level.coerceIn(0f, 1f), errorCode = error, atMillis = SystemClock.elapsedRealtime()))

    /** Loads STT and TTS off the main thread while CORE prepares the LLM (A02) in parallel. */
    fun start() {
        scope.launch {
            try {
                stt = withContext(sttDispatcher) { LocalSpeechRecognizer(sttModel.absolutePath, threads = 2) }
                tts = withContext(ttsDispatcher) { com.jarvis.voice.pocket.ModelPack.verify(ttsDir); PocketSpeech(PocketEngine(ttsDir, threads = 2)) }
                val deadline = SystemClock.elapsedRealtime() + 300_000
                while (client.state.value.link != CoreTextLink.READY && transport.lastError.value == null) {
                    check(SystemClock.elapsedRealtime() < deadline) { "Chargement du modèle de langage trop long" }
                    delay(100)
                }
                transport.lastError.value?.let { error(it) }
                phase = Phase.READY; status = "Prêt · hors ligne · touchez « Parler »"; event(CockpitState.IDLE, EventSource.CORE)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                phase = Phase.ERROR; status = "Échec du chargement : ${e.message}"; event(CockpitState.ERROR, EventSource.CORE, error = "LOAD")
            }
        }
    }

    /** Protections kept from the V1 engine policy: refuse a new turn under thermal or memory pressure. */
    private fun gate(): String? = when {
        closed -> "Session fermée"
        phase != Phase.READY -> "Pas prêt (${phase.name})"
        thermal() >= PowerManager.THERMAL_STATUS_SEVERE -> "Protection thermique : appareil trop chaud, réessayez plus tard"
        lowMemory() -> "Protection mémoire : mémoire système insuffisante"
        else -> null
    }

    /** Voice turn. Returns the job; [capture] is the microphone in the app (a file source only in tests). */
    fun voiceTurn(capture: SpeechCapture = MicCapture()): Job? {
        gate()?.let { status = it; return null }
        stopRequested.set(false)
        return scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            phase = Phase.LISTENING; status = "J'écoute…"; event(CockpitState.LISTENING, EventSource.VOICE)
            val pcm = try {
                withContext(audioDispatcher) { capture.capture({ lvl -> scope.launch { event(CockpitState.LISTENING, EventSource.VOICE, lvl) } }, { stopRequested.get() }) }
            } catch (e: Exception) { finish("Micro : ${e.message}", error = true); return@launch }
            val captureMs = SystemClock.elapsedRealtime() - t0
            if (stopRequested.get()) { finish("Écoute interrompue"); return@launch }
            if (pcm.size < 16_000 / 2) { finish("Je n'ai rien entendu"); return@launch }
            phase = Phase.TRANSCRIBING; status = "Reconnaissance vocale locale…"; event(CockpitState.THINKING, EventSource.VOICE)
            val s0 = SystemClock.elapsedRealtime()
            val text = try { withContext(sttDispatcher) { requireNotNull(stt).transcribe(pcm) } }
                catch (e: Exception) { finish(if (stopRequested.get()) "Interrompu" else "Reconnaissance : ${e.message}", error = !stopRequested.get()); return@launch }
            val sttMs = SystemClock.elapsedRealtime() - s0
            if (text.isBlank()) { finish("Je n'ai pas compris, pouvez-vous répéter ?"); return@launch }
            converse(text, viaVoice = true, captureMs = captureMs, sttMs = sttMs)
        }.also { turn = it }
    }

    /** Typed turn: same CORE → LLM → TTS path. */
    fun textTurn(text: String): Job? {
        gate()?.let { status = it; return null }
        if (text.isBlank()) return null
        stopRequested.set(false)
        return scope.launch { converse(text.trim(), viaVoice = false) }.also { turn = it }
    }

    private suspend fun converse(text: String, viaVoice: Boolean, captureMs: Long = 0, sttMs: Long = 0) {
        phase = Phase.THINKING; status = "Réflexion locale…"; partial = ""; event(CockpitState.THINKING, EventSource.CORE)
        val l0 = SystemClock.elapsedRealtime()
        if (!client.submit(text)) { finish("CORE a refusé la demande (occupé ou fermé)", error = true); return }
        var firstText = -1L
        val deadline = l0 + 300_000
        while (client.state.value.busy) {
            val s = client.state.value
            if (s.text.isNotEmpty()) { partial = s.text; if (firstText < 0) firstText = SystemClock.elapsedRealtime() - l0 }
            if (SystemClock.elapsedRealtime() > deadline) { client.cancel(); break }
            delay(40)
        }
        val llmMs = SystemClock.elapsedRealtime() - l0
        val answer = client.state.value.text.trim()
        val llmError = transport.lastError.value
        if (stopRequested.get() || answer.isEmpty() || llmError != null) {
            val interrupted = stopRequested.get()
            exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, interrupted = interrupted, error = llmError.takeIf { !interrupted })
            finish(if (interrupted) "Réponse interrompue" else "Erreur du modèle : ${llmError ?: "réponse vide"}", error = !interrupted); return
        }
        partial = answer
        phase = Phase.SPEAKING; status = "JARVIS parle… (touchez Interrompre)"; event(CockpitState.SPEAKING, EventSource.VOICE)
        val spoken = try {
            withContext(ttsDispatcher) { requireNotNull(tts).speak(answer) { lvl -> scope.launch { if (phase == Phase.SPEAKING) event(CockpitState.SPEAKING, EventSource.VOICE, lvl) } } }
        } catch (e: Exception) {
            exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, error = "TTS : ${e.message}")
            finish("Synthèse vocale : ${e.message}", error = true); return
        }
        exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, spoken.firstPcmMs, spoken.audioSeconds, spoken.samplesWritten, spoken.interrupted)
        finish(if (spoken.interrupted) "Parole interrompue · prêt" else "Prêt · hors ligne")
    }

    private fun finish(message: String, error: Boolean = false) {
        partial = if (error) partial else ""
        phase = if (closed) Phase.CLOSED else Phase.READY
        status = message
        event(if (error) CockpitState.ERROR else CockpitState.IDLE, EventSource.CORE, error = if (error) "TURN" else null)
    }

    /** Interrupts whatever runs: microphone, recognition, generation and speech. The session stays usable. */
    fun interrupt() {
        stopRequested.set(true)
        stt?.cancel(); tts?.stop()
        if (client.state.value.busy) client.cancel()
    }
    val busy get() = phase in setOf(Phase.LISTENING, Phase.TRANSCRIBING, Phase.THINKING, Phase.SPEAKING)

    /** Releases everything: speech engines, CORE, then the native LLM (unload + destroy) once CORE drained. */
    fun close(onReleased: (Boolean) -> Unit = {}) {
        if (closed) return
        closed = true; interrupt(); phase = Phase.CLOSED; status = "Session fermée · ressources libérées"
        scope.launch {
            turn?.join()
            client.close()
            withContext(sttDispatcher) { stt?.close() }; withContext(ttsDispatcher) { tts?.close() }
            while (transport.coordinator.state.value.busy) delay(50)
            val ok = transport.releaseEngine() == UnloadResult.CONFIRMED && model.destroy() == UnloadResult.CONFIRMED
            audioDispatcher.close(); sttDispatcher.close(); ttsDispatcher.close()
            onReleased(ok)
        }
    }
}
