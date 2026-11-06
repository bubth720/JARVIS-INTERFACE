package com.jarvis.interfaceapp.cockpit

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRulesTest {
    @Test fun manualMoneyUsesExactCentsAndRejectsInvalidAmounts() {
        assertEquals(1234L, parseEuroCents("12,34"))
        assertEquals(0L, parseEuroCents("0"))
        listOf("-3", "NaN", "0.001", "", "9999999999999999999999").forEach { assertNull(parseEuroCents(it)) }
    }
    @Test fun dueDatesAreRealIsoDates() {
        assertTrue(validDueDate("2028-02-29"))
        assertTrue(validDueDate(""))
        assertFalse(validDueDate("2026-02-29"))
        assertFalse(validDueDate("06/11/2026"))
    }
}
