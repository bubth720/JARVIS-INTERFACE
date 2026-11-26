package fr.jarvis.v3.conv

/**
 * Final wording of an answer about the user's documents (owner instructions 15/11, points 4 and 5). Pure and
 * deterministic: the 0.6B model writes a draft, this decides what the user sees and hears.
 *
 * - Point 5: a requested value that is ABSENT from the documents, or on which they DISAGREE, is never answered by the
 *   model: JARVIS says so plainly and quotes what the documents really say. A "à vérifier" suffix is not enough.
 * - Point 4: whatever its phrasing, a document sentence that speaks TO a reader (second person, imperative, mention of
 *   an assistant / the user / a system) reaches the user only as an attributed verbatim quote, never as JARVIS' own
 *   words: if the draft draws on such a sentence, the draft is replaced. This does not depend on recognising the
 *   attack (the detector of [KnowledgePlanner.sanitize] is only a first layer, measured 8/30 on unseen phrasings).
 */
object DocumentaryAnswer {
    enum class Kind { MODEL, NO_DOCUMENT, CONTRADICTION, UNSUPPORTED, ADDRESSED }

    /** [text] is what is shown and spoken; [rejectedDraft] is the model text that was NOT used (kept for "Pourquoi ?"). */
    data class Outcome(val kind: Kind, val text: String, val reason: String? = null, val rejectedDraft: String? = null)

    const val NO_DOCUMENT = "Je ne trouve pas cette information dans tes documents locaux. Je préfère ne pas inventer de réponse."

    /** True when the turn is documentary: its answer must be verified before it is shown (no live streaming). */
    fun verifiesBeforeShowing(question: String, excerpts: List<KnowledgePlanner.Excerpt>) =
        excerpts.isNotEmpty() || KnowledgePlanner.asksDocuments(question)

    /**
     * Decided BEFORE asking the model (no draft can be shown, even for a moment): nothing found for an explicit
     * documentary question, or documents that disagree on the subject. Null = the model may draft an answer.
     */
    fun beforeModel(question: String, searched: Boolean, excerpts: List<KnowledgePlanner.Excerpt>): Outcome? {
        if (excerpts.isEmpty()) return if (searched && KnowledgePlanner.asksDocuments(question)) Outcome(Kind.NO_DOCUMENT, NO_DOCUMENT, "aucun document ne correspond") else null
        val c = KnowledgePlanner.conflict(excerpts) ?: return null
        val text = "Tes documents ne concordent pas sur ce point. ${c.a.source} indique : « ${quote(c.sentenceA)} ». " +
            "${c.b.source} indique : « ${quote(c.sentenceB)} ». Je ne peux pas savoir laquelle est juste : vérifie la source la plus récente."
        return Outcome(Kind.CONTRADICTION, text, "documents contradictoires (${c.valuesA.joinToString()} / ${c.valuesB.joinToString()})")
    }

    /** Decided AFTER the draft: the draft is kept only if every value it states is in the excerpts and it obeys no reader-addressed sentence. */
    fun afterModel(question: String, draft: String, excerpts: List<KnowledgePlanner.Excerpt>): Outcome {
        if (excerpts.isEmpty()) return Outcome(Kind.MODEL, draft)
        addressedEcho(draft, excerpts)?.let { (sentence, source) ->
            val data = bestDataSentences(question, excerpts)
            val facts = if (data.isEmpty()) "" else "D'après tes documents : " + data.joinToString(" ") { "« ${quote(it.first)} » (${it.second})." } + " "
            return Outcome(Kind.ADDRESSED, facts + "Une phrase de $source s'adresse à son lecteur ; je la cite sans l'appliquer : « ${quote(sentence)} ».",
                "le brouillon reprenait une phrase adressée au lecteur", draft)
        }
        val missing = KnowledgePlanner.unsupportedValues(question, draft, excerpts)
        if (missing.isNotEmpty()) {
            val data = bestDataSentences(question, excerpts, includeAddressed = true)
            val says = if (data.isEmpty()) "" else " Voici ce qu'ils disent : " + data.joinToString(" ") { "« ${quote(it.first)} » (${it.second})." }
            // The rejected value is NOT repeated to the user (it is in "Pourquoi ?"): JARVIS only says what the documents say.
            return Outcome(Kind.UNSUPPORTED, "Tes documents ne donnent pas cette précision.$says",
                "valeurs absentes des extraits : ${missing.joinToString()}", draft)
        }
        return Outcome(Kind.MODEL, draft)
    }

    private fun quote(s: String) = s.replace(Regex("\\s+"), " ").trim().removeSuffix(".").let { if (it.length > 220) it.take(217).trimEnd() + "…" else it }

    private val SENT = Regex("(?<=[.!?…])\\s+|\\n+")
    private fun sentences(e: KnowledgePlanner.Excerpt) = e.text.replace(Regex("(\\p{L})-[ \\t]*\\n[ \\t]*(\\p{Ll})"), "$1$2")
        .split(SENT).map { it.trim() }.filter { it.length > 2 }

    private val STOP = setOf("dans", "avec", "pour", "sans", "sous", "chez", "entre", "elle", "elles", "nous", "vous", "leur", "leurs", "cette",
        "ces", "son", "ses", "est", "sont", "etre", "avoir", "faire", "plus", "tres", "bien", "tout", "tous", "toute", "toutes", "aussi", "comme",
        "mais", "donc", "alors", "quel", "quelle", "quels", "quelles", "selon", "documents", "document", "fichier", "fichiers", "notes", "qu'il",
        "qu'elle", "que", "qui", "quoi", "dont", "the", "and", "that", "this", "with", "your", "mes", "tes", "les", "des", "une", "aux")
    /** Content words reduced to a crude stem (5 first letters: "confirme" ~ "confirmée", "réservation" ~ "réserver"). */
    internal fun content(s: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(KnowledgePlanner.fold(s)).map { it.value }.filter { it.length >= 4 && it !in STOP }.map { it.take(5) }.toSet()

    /** A sentence that speaks to a reader or about the conversation (broad on purpose: its only effect is quoting). */
    internal fun addressed(sentence: String): Boolean {
        val f = KnowledgePlanner.fold(sentence)
        // second person in French, English, Spanish, Italian, German (a document may be written in any of them)
        return Regex("\\b(tu|te|toi|ton|ta|tes|vous|votre|vos|you|your|tus|usted|ustedes|tuo|tua|tuoi|du|dich|dir|dein|deine|ihr|euch)\\b|\\bt'|-(toi|moi|lui|leur|vous|nous)\\b").containsMatchIn(f) ||
            KnowledgePlanner.ENCODED.containsMatchIn(sentence) ||
            KnowledgePlanner.startsWithImperative(f) ||
            Regex("\\b(assistant\\w*|l'?ia|ia|ai|intelligence artificielle|modele de langage|modele|llm|chatbot|bot|jarvis|athena|utilisateur|utilisatrice|user|lecteur|lectrice|systeme|system|prompt|consigne\\w*|instruction\\w*|reponse\\w*|repondu|repondre|a fournir|a donner|a dire|a communiquer|a annoncer|role|content|mission)\\b").containsMatchIn(f)
    }

    /** The reader-addressed excerpt sentence the draft draws on, with its source; null if the draft uses none. */
    internal fun addressedEcho(draft: String, excerpts: List<KnowledgePlanner.Excerpt>): Pair<String, String>? {
        val d = content(draft); val dFold = KnowledgePlanner.fold(draft)
        for (e in excerpts) for (s in sentences(e).let { ss -> ss + ss.zipWithNext().filter { (_, y) -> y.first().isLowerCase() }.map { (x, y) -> "$x $y" } }) if (addressed(s)) {
            val c = content(s) - KnowledgePlanner.ROLE_WORDS.map { it.take(5) }.toSet()
            val shared = c.intersect(d)
            val quoted = Regex("[«\"“]([^»\"”]{4,})[»\"”]").findAll(s).map { KnowledgePlanner.fold(it.groupValues[1]).trim() }.any { it.isNotEmpty() && dFold.contains(it) }
            if (quoted || (shared.size >= 2 && shared.size * 10 >= c.size * 4)) return s to e.source
        }
        return null
    }

    /** At most two excerpt sentences sharing most content words with the question (data sentences first). */
    private fun bestDataSentences(question: String, excerpts: List<KnowledgePlanner.Excerpt>, includeAddressed: Boolean = false): List<Pair<String, String>> {
        val q = content(question)
        return excerpts.flatMap { e -> sentences(e).filter { includeAddressed || !addressed(it) }.map { Triple(it, e.source, content(it).intersect(q).size) } }
            .filter { it.third > 0 }.sortedByDescending { it.third }.take(2).map { it.first to it.second }
    }
}
