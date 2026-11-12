package fr.jarvis.m55d

import org.junit.Assert.assertEquals
import org.junit.Test

class RegulatorManualTest {
    @Test fun manualChoiceStopsAutomaticChangesButNotThermalSafety() {
        val r = RenderRegulator(RenderMode.CINEMATIC, window = 10); r.auto = false
        repeat(100) { r.onFrame(50f, 10_000L + it * 1000) }; assertEquals(RenderMode.CINEMATIC, r.mode)
        r.onFrame(5f, 200_000, thermal = 4); assertEquals(RenderMode.ECONOMY, r.mode)
    }
}
