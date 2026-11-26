package fr.jarvis.v3.conv

import org.junit.Assert.*
import org.junit.Test

class MemoryIntentTest {
    @Test fun explicitRequestsBecomeProposalsAndNothingElseDoes() {
        assertEquals("Je préfère le thé au café.", MemoryIntent.rememberRequest("Souviens-toi que je préfère le thé au café."))
        assertEquals("Mon frère s'appelle Paul.", MemoryIntent.rememberRequest("Retiens que mon frère s'appelle Paul"))
        assertEquals("Mon rendez-vous chez le dentiste est mardi.", MemoryIntent.rememberRequest("JARVIS, n'oublie pas que mon rendez-vous chez le dentiste est mardi !"))
        assertNull(MemoryIntent.rememberRequest("Je me souviens de mon enfance."))
        assertNull(MemoryIntent.rememberRequest("Quelle est la capitale de l'Italie ?"))
        assertNull(MemoryIntent.rememberRequest("Retiens"))
    }
}
