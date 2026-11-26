package fr.jarvis.v3.model

import java.text.Normalizer

/** Kinds of context cards (§05). Every card declares where its content comes from. */
enum class CardKind(val category: String) {
    CONVERSATION("Conversation"), CONCEPT("Sujet"), SYSTEM("Système"), UNAVAILABLE("Module")
}

data class CardAction(val id: String, val label: String, val enabled: Boolean = true, val reason: String? = null)

data class ContextCard(
    val id: String,
    val kind: CardKind,
    val title: String,
    /** Always shown: where the content comes from (local model, Android measurement, nothing). */
    val provenance: String,
    /** 2–4 lines in compact mode. */
    val summary: String,
    val details: List<Pair<String, String>> = emptyList(),
    val actions: List<CardAction> = emptyList(),
    /** Visual: no local image is authorised in V3, so a clearly identified substitute illustration. */
    val illustration: String = "Illustration de substitution (aucune image locale)",
    /** Prompt sent if the owner taps "Explorer" (an explicit user action, never automatic). */
    val explorePrompt: String? = null,
)

/** Real Android measurement (no permission needed); null fields mean "not available on this device". */
data class SystemMeasure(
    val batteryPercent: Int?, val charging: Boolean?, val batteryTempC: Float?,
    val thermal: String, val ramAvailMb: Long?, val ramTotalMb: Long?, val storageFreeMb: Long?, val atLabel: String,
)

enum class SystemTopic { BATTERY, TEMPERATURE, MEMORY, STORAGE }

object CardFactory {
    const val MODEL_PROVENANCE = "Réponse générée localement par Qwen3-0.6B (non vérifiée)"
    const val ANDROID_PROVENANCE = "Mesure Android sur cet appareil (aucune donnée inventée)"

    fun compact(text: String, max: Int = 240): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        return if (t.length <= max) t else t.take(max).substringBeforeLast(' ') + " …"
    }

    fun fromExchange(id: String, question: String, answer: String, llmMs: Long, viaVoice: Boolean): ContextCard = ContextCard(
        id = id, kind = CardKind.CONVERSATION, title = compact(question, 60), provenance = MODEL_PROVENANCE,
        summary = compact(answer).ifBlank { "Aucune réponse (interrompue)." },
        details = listOf("Question" to question, "Saisie" to (if (viaVoice) "voix (reconnaissance locale)" else "clavier"),
            "Génération" to "${llmMs / 1000.0} s, sur l'appareil, hors ligne"),
        actions = standardActions(), explorePrompt = "Peux-tu développer : ${compact(question, 80)}",
    )

    /** Concept card: ONLY sentences actually present in the conversation that mention the keyword. */
    fun concept(id: String, keyword: String, answers: List<String>): ContextCard {
        val k = fold(keyword)
        val sentences = answers.flatMap { it.split(Regex("(?<=[.!?])\\s+")) }.map { it.trim() }.filter { it.isNotEmpty() && fold(it).contains(k) }.distinct()
        return ContextCard(
            id = id, kind = CardKind.CONCEPT, title = keyword,
            provenance = if (sentences.isEmpty()) "Aucune source locale sur ce sujet" else "Extraits de cette conversation · $MODEL_PROVENANCE",
            summary = if (sentences.isEmpty()) "Source indisponible hors ligne : rien n'est connu localement sur « $keyword » en dehors de la conversation."
                      else compact(sentences.take(2).joinToString(" ")),
            details = sentences.drop(2).take(4).mapIndexed { i, s -> "Extrait ${i + 3}" to s },
            actions = standardActions(), explorePrompt = "Parle-moi de $keyword.",
        )
    }

    fun system(id: String, topic: SystemTopic, m: SystemMeasure): ContextCard {
        fun mb(v: Long?) = v?.let { if (it >= 1024) "%.1f Go".format(it / 1024.0) else "$it Mo" } ?: "indisponible"
        val battery = m.batteryPercent?.let { "$it %" + when (m.charging) { true -> " (en charge)"; false -> " (sur batterie)"; null -> "" } } ?: "indisponible"
        val temp = m.batteryTempC?.let { "%.1f °C".format(it) } ?: "indisponible"
        val summary = when (topic) {
            SystemTopic.BATTERY -> "Batterie : $battery."
            SystemTopic.TEMPERATURE -> "Température de la batterie : $temp. État thermique : ${m.thermal}."
            SystemTopic.MEMORY -> "Mémoire disponible : ${mb(m.ramAvailMb)} sur ${mb(m.ramTotalMb)}."
            SystemTopic.STORAGE -> "Stockage libre : ${mb(m.storageFreeMb)}."
        }
        return ContextCard(
            id = id, kind = CardKind.SYSTEM, title = when (topic) {
                SystemTopic.BATTERY -> "Batterie"; SystemTopic.TEMPERATURE -> "Température"; SystemTopic.MEMORY -> "Mémoire"; SystemTopic.STORAGE -> "Stockage" },
            provenance = ANDROID_PROVENANCE, summary = summary,
            details = listOf("Batterie" to battery, "Température batterie" to temp, "État thermique" to m.thermal,
                "Mémoire disponible" to "${mb(m.ramAvailMb)} / ${mb(m.ramTotalMb)}", "Stockage libre" to mb(m.storageFreeMb), "Mesuré" to m.atLabel),
            actions = listOf(CardAction("back", "Revenir à la conversation")),
            illustration = "Instrument de mesure (aucune image)",
        )
    }

    /** NEXUS / SERVICES are prepared but NOT declared operational (§05): an honest empty state. */
    fun unavailable(id: String, module: String, purpose: String): ContextCard = ContextCard(
        id = id, kind = CardKind.UNAVAILABLE, title = module, provenance = "Module non connecté dans cette version",
        summary = "$purpose n'est pas disponible : le module $module n'est pas intégré à cette APK. Aucun contenu n'est inventé.",
        actions = listOf(CardAction("back", "Revenir à la conversation"), CardAction("open", "Ouvrir le document", enabled = false, reason = "$module non connecté")),
        illustration = "Module absent",
    )

    private fun standardActions() = listOf(
        CardAction("explore", "Explorer"), CardAction("cite", "Citer la source"), CardAction("back", "Revenir à la conversation"),
        CardAction("open", "Ouvrir le document", enabled = false, reason = "NEXUS non connecté"),
    )

    fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
}

/** Typed questions about the device are answered from a real measurement, never by the language model (§05). */
object Intents {
    private val rules = listOf(
        SystemTopic.BATTERY to Regex("\\bbatterie\\b|\\bautonomie\\b|niveau de charge"),
        SystemTopic.TEMPERATURE to Regex("\\btemperature\\b.*\\b(telephone|appareil|batterie)\\b|\\b(telephone|appareil)\\b.*\\b(chaud|temperature)\\b"),
        SystemTopic.MEMORY to Regex("\\bmemoire (vive|disponible|libre)\\b|\\bram\\b"),
        SystemTopic.STORAGE to Regex("\\bstockage\\b|espace (libre|disque|disponible)"),
    )
    fun systemTopic(text: String): SystemTopic? { val t = CardFactory.fold(text); return rules.firstOrNull { it.second.containsMatchIn(t) }?.first }
}

/** Conversation keywords shown around the sphere: real words of the last exchange, never invented topics. */
object Keywords {
    private val stop = setOf(
        "bonjour", "merci", "comment", "quelle", "quelles", "quel", "quels", "pourquoi", "parle", "parlez", "explique", "expliquer",
        "raconte", "peux", "pouvez", "vous", "votre", "notre", "cette", "celui", "celle", "entre", "avec", "dans", "pour", "sans",
        "sont", "suis", "etes", "avez", "etait", "etre", "avoir", "faire", "plus", "moins", "tres", "aussi", "mais", "donc", "alors",
        "appelle", "appelez", "appeler", "capitale", "reponse", "question", "jarvis", "chose", "choses", "toujours", "jamais",
        "beaucoup", "peut", "fait", "tout", "tous", "toute", "toutes", "leur", "leurs", "elle", "elles", "nous", "comme", "aujourd",
    )
    fun extract(question: String, answer: String, max: Int = 6): List<String> {
        // Proper nouns first (people, places, eras), then long common words; all taken from the real exchange.
        val proper = LinkedHashMap<String, String>(); val common = LinkedHashMap<String, String>()
        val words = Regex("[\\p{L}][\\p{L}'-]+").findAll("$question $answer").map { it.value.trim('\'', '-') }
        for (w in words) {
            val base = w.substringAfter('\'')
            val f = CardFactory.fold(base)
            if (base.length < 4 || f in stop || f.substringBefore('-') in stop) continue
            val target = if (base.first().isUpperCase()) proper else if (base.length >= 7) common else continue
            if (f !in proper && f !in common) target[f] = base.replaceFirstChar { it.uppercase() }
        }
        return (proper.values + common.values).take(max)
    }
}
