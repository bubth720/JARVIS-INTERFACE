package fr.jarvis.v3.conv

/**
 * Short vocal reaction while the model is still thinking (CONVERSATION-01 §5: first vocal feedback < 5 s
 * without pretending the full answer is ready). Same rules as JARVIS-VOICE studioRuntime
 * QuickReactionTiming (92d0745): after 1.2 s of real waiting, at most once per 20 s, never once the final
 * audio is ready, never over another sound, never under thermal stress. Truthful phrases only.
 */
object QuickReaction {
    const val WAIT_MS = 1_200L
    const val COOLDOWN_MS = 20_000L

    /** Same phrases as VOICE QuickReactionPhrase, tutoiement version for the default persona. */
    val PHRASES_TU = listOf("Je réfléchis à ta demande.", "Je vérifie ça.", "Je prépare ma réponse.")
    val PHRASES_VOUS = listOf("Je réfléchis à votre demande.", "Je vérifie cela.", "Je prépare ma réponse.")

    fun shouldPlay(waitingMs: Long, lastPlayedAgoMs: Long?, voiceOutputEnabled: Boolean, answerTextStarted: Boolean,
                   finalAudioReady: Boolean, anotherAudioPlaying: Boolean, thermalSafe: Boolean): Boolean =
        voiceOutputEnabled && waitingMs >= WAIT_MS && (lastPlayedAgoMs == null || lastPlayedAgoMs >= COOLDOWN_MS) &&
            !answerTextStarted && !finalAudioReady && !anotherAudioPlaying && thermalSafe

    /** Rotates through the phrases so JARVIS never repeats the same reaction twice in a row. */
    fun phrase(index: Int, tutoiement: Boolean): String = (if (tutoiement) PHRASES_TU else PHRASES_VOUS).let { it[Math.floorMod(index, it.size)] }
}
