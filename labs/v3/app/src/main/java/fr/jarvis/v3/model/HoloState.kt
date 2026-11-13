package fr.jarvis.v3.model

/**
 * Visual states of the V3 cockpit (§03 / §04). They are DERIVED from observable engine states; the
 * graphics never command the engine (§08 separation rule).
 */
enum class HoloState(val label: String) {
    IDLE("Repos"), LISTENING("Écoute"), TRANSCRIBING("Transcription"), THINKING("Réflexion"),
    STREAMING("Réponse"), SPEAKING("Parole"), ERROR("Erreur")
}

object SphereStateAdapter {
    /** The amber error accent is temporary, then the scene returns to a stable state (§04 Erreur). */
    const val ERROR_ACCENT_MS = 4_000L

    /**
     * @param phase name of the V1 session phase (LOADING, READY, LISTENING, TRANSCRIBING, THINKING, SPEAKING, ERROR, CLOSED), null without session
     * @param streaming true when real answer text has already arrived for the running turn
     * @param errorAgeMs age of the last error event, or null when the last event was not an error
     */
    fun resolve(phase: String?, streaming: Boolean, errorAgeMs: Long?): HoloState = when (phase) {
        "LISTENING" -> HoloState.LISTENING
        "TRANSCRIBING" -> HoloState.TRANSCRIBING
        "THINKING" -> if (streaming) HoloState.STREAMING else HoloState.THINKING
        "LOADING" -> HoloState.THINKING
        "SPEAKING" -> HoloState.SPEAKING
        "ERROR" -> HoloState.ERROR
        else -> if (errorAgeMs != null && errorAgeMs < ERROR_ACCENT_MS) HoloState.ERROR else HoloState.IDLE
    }

    /** Short status line for the state chip; never a fake "thought" or an invented percentage. */
    fun status(state: HoloState): String = when (state) {
        HoloState.IDLE -> "Prêt"
        HoloState.LISTENING -> "Écoute…"
        HoloState.TRANSCRIBING -> "Transcription locale…"
        HoloState.THINKING -> "Réflexion locale…"
        HoloState.STREAMING -> "Réponse en cours…"
        HoloState.SPEAKING -> "JARVIS parle"
        HoloState.ERROR -> "Erreur"
    }
}
