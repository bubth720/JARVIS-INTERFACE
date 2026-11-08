package com.jarvis.interfaceapp.m51

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.BuildConfig
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class M51FoundationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val context get()=ui.activity
    private fun click(tag: String) { ui.onNodeWithTag(tag).performScrollTo().performClick() }
    @Test fun distinctTestOnlyPackageNoSensitivePermissionsAndLlmOff() {
        assertEquals("fr.jarvis.v1.lab",context.packageName)
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY != 0)
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("No Android platform permission",permissions.none { it.startsWith("android.permission.") })
        assertTrue(m51Registry(BuildConfig.SOURCE_COMMIT,true).access("LLM") is CapabilityAccess.Refused)
        ui.onNodeWithTag("m51-banner").assertExists()
    }
    @Test fun capabilityPanelShowsBlockedDependenciesAndReturnsWithoutEnablingAnything() {
        click("nav-Supervision");ui.onNodeWithTag("capabilities-screen").assertExists()
        click("capability-blocked");ui.onNodeWithTag("capability-LLM").assertExists()
        click("capability-detail-LLM");ui.onNodeWithText("LLM").assertExists()
        ui.onNodeWithText("Dernier refus : LAB_AUTHORITY_NOT_APPROVED",substring=true).assertExists()
        click("capability-detail-close");click("capability-all")
        ui.onNodeWithTag("capability-filter").performTextReplacement("NEXUS")
        ui.onNodeWithTag("capability-NEXUS").assertExists();ui.onNodeWithTag("capability-LLM").assertDoesNotExist()
        click("close-settings");ui.onNodeWithTag("dashboard").assertExists()
    }
    @Test fun capabilityFilterAndNavigationSurviveActivityRecreation() {
        click("nav-Supervision");ui.onNodeWithTag("capability-filter").performTextReplacement("LLM")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("capability-filter").assertTextEquals("LLM");ui.onNodeWithTag("capability-LLM").assertExists()
        assertEquals("Supervision",context.getSharedPreferences("ui-01",0).getString("route",""))
    }
    @Test fun reduceMotionChoicePersistsAndDoesNotRewriteExistingData() {
        val prefs=context.getSharedPreferences("ui-01",0)
        prefs.edit().putBoolean("reduceMotion",false).commit();ui.activityRule.scenario.recreate()
        click("open-settings");click("reduce-motion")
        assertTrue(prefs.getBoolean("reduceMotion",false));ui.activityRule.scenario.recreate()
        assertTrue(prefs.getBoolean("reduceMotion",false));ui.onNodeWithTag("reduce-motion").performScrollTo().assertIsOn()
        prefs.edit().remove("reduceMotion").commit()
    }
    private class ExportBoundary(private val uri: android.net.Uri): Instrumentation.ActivityMonitor() {
        @Volatile var seen=false
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = if(intent.action==Intent.ACTION_CREATE_DOCUMENT) {
            seen=true;Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        } else null
    }
    @Test fun voluntaryTechnicalExportContainsNoWorkspaceContent() {
        val token=UUID.randomUUID().toString();val privateText="SYNTHETIC_PRIVATE_SENTINEL_$token"
        val entry=WorkspaceEntry(id=token,module="Projets",title=privateText,body=privateText)
        LocalWorkspace(context).use { it.save(entry) }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"M51_PUBLIC_EXPORT_$token.txt");put(MediaStore.MediaColumns.MIME_TYPE,"text/plain")
        }))
        val monitor=ExportBoundary(uri);val inst=InstrumentationRegistry.getInstrumentation();inst.addMonitor(monitor)
        try {
            click("nav-Supervision");click("capabilities-export")
            ui.waitUntil(15000) { resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().contains("UNIFIED:") } == true }
            assertTrue(monitor.seen)
            val exported=resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertTrue(exported.contains("jarvis.capabilities/1"));assertTrue(exported.contains("LLM: BLOCKED"))
            assertFalse(exported.contains(privateText));assertFalse(exported.contains("content://"));assertFalse(exported.contains("/data/"))
        } finally { inst.removeMonitor(monitor);resolver.delete(uri,null,null);LocalWorkspace(context).use { it.delete(entry.id) } }
    }
    @Test fun additiveMigrationOnSyntheticCopyPreservesRows() {
        val name="m51-migration-fixture-${UUID.randomUUID()}.db";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, module TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, due TEXT NOT NULL, cents INTEGER NOT NULL, progress INTEGER NOT NULL, done INTEGER NOT NULL, uri TEXT NOT NULL, created INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, action TEXT NOT NULL)")
            db.execSQL("INSERT INTO entries VALUES ('migration-unit','Projets','SYNTHETIC COPY','preserved','',0,37,0,'',1)")
            db.version=1
        }
        try { LocalWorkspace(context,name).use { db ->
            val row=db.entries("Projets").single();assertEquals(37,row.progress);assertEquals("preserved",row.body)
            db.saveStep(WorkspaceStep(id="unit-step",parent=row.id,title="SYNTHETIC STEP"));assertEquals(1,db.steps(row.id).size)
            assertEquals(2,db.readableDatabase.version)
        } } finally { context.deleteDatabase(name) }
    }
}
