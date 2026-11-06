package com.jarvis.interfaceapp.cockpit

import java.time.LocalTime

enum class Weather { CLEAR, CLOUDS, RAIN, STORM, FOG, SNOW }
data class WeatherObservation(val weather: Weather, val observedAtEpochMillis: Long, val source: String)
/** Future adapters supply identified observations; null means unknown, never clear by default. */
interface WeatherSource { fun lastKnown(): WeatherObservation? }
interface ModuleGateway { fun connectionStatus(module: String): String }
enum class DayPhase { SUNRISE, MORNING, AFTERNOON, SUNSET, NIGHT }
fun dayPhase(time: LocalTime): DayPhase = when (time.hour) {
    in 6..7 -> DayPhase.SUNRISE
    in 8..11 -> DayPhase.MORNING
    in 12..16 -> DayPhase.AFTERNOON
    in 17..19 -> DayPhase.SUNSET
    else -> DayPhase.NIGHT
}
/** Time bands are artistic local-time presets, not calculated astronomical events. */
data class SceneReference(val phase: DayPhase, val weather: Weather?, val localUri: String)
