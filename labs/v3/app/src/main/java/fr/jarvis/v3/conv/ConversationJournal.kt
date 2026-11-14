package fr.jarvis.v3.conv

import java.io.File

/**
 * Local conversation history (CONVERSATION-01 §4): each finished exchange is appended to an app-private
 * file, so a conversation resumes after the app is closed. Only the exchange text the user saw is stored
 * (no audio, no prompt, no instruction). The owner can list, resume or delete conversations; deletion
 * removes the file. Durable MEMORIES are a different thing (CORE consent workflow), never this journal.
 */
class ConversationJournal(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    data class Entry(val user: String, val answer: String, val viaVoice: Boolean, val atMs: Long, val sources: List<String> = emptyList())
    data class Conversation(val id: String, val entries: List<Entry>, val updatedMs: Long)

    init { dir.mkdirs() }

    fun newConversationId(): String = "c" + clock().toString(36) + "-" + (1000..9999).random()

    fun append(conversationId: String, e: Entry) {
        require(ID.matches(conversationId)) { "invalid conversation id" }
        val f = File(dir, "$conversationId.jlog")
        f.appendText(encode(e) + "\n", Charsets.UTF_8)
    }

    fun read(conversationId: String): Conversation? {
        require(ID.matches(conversationId)) { "invalid conversation id" }
        val f = File(dir, "$conversationId.jlog")
        if (!f.isFile) return null
        // A torn last line (process killed while writing) is ignored, never "repaired" into fake content.
        val entries = f.readLines(Charsets.UTF_8).mapNotNull { decode(it) }
        return Conversation(conversationId, entries, f.lastModified())
    }

    /** Most recently updated first. */
    fun list(): List<Conversation> = dir.listFiles { f -> f.name.endsWith(".jlog") }.orEmpty()
        .mapNotNull { read(it.name.removeSuffix(".jlog")) }.sortedByDescending { it.updatedMs }

    fun latest(): Conversation? = list().firstOrNull()

    fun delete(conversationId: String): Boolean {
        require(ID.matches(conversationId)) { "invalid conversation id" }
        return File(dir, "$conversationId.jlog").delete()
    }

    companion object {
        private val ID = Regex("[a-z0-9-]{3,40}")
        private const val SEP = '\u001f'
        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(SEP.toString(), " ")
        private fun unesc(s: String): String {
            val b = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { b.append(if (s[i + 1] == 'n') '\n' else s[i + 1]); i += 2 } else { b.append(c); i++ }
            }
            return b.toString()
        }
        /** v1 line; an optional 6th field lists the cited local sources (separated by \u001e). */
        fun encode(e: Entry) = (listOf("v1", e.atMs.toString(), if (e.viaVoice) "1" else "0", esc(e.user), esc(e.answer)) +
            (if (e.sources.isEmpty()) emptyList() else listOf(e.sources.joinToString("\u001e") { esc(it).replace("\u001e", " ") }))).joinToString(SEP.toString())
        fun decode(line: String): Entry? {
            val p = line.split(SEP)
            if (p.size !in 5..6 || p[0] != "v1") return null
            val at = p[1].toLongOrNull() ?: return null
            return Entry(unesc(p[3]), unesc(p[4]), p[2] == "1", at, if (p.size == 6) p[5].split('\u001e').map(::unesc).filter { it.isNotBlank() } else emptyList())
        }
    }
}
