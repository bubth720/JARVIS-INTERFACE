package fr.jarvis.v3.conv

import com.stark.jarvislocal.v1b.ConversationHistory
import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    private fun turns(vararg pairs: Pair<String, String>) = pairs.map { ConversationHistory.Turn(it.first, it.second) }

    @Test fun factsOlderThanTheEngineWindowReachTheInstruction() {
        // 5 exchanges: the protected engine only re-sends the last 3; the name and the brother are older.
        val h = turns("Je m'appelle Marc." to "Enchanté Marc !", "Non pardon, je m'appelle Léo." to "D'accord Léo.",
            "Mon frère s'appelle Paul et il a douze ans." to "Noté.", "Quelle est la capitale de l'Italie ?" to "Rome.",
            "Et celle de l'Espagne ?" to "Madrid.")
        val ctx = ConversationContext(PersonaSettings.ATHENA)
        val general = ctx.instructionFor(h, "Quel âge a-t-il ?")
        assertTrue(general, general.startsWith(PersonaRenderer.general(PersonaSettings.ATHENA)))
        assertTrue(general, general.contains("son frère s'appelle Paul (douze ans)") && general.contains("l'utilisateur s'appelle Léo") && !general.contains("Marc"))
        val name = ctx.instructionFor(h, "Comment je m'appelle ?")
        assertTrue(name, name.startsWith(PersonaRenderer.name(PersonaSettings.ATHENA)) && name.contains("Fait connu : l'utilisateur s'appelle Léo (ce n'est pas ton nom)."))
    }

    @Test fun personaChangeAppliesToTheNextTurnAndNothingIsInvented() {
        val ctx = ConversationContext(PersonaSettings.ATHENA)
        assertEquals(PersonaRenderer.general(PersonaSettings.ATHENA), ctx.instructionFor(emptyList(), "Quelle heure est-il à Tokyo ?"))
        ctx.persona = PersonaSettings.NEUTRAL.copy(tutoiement = false)
        assertTrue(ctx.instructionFor(emptyList(), "Bonjour").startsWith("Vouvoie"))
    }

    @Test fun confirmedMemoriesAreAppendedOnlyWhenProvided() {
        val ctx = ConversationContext(PersonaSettings.ATHENA, confirmedMemories = { q -> if (q.contains("café")) "Souvenir confirmé : préfère le café sans sucre." else "" })
        assertTrue(ctx.instructionFor(emptyList(), "Tu me fais un café ?").endsWith("Souvenir confirmé : préfère le café sans sucre."))
        assertFalse(ctx.instructionFor(emptyList(), "Bonjour").contains("Souvenir"))
    }

    @Test fun instructionStaysWithinTheEngineLimit() {
        val long = (1..40).map { "J'aime le sujet numéro $it qui est assez long pour remplir." to "Ok." }.toTypedArray()
        val s = ConversationContext(PersonaSettings.ATHENA.copy(rendering = PersonaSettings.Rendering.FULL)).instructionFor(turns(*long), "Et alors ?")
        assertTrue("protected engine refuses > 8192 bytes", s.toByteArray().size < 8192)
    }
}
