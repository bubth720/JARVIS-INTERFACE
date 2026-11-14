package fr.jarvis.v3.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The five first-level destinations of the radial menu (§06). The menu publishes intents only. */
enum class Destination(val label: String, val available: Boolean, val unavailableReason: String? = null) {
    COCKPIT("Cockpit", true),
    KNOWLEDGE("Connaissances", true),
    DOCUMENTS("Documents", false, "NEXUS non connecté"),
    SERVICES("Services", false, "SERVICES non connecté"),
    SETTINGS("Réglages", true),
}

object RadialGeometry {
    const val RADIUS_DP = 165f
    /** The arc centre is lifted above the dock anchor: the lowest item stays above the input field. */
    const val LIFT_DP = 72f

    /**
     * Item centres (dp offsets from the anchor button centre) on an arc opening up-left from a
     * bottom-right anchor: from 180° (left) to 270° (up), evenly spread.
     */
    fun positions(count: Int, radiusDp: Float, startDeg: Float = 180f, endDeg: Float = 270f): List<Pair<Float, Float>> =
        List(count) { i ->
            val a = Math.toRadians((startDeg + (endDeg - startDeg) * (if (count == 1) 0.5f else i / (count - 1f))).toDouble())
            Pair((cos(a) * radiusDp).toFloat(), (sin(a) * radiusDp).toFloat())
        }

    /** Minimum chord between adjacent items, to check 48 dp touch targets never overlap. */
    fun minChordDp(count: Int, radiusDp: Float, spanDeg: Float = 90f): Float =
        if (count < 2) Float.MAX_VALUE else (2 * radiusDp * sin((spanDeg / (count - 1)) * PI / 360)).toFloat()
}
