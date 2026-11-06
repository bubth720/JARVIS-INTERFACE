package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.cockpit.LocalWorkspace
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fiveLocalFormsSaveAndSurviveActivityRecreation() {
        val context = compose.activity
        context.getSharedPreferences("ui-01", 0).edit().putString("route", "Accueil").putBoolean("demo", false).putBoolean("secure", false).commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-Accueil").performClick()
        listOf(1 to "Administratif", 2 to "Finance", 3 to "Projets", 4 to "Écriture", 5 to "Création").forEach { (index, name) ->
            val title = "TEST FIXTURE $index ${System.nanoTime()}"
            compose.onNodeWithTag("module-$index").performScrollTo().performClick()
            compose.onNodeWithTag("$name-title").performScrollTo().performTextReplacement(title)
            if (name == "Finance") compose.onNodeWithTag("Finance-amount").performScrollTo().performTextReplacement("12,34")
            else compose.onNodeWithTag("$name-body").performScrollTo().performTextReplacement("TEST FIXTURE — contenu de validation")
            if (name in listOf("Administratif", "Projets")) compose.onNodeWithTag("$name-date").performScrollTo().performTextReplacement(java.time.LocalDate.now().toString())
            compose.onNodeWithTag("save-entry").performScrollTo().performClick()
            compose.waitUntil(15000) { LocalWorkspace(context).use { db -> db.entries(name).any { it.title == title } } }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("module-screen").assertExists()
            LocalWorkspace(context).use { db ->
                val saved = db.entries(name).single { it.title == title }
                if (name in listOf("Administratif", "Projets")) assertEquals(java.time.LocalDate.now().toString(), saved.due)
                if (name == "Finance") assertEquals(-1234L, saved.amountCents)
                else assertEquals("TEST FIXTURE — contenu de validation", saved.body)
            }
            compose.onNodeWithTag("close-settings").performScrollTo().performClick()
        }
    }
}
