package fr.jarvis.v3.model

import fr.jarvis.m55d.RenderMode
import fr.jarvis.m55d.SphereModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detail budget of the holographic sphere per render profile (V3 §09). Values are initial budgets, not
 * measured performance: Economy 20 FPS, Balanced 30 FPS, Cinematic up to 60 FPS.
 */
data class HoloDetail(val nodes: Int, val latRings: Int, val meridians: Int, val ringSamples: Int, val glow: Boolean, val fps: Int, val orbitals: Int) {
    companion object {
        fun of(mode: RenderMode) = when (mode) {
            RenderMode.ECONOMY -> HoloDetail(nodes = 70, latRings = 3, meridians = 4, ringSamples = 36, glow = false, fps = 20, orbitals = 1)
            RenderMode.BALANCED -> HoloDetail(nodes = 110, latRings = 5, meridians = 6, ringSamples = 48, glow = true, fps = 30, orbitals = 2)
            RenderMode.CINEMATIC -> HoloDetail(nodes = 150, latRings = 7, meridians = 8, ringSamples = 64, glow = true, fps = 60, orbitals = 3)
        }
    }
}

/**
 * Pure geometry of the V3 sphere, built once per detail level: Fibonacci nodes (reused M55-D model),
 * a stable link network (each node to its nearest neighbours), "alternate" links that only appear while
 * JARVIS thinks, the geometric shell (latitudes + meridians) and a few rare accent nodes.
 */
class HoloGeometry(val detail: HoloDetail) {
    val nodes: List<SphereModel.P3> = SphereModel.fibonacci(detail.nodes)
    /** Permanent links (2 nearest neighbours, deduplicated). */
    val links: List<Pair<Int, Int>>
    /** Third/fourth neighbours: shown only in the THINKING state, fading in and out ("reorganising"). */
    val altLinks: List<Pair<Int, Int>>
    /** Shell polylines (closed), each a list of unit-sphere points. */
    val shell: List<List<SphereModel.P3>>
    /** Rare gold accent nodes (V3 palette: gold is rare). */
    val accents: Set<Int>
    /** Nodes that carry the conversation keywords, spread over the sphere. */
    val anchors: List<Int>

    init {
        val n = nodes.size
        val near = Array(n) { i ->
            (0 until n).filter { it != i }.sortedBy { j -> d2(nodes[i], nodes[j]) }.take(4)
        }
        val base = LinkedHashSet<Pair<Int, Int>>(); val alt = LinkedHashSet<Pair<Int, Int>>()
        for (i in 0 until n) {
            near[i].take(2).forEach { base += key(i, it) }
            near[i].drop(2).forEach { alt += key(i, it) }
        }
        alt.removeAll(base)
        links = base.toList(); altLinks = alt.toList()
        val rings = ArrayList<List<SphereModel.P3>>()
        for (k in 1..detail.latRings) {
            val y = -0.8f + 1.6f * k / (detail.latRings + 1)
            val r = sqrt(1 - y * y)
            rings += List(detail.ringSamples) { s -> val a = 2 * PI * s / detail.ringSamples; SphereModel.P3((cos(a) * r).toFloat(), y, (sin(a) * r).toFloat()) }
        }
        for (m in 0 until detail.meridians) {
            val phi = PI * m / detail.meridians
            rings += List(detail.ringSamples) { s -> val a = 2 * PI * s / detail.ringSamples
                SphereModel.P3((cos(phi) * sin(a)).toFloat(), cos(a).toFloat(), (sin(phi) * sin(a)).toFloat()) }
        }
        shell = rings
        accents = (0 until n step maxOf(1, n / 4)).map { (it * 7 + 3) % n }.toSet()
        anchors = List(6) { k -> ((k + 0.5) * n / 6).toInt().coerceIn(0, n - 1) }
    }

    companion object {
        private fun key(a: Int, b: Int) = if (a < b) a to b else b to a
        private fun d2(a: SphereModel.P3, b: SphereModel.P3): Float { val x = a.x - b.x; val y = a.y - b.y; val z = a.z - b.z; return x * x + y * y + z * z }

        /** Noise gate + exponential smoothing for audio-driven motion (no jitter on background noise). */
        fun smoothLevel(current: Float, target: Float, dtSeconds: Float, gate: Float = 0.06f, rate: Float = 8f): Float {
            val t = if (target < gate) 0f else target.coerceIn(0f, 1f)
            return current + (t - current) * minOf(1f, dtSeconds * rate)
        }

        /** Capped inertial angular velocity after a drag (rad/s). */
        fun capVelocity(v: Float, cap: Float = 4f) = v.coerceIn(-cap, cap)

        /** Breathing light of the idle state: slow, period ~4.5 s, amplitude 0..1. */
        fun breath(timeSeconds: Float) = (0.5f + 0.5f * sin(timeSeconds * (2 * PI / 4.5)).toFloat())
    }
}
