package fr.jarvis.m55d

/**
 * Versioned visual contract (M55-D → M55-E). The cockpit only CONSUMES these events; it never calls
 * llama.cpp, CORE or VOICE. A source is always declared: DEMO events exist only for visual development
 * and are labelled as such on screen; they never carry an answer text.
 */
const val COCKPIT_CONTRACT_VERSION = 1

enum class CockpitState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }
enum class EventSource { CORE, VOICE, DEMO }

data class CockpitEvent(
    val sessionId: String,
    val state: CockpitState,
    val source: EventSource,
    /** 0..1, voice/listening intensity; ignored for other states. */
    val audioLevel: Float = 0f,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val atMillis: Long,
) { init { require(audioLevel in 0f..1f) { "audioLevel" } } }

/** Holds the current visual state; with no event for [silenceMs] it falls back to a safe neutral IDLE. */
class CockpitStateHolder(private val silenceMs: Long = 10_000) {
    var current: CockpitEvent? = null; private set
    fun accept(event: CockpitEvent): Boolean {
        val last = current
        if (last != null && event.sessionId == last.sessionId && event.atMillis < last.atMillis) return false // stale
        current = event; return true
    }
    /** ERROR stays visible until a new event; any other state decays to IDLE after silence. */
    fun stateAt(nowMillis: Long): CockpitState {
        val e = current ?: return CockpitState.IDLE
        if (e.state == CockpitState.ERROR) return CockpitState.ERROR
        return if (nowMillis - e.atMillis > silenceMs) CockpitState.IDLE else e.state
    }
}
