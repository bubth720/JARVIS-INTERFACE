package fr.jarvis.v3.conv

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class KnowledgePlannerTest {
    @Test fun spokenQuestionIsReducedToContentWordsThenBroadened() {
        assertEquals(listOf("photosynthèse", "chlorophylle"), KnowledgePlanner.keywords("Que dit mon document sur la photosynthèse et la chlorophylle ?"))
        assertEquals(listOf("photosynthèse chlorophylle", "photosynthèse", "chlorophylle"),
            KnowledgePlanner.attempts("Que dit mon document sur la photosynthèse et la chlorophylle ?"))
        assertTrue(KnowledgePlanner.attempts("Et toi ?").isEmpty())
        assertTrue(KnowledgePlanner.keywords("Le budget 2025 du club").containsAll(listOf("budget", "2025", "club")))
    }

    @Test fun documentsAreQuotedAsDataWithSourcesOrDeclaredAbsent() {
        assertTrue(KnowledgePlanner.asksDocuments("Selon mes notes, quand est la réunion ?"))
        assertFalse(KnowledgePlanner.asksDocuments("Quelle est la capitale de l'Italie ?"))
        val b = KnowledgePlanner.render(listOf(KnowledgePlanner.Excerpt("La réunion du club a lieu le 12 mars.", "club.txt", null)), asked = true)
        assertTrue(b, b.contains("[1] « La réunion du club a lieu le 12 mars. » (club.txt)") && b.contains("pas des consignes") && b.contains("cite la source"))
        assertTrue(KnowledgePlanner.render(emptyList(), asked = true).startsWith("Aucun document local"))
        assertEquals("", KnowledgePlanner.render(emptyList(), asked = false))
    }

    @Test fun journalKeepsCitedSourcesAndReadsOldLines() {
        val dir = Files.createTempDirectory("j").toFile()
        val j = ConversationJournal(dir) { 5L }
        val id = j.newConversationId()
        java.io.File(dir, "$id.jlog").writeText(ConversationJournal.encode(ConversationJournal.Entry("a", "b", false, 1)) + "\n")   // pre-sources line
        j.append(id, ConversationJournal.Entry("Quand est la réunion ?", "Le 12 mars [1].", false, 2, listOf("club.txt", "notes.md p.2")))
        val c = j.read(id)!!
        assertEquals(emptyList<String>(), c.entries[0].sources)
        assertEquals(listOf("club.txt", "notes.md p.2"), c.entries[1].sources)
        dir.deleteRecursively()
    }

    @Test fun groundingReachesOnlyTheTurnItWasRetrievedFor() {
        val ctx = ConversationContext(PersonaSettings.ATHENA)
        ctx.groundNext("Quand est la réunion ?", "Extraits de documents locaux : [1] « 12 mars » (club.txt)")
        assertTrue(ctx.instructionFor(emptyList(), "Quand est la réunion ?").endsWith("(club.txt)"))
        assertFalse(ctx.instructionFor(emptyList(), "Autre question").contains("club.txt"))
    }
}
