package com.jarvis.interfaceapp.workspace

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.avatar.*
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MotionPreferenceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun reducedMotionFreezesExistingControllerAndCanResume() {
        val controller=AvatarAnimationController {10000}
        var reduced by mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent {JarvisTheme(VisualPreferences(reduceMotion=reduced)) {AvatarDemo(Modifier.width(300.dp),controller)}}
        compose.mainClock.advanceTimeBy(100);compose.waitForIdle()
        val before=compose.runOnIdle {controller.framesAdvanced}
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        compose.runOnIdle {assertEquals(before,controller.framesAdvanced);assertFalse(controller.status.value.advancing);reduced=false}
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle()
        compose.runOnIdle {assertTrue(controller.framesAdvanced>before)}
    }
}
