package fr.jarvis.m55d

/** Graphics modes: the conversation always has priority; ECONOMY keeps every control. */
enum class RenderMode(val points: Int, val particles: Int, val targetFps: Int, val glow: Boolean) {
    CINEMATIC(900, 60, 60, true), BALANCED(450, 20, 60, true), ECONOMY(160, 0, 30, false);
    fun lower() = entries.getOrElse(ordinal + 1) { this }
    fun higher() = entries.getOrElse(ordinal - 1) { this }
}

/**
 * Hysteresis regulator on measured frame times and Android thermal status. Downgrades fast when the
 * p95 frame time exceeds the budget or the device heats up; upgrades slowly; never changes mode more
 * often than [minDwellMs]. Thermal >= SEVERE forces ECONOMY. Pure and clock-injected for tests.
 */
class RenderRegulator(var mode: RenderMode = RenderMode.BALANCED, private val minDwellMs: Long = 3_000, private val window: Int = 90) {
    private val frames = ArrayDeque<Float>()
    private var lastChange = Long.MIN_VALUE / 2
    private var goodWindows = 0
    private var badWindows = 0
    var changes = 0; private set
    /** false after a manual choice: only the thermal safety (>= SEVERE -> ECONOMY) still applies. */
    var auto = true

    /** thermal: PowerManager.THERMAL_STATUS_* (0 none … 3 severe … 6 shutdown). Returns true if the mode changed. */
    fun onFrame(frameMs: Float, nowMs: Long, thermal: Int = 0): Boolean {
        if (thermal >= 3 && mode != RenderMode.ECONOMY) return change(RenderMode.ECONOMY, nowMs)
        if (!auto) return false
        frames.addLast(frameMs); if (frames.size < window) return false
        val p95 = frames.sorted()[maxOf(0, (frames.size * 95) / 100 - 1)].also { frames.clear() }
        val budget = 1000f / mode.targetFps
        when {
            p95 > budget * 1.25f || thermal >= 2 -> { badWindows++; goodWindows = 0 }
            p95 < budget * 0.70f && thermal <= 1 -> { goodWindows++; badWindows = 0 }
            else -> { goodWindows = 0; badWindows = 0 }
        }
        if (nowMs - lastChange < minDwellMs) return false
        if (badWindows >= 2 && mode != RenderMode.ECONOMY) return change(mode.lower(), nowMs)
        if (goodWindows >= 4 && mode != RenderMode.CINEMATIC) return change(mode.higher(), nowMs)
        return false
    }
    fun force(m: RenderMode, nowMs: Long) { if (m != mode) change(m, nowMs) }
    private fun change(m: RenderMode, nowMs: Long): Boolean {
        mode = m; lastChange = nowMs; goodWindows = 0; badWindows = 0; frames.clear(); changes++; return true
    }
}

/** Frame-time statistics actually measured on the device (Compose frame clock). */
class FrameStats(private val cap: Int = 4000) {
    private val samples = ArrayList<Float>()
    @Synchronized fun add(ms: Float) { if (samples.size < cap) samples.add(ms) }
    @Synchronized fun reset() = samples.clear()
    @Synchronized fun summary(): Map<String, Any> {
        if (samples.isEmpty()) return mapOf("frames" to 0)
        val s = samples.sorted(); val total = samples.sum()
        return mapOf("frames" to samples.size, "fps" to Math.round(samples.size * 1000.0 / total * 10) / 10.0,
            "p50Ms" to s[s.size / 2], "p95Ms" to s[maxOf(0, (s.size * 95) / 100 - 1)],
            "jankPercent" to Math.round(samples.count { it > 1000f / 60 * 1.5f } * 1000.0 / samples.size) / 10.0)
    }
}
