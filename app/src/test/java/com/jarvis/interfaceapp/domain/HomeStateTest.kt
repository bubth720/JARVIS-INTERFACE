package com.jarvis.interfaceapp.domain

import org.junit.Assert.*
import org.junit.Test

class HomeStateTest {
    @Test fun startupRequiresNoExternalState() {
        assertEquals(HomeState(settingsOpen = false), HomeState())
    }
    @Test fun settingsCanBeOpenedAndClosed() {
        val opened = reduceHome(HomeState(), HomeAction.OpenSettings)
        assertTrue(opened.settingsOpen)
        assertFalse(reduceHome(opened, HomeAction.CloseSettings).settingsOpen)
    }
    @Test fun repeatedNavigationIsIdempotent() {
        val opened = reduceHome(HomeState(), HomeAction.OpenSettings)
        assertEquals(opened, reduceHome(opened, HomeAction.OpenSettings))
    }
}
