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
class LocalWorkspace(context: Context, name: String = "ui01b-workspace.db") : SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, module TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, due TEXT NOT NULL, cents INTEGER NOT NULL, progress INTEGER NOT NULL, done INTEGER NOT NULL, uri TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX entries_module ON entries(module, created)")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, action TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        writableDatabase.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        log("Enregistrement local · ${entry.module}")
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
