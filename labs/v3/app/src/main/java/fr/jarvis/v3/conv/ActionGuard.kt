package fr.jarvis.v3.conv

/**
 * Owner rule (14/11): JARVIS must never pretend it performed an action it did not execute. JARVIS executes NO
 * real-world action (no device, message, call, purchase or booking). Measured: the instruction alone is not
 * enough (CONVERSATION-01 study 3e2560e: "La lumière du salon est allumée." from the 0.6B AND the 1.7B), so:
 *  1. before generation, an action request adds an explicit sentence to that turn's instruction;
 *  2. after generation, an answer that still claims the action is done gets a visible, spoken SYSTEM notice.
 * The model's text is never rewritten or replaced; the notice is labelled as coming from the system.
 */
object ActionGuard {
    private val REQUEST = Regex("(?i)\\b(allume|allumer|éteins|eteins|éteindre|eteindre|envoie|envoyer|appelle|appeler|téléphone à|ouvre|ouvrir|ferme|fermer|" +
        "réserve|reserve|réserver|commande|commander|achète|achete|acheter|paie|payer|vire|virer|transfère|transfere|programme|programmer|règle|regle|régler|" +
        "monte|baisse|augmente|diminue|lance|lancer|démarre|demarre|démarrer|arrête|arrete|supprime|supprimer|efface|effacer|publie|publier|poste|verrouille|déverrouille)\\b")
    private val QUESTION_ONLY = Regex("(?i)^\\s*(comment|pourquoi|qu'est-ce|que veut dire|explique)")
    private val CLAIM = Regex("(?i)(c'est fait|voilà,? c'est|voici la lumière|je l'(ai )?(allume|éteins|envoie|ouvre|ferme|appelle|lance)|" +
        "j'ai (bien )?(allumé|éteint|envoyé|appelé|ouvert|fermé|réservé|commandé|acheté|payé|viré|transféré|programmé|réglé|lancé|démarré|arrêté|supprimé|effacé|publié|posté|verrouillé|monté|baissé)|" +
        "\\b(est|sont) (maintenant |désormais |bien )?(allumée?s?|éteinte?s?|envoyée?s?|ouverte?s?|fermée?s?|réservée?s?|commandée?s?|programmée?s?|réglée?s?|lancée?s?|verrouillée?s?|publiée?s?|supprimée?s?)\\b|" +
        "(e-?mail|message|sms|courriel) (a été|est) envoyé)")
    private val REFUSAL = Regex("(?i)(je ne (peux|suis) pas|je n'ai pas (la possibilité|accès)|impossible pour moi|je ne contrôle)")

    /** The action the user asks JARVIS to perform, or null (questions about how to do something are not requests). */
    fun requestedAction(question: String): String? =
        if (QUESTION_ONLY.containsMatchIn(question)) null else REQUEST.find(question)?.let { question.trim().take(80) }

    /** Extra sentence for that turn's instruction when an action is requested. */
    fun instruction(question: String): String = requestedAction(question)?.let {
        "L'utilisateur te demande une action (« $it »). Tu ne peux pas l'exécuter : dis clairement que tu ne peux pas le faire, sans jamais affirmer qu'elle est faite, puis propose une aide possible."
    }.orEmpty()

    /** True when the answer asserts that a requested action was carried out (and does not refuse it). */
    fun claimsDone(question: String, answer: String): Boolean =
        requestedAction(question) != null && CLAIM.containsMatchIn(answer) && !REFUSAL.containsMatchIn(answer)

    const val NOTICE = "Aucune action n'a été exécutée : JARVIS ne contrôle ni appareils, ni messages, ni achats."
}
