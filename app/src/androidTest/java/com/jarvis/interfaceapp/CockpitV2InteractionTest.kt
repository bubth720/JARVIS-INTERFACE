package com.jarvis.interfaceapp

import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry

@OptIn(ExperimentalTestApi::class)
class CockpitV2InteractionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val controller get() = (ui.activity.application as JarvisApplication).dashboard
    private fun click(tag: String) { val node = ui.onNodeWithTag(tag); if (!node.isDisplayed()) node.performScrollTo(); node.performClick() }
    private fun idle() { runBlocking { withTimeout(30_000) { controller.awaitIdle() } }; ui.waitForIdle() }
    private fun open() { click("open-settings"); click("open-cockpit-v2"); ui.onNodeWithTag("cockpit-v2").assertExists() }
    private fun stored() = File(ui.activity.filesDir, "datastore/dashboard.json").readBytes()
    private fun back() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    @Before fun prepare() { ui.resetLayoutFixture() }

    @Test fun oldDashboardRemainsDefaultAndV2NeverChangesSavedBytes() {
        ui.onNodeWithTag("cockpit-v2").assertDoesNotExist()
        controller.dispatch(HomeAction.ToggleEditing)
        controller.dispatch(HomeAction.Place(DashboardProfile.COMPACT, PanelId.AVATAR, 0, 1, 3, 3))
        controller.dispatch(HomeAction.ToggleLock(DashboardProfile.WIDE, PanelId.SYSTEM)); idle()
        controller.dispatch(HomeAction.ExitEditing); idle()
        val before = stored(); val layouts = controller.state.value.home.layouts
        open()
        ui.onNodeWithTag("v2-knowledge-card").assertDoesNotExist()
        listOf("v2-avatar", "v2-sphere", "v2-command-bar", "v2-wheel").forEach { ui.onNodeWithTag(it).assertExists() }
        ui.onNodeWithTag("v2-draft").performTextInput("TEST ÉPHÉMÈRE 008")
        click("v2-demo-open"); ui.onNodeWithTag("v2-knowledge-card").assertExists()
        ui.onNodeWithTag("v2-source-status").assertTextContains("aucune source consultée", substring = true)
        ui.onNodeWithTag("v2-sphere").assertExists()
        click("v2-demo-close"); ui.onNodeWithTag("v2-knowledge-card").assertDoesNotExist()
        click("v2-return"); idle()
        listOf("AVATAR", "SYSTEM", "APPLICATIONS", "COMMANDS").forEach { ui.onNodeWithTag("panel-$it").assertExists() }
        assertArrayEquals(before, stored()); assertEquals(layouts, controller.state.value.home.layouts)
        open(); assertEquals("", ui.onNodeWithTag("v2-draft").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        back(); ui.onNodeWithTag("dashboard").assertExists(); idle(); assertArrayEquals(before, stored())
    }

    @Test fun singleWheelAndAllAvailableDestinationsHaveRealRoutes() {
        open(); ui.onAllNodesWithTag("v2-wheel").assertCountEquals(1)
        click("v2-wheel"); ui.onNodeWithTag("v2-wheel-menu").assertIsDisplayed()
        listOf("CORE", "SERVICES", "VOICE").forEach { ui.onNodeWithTag("v2-unavailable-$it").assertIsNotEnabled() }
        click("v2-wheel"); ui.onNodeWithTag("v2-wheel-menu").assertDoesNotExist()
        click("v2-wheel"); back(); ui.onNodeWithTag("v2-wheel-menu").assertDoesNotExist()
        click("v2-wheel"); click("v2-menu-demo")
        ui.onNodeWithTag("v2-knowledge-card").assertExists(); ui.onNodeWithTag("v2-wheel-menu").assertDoesNotExist()
        back(); ui.onNodeWithTag("v2-knowledge-card").assertDoesNotExist(); ui.onNodeWithTag("cockpit-v2").assertExists()
        click("v2-wheel"); click("v2-go-settings"); ui.onNodeWithTag("reset-layouts").assertExists()
        click("open-cockpit-v2"); click("v2-wheel"); click("v2-go-dashboard"); ui.onNodeWithTag("dashboard").assertExists()
    }

    @Test fun disabledCoreAndVoiceDoNotLaunchAnythingAndKeyboardDoneOnlyDismissesInput() {
        open()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            var starts = 0
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                starts++; return Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ui.onNodeWithTag("v2-send").assertIsNotEnabled().assertContentDescriptionEquals("Envoyer")
            ui.onNodeWithTag("v2-microphone").assertIsNotEnabled().assertContentDescriptionEquals("Microphone")
            ui.onNodeWithTag("v2-command-explanation").assertTextContains("non connectés", substring = true)
            ui.onNodeWithTag("v2-send").performTouchInput { click() }
            ui.onNodeWithTag("v2-microphone").performTouchInput { click() }
            ui.onNodeWithTag("v2-draft").performTextInput("AUCUN ENVOI TEST")
            ui.onNodeWithTag("v2-draft").performImeAction()
            ui.onNodeWithTag("v2-draft").assertTextContains("AUCUN ENVOI TEST")
            click("v2-wheel"); listOf("CORE", "SERVICES", "VOICE").forEach { ui.onNodeWithTag("v2-unavailable-$it").assertIsNotEnabled() }
            back(); assertEquals(0, monitor.starts)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun configurationRecreationRetainsOnlyLiveSessionAndInput() {
        open(); ui.onNodeWithTag("v2-draft").performTextInput("ROTATION RAM TEST")
        click("v2-demo-open")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("cockpit-v2").assertExists(); ui.onNodeWithTag("v2-draft").assertTextContains("ROTATION RAM TEST")
        ui.onNodeWithTag("v2-knowledge-card").assertExists()
        back(); back(); ui.onNodeWithTag("dashboard").assertExists()
    }

    @Test fun wheelAcceptsKeyboardActivationAndHasFingerSizedTargets() {
        open()
        val wheel = ui.onNodeWithTag("v2-wheel")
        if (!wheel.isDisplayed()) wheel.performScrollTo()
        wheel.assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        wheel.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        wheel.performKeyInput { pressKey(Key.Enter) }
        ui.onNodeWithTag("v2-wheel-menu").assertIsDisplayed()
        ui.onNodeWithTag("v2-go-dashboard").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
    }
}
