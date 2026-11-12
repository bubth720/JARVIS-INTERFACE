package fr.jarvis.m55d

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class M55dUnitTest {
    @Test fun fibonacciPointsAreOnTheUnitSphereAndSpread() {
        val pts = SphereModel.fibonacci(500)
        assertEquals(500, pts.size)
        pts.forEach { assertEquals(1f, sqrt(it.x * it.x + it.y * it.y + it.z * it.z), 1e-4f) }
        assertTrue(pts.count { it.y > 0 } in 240..260) // balanced hemispheres
    }
    @Test fun rotationPreservesNormAndYawTurnsAroundVerticalAxis() {
        val p = SphereModel.P3(1f, 0f, 0f)
        val r = SphereModel.rotate(p, (Math.PI / 2).toFloat(), 0f)
        assertEquals(0f, r.x, 1e-5f); assertEquals(-1f, r.z, 1e-5f)
        val q = SphereModel.rotate(SphereModel.P3(0.3f, 0.5f, 0.81f), 1.1f, -0.7f)
        assertEquals(sqrt(0.3f * 0.3f + 0.25f + 0.81f * 0.81f), sqrt(q.x * q.x + q.y * q.y + q.z * q.z), 1e-5f)
    }
    @Test fun projectionPutsFrontPointsLargerThanBackPoints() {
        val front = SphereModel.project(SphereModel.P3(0f, 0f, 1f), 100f, 0f, 0f)
        val back = SphereModel.project(SphereModel.P3(0f, 0f, -1f), 100f, 0f, 0f)
        assertTrue(front.scale > back.scale); assertEquals(1f, front.depth)
    }
    @Test fun inertiaDecaysToRest() {
        var v = 3f; repeat(300) { v = SphereModel.decay(v, 1 / 60f) }
        assertEquals(0f, v, 0f)
        assertTrue(abs(SphereModel.decay(3f, 0.45f) - 1.5f) < 1e-3f) // half-life
    }
    @Test fun regulatorDowngradesOnSlowFramesWithHysteresisAndUpgradesSlowly() {
        val r = RenderRegulator(RenderMode.CINEMATIC, minDwellMs = 3_000, window = 30)
        var t = 10_000L
        repeat(30) { r.onFrame(30f, t) }; assertEquals(RenderMode.CINEMATIC, r.mode) // one bad window is not enough
        repeat(30) { r.onFrame(30f, t) }; assertEquals(RenderMode.BALANCED, r.mode)
        repeat(60) { r.onFrame(30f, t + 100) }; assertEquals("dwell time blocks a second change", RenderMode.BALANCED, r.mode)
        t += 4_000; repeat(60) { r.onFrame(30f, t) }; assertEquals(RenderMode.ECONOMY, r.mode)
        t += 4_000; repeat(30 * 3) { r.onFrame(8f, t) }; assertEquals("3 good windows are not enough", RenderMode.ECONOMY, r.mode)
        repeat(30) { r.onFrame(8f, t) }; assertEquals(RenderMode.BALANCED, r.mode)
    }
    @Test fun severeThermalForcesEconomyImmediately() {
        val r = RenderRegulator(RenderMode.CINEMATIC)
        assertTrue(r.onFrame(5f, 0, thermal = 3)); assertEquals(RenderMode.ECONOMY, r.mode)
    }
    @Test fun economyModeKeepsTheConversationCheapButVisible() {
        assertTrue(RenderMode.ECONOMY.points < RenderMode.BALANCED.points && RenderMode.ECONOMY.particles == 0 && !RenderMode.ECONOMY.glow)
    }
    @Test fun stateHolderFallsBackToNeutralAndRefusesStaleEventsButKeepsErrors() {
        val h = CockpitStateHolder(silenceMs = 1_000)
        assertEquals(CockpitState.IDLE, h.stateAt(0))
        assertTrue(h.accept(CockpitEvent("s", CockpitState.THINKING, EventSource.CORE, atMillis = 100)))
        assertEquals(CockpitState.THINKING, h.stateAt(500)); assertEquals(CockpitState.IDLE, h.stateAt(1_200))
        assertFalse(h.accept(CockpitEvent("s", CockpitState.SPEAKING, EventSource.CORE, atMillis = 50)))
        h.accept(CockpitEvent("s", CockpitState.ERROR, EventSource.CORE, errorCode = "X", atMillis = 2_000))
        assertEquals(CockpitState.ERROR, h.stateAt(60_000))
        assertTrue(runCatching { CockpitEvent("s", CockpitState.SPEAKING, EventSource.VOICE, audioLevel = 2f, atMillis = 0) }.isFailure)
    }
    @Test fun frameStatsReportFpsP95AndJank() {
        val s = FrameStats(); repeat(90) { s.add(16f) }; repeat(10) { s.add(40f) }
        val m = s.summary(); assertEquals(100, m["frames"]); assertEquals(10.0, m["jankPercent"])
        assertEquals(40f, m["p95Ms"]); assertTrue((m["fps"] as Double) in 50.0..55.0)
        assertEquals(0, FrameStats().summary()["frames"])
    }
}
