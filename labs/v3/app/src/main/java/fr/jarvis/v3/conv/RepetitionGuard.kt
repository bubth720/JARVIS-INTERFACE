package fr.jarvis.v3.conv

/**
 * A-022 / model limit M-06 (long conversation run 43620844372): the 0.6B sometimes repeats its previous answer word
 * for word to a different question. Detected deterministically; the answer gets a system notice (never rewritten).
 */
object RepetitionGuard {
    const val NOTICE = "Cette réponse reprend presque mot pour mot la précédente : elle ne répond probablement pas à ta question. Reformule-la."
    private fun words(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()
    private fun jaccard(a: Set<String>, b: Set<String>) = if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size

    /** True when [answer] (to [question]) nearly repeats [previousAnswer] although the question is a different one. */
    fun repeats(previousQuestion: String, previousAnswer: String, question: String, answer: String): Boolean {
        val aw = words(answer); if (aw.size < 4) return false
        return jaccard(aw, words(previousAnswer)) >= 0.8 && jaccard(words(question), words(previousQuestion)) < 0.6
    }
}
