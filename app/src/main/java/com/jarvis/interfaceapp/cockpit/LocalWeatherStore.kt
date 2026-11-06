package com.jarvis.interfaceapp.cockpit

import android.content.Context

/** No fetch or fabricated default: only a future authorized adapter can record observations. */
class LocalWeatherStore(context: Context) : WeatherSource {
    private val preferences = context.getSharedPreferences("ui-01-weather", Context.MODE_PRIVATE)

    override fun lastKnown(): WeatherObservation? {
        val name = preferences.getString("weather", null) ?: return null
        val source = preferences.getString("source", null)?.takeIf { it.isNotBlank() } ?: return null
        val timestamp = preferences.getLong("observedAt", -1L)
        if (timestamp <= 0L) return null
        val weather = Weather.entries.firstOrNull { it.name == name } ?: return null
        return WeatherObservation(weather, timestamp, source)
    }

    fun record(observation: WeatherObservation) {
        require(observation.source.isNotBlank()) { "An identified source is required" }
        require(observation.observedAtEpochMillis in 1..System.currentTimeMillis()) { "Invalid observation time" }
        preferences.edit().putString("weather", observation.weather.name)
            .putString("source", observation.source)
            .putLong("observedAt", observation.observedAtEpochMillis).apply()
    }
}
