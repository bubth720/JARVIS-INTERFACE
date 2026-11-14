package fr.jarvis.v3.model

/** Avatar expression for a visual state (also exposed to accessibility); "parle" exactly when the sphere may animate. */
object AvatarExpression {
    fun of(state: HoloState): String = when (state) {
        HoloState.LISTENING -> "attentive"
        HoloState.THINKING, HoloState.TRANSCRIBING -> "pensive"
        HoloState.STREAMING, HoloState.SPEAKING, HoloState.RESPONDING -> "parle"
        HoloState.ERROR -> "préoccupée"
        else -> "sereine"
    }
}
