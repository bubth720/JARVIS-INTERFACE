package com.jarvis.interfaceapp.ui.identity

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.resetLayoutFixture
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class IdentitySettingsInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun defaults() { compose.resetLayoutFixture() }
    private fun bytes()=File(compose.activity.filesDir,"datastore/dashboard.json").readBytes()
    private fun open() { compose.onNodeWithTag("open-personality").performScrollTo().performClick() }
    private fun laboratory() { open();compose.onNodeWithTag("identity-lab-toggle").performScrollTo().performClick() }
    @Test fun disconnectedDefaultIsInertAndDoesNotChangeDashboardStorage() {
        val before=bytes();open()
        compose.onNodeWithTag("identity-status").assertTextContains("NON CONNECTÉ",substring=true)
        compose.onNodeWithTag("identity-propose").assertDoesNotExist()
        compose.onNodeWithTag("memory-projection").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("identity-close").performClick()
        compose.onNodeWithTag("dashboard").assertExists();assertArrayEquals(before,bytes())
    }
    @Test fun approvalStartsDisabledAndLaboratoryCommitNeverClaimsCorePersistence() {
        val before=bytes();laboratory()
        compose.onNodeWithTag("identity-humour").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        compose.onNodeWithTag("identity-propose").performScrollTo().performClick()
        compose.onNodeWithTag("identity-approve").assertIsNotEnabled()
        compose.onNodeWithTag("identity-approve-check").performScrollTo().performClick()
        compose.onNodeWithTag("identity-approve").assertIsEnabled().performClick()
        compose.onNodeWithTag("identity-status").assertTextContains("RAM seulement",substring=true)
        assertArrayEquals(before,bytes())
    }
    @Test fun actualActivityRecreationKeepsDraftAndBackClosesLaboratoryWithoutLayoutMutation() {
        val before=bytes();laboratory()
        compose.onNodeWithTag("identity-humour").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(17f) }
        compose.activityRule.scenario.recreate()
        val node=compose.onNodeWithTag("identity-humour").performScrollTo().fetchSemanticsNode()
        assertEquals(17f,node.config[SemanticsProperties.ProgressBarRangeInfo].current)
        compose.onNodeWithTag("identity-close").performClick();open()
        compose.onNodeWithTag("identity-status").assertTextContains("NON CONNECTÉ",substring=true)
        assertArrayEquals(before,bytes())
    }
    @Test fun inheritedContextNeedsExplicitOverrideAndControlsHaveAccessibleLabels() {
        laboratory()
        compose.onNodeWithTag("identity-context-WORK").performScrollTo().performClick()
        compose.onNodeWithTag("identity-humour").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("identity-context-override").performScrollTo().performClick()
        compose.onNodeWithTag("identity-humour").performScrollTo().assertIsEnabled()
        compose.onNodeWithContentDescription("Tutoiement").assertExists()
    }
    @Test fun memoryCandidateNeedsExplicitConsentAndProjectionRevocationClearsIt() {
        laboratory();compose.onNodeWithTag("memory-projection").performScrollTo().performClick()
        compose.onNodeWithTag("memory-input").performScrollTo().performTextInput("Préférence fictive de test public")
        compose.onNodeWithTag("memory-propose").performScrollTo().performClick()
        compose.onNodeWithTag("memory-consent-approve").assertIsNotEnabled()
        compose.onNodeWithTag("memory-consent-check").performScrollTo().performClick()
        compose.onNodeWithTag("memory-consent-approve").performClick()
        compose.onNodeWithTag("identity-status").assertTextContains("RAM uniquement",substring=true)
        compose.onNodeWithTag("memory-projection").performScrollTo().performClick()
        compose.onNodeWithText("Vue mémoire fermée · aucune donnée projetée").assertExists()
    }
}
