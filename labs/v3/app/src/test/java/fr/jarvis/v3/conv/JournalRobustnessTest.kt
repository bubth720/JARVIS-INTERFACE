package fr.jarvis.v3.conv

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** S6 / G4 (A-032): the process killed while appending leaves a torn last line; nothing written afterwards may be lost. */
class JournalRobustnessTest {
    private fun journal() = ConversationJournal(Files.createTempDirectory("jlog").toFile()) { 1_700_000_000_000L }

    @Test fun anExchangeAppendedAfterATornLineIsKept() {
        val dir = Files.createTempDirectory("jlog").toFile(); val j = ConversationJournal(dir) { 1L }; val id = j.newConversationId()
        j.append(id, ConversationJournal.Entry("Bonjour", "Salut !", false, 1))
        val f = java.io.File(dir, "$id.jlog")
        val torn = ConversationJournal.encode(ConversationJournal.Entry("Question coupée", "Réponse jamais finie", false, 2)).take(14)
        f.appendText(torn, Charsets.UTF_8)                                       // killed mid-write: no end of line
        assertEquals("the torn line is ignored", 1, j.read(id)!!.entries.size)
        j.append(id, ConversationJournal.Entry("Après la reprise", "Toujours là", false, 3))
        val e = j.read(id)!!.entries
        assertEquals("the exchange written after the torn line must be readable: ${f.readText()}", listOf("Bonjour", "Après la reprise"), e.map { it.user })
    }

    @Test fun aTornLineInTheMiddleNeverCorruptsItsNeighbours() {
        val dir = Files.createTempDirectory("jlog").toFile(); val j = ConversationJournal(dir) { 1L }; val id = j.newConversationId()
        val f = java.io.File(dir, "$id.jlog")
        f.writeText(ConversationJournal.encode(ConversationJournal.Entry("a", "b", false, 1)) + "\n" + "v1\u001f12\u001f0\u001fcoup" + "\n" +
            ConversationJournal.encode(ConversationJournal.Entry("c", "d", true, 3)) + "\n" + "\u0000\u0000garbage", Charsets.UTF_8)
        assertEquals(listOf("a", "c"), j.read(id)!!.entries.map { it.user })
        j.append(id, ConversationJournal.Entry("e", "f", false, 4))
        assertEquals(listOf("a", "c", "e"), j.read(id)!!.entries.map { it.user })
    }

    @Test fun anEmptyOrMissingFileStartsCleanly() {
        val dir = Files.createTempDirectory("jlog").toFile(); val j = ConversationJournal(dir) { 1L }; val id = j.newConversationId()
        java.io.File(dir, "$id.jlog").writeText("")
        j.append(id, ConversationJournal.Entry("x", "y", false, 1))
        assertEquals(1, j.read(id)!!.entries.size)
        assertEquals("no empty first line", ConversationJournal.encode(ConversationJournal.Entry("x", "y", false, 1)) + "\n", java.io.File(dir, "$id.jlog").readText())
    }
}
