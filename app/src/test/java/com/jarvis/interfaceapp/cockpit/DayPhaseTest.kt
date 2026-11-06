package com.jarvis.interfaceapp.cockpit

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayPhaseTest {
    @Test fun allBoundariesUseLocalHourWithoutImplyingAstronomy() {
        val boundaries = mapOf(
            0 to DayPhase.NIGHT, 5 to DayPhase.NIGHT,
            6 to DayPhase.SUNRISE, 7 to DayPhase.SUNRISE,
            8 to DayPhase.MORNING, 11 to DayPhase.MORNING,
            12 to DayPhase.AFTERNOON, 16 to DayPhase.AFTERNOON,
            17 to DayPhase.SUNSET, 19 to DayPhase.SUNSET,
            20 to DayPhase.NIGHT, 23 to DayPhase.NIGHT
        )
        boundaries.forEach { (hour, expected) ->
            assertEquals(expected, dayPhase(LocalTime.of(hour, 59)))
        }
    }
}
