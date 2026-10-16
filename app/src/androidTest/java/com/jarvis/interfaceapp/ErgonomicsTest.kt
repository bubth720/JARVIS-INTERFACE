package com.jarvis.interfaceapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Bounded Compose windows, not device emulation or proof of spoken TalkBack output. */
@OptIn(ExperimentalTestApi::class)
class ErgonomicsTest {
    @get:Rule val compose = createComposeRule()
    private val controller get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JarvisApplication).dashboard
    @Before fun defaults() {
        controller.reset(); controller.startSession(); awaitWrites()
    }
    private fun awaitWrites() {
        runBlocking { withTimeout(30_000) { controller.awaitIdle() } }
        assertEquals(SavePhase.SAVED, controller.state.value.storage.phase)
    }
    private fun window(height: Int, fontScale: Float = 1f) {
        val vm = HomeViewModel(controller)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.requiredSize(360.dp, height.dp)) { JarvisTheme { JarvisApp(vm) } }
            }
        }
    }
    private fun reach(tag: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        return node.assertIsDisplayed()
    }
    private fun geometry() = compose.onNodeWithTag("panel-AVATAR").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription]

    @Test fun shortWindowKeepsEditingHandlesReachable() {
        window(320)
        reach("toggle-edit").performClick()
        reach("move-AVATAR")
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        reach("toggle-edit").performClick()
        compose.onNodeWithTag("move-AVATAR").assertDoesNotExist()
    }
    @Test fun doubleTextInShortWindowKeepsSettingsAndSavedStatusReachable() {
        window(360, 2f)
        reach("open-settings").performClick()
        reach("reset-layouts")
        reach("close-settings").performClick()
        reach("storage-status")
        reach("toggle-edit").performClick()
        reach("resize-AVATAR")
    }
    @Test fun accessibilityActionsPersistAndRespectLocksAndMinimumSizes() {
        window(600)
        compose.onNodeWithTag("toggle-edit").performClick()
        compose.onNodeWithTag("move-AVATAR").performCustomAccessibilityActionWithLabel("Déplacer en bas")
        awaitWrites(); compose.waitForIdle()
        assertTrue(geometry().startsWith("0,1 "))
        compose.onNodeWithTag("resize-AVATAR").performCustomAccessibilityActionWithLabel("Réduire la hauteur")
        awaitWrites(); compose.waitForIdle()
        val minimum = geometry()
        assertTrue(minimum.endsWith("3x3"))
        compose.onNodeWithTag("resize-AVATAR").performCustomAccessibilityActionWithLabel("Réduire la hauteur")
        awaitWrites(); compose.waitForIdle()
        assertEquals(minimum, geometry())
        compose.onNodeWithText("Taille refusée : minimum 3×3 cellules.").assertIsDisplayed()
        compose.onNodeWithTag("lock-AVATAR").performClick()
        awaitWrites(); compose.waitForIdle()
        compose.onNodeWithTag("move-AVATAR").assertIsNotEnabled()
        assertFalse(compose.onNodeWithTag("move-AVATAR").fetchSemanticsNode().config
            .contains(SemanticsActions.CustomActions))
    }
    @Test fun scrollingPanelContentInEditingModeDoesNotMoveIt() {
        window(600)
        compose.onNodeWithTag("toggle-edit").performClick()
        val before = geometry()
        compose.onNodeWithTag("avatar-placeholder").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(before, geometry())
        compose.onNodeWithTag("move-COMMANDS").performScrollTo().assertIsDisplayed()
        assertEquals(before, geometry())
    }
}
