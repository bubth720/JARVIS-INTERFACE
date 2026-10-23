package com.stark.jarvislocal.events

import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Versioned internal metadata, not an IPC envelope or a source of permissions. No conversation text. */
sealed class CoreEvent(val kind: String) {
    data object UserInput : CoreEvent("USER_INPUT")
    data object Thinking : CoreEvent("THINKING")
    data class ResponseChunk(val index: Int) : CoreEvent("RESPONSE_CHUNK") { init { require(index >= 0) } }
    data object ResponseCompleted : CoreEvent("RESPONSE_COMPLETED")
    data object ToolPending : CoreEvent("TOOL_PENDING")
    data object SpeechRequested : CoreEvent("SPEECH_REQUESTED")
    data object CancelRequested : CoreEvent("CANCEL_REQUESTED")
    data object Cancelled : CoreEvent("CANCELLED")
    data class Error(val code: Code) : CoreEvent("ERROR")
    enum class Code { CORE_FAILURE, RESOURCE_UNCONFIRMED, MISSING_RESULT }
}
data class EventEnvelope(val sessionId: String, val requestId: String, val event: CoreEvent,
    val eventId: String = UUID.randomUUID().toString(), val version: Int = 1) {
    init {
        require(version == 1)
        listOf(sessionId, requestId, eventId).forEach { require(UUID.fromString(it).toString() == it) }
    }
}
data class ObservedEvent(val envelope: EventEnvelope, val sequence: Long, val elapsedMs: Long)
data class EventSnapshot(val enabled: Boolean = false, val generation: Long = 0,
    val events: List<ObservedEvent> = emptyList(), val dropped: Long = 0,
    internal val sequence: Long = 0, internal val seen: Set<String> = emptySet())

/** Bounded replay in RAM. Disabling revokes old traces and clears their metadata, never engine state. */
class CoreEventBus(private val elapsedMs: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val capacity: Int = 64, private val dedupCapacity: Int = 256) {
    private val mutableState = MutableStateFlow(EventSnapshot())
    val state = mutableState.asStateFlow()
    init { require(capacity in 1..1024 && dedupCapacity in capacity..4096) }
    fun setEnabled(enabled: Boolean) {
        mutableState.update { previous ->
            if (enabled == previous.enabled) previous
            else EventSnapshot(enabled, previous.generation + 1, sequence = previous.sequence)
        }
    }
    fun generationIfEnabled(): Long? = state.value.takeIf { it.enabled }?.generation
    fun publish(envelope: EventEnvelope, expectedGeneration: Long): Boolean {
        val timestamp = elapsedMs()
        require(timestamp >= 0)
        var published = false
        // No external monitor is held while StateFlow resumes consumers (which may cancel CORE).
        mutableState.update { snapshot ->
            published = false
            val last = snapshot.events.lastOrNull {
                it.envelope.sessionId == envelope.sessionId && it.envelope.requestId == envelope.requestId
            }?.envelope?.event
            val terminal = last is CoreEvent.ResponseCompleted || last is CoreEvent.Cancelled || last is CoreEvent.Error
            val stale = (terminal && envelope.event !is CoreEvent.Error) ||
                (last is CoreEvent.CancelRequested && envelope.event !is CoreEvent.Cancelled && envelope.event !is CoreEvent.Error)
            if (!snapshot.enabled || snapshot.generation != expectedGeneration || envelope.eventId in snapshot.seen || stale) snapshot
            else {
                published = true
                val seen = (snapshot.seen + envelope.eventId).toList().takeLast(dedupCapacity).toSet()
                val events = snapshot.events + ObservedEvent(envelope, snapshot.sequence + 1,
                    maxOf(timestamp, snapshot.events.lastOrNull()?.elapsedMs ?: 0))
                snapshot.copy(events = Collections.unmodifiableList(events.takeLast(capacity)),
                    dropped = snapshot.dropped + if (events.size > capacity) 1 else 0,
                    sequence = snapshot.sequence + 1, seen = seen)
            }
        }
        return published
    }
}
