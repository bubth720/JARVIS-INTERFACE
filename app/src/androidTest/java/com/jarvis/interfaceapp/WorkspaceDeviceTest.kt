package com.jarvis.interfaceapp

import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceDeviceTest {
    @Test fun v1DatabaseMigratesWithoutLosingEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ui01c-migration-${java.util.UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, module TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, due TEXT NOT NULL, cents INTEGER NOT NULL, progress INTEGER NOT NULL, done INTEGER NOT NULL, uri TEXT NOT NULL, created INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, action TEXT NOT NULL)")
                db.execSQL("INSERT INTO entries VALUES ('migration', 'Projets', 'TEST V1', 'OLD BODY', '', 0, 27, 0, '', 1)")
                db.version = 1
            }
            LocalWorkspace(context, name).use { db ->
                assertEquals(2, db.readableDatabase.version)
                val row = db.entries("Projets").single()
                assertEquals("OLD BODY", row.body); assertEquals(27, row.progress)
                assertTrue(db.steps(row.id).isEmpty()); assertTrue(db.history(row.id).isEmpty())
                db.saveStep(WorkspaceStep(parent = row.id, title = "TEST STEP"))
                assertEquals(1, db.steps(row.id).size)
                assertEquals("OLD BODY", db.entries("Projets").single().body)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun allLocalRecordTypesSurviveDatabaseCloseAndReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ui01b-instrumentation-fixture.db"
        context.deleteDatabase(name)
        try {
            val entries = listOf("Administratif", "Finance", "Projets", "Écriture", "Création").map {
                WorkspaceEntry(module = it, title = "TEST FIXTURE", body = "PRIVATE TEST CONTENT", amountCents = -1234, progress = 42, done = true)
            }
            LocalWorkspace(context, name).use { db -> entries.forEach(db::save) }
            LocalWorkspace(context, name).use { db ->
                entries.forEach { assertEquals(it, db.entries(it.module).single()) }
                assertFalse(db.events().joinToString().contains("PRIVATE TEST CONTENT"))
                assertFalse(db.events().joinToString().contains("TEST FIXTURE"))
            }
        } finally { context.deleteDatabase(name) }
    }
}
