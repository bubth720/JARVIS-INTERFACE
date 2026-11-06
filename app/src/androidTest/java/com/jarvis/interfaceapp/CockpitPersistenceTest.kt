package com.jarvis.interfaceapp

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.cockpit.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class CockpitPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun home() {
        compose.activity.getSharedPreferences("ui-01", android.content.Context.MODE_PRIVATE).edit().putString("route", "Accueil").putBoolean("demo", false).putBoolean("secure", false).commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Accueil").performClick()
    }

    @Test fun economyPreferenceSurvivesRecreationAndAndroidBack() {
        compose.activity.getSharedPreferences("ui-01", Context.MODE_PRIVATE).edit().putBoolean("economy", false).commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithTag("economy-switch").performScrollTo().assertIsOff().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("economy-switch").performScrollTo().assertIsOn()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("dashboard").assertExists()
    }

    @Test fun lastKnownObservationRetainsItsSourceAndTimestamp() {
        val preferences = compose.activity.getSharedPreferences("ui-01-weather", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertNull(LocalWeatherStore(compose.activity).lastKnown())
            // Synthetic fixture confined to test storage; cleared before screenshots.
            val fixture = WeatherObservation(Weather.SNOW, 1L, "TEST FIXTURE")
            LocalWeatherStore(compose.activity).record(fixture)
            assertEquals(fixture, LocalWeatherStore(compose.activity).lastKnown())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
