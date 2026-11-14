package fr.jarvis.v3.model

/**
 * Visual states of the V3 cockpit (§03 / §04). They are DERIVED from observable engine states; the
 * graphics never command the engine (§08 separation rule).
 */
enum class HoloState(val label: String) {
    IDLE("Repos"), LISTENING("Écoute"), TRANSCRIBING("Transcription"), THINKING("Réflexion"),
    STREAMING("Réponse"), SPEAKING("Parole"), RESPONDING("Réponse"), ERROR("Erreur")
}

object SphereStateAdapter {
    /** The amber error accent is temporary, then the scene returns to a stable state (§04 Erreur). */
    const val ERROR_ACCENT_MS = 4_000L
    /** A written answer without voice still "plays" briefly, then the sphere stops (V3-FIX-01). */
    const val REVEAL_MS = 2_500L
    /** While the model is still writing, the animation is capped low so it never competes with the LLM. */
    const val STREAMING_FPS = 12

    /**
     * V3-FIX-01 rule: the sphere is STILL at rest, while listening, transcribing and thinking; it animates only
     * while JARVIS delivers its answer (text streaming, speech, short reveal of a written answer), then stops.
     */
    fun animates(state: HoloState): Boolean = state == HoloState.STREAMING || state == HoloState.SPEAKING || state == HoloState.RESPONDING
    fun fpsCap(state: HoloState, profileFps: Int): Int = if (state == HoloState.STREAMING) minOf(STREAMING_FPS, profileFps) else profileFps

    /**
     * @param phase name of the V1 session phase (LOADING, READY, LISTENING, TRANSCRIBING, THINKING, SPEAKING, ERROR, CLOSED), null without session
     * @param streaming true when real answer text has already arrived for the running turn
     * @param errorAgeMs age of the last error event, or null when the last event was not an error
     */
    fun resolve(phase: String?, streaming: Boolean, errorAgeMs: Long?, answeredAgoMs: Long? = null): HoloState = when (phase) {
        "LISTENING" -> HoloState.LISTENING
        "TRANSCRIBING" -> HoloState.TRANSCRIBING
        "THINKING" -> if (streaming) HoloState.STREAMING else HoloState.THINKING
        "LOADING" -> HoloState.THINKING
        "SPEAKING" -> HoloState.SPEAKING
        "ERROR" -> HoloState.ERROR
        else -> when {
            errorAgeMs != null && errorAgeMs < ERROR_ACCENT_MS -> HoloState.ERROR
            answeredAgoMs != null && answeredAgoMs in 0 until REVEAL_MS -> HoloState.RESPONDING
            else -> HoloState.IDLE
        }
    }

    /** Short status line for the state chip; never a fake "thought" or an invented percentage. */
    fun status(state: HoloState): String = when (state) {
        HoloState.IDLE -> "Prêt"
        HoloState.LISTENING -> "Écoute…"
        HoloState.TRANSCRIBING -> "Transcription locale…"
        HoloState.THINKING -> "Réflexion locale…"
        HoloState.STREAMING -> "Réponse en cours…"
        HoloState.SPEAKING -> "JARVIS parle"
        HoloState.RESPONDING -> "Réponse"
        HoloState.ERROR -> "Erreur"
    }
}
