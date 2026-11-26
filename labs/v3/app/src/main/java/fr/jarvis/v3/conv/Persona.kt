package fr.jarvis.v3.conv

/**
 * Structured personality (CONVERSATION-01 §3): the CORE identity profile parameters (identity/profiles,
 * CORE 5da9838: frankness / humour / initiative 0..100, tutoiement, style) rendered into the subordinate
 * instruction of the protected engine. The personality is data the owner edits and keeps; the text is
 * derived from it, never the other way round. Instructions grant no permission.
 */
data class PersonaSettings(
    val profileName: String,
    val frankness: Int, val humour: Int, val initiative: Int,
    val tutoiement: Boolean, val elegant: Boolean,
    /** Temporary, session-only adaptation (e.g. "plus court") — never stored without confirmation. */
    val brevity: Brevity = Brevity.ADAPTIVE,
    /** COMPACT = short rendering for the 0.6B (CONVERSATION-01 study: the FULL text doubled its first-token latency). */
    val rendering: Rendering = Rendering.COMPACT,
) {
    enum class Brevity { CONCISE, ADAPTIVE, DETAILED }
    enum class Rendering { COMPACT, FULL }
    init { require(frankness in 0..100 && humour in 0..100 && initiative in 0..100 && profileName.isNotBlank()) }

    companion object {
        /** Same values as CORE PersonaPreset.ATHENA (75 / 60 / 100, tutoiement, ELEGANT). */
        val ATHENA = PersonaSettings("Athéna", 75, 60, 100, tutoiement = true, elegant = true)
        /** Same values as CORE PersonaPreset.NEUTRAL (50 / 0 / 0, tutoiement, PLAIN). */
        val NEUTRAL = PersonaSettings("Neutre", 50, 0, 0, tutoiement = true, elegant = false)
    }
}

object PersonaRenderer {
    /** Questions about the user's own name use the dedicated instruction (measured V1-B routing). */
    private val NAME_INTENT = Regex("(?i)(m'appelle|m’appelle|appelle-moi|pr[ée]nom|mon nom)")

    /**
     * General / name instructions. For ATHENA the general text is identical to the instruction measured in
     * JARVIS-LLM a02/tests/personas/conv01-instructions.json key ATHENA (CONVERSATION-01 study).
     */
    fun general(p: PersonaSettings): String = if (p.rendering == PersonaSettings.Rendering.COMPACT) compactGeneral(p) else buildString {
        append("Tu restes JARVIS ; ne te donne aucun autre nom. ")
        append(if (p.elegant) "Ton style : chaleureux, naturel, spontané et un brin malicieux" else "Ton style : clair, sobre et bienveillant")
        append(if (p.humour >= 50) ", avec un humour léger quand c'est approprié. " else ", sans humour forcé. ")
        append(if (p.tutoiement) "Tutoie l'utilisateur. " else "Vouvoie l'utilisateur. ")
        append("Réponds à sa dernière demande en tenant compte de la conversation (il, elle, là-bas, ce résultat). ")
        append("S'il se corrige, la correction prime. S'il manque une information pour agir, pose une seule question précise. ")
        append("Réponds directement aux questions de culture générale, de calcul ou de date. ")
        append("Si une information personnelle ou un document ne t'a pas été donné, dis-le simplement, sans inventer. ")
        append(when (p.brevity) {
            PersonaSettings.Brevity.CONCISE -> "Réponds en une ou deux phrases."
            PersonaSettings.Brevity.ADAPTIVE -> "Adapte la longueur : une phrase pour une question simple, quelques phrases sinon."
            PersonaSettings.Brevity.DETAILED -> "Développe les points utiles, en restant clair."
        })
        if (p.initiative >= 50) append(" Propose une piste utile quand c'est pertinent.")
        // frankness is stored with the profile but not rendered yet: any new sentence is measured first (LLM study).
    }

    /** Identical, for ATHENA, to JARVIS-LLM conv01-instructions.json key ATHENA_COMPACT (general part). */
    private fun compactGeneral(p: PersonaSettings): String =
        (if (p.tutoiement) "Tutoie" else "Vouvoie") + " l'utilisateur" +
        (if (p.elegant && p.humour >= 50) ", avec chaleur et un brin d'humour. " else if (p.elegant) ", avec chaleur. " else ". ") +
        "Réponds seulement à sa dernière question, directement. Donne directement les faits connus, comme les capitales ou les calculs. " +
        "S'il se corrige, la correction prime. Tu ne peux faire aucune action (message, appareil, lumière) : dis-le simplement, sans prétendre l'avoir fait." +
        when (p.brevity) { PersonaSettings.Brevity.CONCISE -> " Réponds en une ou deux phrases."; PersonaSettings.Brevity.DETAILED -> " Développe les points utiles."; else -> "" }

    /** Identical, for ATHENA, to conv01-instructions.json ATHENA_COMPACT (name part). */
    private fun compactName(p: PersonaSettings): String =
        (if (p.tutoiement) "Tutoie" else "Vouvoie") + " l'utilisateur. Ne te présente qu'une fois. S'il demande son prénom, et seulement dans ce cas, réponds « " +
        (if (p.tutoiement) "Tu t'appelles" else "Vous vous appelez") + " » suivi du dernier prénom qu'il t'a donné. Pour une question de culture générale, donne directement la réponse."

    fun name(p: PersonaSettings): String = if (p.rendering == PersonaSettings.Rendering.COMPACT) compactName(p) else
        "Tu restes JARVIS, au style ${if (p.elegant) "chaleureux et naturel" else "clair et sobre"}. " +
        (if (p.tutoiement) "Tutoie l'utilisateur. " else "Vouvoie l'utilisateur. ") +
        "Ne te présente qu'une fois. S'il demande son prénom, réponds « ${if (p.tutoiement) "Tu t'appelles" else "Vous vous appelez"} » " +
        "suivi du dernier prénom qu'il t'a donné, en tenant compte de ses corrections."

    /** Full subordinate instruction for one turn: routed persona text + working-memory facts + confirmed memories. */
    fun instruction(p: PersonaSettings, question: String, facts: String = "", confirmedMemories: String = ""): String =
        listOf(if (NAME_INTENT.containsMatchIn(question)) name(p) else general(p), facts, confirmedMemories)
            .filter { it.isNotBlank() }.joinToString(" ")
}
