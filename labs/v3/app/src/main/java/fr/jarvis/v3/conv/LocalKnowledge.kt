package fr.jarvis.v3.conv

import android.os.SystemClock
import fr.jarvis.nexus.android.AndroidNexusStorage
import fr.jarvis.nexus.android.OpenFailure
import fr.jarvis.nexus.android.StorageOpenResult
import fr.jarvis.nexus.api.AccessPolicy
import fr.jarvis.nexus.api.CatalogResult
import fr.jarvis.nexus.api.KnowledgeCatalogService
import fr.jarvis.nexus.importing.ExtractedPage
import fr.jarvis.nexus.importing.ImportCoordinator
import fr.jarvis.nexus.importing.ImportSource
import fr.jarvis.nexus.importing.ParserPort
import fr.jarvis.nexus.importing.StructuredParser
import fr.jarvis.nexus.rag.ContextLimits
import fr.jarvis.nexus.rag.DocumentaryCall
import fr.jarvis.nexus.rag.DocumentaryGateway
import fr.jarvis.nexus.rag.DocumentaryResult
import fr.jarvis.nexus.rag.KnowledgeQuery
import fr.jarvis.nexus.rag.SearchScope
import java.io.File

/**
 * Local documentary knowledge (CONVERSATION-01 §6) over the NEXUS engine, compiled unchanged from JARVIS-NEXUS
 * (FUSION-02 06ccf98): app-private SQLite store, import of documents the user explicitly picked, FTS5 search,
 * DocumentaryGateway (authorization, deadline, citation re-verification). Nothing is scanned or imported
 * automatically. If the platform SQLite lacks FTS5, NEXUS refuses BEFORE creating any file and this class
 * reports the capability as unavailable: no degraded or simulated search.
 */
class LocalKnowledge(private val dir: File) : AutoCloseable {
    sealed interface State {
        data class Ready(val documents: Int) : State
        data class Unavailable(val reason: String) : State
    }
    data class Grounding(val block: String, val sources: List<String>, val searched: Boolean, val ms: Long)

    @Volatile var state: State = State.Unavailable("non ouvert"); private set
    /** Owner setting: use the imported documents in answers. */
    @Volatile var enabled = true
    private var storage: AndroidNexusStorage? = null
    private var catalog: KnowledgeCatalogService? = null
    private var gateway: DocumentaryGateway? = null
    private var collection: String? = null

    @Synchronized fun open(): State {
        if (storage != null) return state
        dir.mkdirs()
        state = when (val r = AndroidNexusStorage.open(dir)) {
            is StorageOpenResult.Rejected -> State.Unavailable(when (r.reason) {
                OpenFailure.CAPABILITY_UNAVAILABLE -> "recherche plein texte (FTS5) absente du SQLite de cet Android"
                OpenFailure.INCOMPATIBLE_VERSION -> "base de connaissances d'une version incompatible (conservée intacte)"
                OpenFailure.STORAGE_FAILURE -> "stockage local indisponible"
            })
            is StorageOpenResult.Ready -> {
                val db = r.storage; storage = db
                val cat = KnowledgeCatalogService(db, AccessPolicy { true }).also { catalog = it }
                val idFile = File(dir, "collection.id")
                collection = idFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: (cat.createCollection("Documents de l'utilisateur") as? CatalogResult.Ok)?.value?.id?.also { idFile.writeText(it) }
                val col = collection
                if (col == null) State.Unavailable("collection locale impossible à créer")
                else {
                    gateway = DocumentaryGateway(db, cat, { SearchScope(setOf(col)) }, { SystemClock.elapsedRealtime() })
                    State.Ready(count())
                }
            }
        }
        return state
    }

    private fun count(): Int = (catalog?.documents(100, 0, false) as? CatalogResult.Ok)?.value?.size ?: 0

    /** Imports ONE document the user picked (txt, md, csv, tsv, docx, xml; ≤ 2 Mio). Returns a short French outcome. */
    @Synchronized fun import(name: String, bytes: ByteArray): String {
        val db = storage ?: return "Connaissances locales indisponibles : ${(state as? State.Unavailable)?.reason}"
        val cat = catalog!!; val col = collection!!
        val text = ParserPort { _, b, stop -> stop.check(); listOf(ExtractedPage(null, b.toString(Charsets.UTF_8))) }
        val imports = ImportCoordinator(db, cat, StructuredParser(text).registry())
        val source = ImportSource(col, "picked://" + name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_"), name.take(120), name.take(120))
        val queued = imports.enqueue(source, bytes.inputStream())
        if (queued !is CatalogResult.Ok) return "$name : refusé (${(queued as CatalogResult.Rejected).error})"
        val done = imports.process(queued.value.id, bytes.inputStream())
        state = State.Ready(count())
        return if (done is CatalogResult.Ok) "$name : indexé localement (${bytes.size} octets)" else "$name : échec (${(done as CatalogResult.Rejected).error})"
    }

    /** Titles of indexed documents (local list for the NEXUS screen). */
    fun titles(): List<String> = (catalog?.documents(100, 0, false) as? CatalogResult.Ok)?.value?.map { it.title } ?: emptyList()

    /**
     * Searches the user's documents for [question] within [budgetMs], through the gateway (authorization,
     * deadline, citations re-resolved). Returns the instruction block and the cited sources.
     */
    fun ground(question: String, budgetMs: Long = 1_500): Grounding {
        val t0 = SystemClock.elapsedRealtime()
        val asked = KnowledgePlanner.asksDocuments(question)
        val g = gateway
        if (g == null || !enabled || (state as? State.Ready)?.documents == 0)
            return Grounding(KnowledgePlanner.render(emptyList(), asked && g != null && enabled), emptyList(), false, 0)
        val deadline = t0 + budgetMs
        val docs = titlesById()
        for (q in KnowledgePlanner.attempts(question)) {
            if (SystemClock.elapsedRealtime() >= deadline) break
            val r = synchronized(this) {
                g.retrieve(KnowledgeQuery(q), SearchScope(setOf(collection!!)), DocumentaryCall(deadline, authorized = { enabled }),
                    ContextLimits(maxCharacters = 1_600, maxItems = 2, maxSources = 2), maxUtf8Bytes = 4_000)
            }
            if (r is DocumentaryResult.Ready && r.bundle.items.isNotEmpty()) {
                val ex = r.bundle.items.map { item -> val c = item.citations.first()
                    KnowledgePlanner.Excerpt(item.text, docs[c.documentId] ?: c.sourceUri.removePrefix("picked://"), c.page) }
                return Grounding(KnowledgePlanner.render(ex, asked), ex.map { it.source + (it.page?.let { p -> " p.$p" } ?: "") }.distinct(), true,
                    SystemClock.elapsedRealtime() - t0)
            }
        }
        return Grounding(KnowledgePlanner.render(emptyList(), asked), emptyList(), true, SystemClock.elapsedRealtime() - t0)
    }

    private fun titlesById(): Map<String, String> = (catalog?.documents(100, 0, false) as? CatalogResult.Ok)?.value?.associate { it.id to it.title } ?: emptyMap()

    @Synchronized override fun close() { storage?.close(); storage = null; gateway = null }
}
