package fr.jarvis.v3.conv

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
import com.stark.jarvislocal.v1b.ConversationHistory
import com.stark.jarvislocal.v1b.V1bCoreTransport
import com.stark.jarvislocal.v1b.V1bPolicy
import fr.jarvis.m55d.CockpitEvent
import fr.jarvis.m55d.CockpitState
import fr.jarvis.m55d.CockpitViewModel
import fr.jarvis.m55d.EventSource
import fr.jarvis.v1.Exchange
import fr.jarvis.v1.MicCapture
import fr.jarvis.v1.Phase
import fr.jarvis.v1.PocketSpeech
import fr.jarvis.v1.SpeechCapture
import fr.jarvis.v1.stt.LocalSpeechRecognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * CONVERSATION-01 session, derived from [fr.jarvis.v1.V1Session] (unchanged, still used by V1). Same chain
 * and same protections: Microphone → whisper.cpp → CORE SessionCoordinator (V1-B transport, unchanged) →
 * A02 sealed engine → Pocket TTS. No synthetic answer anywhere. Added:
 *  - persona + working memory instruction ([ConversationContext]) through [ConvLanguageModel];
 *  - persistent local journal: each finished exchange is appended; [resume] restores the visible
 *    conversation AND the engine context (last exchanges + stated facts) after the app was closed;
 *  - short truthful vocal reaction while the model is still thinking ([QuickReaction] rules);
 *  - per-turn measurements kept for the owner (no prompt content).
 */
class ConvSession(
    private val scope: CoroutineScope,
    val model: ConvLanguageModel,
    val context: ConversationContext,
    private val journal: ConversationJournal?,
    private val sttModel: File?,
    private val ttsDir: File?,
    private val thermal: () -> Int,
    private val lowMemory: () -> Boolean,
    resume: ConversationJournal.Conversation? = null,
    /** Local documents (NEXUS); null = not available in this build. */
    val knowledge: LocalKnowledge? = null,
    /** Disabled in the instrumented tests that measure the raw answer path. */
    var quickReactionEnabled: Boolean = true,
) {
    /** Owner setting: spoken answers on/off (off = written answers only; the microphone stays usable). */
    var voiceOutputEnabled by mutableStateOf(true)
    val cockpit = CockpitViewModel(thermal)
    private val history = ConversationHistory()
    val transport = V1bCoreTransport(scope, model, history = history)
    val client = CoreConversationClientAdapter(transport, scope)
    val exchanges = mutableStateListOf<Exchange>()
    /** Cited local sources per exchange index (provenance shown under the answer). */
    val sources = androidx.compose.runtime.mutableStateMapOf<Int, List<String>>()
    /** System notices per exchange index (ActionGuard: an answer claimed an action that was not executed). */
    val notices = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    /** Last documentary search: searched?, duration, sources (diagnostics shown on the NEXUS screen). */
    var lastGrounding by mutableStateOf<LocalKnowledge.Grounding?>(null); private set
    /** Exchanges restored from the journal (shown first, marked as earlier). */
    var restoredCount by mutableStateOf(0); private set
    val conversationId: String = resume?.id ?: journal?.newConversationId() ?: "local"
    var phase by mutableStateOf(Phase.LOADING); private set
    var status by mutableStateOf("Chargement local : modèle de langage, reconnaissance vocale, voix…"); private set
    var partial by mutableStateOf(""); private set
    /** Text of the vocal reaction played during the current wait, if any (shown as such, never as the answer). */
    var reaction by mutableStateOf<String?>(null); private set
    /** Measured time between the end of the user's request and JARVIS' first audible sound (reaction or answer). */
    var lastFirstSoundMs by mutableStateOf(-1L); private set
    private val sessionId = "conv-" + SystemClock.elapsedRealtimeNanos()
    private val audioDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-mic") }.asCoroutineDispatcher()
    private val sttDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-stt") }.asCoroutineDispatcher()
    private val ttsDispatcher = Executors.newSingleThreadExecutor { Thread(it, "jarvis-tts") }.asCoroutineDispatcher()
    private var stt: LocalSpeechRecognizer? = null
    private var tts: PocketSpeech? = null
    var sttUnavailable by mutableStateOf<String?>(null); private set
    var ttsUnavailable by mutableStateOf<String?>(null); private set
    val voiceInputAvailable get() = stt != null
    private val stopRequested = AtomicBoolean(false)
    private var turn: Job? = null
    private var reactionIndex = 0
    private var lastReactionAt: Long? = null
    var closed = false; private set

    init {
        resume?.entries?.forEach { e ->
            if (e.sources.isNotEmpty()) sources[exchanges.size] = e.sources
            exchanges += Exchange(e.user, e.answer, e.viaVoice)
            // Only exchanges the protected engine would accept go back into its context.
            if (e.answer.isNotBlank() && V1bPolicy.admissible(e.user) && V1bPolicy.admissible(e.answer)) history.record(e.user, e.answer)
        }
        restoredCount = exchanges.size
    }

    private fun event(state: CockpitState, source: EventSource, level: Float = 0f, error: String? = null) =
        cockpit.accept(CockpitEvent(sessionId, state, source, level.coerceIn(0f, 1f), errorCode = error, atMillis = SystemClock.elapsedRealtime()))

    fun start() {
        scope.launch {
            try {
                if (sttModel == null) sttUnavailable = "Reconnaissance vocale non installée"
                else try { stt = withContext(sttDispatcher) { LocalSpeechRecognizer(sttModel.absolutePath, threads = 2) } }
                    catch (e: Throwable) { if (e is CancellationException) throw e; sttUnavailable = "Reconnaissance vocale indisponible : ${e.message}" }
                if (ttsDir == null) ttsUnavailable = "Voix Pocket non installée"
                else try { tts = withContext(ttsDispatcher) { com.jarvis.voice.pocket.ModelPack.verify(ttsDir); PocketSpeech(PocketEngine(ttsDir, threads = 2)) } }
                    catch (e: Throwable) { if (e is CancellationException) throw e; ttsUnavailable = "Voix Pocket indisponible : ${e.message}" }
                val deadline = SystemClock.elapsedRealtime() + 300_000
                while (client.state.value.link != CoreTextLink.READY && transport.lastError.value == null) {
                    check(SystemClock.elapsedRealtime() < deadline) { "Chargement du modèle de langage trop long" }
                    delay(100)
                }
                transport.lastError.value?.let { error(it) }
                phase = Phase.READY
                status = (if (restoredCount > 0) "Conversation reprise · " else "") +
                    (if (stt != null) "Prêt · hors ligne · touche « Parler » ou écris" else "Prêt · hors ligne · écris ta question")
                event(CockpitState.IDLE, EventSource.CORE)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                phase = Phase.ERROR; status = "Échec du chargement : ${e.message}"; event(CockpitState.ERROR, EventSource.CORE, error = "LOAD")
            }
        }
    }

    private fun gate(): String? = when {
        closed -> "Session fermée"
        phase != Phase.READY -> "Pas prêt (${phase.name})"
        thermal() >= PowerManager.THERMAL_STATUS_SEVERE -> "Protection thermique : appareil trop chaud, réessaie plus tard"
        lowMemory() -> "Protection mémoire : mémoire système insuffisante"
        else -> null
    }

    fun voiceTurn(capture: SpeechCapture = MicCapture()): Job? {
        gate()?.let { status = it; return null }
        if (stt == null) { status = sttUnavailable ?: "Reconnaissance vocale indisponible"; return null }
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
            val heardAt = SystemClock.elapsedRealtime()
            val text = try { withContext(sttDispatcher) { requireNotNull(stt).transcribe(pcm) } }
                catch (e: Exception) { finish(if (stopRequested.get()) "Interrompu" else "Reconnaissance : ${e.message}", error = !stopRequested.get()); return@launch }
            val sttMs = SystemClock.elapsedRealtime() - heardAt
            if (text.isBlank()) { finish("Je n'ai pas compris, tu peux répéter ?"); return@launch }
            converse(text, viaVoice = true, captureMs = captureMs, sttMs = sttMs, requestEndAt = heardAt)
        }.also { turn = it }
    }

    fun textTurn(text: String): Job? {
        gate()?.let { status = it; return null }
        if (text.isBlank()) return null
        stopRequested.set(false)
        return scope.launch { converse(text.trim(), viaVoice = false, requestEndAt = SystemClock.elapsedRealtime()) }.also { turn = it }
    }

    /**
     * Plays one truthful waiting phrase if the answer is slow (voice turns only, VOICE rules): never once
     * answer text exists, never over another sound, never under thermal stress. Returns its job, or null.
     */
    private fun scheduleReaction(viaVoice: Boolean, requestEndAt: Long, answerStarted: () -> Boolean): Job? {
        val speech = tts?.takeIf { voiceOutputEnabled } ?: return null
        if (!viaVoice || !quickReactionEnabled) return null
        return scope.launch {
            delay(QuickReaction.WAIT_MS)
            val now = SystemClock.elapsedRealtime()
            val play = QuickReaction.shouldPlay(now - requestEndAt, lastReactionAt?.let { now - it }, voiceOutputEnabled = true,
                answerTextStarted = answerStarted(), finalAudioReady = false, anotherAudioPlaying = speech.isSpeaking,
                thermalSafe = thermal() < PowerManager.THERMAL_STATUS_MODERATE)
            if (!play || phase != Phase.THINKING) return@launch
            val phrase = QuickReaction.phrase(reactionIndex++, context.persona.tutoiement)
            lastReactionAt = now; reaction = phrase
            if (lastFirstSoundMs < 0) lastFirstSoundMs = now - requestEndAt
            runCatching { withContext(ttsDispatcher) { speech.speak(phrase) { } } }
        }
    }

    private suspend fun converse(text: String, viaVoice: Boolean, captureMs: Long = 0, sttMs: Long = 0, requestEndAt: Long) {
        phase = Phase.THINKING; status = "Réflexion locale…"; partial = ""; reaction = null; lastFirstSoundMs = -1
        event(CockpitState.THINKING, EventSource.CORE)
        // Local documents first (bounded, off the main thread): cited excerpts or an explicit "nothing found".
        val grounding = knowledge?.let { k -> runCatching { withContext(Dispatchers.IO) { k.ground(text) } }.getOrNull() }
        lastGrounding = grounding
        context.groundNext(text, grounding?.block.orEmpty())
        val cited = grounding?.sources.orEmpty()
        val l0 = SystemClock.elapsedRealtime()
        if (!client.submit(text)) { finish("CORE a refusé la demande (occupé ou fermé)", error = true); return }
        val waiting = scheduleReaction(viaVoice, requestEndAt) { client.state.value.text.isNotEmpty() }
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
        // A reaction that has not started yet is dropped; one already speaking is cut so the answer comes at once.
        waiting?.cancel(); if (tts?.isSpeaking == true) tts?.stop(); waiting?.join()
        if (stopRequested.get() || answer.isEmpty() || llmError != null) {
            val interrupted = stopRequested.get()
            exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, interrupted = interrupted, error = llmError.takeIf { !interrupted })
            finish(if (interrupted) "Réponse interrompue" else "Erreur du modèle : ${llmError ?: "réponse vide"}", error = !interrupted); return
        }
        partial = answer
        record(text, answer, viaVoice, cited)
        if (cited.isNotEmpty()) sources[exchanges.size] = cited
        val notice = if (ActionGuard.claimsDone(text, answer)) ActionGuard.NOTICE else null
        if (notice != null) notices[exchanges.size] = notice
        val speech = tts?.takeIf { voiceOutputEnabled }
        if (speech == null) {
            exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText)
            finish("Prêt · hors ligne (réponse écrite : ${if (!voiceOutputEnabled) "voix désactivée" else ttsUnavailable ?: "voix indisponible"})"); return
        }
        phase = Phase.SPEAKING; status = "JARVIS parle… (touche Interrompre)"; event(CockpitState.SPEAKING, EventSource.VOICE)
        val speakAt = SystemClock.elapsedRealtime()
        val spoken = try {
            withContext(ttsDispatcher) { speech.speak(if (notice != null) "$answer Note du système : $notice" else answer) { lvl -> scope.launch { if (phase == Phase.SPEAKING) event(CockpitState.SPEAKING, EventSource.VOICE, lvl) } } }
        } catch (e: Exception) {
            exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, error = "TTS : ${e.message}")
            finish("Synthèse vocale : ${e.message}", error = true); return
        }
        if (lastFirstSoundMs < 0 && spoken.firstPcmMs >= 0) lastFirstSoundMs = speakAt - requestEndAt + spoken.firstPcmMs.toLong()
        exchanges += Exchange(text, answer, viaVoice, captureMs, sttMs, llmMs, firstText, spoken.firstPcmMs, spoken.audioSeconds, spoken.samplesWritten, spoken.interrupted)
        finish(if (spoken.interrupted) "Parole interrompue · prêt" else "Prêt · hors ligne")
    }

    /** Appends the finished exchange to the local journal (the text the user saw; no audio, no instruction). */
    private suspend fun record(user: String, answer: String, viaVoice: Boolean, cited: List<String>) {
        val j = journal ?: return
        runCatching { withContext(Dispatchers.IO) { j.append(conversationId, ConversationJournal.Entry(user, answer, viaVoice, System.currentTimeMillis(), cited)) } }
            .onFailure { status = "Historique local non enregistré : ${it.message}" }
    }

    private fun finish(message: String, error: Boolean = false) {
        partial = if (error) partial else ""
        reaction = null
        phase = if (closed) Phase.CLOSED else Phase.READY
        status = message
        event(if (error) CockpitState.ERROR else CockpitState.IDLE, EventSource.CORE, error = if (error) "TURN" else null)
    }

    fun interrupt() {
        stopRequested.set(true)
        stt?.cancel(); tts?.stop()
        if (client.state.value.busy) client.cancel()
    }
    val busy get() = phase in setOf(Phase.LISTENING, Phase.TRANSCRIBING, Phase.THINKING, Phase.SPEAKING)

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
