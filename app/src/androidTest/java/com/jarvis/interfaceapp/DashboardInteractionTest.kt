package com.jarvis.interfaceapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Portrait device, standalone / airplane mode. Actual execution requires a device. */
class DashboardInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetFixture() { compose.resetLayoutFixture() }
    private fun geometry() = compose.onNodeWithTag("panel-AVATAR").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription]
    private fun cellHeight(): Float {
        val rows = geometry().substringAfter('x').toInt()
        return (compose.onNodeWithTag("panel-AVATAR").fetchSemanticsNode().boundsInRoot.height +
            with(compose.density) { androidx.compose.ui.unit.Dp(8f).toPx() }) / rows
    }
    @Test fun fourPanelsAndEditModeAreAvailable() {
        listOf("AVATAR", "SYSTEM", "APPLICATIONS", "COMMANDS").forEach {
            compose.onNodeWithTag("panel-$it").assertExists()
        }
        compose.onNodeWithTag("move-AVATAR").assertDoesNotExist()
        compose.onNodeWithTag("toggle-edit").performClick()
        compose.onNodeWithTag("move-AVATAR").assertIsDisplayed()
        compose.onNodeWithTag("toggle-edit").performClick()
        compose.onNodeWithTag("move-AVATAR").assertDoesNotExist()
    }
    @Test fun draggingMoveHandleSnapsToNextRow() {
        compose.onNodeWithTag("toggle-edit").performClick()
        val before = geometry()
        val cell = cellHeight()
        compose.onNodeWithTag("move-AVATAR").performTouchInput {
            swipe(center, center + Offset(0f, cell), durationMillis = 500)
        }
        assertEquals(before.replace("0,0", "0,1"), geometry())
    }
    @Test fun draggingResizeHandleAddsOneRow() {
        compose.onNodeWithTag("toggle-edit").performClick()
        val before = geometry()
        val height = before.substringAfter('x').toInt()
        val cell = cellHeight()
        compose.onNodeWithTag("resize-AVATAR").performTouchInput {
            swipe(center, center + Offset(0f, cell), durationMillis = 500)
        }
        assertEquals(before.substringBefore('x') + "x${height + 1}", geometry())
    }
    @Test fun normalModeSwipeCannotMovePanel() {
        val before = geometry()
        compose.onNodeWithTag("panel-AVATAR").performTouchInput { swipeDown() }
        assertEquals(before, geometry())
    }
    @Test fun lockDisablesBothGestureHandles() {
        compose.onNodeWithTag("toggle-edit").performClick()
        compose.onNodeWithTag("lock-AVATAR").performClick()
        // The actor saves asynchronously; Compose idleness alone is not its barrier.
        runBlocking { withTimeout(30_000) { (compose.activity.application as JarvisApplication).dashboard.awaitIdle() } }
        compose.waitForIdle()
        compose.onNodeWithTag("move-AVATAR").assertIsNotEnabled()
        compose.onNodeWithTag("resize-AVATAR").assertIsNotEnabled()
        compose.onNodeWithTag("lock-AVATAR").performClick()
        // The actor saves asynchronously; Compose idleness alone is not its barrier.
        runBlocking { withTimeout(30_000) { (compose.activity.application as JarvisApplication).dashboard.awaitIdle() } }
        compose.waitForIdle()
        compose.onNodeWithTag("move-AVATAR").assertIsEnabled()
    }
    @Test fun collisionRefusesGestureAndShowsReason() {
        compose.onNodeWithTag("toggle-edit").performClick()
        val before = geometry()
        val width = before.substringAfter(' ').substringBefore('x').toInt()
        val cell = (compose.onNodeWithTag("panel-AVATAR").fetchSemanticsNode().boundsInRoot.width +
            with(compose.density) { androidx.compose.ui.unit.Dp(8f).toPx() }) / width
        compose.onNodeWithTag("move-AVATAR").performTouchInput {
            swipe(center, center + Offset(cell, 0f), durationMillis = 500)
        }
        assertEquals(before, geometry())
        compose.onNodeWithText("Position refusée : chevauchement.").assertIsDisplayed()
    }
    @Test fun recreationKeepsEditedSessionLayout() {
        compose.onNodeWithTag("toggle-edit").performClick()
        val cell = cellHeight()
        compose.onNodeWithTag("move-AVATAR").performTouchInput {
            swipe(center, center + Offset(0f, cell), durationMillis = 500)
        }
        val moved = geometry()
        assertTrue(moved.startsWith("0,1 "))
        compose.activityRule.scenario.recreate()
        assertEquals(moved, geometry())
        compose.onNodeWithTag("move-AVATAR").assertIsDisplayed()
    }

}
