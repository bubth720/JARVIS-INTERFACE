package com.jarvis.interfaceapp.cockpitv2

import org.junit.Assert.*
import org.junit.Test

class CockpitSessionTest {
    @Test fun newSessionAlwaysStartsOutsideCockpitWithNoPersonalState() {
        val first = CockpitSession().apply { open(); editDraft("RAM TEST"); showKnowledge(); toggleWheel() }
        assertNotEquals(CockpitState(), first.state.value)
        assertEquals(CockpitState(), CockpitSession().state.value)
    }
    @Test fun explicitOpeningStartsWithClosedWheelAndKnowledge() {
        val session = CockpitSession(); session.open()
        assertEquals(CockpitState(open = true), session.state.value)
    }
    @Test fun wheelIsSingleReversibleToggle() {
        val session = CockpitSession(); session.open(); session.toggleWheel()
        assertTrue(session.state.value.wheelOpen); session.toggleWheel()
        assertFalse(session.state.value.wheelOpen)
    }
    @Test fun demoClosesWheelAndPreservesLocalDraft() {
        val session = CockpitSession(); session.open(); session.editDraft("LOCAL TEST"); session.toggleWheel(); session.showKnowledge()
        assertTrue(session.state.value.knowledgeOpen); assertFalse(session.state.value.wheelOpen)
        session.hideKnowledge(); assertEquals("LOCAL TEST", session.state.value.draft)
    }
    @Test fun backClosesMenuThenCardThenSessionAndErasesDraft() {
        val session = CockpitSession(); session.open(); session.editDraft("PRIVATE TEST"); session.showKnowledge(); session.toggleWheel()
        session.back(); assertFalse(session.state.value.wheelOpen); assertTrue(session.state.value.knowledgeOpen)
        session.back(); assertFalse(session.state.value.knowledgeOpen); assertTrue(session.state.value.open)
        session.back(); assertEquals(CockpitState(), session.state.value)
    }
    @Test fun closingAndReopeningDoesNotRestoreInput() {
        val session = CockpitSession(); session.open(); session.editDraft("TEST"); session.close(); session.open()
        assertEquals("", session.state.value.draft)
    }
    @Test fun inputIsBoundedAndCannotActivateClosedSession() {
        val session = CockpitSession(); session.editDraft("IGNORED"); session.showKnowledge(); session.toggleWheel()
        assertEquals(CockpitState(), session.state.value)
        session.open(); session.editDraft("x".repeat(3_000)); assertEquals(2_000, session.state.value.draft.length)
    }
}
