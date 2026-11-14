package fr.jarvis.v3.conv

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ConversationLayerTest {
    @Test fun workingMemoryKeepsStatedFactsAndAppliesCorrections() {
        val m = WorkingMemory()
        m.observe("Je m'appelle Marc.")
        m.observe("Pardon, je me suis trompé : je m'appelle Léo.")
        m.observe("Mon frère s'appelle Paul et il a douze ans.")
        m.observe("Mon rendez-vous est mardi à 10 heures.")
        m.observe("Non, je me suis trompé, c'est mercredi.")
        m.observe("Je prépare un voyage à Lyon le week-end prochain.")
        m.observe("J'aime beaucoup le chocolat.")
        val r = m.render()
        assertTrue(r, r.startsWith("Fait connu : l'utilisateur s'appelle Léo (ce n'est pas ton nom).")); assertFalse(r, r.contains("Marc"))
        assertTrue(r, r.contains("son frère s'appelle Paul (douze ans)"))
        assertTrue(r, r.contains("son rendez-vous : mercredi à 10 heures")); assertFalse(r, r.contains("mardi"))
        assertTrue(r, r.contains("lieu évoqué : Lyon"))
        assertTrue(r, r.contains("il aime le chocolat"))
    }

    @Test fun workingMemoryInfersNothingAndStaysBounded() {
        val m = WorkingMemory(maxFacts = 3)
        m.observe("Quelle est la capitale de la France ?")
        assertEquals("", m.render())
        for (i in 1..6) m.observe("J'aime le sujet numéro $i.")
        assertEquals(3, m.facts().size)
        assertTrue(m.render().contains("numéro 6"))
    }

    @Test fun athenaPersonaRendersTheMeasuredInstruction() {
        val full = PersonaSettings.ATHENA.copy(rendering = PersonaSettings.Rendering.FULL)
        val g = PersonaRenderer.general(full)
        // Same text as JARVIS-LLM a02/tests/personas/conv01-instructions.json, key ATHENA (general part).
        assertEquals("Tu restes JARVIS ; ne te donne aucun autre nom. Ton style : chaleureux, naturel, spontané et un brin malicieux, avec un humour léger quand c'est approprié. " +
            "Tutoie l'utilisateur. Réponds à sa dernière demande en tenant compte de la conversation (il, elle, là-bas, ce résultat). " +
            "S'il se corrige, la correction prime. S'il manque une information pour agir, pose une seule question précise. " +
            "Réponds directement aux questions de culture générale, de calcul ou de date. " +
            "Si une information personnelle ou un document ne t'a pas été donné, dis-le simplement, sans inventer. " +
            "Adapte la longueur : une phrase pour une question simple, quelques phrases sinon. Propose une piste utile quand c'est pertinent.", g)
        assertFalse("the model must never be told another name", g.contains("Athéna"))
        val n = PersonaRenderer.instruction(full, "Comment je m'appelle ?", "Faits : prénom : Léo.")
        assertTrue(n.contains("« Tu t'appelles »") && n.endsWith("Faits : prénom : Léo."))
        val neutral = PersonaRenderer.general(PersonaSettings.NEUTRAL.copy(tutoiement = false, rendering = PersonaSettings.Rendering.FULL))
        assertTrue(neutral.contains("Vouvoie") && neutral.contains("sans humour forcé") && !neutral.contains("piste utile"))
        assertTrue(PersonaRenderer.general(full.copy(brevity = PersonaSettings.Brevity.CONCISE)).contains("une ou deux phrases"))
        // Default (0.6B) rendering = measured ATHENA_COMPACT, both routes.
        assertEquals("Tutoie l'utilisateur, avec chaleur et un brin d'humour. Réponds seulement à sa dernière question, directement. " +
            "Donne directement les faits connus, comme les capitales ou les calculs. S'il se corrige, la correction prime. " +
            "Tu ne peux faire aucune action (message, appareil, lumière) : dis-le simplement, sans prétendre l'avoir fait.", PersonaRenderer.general(PersonaSettings.ATHENA))
        assertEquals("Tutoie l'utilisateur. Ne te présente qu'une fois. S'il demande son prénom, et seulement dans ce cas, réponds « Tu t'appelles » " +
            "suivi du dernier prénom qu'il t'a donné. Pour une question de culture générale, donne directement la réponse.", PersonaRenderer.name(PersonaSettings.ATHENA))
    }

    @Test fun compactRenderingMatchesTheMeasuredStudyText() {
        // Snapshot of JARVIS-LLM a02/tests/personas/conv01-instructions.json at 3e2560e (study branch ccr-5cf17ed2-phgk00).
        val json = requireNotNull(javaClass.classLoader!!.getResource("conv01-instructions.json")).readText()
        val athena = Regex("\"ATHENA_COMPACT\": \"ROUTE:([^|]*)\\|([^\"]*)\"").find(json) ?: error("ATHENA_COMPACT missing")
        assertEquals(athena.groupValues[1], PersonaRenderer.general(PersonaSettings.ATHENA))
        assertEquals(athena.groupValues[2], PersonaRenderer.name(PersonaSettings.ATHENA))
    }

    @Test fun journalPersistsResumesAndDeletes() {
        val dir = Files.createTempDirectory("journal").toFile()
        var now = 1_000L
        val j = ConversationJournal(dir) { now }
        val id = j.newConversationId()
        j.append(id, ConversationJournal.Entry("Bonjour,\nje m'appelle Léo", "Bonjour Léo !", false, now))
        now = 2_000L
        j.append(id, ConversationJournal.Entry("Et toi ?", "Je suis JARVIS.", true, now))
        java.io.File(dir, "$id.jlog").appendText("v1\u001f9", Charsets.UTF_8)   // torn line: ignored
        val c = requireNotNull(ConversationJournal(dir).read(id))
        assertEquals(listOf("Bonjour,\nje m'appelle Léo", "Et toi ?"), c.entries.map { it.user })
        assertEquals(true, c.entries[1].viaVoice)
        assertEquals(id, ConversationJournal(dir).latest()?.id)
        assertTrue(j.delete(id)); assertNull(j.read(id))
        assertThrows(IllegalArgumentException::class.java) { j.read("../etc") }
        dir.deleteRecursively()
    }

    @Test fun quickReactionFollowsTheVoiceRules() {
        assertFalse(QuickReaction.shouldPlay(800, null, true, false, false, false, true))         // < 1.2 s
        assertTrue(QuickReaction.shouldPlay(1_300, null, true, false, false, false, true))
        assertFalse(QuickReaction.shouldPlay(1_300, 5_000, true, false, false, false, true))      // cooldown
        assertFalse(QuickReaction.shouldPlay(1_300, null, true, true, false, false, true))        // answer already started
        assertFalse(QuickReaction.shouldPlay(1_300, null, true, false, true, false, true))        // final audio ready
        assertFalse(QuickReaction.shouldPlay(1_300, null, true, false, false, true, true))        // other sound
        assertFalse(QuickReaction.shouldPlay(1_300, null, false, false, false, false, true))      // no voice output
        assertNotEquals(QuickReaction.phrase(0, true), QuickReaction.phrase(1, true))
    }
}
