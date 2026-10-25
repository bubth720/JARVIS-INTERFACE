package com.jarvis.interfaceapp.workspace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.JarvisApplication
import com.jarvis.interfaceapp.resetLayoutFixture
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class WorkspaceInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as JarvisApplication
    @Before fun defaults() {
        compose.resetLayoutFixture()
        runBlocking { withTimeout(30_000) {
            app.workspace.change {Workspace()};app.workspace.awaitIdle()
        } }
        compose.waitForIdle()
    }
    private fun studio(tab:String) {
        compose.onNodeWithTag("open-studio").performScrollTo().performClick()
        compose.onNodeWithTag("studio-tab-$tab").performScrollTo().performClick()
    }
    private fun idle() { runBlocking {withTimeout(30_000){app.workspace.awaitIdle();app.dashboard.awaitIdle()}};compose.waitForIdle() }
    @Test fun localPersonaRequiresConfirmationAndSurvivesRecreation() {
        studio("Personnalité")
        compose.onNodeWithTag("local-persona-restore").performScrollTo().assertExists()
        // A default restore is a draft; it never silently activates CORE.
        compose.onNodeWithText("Tutoiement").performScrollTo()
        compose.onNode(hasContentDescription("Tutoiement") and hasClickAction()).performClick()
        compose.onNodeWithTag("local-persona-propose").performScrollTo().performClick()
        assertTrue(app.workspace.state.value.value.persona.profile.defaults.tutoiement)
        if(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureReview")=="true") {
            require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
            compose.onNodeWithTag("local-persona-confirm").assertIsDisplayed()
            compose.waitForIdle()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // API26 cannot use Compose PixelCopy for dialogs. Capture the actual
            // display after its separate modal surface has been presented.
            android.os.SystemClock.sleep(1000)
            val picture=checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(compose.activity.filesDir,"final01-personality-confirmation.png").outputStream().use {picture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            picture.recycle()
        }
        compose.onNodeWithTag("local-persona-confirm").performClick();idle()
        assertFalse(app.workspace.state.value.value.persona.profile.defaults.tutoiement)
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        assertFalse(app.workspace.state.value.value.persona.profile.defaults.tutoiement)
        compose.onNodeWithTag("local-persona-preview").performScrollTo().assertTextContains("Vous",substring=true)
    }
    @Test fun namedLayoutRestoreIsConfirmedAndKeepsV1Profiles() {
        studio("Dispositions")
        compose.onNodeWithTag("layout-name").performScrollTo().performTextInput("Test local")
        compose.onNodeWithTag("layout-save").performScrollTo().performClick();idle()
        assertEquals(2,app.workspace.state.value.value.layouts.getValue("Test local").size)
        compose.onNodeWithTag("layout-restore-Test local").performScrollTo().performClick()
        compose.onNodeWithTag("layout-confirm-restore").performClick();idle()
        assertTrue(app.dashboard.state.value.storage.durable)
        assertEquals(app.workspace.state.value.value.layouts.getValue("Test local"),app.dashboard.state.value.home.layouts)
    }
    @Test fun modulesCannotClaimConnectedAndDoNotSendAnything() {
        studio("Modules")
        for(id in ModuleId.entries)compose.onNodeWithTag("module-state-$id").performScrollTo().assertTextContains("UNCONFIGURED",substring=true)
        assertTrue(app.workspace.state.value.value.packages.isEmpty())
        val permissions=compose.activity.packageManager.getPackageInfo(compose.activity.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
    @Test fun archiveDeletionIsExplicitAndDurable() {
        app.workspace.change {it.copy(keepHistory=true,history=listOf(ArchivedMessage("sample","Exemple public",true,true)))};idle()
        studio("Historique");compose.onNodeWithTag("history-clear").performScrollTo().performClick()
        assertEquals(1,app.workspace.state.value.value.history.size)
        compose.onNodeWithTag("history-confirm-clear").performClick();idle()
        assertTrue(app.workspace.state.value.value.history.isEmpty());assertFalse(app.workspace.state.value.value.keepHistory)
    }
    @Test fun precisePlacementRefusesCollisionWithoutChangingGeometry() {
        val before=app.dashboard.state.value.home.layouts
        compose.onNodeWithTag("toggle-edit").performScrollTo().performClick()
        compose.onNodeWithTag("precise-AVATAR").performScrollTo().performClick()
        compose.onNodeWithTag("precise-X").performTextReplacement("1")
        compose.onNodeWithTag("precise-confirm").performClick();idle()
        assertEquals(before,app.dashboard.state.value.home.layouts)
        compose.onNodeWithText("Position refusée : chevauchement.").performScrollTo().assertIsDisplayed()
    }

    @Test fun dashboardShortcutOpensRealModuleManagerWithoutConnecting() {
        // Reveal the panel in the outer grid before scrolling its inner content.
        compose.onNodeWithTag("panel-APPLICATIONS").performScrollTo()
        compose.onNodeWithTag("panel-open-APPLICATIONS").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("module-state-CORE").performScrollTo().assertTextContains("UNCONFIGURED",substring=true)
    }
}
