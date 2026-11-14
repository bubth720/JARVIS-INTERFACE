package fr.jarvis.v3.conv

import java.text.Normalizer

/**
 * Working memory of the CURRENT conversation (CONVERSATION-01 §4): facts the user stated about himself
 * and his world, with his corrections applied ("non, c'est mercredi"). The protected engine only accepts
 * 3 previous exchanges (8 ChatML entries); these facts carry the older context forward as a short, quoted
 * block of the instruction. Deterministic rules only: nothing is inferred, nothing is stored durably here
 * (durable memories go through the CORE consent workflow).
 */
class WorkingMemory(private val maxFacts: Int = 8) {
    enum class Kind { NAME, AGE, RELATIVE, LIKE, APPOINTMENT, PLACE }
    data class Fact(val kind: Kind, val key: String, val value: String, val turn: Int)

    private val facts = LinkedHashMap<String, Fact>()
    private var turn = 0

    fun facts(): List<Fact> = facts.values.sortedBy { it.turn }

    /** Reads one user message; later statements and explicit corrections replace earlier ones. */
    fun observe(userText: String) {
        turn++
        val t = userText.trim()
        val correction = CORRECTION.containsMatchIn(fold(t))
        NAME.findAll(t).lastOrNull()?.let { put(Kind.NAME, "name", cap(it.groupValues[1])) }
        AGE.find(t)?.let { put(Kind.AGE, "age", it.groupValues[1] + " ans") }
        for (m in RELATIVE.findAll(t)) {
            val rel = m.groupValues[1].lowercase(); val who = cap(m.groupValues[2])
            val age = RELATIVE_AGE.find(t.substring(m.range.last + 1))?.groupValues?.get(1)
            put(Kind.RELATIVE, rel, who + (age?.let { ", $it ans" } ?: ""))
        }
        LIKE.find(t)?.let { m -> m.groupValues[2].trim().trimEnd('.', '!', '?').takeIf { it.length in 2..60 }?.let { put(Kind.LIKE, "like:" + fold(it), it) } }
        PLACE.find(t)?.let { put(Kind.PLACE, "place", cap(it.groupValues[1])) }
        val day = DAY.find(fold(t))?.groupValues?.get(1)
        val hour = HOUR.find(t)?.value
        if (APPOINTMENT.containsMatchIn(fold(t)) && day != null) put(Kind.APPOINTMENT, "appointment", listOfNotNull(day, hour?.let { "à $it" }).joinToString(" "))
        else if (correction && day != null && facts.containsKey("appointment")) {
            val old = facts.getValue("appointment").value
            put(Kind.APPOINTMENT, "appointment", DAY_WORD.replace(old, day))
        }
    }

    /** Short quoted block appended to the instruction; empty when nothing is known. */
    fun render(): String {
        if (facts.isEmpty()) return ""
        val parts = facts().map { f ->
            when (f.kind) {
                Kind.NAME -> "prénom : ${f.value}"
                Kind.AGE -> "âge : ${f.value}"
                Kind.RELATIVE -> "${f.key} : ${f.value}"
                Kind.LIKE -> "aime : ${f.value}"
                Kind.APPOINTMENT -> "rendez-vous : ${f.value}"
                Kind.PLACE -> "lieu évoqué : ${f.value}"
            }
        }
        return "Faits dits par l'utilisateur dans cette conversation (ce sont les siens, pas les tiens) : " + parts.joinToString(" ; ") + "."
    }

    fun clear() { facts.clear(); turn = 0 }

    private fun put(kind: Kind, key: String, value: String) {
        facts.remove(key)
        facts[key] = Fact(kind, key, value, turn)
        while (facts.size > maxFacts) facts.remove(facts.keys.first())
    }

    companion object {
        private const val W = "\\p{L}[\\p{L}-]{1,30}"
        private val NAME = Regex("(?i:je m'appelle|je m’appelle|mon prénom est|mon prenom est|mon nom est|appelle-moi)\\s+($W)")
        private val AGE = Regex("(?i)\\bj'ai\\s+(\\d{1,3})\\s+ans\\b")
        private val RELATIVE = Regex("(?i)\\b(?:mon|ma)\\s+(frère|frere|sœur|soeur|fils|fille|père|pere|mère|mere|mari|femme|ami|amie|chien|chat)\\s+s'appelle\\s+($W)")
        private val RELATIVE_AGE = Regex("(?i)^[^.!?]{0,40}?\\ba\\s+(\\d{1,3}|un|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|onze|douze|treize|quatorze|quinze|seize)\\s+ans\\b")
        private val LIKE = Regex("(?i)\\bj'(aime|adore)\\s+(?:beaucoup\\s+|bien\\s+)?([^.!?]+)")
        private val PLACE = Regex("(?i)\\b(?:voyage|séjour|sejour|vacances|je pars|je vais)\\s+(?:\\p{L}+\\s+){0,4}?(?:à|a|en)\\s+(\\p{Lu}[\\p{L}-]{1,30})")
        private val APPOINTMENT = Regex("rendez-vous|rdv|reunion")
        private val CORRECTION = Regex("\\b(non|pardon|en fait|je me suis trompe|plutot|correction)\\b")
        private val DAY = Regex("\\b(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\b")
        private val DAY_WORD = Regex("\\b(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\b")
        private val HOUR = Regex("(?i)\\b\\d{1,2}\\s*(?:h|heures?)(?:\\s*\\d{2})?\\b")
        fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        private fun cap(s: String) = s.replaceFirstChar { it.uppercaseChar() }
    }
}
