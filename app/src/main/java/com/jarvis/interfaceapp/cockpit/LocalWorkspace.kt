package com.jarvis.interfaceapp.cockpit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

data class WorkspaceEntry(
    val id: String = UUID.randomUUID().toString(),
    val module: String,
    val title: String,
    val body: String = "",
    val due: String = "",
    val amountCents: Long = 0,
    val progress: Int = 0,
    val done: Boolean = false,
    val uri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

fun parseEuroCents(input: String): Long? = try {
    val value = BigDecimal(input.trim().replace(',', '.'))
    if (value.signum() < 0 || value.scale() > 2) null
    else value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
} catch (_: Exception) { null }

fun validDueDate(input: String): Boolean = input.isBlank() || try {
    LocalDate.parse(input).toString() == input
} catch (_: Exception) { false }

/** Private offline database, no seeded rows and no connector. Schema changes are additive. */
class LocalWorkspace(context: Context, name: String = "ui01b-workspace.db") : SQLiteOpenHelper(context.applicationContext, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, module TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, due TEXT NOT NULL, cents INTEGER NOT NULL, progress INTEGER NOT NULL, done INTEGER NOT NULL, uri TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX entries_module ON entries(module, created)")
        createDetails(db)
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, action TEXT NOT NULL)")
    }
    private fun createDetails(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS steps (id TEXT PRIMARY KEY, parent TEXT NOT NULL, title TEXT NOT NULL, done INTEGER NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, parent TEXT NOT NULL, time INTEGER NOT NULL, action TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDetails(db)
    }
    fun allEntries(): List<WorkspaceEntry> = listOf("Administratif", "Finance", "Projets", "Écriture", "Création").flatMap(::entries)
    private fun recordHistory(id: String, action: String) {
        writableDatabase.insertOrThrow("history", null, ContentValues().apply { put("parent", id); put("time", System.currentTimeMillis()); put("action", action) })
        writableDatabase.execSQL("DELETE FROM history WHERE parent = ? AND id NOT IN (SELECT id FROM history WHERE parent = ? ORDER BY id DESC LIMIT 200)", arrayOf(id, id))
    }
    fun history(id: String): List<String> = readableDatabase.rawQuery("SELECT time, action FROM history WHERE parent = ? ORDER BY id DESC", arrayOf(id)).use { c ->
        buildList { while (c.moveToNext()) add("${java.time.Instant.ofEpochMilli(c.getLong(0))} · ${c.getString(1)}") }
    }
    @Synchronized fun delete(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(db.delete("entries", "id = ?", arrayOf(id)) == 1)
            db.delete("steps", "parent = ?", arrayOf(id)); db.delete("history", "parent = ?", arrayOf(id))
            log("Suppression locale confirmée")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun steps(id: String): List<WorkspaceStep> = readableDatabase.rawQuery("SELECT id, parent, title, done FROM steps WHERE parent = ? ORDER BY created, id", arrayOf(id)).use { c ->
        buildList { while (c.moveToNext()) add(WorkspaceStep(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) == 1)) }
    }
    @Synchronized fun saveStep(step: WorkspaceStep) {
        require(step.title.isNotBlank() && step.title.length <= 160)
        val parent = allEntries().singleOrNull { it.id == step.parent }
        require(parent?.module in listOf("Projets", "Création"))
        val db = writableDatabase; db.beginTransaction()
        try {
            db.insertWithOnConflict("steps", null, ContentValues().apply { put("id", step.id); put("parent", step.parent); put("title", step.title); put("done", if (step.done) 1 else 0); put("created", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            recordHistory(step.parent, if (step.done) "Étape terminée" else "Étape ajoutée ou rouverte")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun entries(module: String): List<WorkspaceEntry> = readableDatabase.query(
        "entries", null, "module = ?", arrayOf(module), null, null, "created DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                fun str(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                fun num(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                add(WorkspaceEntry(str("id"), str("module"), str("title"), str("body"), str("due"), num("cents"), num("progress").toInt(), num("done") != 0L, str("uri"), num("created")))
            }
        }
    }

    @Synchronized fun save(entry: WorkspaceEntry) {
        require(entry.module in listOf("Administratif", "Finance", "Projets", "Écriture", "Création"))
        require(entry.title.isNotBlank() && entry.title.length <= 160)
        require(entry.body.length <= 100_000 && validDueDate(entry.due))
        require(entry.progress in 0..100)
        val values = ContentValues().apply {
            put("id", entry.id); put("module", entry.module); put("title", entry.title.trim())
            put("body", entry.body); put("due", entry.due); put("cents", entry.amountCents)
            put("progress", entry.progress); put("done", if (entry.done) 1 else 0)
            put("uri", entry.uri); put("created", entry.createdAt)
        }
        val previous = entries(entry.module).firstOrNull { it.id == entry.id }
        val db = writableDatabase; db.beginTransaction()
        try {
            db.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            val action = if (previous == null) "Création" else if (previous.progress != entry.progress) "Progression : ${entry.progress} %" else if (previous.done != entry.done) { if (entry.done) "Terminé ou exclu" else "Rouvert ou réinclus" } else "Modification"
            recordHistory(entry.id, action)
            log("Enregistrement local · ${entry.module}")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun log(action: String) {
        // No user content, document URI, title or financial amount in diagnostic events.
        val allowed = action.take(80)
        writableDatabase.insertOrThrow("events", null, ContentValues().apply { put("time", System.currentTimeMillis()); put("action", allowed) })
        writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 100)")
    }

    fun events(): List<String> = readableDatabase.rawQuery("SELECT time, action FROM events ORDER BY id DESC LIMIT 100", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add("${java.time.Instant.ofEpochMilli(cursor.getLong(0))} · ${cursor.getString(1)}") }
    }
}

data class WorkspaceStep(val id: String = UUID.randomUUID().toString(), val parent: String, val title: String, val done: Boolean = false)
