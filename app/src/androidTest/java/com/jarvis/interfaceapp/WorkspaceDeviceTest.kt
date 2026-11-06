package com.jarvis.interfaceapp

import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceDeviceTest {
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
