package fr.jarvis.v3.conv

/**
 * Audit D2 (run 43627862972): 2 s of silence produced a transcript and a model answer. A capture with almost no
 * energy, or a transcript typical of whisper on silence, is "nothing heard" — never a turn.
 */
object SpeechGuard {
    /** RMS below this is silence for a 16 kHz float capture (speech is ~0.02–0.2; room noise ~0.001–0.004). */
    const val SILENCE_RMS = 0.005f
    fun isSilent(pcm: FloatArray): Boolean {
        if (pcm.isEmpty()) return true
        var e = 0.0; for (x in pcm) e += x * x
        return Math.sqrt(e / pcm.size) < SILENCE_RMS
    }
    private val HALLUCINATION = Regex("(?i)^\\W*(sous-titr|merci d'avoir regardé|merci de votre attention|abonnez-vous)")
    /** A lone noise word is no speech only when it is the WHOLE transcript ("Musique classique, tu aimes ?" is speech). */
    private val NOISE_WORD = Regex("(?i)^\\W*(musique|music|silence|rires?|applaudissements|bruit)\\W*$")
    /** Transcript without speech: only brackets/punctuation, or a known whisper silence hallucination. */
    fun isNoSpeech(transcript: String): Boolean {
        val t = transcript.trim()
        return t.none { it.isLetterOrDigit() } || Regex("^[\\[(].*[\\])]$").matches(t) || HALLUCINATION.containsMatchIn(t) || NOISE_WORD.matches(t)
    }
}
