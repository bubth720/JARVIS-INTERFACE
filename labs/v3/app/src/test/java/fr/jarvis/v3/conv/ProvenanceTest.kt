package fr.jarvis.v3.conv

import org.junit.Assert.*
import org.junit.Test

class ProvenanceTest {
    @Test fun explainsOnlyWhatReallyFedTheAnswer() {
        val f = WorkingMemory().also { it.observe("Je m'appelle Léo.") }.facts()
        val w = Provenance.explain(f, 1, listOf("club.txt"), actionRequested = false, actionNotice = false, historyPairs = 2)
        assertTrue(w.first().contains("non vérifié"))
        assertTrue(w.any { it.contains("2 échange(s)") } && w.any { it.contains("Léo") } && w.any { it.contains("Souvenirs confirmés par toi : 1") } && w.any { it.contains("club.txt") })
        val none = Provenance.explain(emptyList(), 0, emptyList(), actionRequested = true, actionNotice = true, historyPairs = null)
        assertTrue(none.contains("Aucun document local cité.") && none.none { it.contains("Souvenirs") } && none.any { it.contains("note système") })
    }
}
