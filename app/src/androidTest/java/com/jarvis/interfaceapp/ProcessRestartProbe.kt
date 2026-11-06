package com.jarvis.interfaceapp

import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Test

/** Invoked twice by CI across am force-stop; excluded from the ordinary Gradle suite. */
class ProcessRestartProbe {
    @Test fun persistedDataAndNavigationAcrossActualProcessRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stage = InstrumentationRegistry.getArguments().getString("processProbe")
        val prefs = context.getSharedPreferences("ui-01", 0)
        LocalWorkspace(context).use { db ->
            if (stage == "write") {
                db.save(WorkspaceEntry(id = "ui01c-process-fixture", module = "Projets", title = "TEST PROCESS FIXTURE", body = "PERSISTED TEST", progress = 37))
                assertTrue(prefs.edit().putString("route", "Projets").putString("selected", "Projets").putString("navHistory", "Accueil|Modules").putInt("probePid", android.os.Process.myPid()).putFloat("intensity", .22f).commit())
            } else {
                assertEquals("verify", stage)
                assertNotEquals(prefs.getInt("probePid", -1), android.os.Process.myPid())
                val entry = db.entries("Projets").single { it.id == "ui01c-process-fixture" }
                assertEquals("PERSISTED TEST", entry.body); assertEquals(37, entry.progress)
                assertEquals("Projets", prefs.getString("route", "")); assertEquals("Projets", prefs.getString("selected", "")); assertEquals("Accueil|Modules", prefs.getString("navHistory", ""))
                assertEquals(.22f, prefs.getFloat("intensity", 0f), .001f)
                db.delete(entry.id)
                prefs.edit().putString("route", "Accueil").putString("selected", "Système").putString("navHistory", "").putFloat("intensity", .35f).remove("probePid").commit()
            }
        }
    }
}
