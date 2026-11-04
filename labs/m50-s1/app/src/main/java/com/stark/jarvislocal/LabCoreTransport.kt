package com.stark.jarvislocal

import com.jarvis.interfaceapp.ui.chat.core.*
import com.stark.jarvislocal.events.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** In-process laboratory only. All mutable protocol state belongs to the UI dispatcher.
 * The worker emits fixed synthetic text; it never reads a document or calls a model. */
class LabCoreTransport(
    private val scope: CoroutineScope,
    worker: CoroutineDispatcher = Dispatchers.Default,
    private val scenario: Scenario = Scenario.NORMAL,
    private val chunkDelayMs: Long = 120,
    private val ackTimeoutMs: Long = 1_500
) : CoreConversationTransport {
    enum class Scenario { NORMAL, OWNER_ERROR, RESOURCE_UNKNOWN }
    private class Turn(val submissionId: String) {
        val key = CompletableDeferred<ContentKey>()
        var outcome: ContentBody? = null
        var sequence = 0L
    }
    private val ui = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    private val link = MutableStateFlow(CoreTextLink.DISABLED)
    override val origin = CoreTextOrigin.LABORATORY
    override val connection = link.asStateFlow()
    private val messages = MutableSharedFlow<CoreMessageEnvelope>(replay = 4, extraBufferCapacity = 16)
    override val updates = messages.asSharedFlow()
    private val stopped = AtomicBoolean()
    private val worked = AtomicBoolean()
    private var closed = false
    private var poisoned = false
    private var lastObserved = 0L
    private val submissions = linkedSetOf<String>()
    @Volatile private var active: Turn? = null
    private val bus = CoreEventBus()
    var acknowledgements: Int = 0
        private set
    val instanceId: String = UUID.randomUUID().toString()
    private val actions = object : SessionActions {
        override suspend fun prepare(progress: (String) -> Unit) { /* No model, no warmup. */ }
        override fun beforeTextChat() { stopped.set(false) }
        override fun requestInferenceStop() { stopped.set(true) }
        override fun inferenceReleaseUnconfirmed() = worked.get() && scenario == Scenario.RESOURCE_UNKNOWN
        override suspend fun chat(text: String): ChatMessage {
            val turn = checkNotNull(active)
            turn.key.await()
            worked.set(true)
            if (scenario == Scenario.OWNER_ERROR) error("LAB_OWNER_FAILURE")
            for (part in listOf("LAB/SYNTHETIC · liaison CORE réelle. ", "LAB/SYNTHETIC · aucun moteur LLM chargé.")) {
                delay(chunkDelayMs)
                if (stopped.get()) break
                withContext(ui) {
                    if (!closed && !poisoned && !stopped.get() && active === turn)
                        emit(turn, ContentBody.AssistantTextChunk(part))
                }
            }
            return ChatMessage(role = "assistant", text = "LAB/SYNTHETIC")
        }
        override suspend fun reportError(detail: String) { /* No log or persistence. */ }
        override suspend fun importPdf(uri: String): Unit = error("UNSUPPORTED")
        override suspend fun startRecording(): Unit = error("UNSUPPORTED")
        override suspend fun awaitRecording(): File = error("UNSUPPORTED")
        override suspend fun stopRecording(discard: Boolean): File? = null
        override suspend fun transcribe(file: File): String = error("UNSUPPORTED")
        override fun deleteRecording(file: File) = Unit
        override fun speakIfEnabled(text: String) = Unit
        override fun stopSpeech() = Unit
        override fun releaseSpeech() = Unit
        override fun initializeSpeechIfEnabled() = Unit
    }
    val coordinator = SessionCoordinator(actions, scope, worker,
        conversationEvents = ConversationEventPublisher(bus))
    private val observer: Job
    private val readiness: Job

    init {
        require(chunkDelayMs >= 0 && ackTimeoutMs > 0)
        bus.setEnabled(true)
        observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.state.collect { observe(it); finishIfDrained() }
        }
        readiness = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.state.collect { state ->
                // Read the complete bounded snapshot, even if StateFlow conflates notifications.
                observe(bus.state.value)
                finishIfDrained()
                if (!closed && !poisoned && state.prepared) link.value = CoreTextLink.READY
            }
        }
        coordinator.prepare()
    }

    private fun observe(snapshot: EventSnapshot) {
        if (closed || poisoned) return
        if (snapshot.dropped > 0 && snapshot.events.firstOrNull()?.sequence?.let { it > lastObserved + 1 } == true) {
            unknown(); return
        }
        for (observed in snapshot.events) {
            if (observed.sequence <= lastObserved) continue
            lastObserved = observed.sequence
            val turn = active ?: continue
            val envelope = observed.envelope
            if (envelope.event == CoreEvent.UserInput && !turn.key.isCompleted) {
                val key = ContentKey(envelope.sessionId, envelope.requestId, snapshot.generation)
                turn.key.complete(key)
                acknowledgements++
            }
            val key = turn.key.getCompletedOrNull() ?: continue
            if (key.sessionId != envelope.sessionId || key.requestId != envelope.requestId || key.generation != snapshot.generation) {
                unknown(); return
            }
            when (val event = envelope.event) {
                CoreEvent.ResponseCompleted -> turn.outcome = ContentBody.Completed
                CoreEvent.Cancelled -> turn.outcome = ContentBody.Cancelled
                is CoreEvent.Error -> turn.outcome = ContentBody.Failure(
                    if (event.code == CoreEvent.Code.RESOURCE_UNCONFIRMED) ContentFailure.RESOURCE_UNCONFIRMED
                    else ContentFailure.OWNER_FAILURE)
                else -> Unit
            }
        }
    }

    private fun finishIfDrained() {
        val turn = active ?: return
        if (coordinator.state.value.busy || !turn.key.isCompleted) return
        val outcome = turn.outcome ?: run { unknown(); return }
        if (!closed && !poisoned) {
            emit(turn, outcome)
            if (outcome is ContentBody.Failure && outcome.code == ContentFailure.RESOURCE_UNCONFIRMED) unknown()
        }
        active = null
    }

    private fun emit(turn: Turn, body: ContentBody) {
        val key = turn.key.getCompletedOrNull() ?: run { unknown(); return }
        if (!messages.tryEmit(CoreMessageEnvelope(UUID.randomUUID().toString(), key, ++turn.sequence, body))) unknown()
    }

    override suspend fun submit(value: SubmitUserText): ContentAck? {
        checkDispatcher()
        if (closed || poisoned || link.value != CoreTextLink.READY || coordinator.state.value.busy || active != null) return null
        if (submissions.size >= 128) { unknown(); return null }
        if (!submissions.add(value.submissionId)) return null
        val turn = Turn(value.submissionId)
        active = turn
        if (!coordinator.send(value.text)) { active = null; return null }
        return try { ContentAck(value.submissionId, withTimeout(ackTimeoutMs) { turn.key.await() }) }
        catch (e: TimeoutCancellationException) { unknown(); null }
        catch (e: CancellationException) { unknown(); throw e }
    }

    override suspend fun stop(key: ContentKey): Boolean {
        checkDispatcher()
        val turn = active ?: return false
        if (closed || poisoned || turn.key.getCompletedOrNull() != key) return false
        coordinator.cancelCurrent()
        return true // Logical request only; terminal delivery waits for the real coordinator.
    }

    private fun unknown() {
        if (closed || poisoned) return
        poisoned = true
        link.value = CoreTextLink.UNKNOWN
        coordinator.cancelCurrent()
    }

    override fun close() {
        checkDispatcher()
        if (closed) return
        closed = true
        coordinator.cancelCurrent()
        observer.cancel(); readiness.cancel(); bus.setEnabled(false)
        clearReplay()
        link.value = CoreTextLink.DISABLED
        // Do not cancel the owner scope: SessionCoordinator must drain NonCancellable work.
    }

    private fun checkDispatcher() { check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { "UI thread required" } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun clearReplay() { messages.resetReplayCache() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> CompletableDeferred<T>.getCompletedOrNull(): T? = if (isCompleted && !isCancelled) getCompleted() else null
}
