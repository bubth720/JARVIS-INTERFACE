package com.jarvis.interfaceapp

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class Ui01cContractTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val context get() = ui.activity
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun click(tag: String) { val node = ui.onNodeWithTag(tag); try { node.performScrollTo() } catch (_: AssertionError) { node.assertIsDisplayed() }; node.performClick() }
    private fun exists(tag: String) = ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String) { ui.waitUntil(15000) { exists(tag) } }
    private fun home() { ui.onNodeWithTag("logo-home").performClick() }
    private fun route(name: String) { ui.waitUntil(15000) { context.getSharedPreferences("ui-01", 0).getString("route", "") == name } }
    private fun open(index: Int) { home(); click("module-$index"); waitTag("module-screen") }
    private fun rows(module: String) = LocalWorkspace(context).use { it.entries(module) }
    private fun seed(module: String, due: String = "", uri: String = ""): WorkspaceEntry = WorkspaceEntry(module = module, title = "TEST UI01C ${UUID.randomUUID()}", body = "TEST PRIVATE CONTENT", due = due, amountCents = if (module == "Finance") -1234 else 0, uri = uri).also { LocalWorkspace(context).use { db -> db.save(it) } }
    private fun replace(tag: String, text: String) { ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }

    @Before fun prepare() {
        context.getSharedPreferences("ui-01", 0).edit().putBoolean("secure", false).putBoolean("demo", false).putBoolean("economy", false).putInt("performance", 1).putBoolean("glow", true).commit()
        ui.activityRule.scenario.recreate()
        home()
    }

    @Test fun navigationContractAllIconsAndModulesInBothOrientations() {
        listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE).forEach { orientation ->
            ui.runOnIdle { context.requestedOrientation = orientation }
            ui.waitForIdle()
            listOf("Accueil", "Modules", "Documents", "Calendrier", "Statistiques", "Paramètres", "Supervision", "Avatar").forEach { name ->
                home(); click("nav-$name"); route(name)
                if (name != "Accueil") { click("close-settings"); route("Accueil") }
            }
            (0..7).forEach { index -> open(index); route(cockpitModules[index].name); click("close-settings"); route("Accueil") }
            click("open-settings"); home(); route("Accueil")
            ui.runOnIdle { context.onBackPressedDispatcher.onBackPressed() }; route("Paramètres")
            ui.runOnIdle { context.onBackPressedDispatcher.onBackPressed() }; route("Accueil")
            click("nav-Modules"); click("module-0"); click("close-settings"); route("Modules"); home()
        }
    }

    @Test fun crudContractEditsCancellationDeletionAndStatusAcrossFiveModules() {
        listOf(1 to "Administratif", 2 to "Finance", 3 to "Projets", 4 to "Écriture", 5 to "Création").forEach { (index, module) ->
            val entry = seed(module)
            open(index); waitTag("entry-${entry.id}")
            click("edit-${entry.id}"); replace("edit-title", "")
            click("edit-save"); ui.onNodeWithTag("edit-error").assertExists()
            click("edit-cancel"); assertEquals(entry.title, rows(module).single { it.id == entry.id }.title)
            click("edit-${entry.id}"); replace("edit-title", "TEST UI01C modifié"); replace("edit-body", "TEST MODIFIED")
            if (module in listOf("Administratif", "Projets", "Finance")) replace("edit-date", LocalDate.now().toString())
            if (module == "Finance") { replace("edit-amount", "2,50"); click("edit-expense"); click("edit-income") }
            click("edit-save")
            ui.waitUntil(15000) { rows(module).single { it.id == entry.id }.body == "TEST MODIFIED" }
            if (module == "Finance") {
                assertEquals(250L, rows(module).single { it.id == entry.id }.amountCents)
                ui.waitUntil(15000) { ui.onAllNodes(hasTestTag("finance-chart") and hasContentDescription("Revenus 2.50 EUR", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            }
            click("toggle-${entry.id}"); ui.waitUntil(15000) { rows(module).single { it.id == entry.id }.done }
            click("toggle-${entry.id}"); ui.waitUntil(15000) { !rows(module).single { it.id == entry.id }.done }
            click("delete-${entry.id}"); click("delete-cancel"); assertTrue(rows(module).any { it.id == entry.id })
            click("delete-${entry.id}"); click("delete-confirm"); ui.waitUntil(15000) { rows(module).none { it.id == entry.id } }
        }
    }

    @Test fun projectContractStepsProgressAndHistoryAreReal() {
        listOf(3 to "Projets", 5 to "Création").forEach { (index, module) ->
            val entry = seed(module); open(index); waitTag("entry-${entry.id}")
            replace("step-title-${entry.id}", "TEST étape"); click("step-add-${entry.id}")
            ui.waitUntil(15000) { LocalWorkspace(context).use { it.steps(entry.id).isNotEmpty() } }
            val step = LocalWorkspace(context).use { it.steps(entry.id).single() }
            click("step-toggle-${step.id}")
            ui.waitUntil(15000) { LocalWorkspace(context).use { it.steps(entry.id).single().done } }
            click("step-toggle-${step.id}")
            ui.waitUntil(15000) { LocalWorkspace(context).use { !it.steps(entry.id).single().done } }
            click("step-toggle-${step.id}")
            ui.waitUntil(15000) { LocalWorkspace(context).use { it.steps(entry.id).single().done } }
            ui.onNodeWithTag("project-progress-${entry.id}").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
            ui.waitUntil(15000) { rows(module).single { it.id == entry.id }.progress == 75 }
            click("history-${entry.id}"); waitTag("history-content")
            ui.waitUntil(15000) { ui.onAllNodesWithText("Progression : 75 %", substring = true).fetchSemanticsNodes().isNotEmpty() }
            click("history-close")
            LocalWorkspace(context).use { db -> assertTrue(db.history(entry.id).any { it.contains("Étape") }); db.delete(entry.id); assertTrue(db.steps(entry.id).isEmpty()); assertTrue(db.history(entry.id).isEmpty()) }
        }
    }

    @Test fun collectionContractCalendarDocumentsStatisticsAndChart() {
        val entry = seed("Administratif", LocalDate.now().toString())
        val project = seed("Projets")
        home(); click("nav-Calendrier"); waitTag("deadline-open-${entry.id}")
        click("month-next"); ui.onNodeWithText(YearMonth.now().plusMonths(1).toString()).assertExists()
        click("month-previous"); click("month-today"); click("day-${LocalDate.now().dayOfMonth}")
        click("deadline-open-${entry.id}"); route("Administratif"); click("close-settings"); route("Calendrier")
        click("nav-Documents"); click("documents-import"); route("Administratif"); click("close-settings"); route("Documents")
        click("nav-Statistiques"); waitTag("stats-open-${project.id}"); ui.onNodeWithTag("statistics-summary").assertExists(); ui.onNodeWithTag("finance-chart").assertExists()
        click("stats-open-${project.id}"); route("Projets")
        open(2); ui.onNodeWithTag("finance-chart").assertExists()
        val key = "budget-${YearMonth.now()}"; replace(key, "incorrect"); click("save-budget"); ui.onNodeWithTag("workspace-message").assertExists()
        replace(key, "100,25"); click("save-budget")
        assertEquals(10025L, context.getSharedPreferences("ui01b-finance", 0).getLong(YearMonth.now().toString(), 0))
        click("movement-income"); click("movement-expense")
        LocalWorkspace(context).use { it.delete(entry.id); it.delete(project.id) }
    }

    @Test fun settingsContractAllWeatherProfilesSlidersAndRealCacheIsolation() {
        val original = LocalWeatherStore(context).lastKnown()
        click("open-settings")
        (0..2).forEach { click("performance-$it"); assertEquals(it, context.getSharedPreferences("ui-01", 0).getInt("performance", -1)) }
        click("economy-switch"); click("economy-switch"); click("glow-switch"); click("glow-switch")
        ui.onNodeWithTag("animation-intensity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        assertEquals(.4f, context.getSharedPreferences("ui-01", 0).getFloat("intensity", 0f), .001f)
        if (!context.getSharedPreferences("ui-01", 0).getBoolean("demo", false)) click("demo-switch")
        ui.onNodeWithTag("demo-hour").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        assertEquals(22f, context.getSharedPreferences("ui-01", 0).getFloat("demoHour", 0f), .001f)
        Weather.entries.forEach { click("weather-${it.name}"); assertEquals(it.name, context.getSharedPreferences("ui-01", 0).getString("demoWeather", "")) }
        click("close-settings"); ui.onNodeWithTag("demo-status").assertExists()
        click("open-settings"); click("ambience-auto"); assertFalse(context.getSharedPreferences("ui-01", 0).getBoolean("demo", true))
        assertEquals(original, LocalWeatherStore(context).lastKnown())
        home()
        listOf(2, 5, 7, 3).forEach { index ->
            open(index); ui.mainClock.advanceTimeBy(1200)
            val pixels = ui.onNodeWithTag("office-canvas").performScrollTo().captureToImage().toPixelMap()
            val color = pixels[(pixels.width * .5).toInt(), (pixels.height * .58).toInt()]
            when (index) { 2 -> assertTrue(color.green > color.red); 5 -> assertTrue(color.blue > color.red); 7 -> assertTrue(color.red > color.green); 3 -> assertTrue(color.red > color.blue) }
        }
    }

    private class BoundaryMonitor(private val action: String, private val result: Instrumentation.ActivityResult) : Instrumentation.ActivityMonitor() {
        @Volatile var last: Intent? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != action) return null
            last = Intent(intent); return result
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun externalBoundaryContractsExportImportCancellationViewerAndPermissions() {
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "ui01c-test-diagnostic.txt"); put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) })!!
        resolver.openOutputStream(uri)!!.use { it.write(byteArrayOf()) }
        try {
            open(6); ui.waitUntil(15000) { ui.onAllNodes(hasTestTag("diagnostic-export") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            click("diagnostic-refresh")
            val export = BoundaryMonitor(Intent.ACTION_CREATE_DOCUMENT, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            instrumentation.addMonitor(export)
            try { click("diagnostic-export"); ui.waitUntil(15000) { export.last != null }; ui.waitUntil(15000) { runCatching { resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }.contains("Version application") }.getOrDefault(false) } }
            finally { instrumentation.removeMonitor(export) }
            val report = resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertFalse(report.contains("TEST PRIVATE CONTENT"))
            listOf(1, 5).forEach { index ->
                open(index); val cancel = BoundaryMonitor(Intent.ACTION_OPEN_DOCUMENT, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)); instrumentation.addMonitor(cancel)
                try { click("import-document"); ui.waitUntil(15000) { cancel.last != null }; assertEquals(Intent.ACTION_OPEN_DOCUMENT, cancel.last!!.action) }
                finally { instrumentation.removeMonitor(cancel) }
            }
            val doc = seed("Administratif", uri = uri.toString())
            val view = BoundaryMonitor(Intent.ACTION_VIEW, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)); instrumentation.addMonitor(view)
            try {
                home(); click("nav-Documents"); waitTag("document-open-${doc.id}"); click("document-open-${doc.id}"); ui.waitUntil(15000) { view.last != null }; assertEquals(uri, view.last!!.data)
                open(1); click("file-open-${doc.id}")
            } finally { instrumentation.removeMonitor(view); LocalWorkspace(context).use { it.delete(doc.id) } }
            open(7); ui.onNodeWithTag("permissions-list").assertExists()
            click("privacy-switch"); ui.runOnIdle { assertTrue(context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            click("privacy-switch"); ui.runOnIdle { assertFalse(context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            val settings = BoundaryMonitor(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)); instrumentation.addMonitor(settings)
            try { click("permissions-open"); ui.waitUntil(15000) { settings.last != null }; assertEquals("package:${context.packageName}", settings.last!!.data.toString()) }
            finally { instrumentation.removeMonitor(settings) }
        } finally { resolver.delete(uri, null, null) }
    }

    @Test fun powerContractCancelSleepResumeAndCloseOnlyThisActivity() {
        open(3); click("nav-Alimentation"); click("power-cancel"); ui.onNodeWithTag("module-screen").assertExists()
        click("nav-Alimentation"); click("power-sleep"); ui.onNodeWithTag("sleep-screen").assertExists()
        ui.onNodeWithTag("power-resume").performClick(); route("Projets")
        click("nav-Alimentation"); ui.onNodeWithTag("power-close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.waitUntil(15000) { ui.activityRule.scenario.state == Lifecycle.State.DESTROYED }
    }
}
