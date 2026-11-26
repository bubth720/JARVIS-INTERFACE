package fr.jarvis.v3.conv

/**
 * "Pourquoi cette information ?" (Command Center V2 §8, Data Provenance): what really fed one answer, recorded at
 * the moment the turn was built — never reconstructed afterwards, never invented.
 */
object Provenance {
    fun explain(facts: List<WorkingMemory.Fact>, confirmedMemories: Int, sources: List<String>, actionRequested: Boolean,
                actionNotice: Boolean, historyPairs: Int?): List<String> = buildList {
        add("Texte généré localement par le modèle Qwen3-0.6B : non vérifié, il peut se tromper.")
        historyPairs?.let { add("Contexte relu par le modèle : $it échange(s) précédent(s) de la conversation.") }
        if (facts.isNotEmpty()) add("Faits que tu as dits dans cette conversation : " + facts.joinToString(" ; ") { it.value } + ".")
        if (confirmedMemories > 0) add("Souvenirs confirmés par toi : $confirmedMemories.")
        if (sources.isNotEmpty()) add("Extraits de tes documents locaux : " + sources.joinToString(", ") + ".")
        else add("Aucun document local cité.")
        if (actionRequested) add("Demande d'action détectée : JARVIS n'exécute aucune action ; la consigne de refus a été ajoutée.")
        if (actionNotice) add("La réponse affirmait une action : une note système l'a signalée comme non exécutée.")
    }
}
