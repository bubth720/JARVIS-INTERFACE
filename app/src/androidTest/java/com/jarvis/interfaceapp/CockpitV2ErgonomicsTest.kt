package com.jarvis.interfaceapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.cockpitv2.*
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Bounded windows and semantics; does not claim spoken TalkBack or a physical device. */
class CockpitV2ErgonomicsTest {
    @get:Rule val ui = createComposeRule()
    private fun reach(tag: String) = ui.reachCockpit(tag)
    @Test fun shortWindowAndDoubleTextKeepEveryActionReachableWithoutOverlappingTargets() {
        val session = CockpitSession().apply { open() }
        ui.setContent {
            val state by session.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.requiredSize(360.dp, 320.dp)) {
                    JarvisTheme { CockpitV2Screen(state, session, session::close, session::close) }
                }
            }
        }
        reach("v2-avatar"); reach("v2-sphere-art")
        reach("v2-demo-open").performClick(); reach("v2-source-status")
        reach("v2-demo-close").performClick()
        reach("v2-draft").performTextInput("TEXTE AGRANDI TEST")
        ui.onNodeWithTag("v2-draft").performImeAction()
        val send = reach("v2-send").assertIsNotEnabled().fetchSemanticsNode()
        assertTrue(send.config[SemanticsProperties.StateDescription].contains("CORE non connecté"))
        val mic = reach("v2-microphone").assertIsNotEnabled().fetchSemanticsNode()
        assertFalse(send.boundsInRoot.overlaps(mic.boundsInRoot))
        reach("v2-wheel").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        reach("v2-go-dashboard").assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("v2-unavailable-CORE").assertIsNotEnabled()
        ui.runOnIdle { session.dismissWheel() }
        reach("v2-return").assertHeightIsAtLeast(48.dp)
        assertTrue(ui.onNodeWithTag("v2-sphere-art").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains("sans rotation 3D"))
    }
}
