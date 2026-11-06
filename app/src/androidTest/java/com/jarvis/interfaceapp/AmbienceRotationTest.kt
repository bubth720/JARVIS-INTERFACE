package com.jarvis.interfaceapp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.cockpit.LocalWeatherStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AmbienceRotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun simulationAndOrientationPreserveActivityAndRealWeather() {
        val prefs = compose.activity.getSharedPreferences("ui-01", 0)
        prefs.edit().putString("route", "Accueil").putBoolean("demo", false).commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Accueil").performClick()
        val before = LocalWeatherStore(compose.activity).lastKnown()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("demo-switch").performScrollTo().performClick()
        compose.onNodeWithTag("weather-SNOW").performScrollTo().performClick()
        val activity = compose.activity
        compose.runOnIdle { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15000) { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertSame(activity, compose.activity)
        compose.onNodeWithText("Paramètres graphiques").assertExists()
        compose.onNodeWithTag("weather-SNOW").assertIsSelected()
        assertEquals(before, LocalWeatherStore(activity).lastKnown())
        compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        compose.onNodeWithTag("demo-status").assertExists()
        compose.runOnIdle { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(15000) { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertSame(activity, compose.activity)
        prefs.edit().putBoolean("demo", false).commit()
    }
}
