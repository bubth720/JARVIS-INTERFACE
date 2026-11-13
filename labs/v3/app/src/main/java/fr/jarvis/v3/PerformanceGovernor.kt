package fr.jarvis.v3

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.jarvis.m55d.FrameStats
import fr.jarvis.m55d.RenderMode
import fr.jarvis.m55d.RenderRegulator
import fr.jarvis.v3.model.HoloDetail
import fr.jarvis.v3.model.SystemMeasure
import java.text.DateFormat
import java.util.Date

/**
 * §09: three profiles (Economy / Balanced / Cinematic) chosen by the reused M55-D hysteresis regulator
 * from measured vsync intervals and the Android thermal status. Automatic degradation on throttling,
 * battery saver or low battery; animations paused in background, with Android reduced motion, or in
 * the owner's static mode. No Android protection is ever disabled.
 */
class PerformanceGovernor(private val context: Context) {
    val regulator = RenderRegulator(RenderMode.BALANCED)
    val stats = FrameStats()
    var mode by mutableStateOf(regulator.mode); private set
    var automatic by mutableStateOf(true); private set
    /** Owner's choice in Settings: a still sphere (GPU, heat or accessibility). */
    var staticMode by mutableStateOf(false)
    var reducedMotion by mutableStateOf(false); private set
    var lowPower by mutableStateOf(false); private set
    private var thermal = 0; private var lastThermalRead = 0L

    /** Effective profile: low power or severe heat always caps to Economy. */
    val effectiveMode: RenderMode get() = if (lowPower) RenderMode.ECONOMY else mode
    val detail: HoloDetail get() = HoloDetail.of(effectiveMode)
    fun animate(resumed: Boolean) = resumed && !reducedMotion && !staticMode

    /** Re-read system conditions (called on resume and periodically). */
    fun refresh() {
        reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val pm = context.getSystemService(PowerManager::class.java)
        val battery = batteryPercent(context)
        lowPower = pm.isPowerSaveMode || (battery != null && battery <= 15 && charging(context) == false)
        thermal = pm.currentThermalStatus; lastThermalRead = SystemClock.elapsedRealtime()
    }

    /** One vsync interval measured by the sphere's frame loop. */
    fun onVsync(dtMs: Float) {
        stats.add(dtMs)
        val now = SystemClock.elapsedRealtime()
        if (now - lastThermalRead > 2_000) refresh()
        if (regulator.onFrame(dtMs, now, thermal)) mode = regulator.mode
    }
    fun force(m: RenderMode) { regulator.auto = false; automatic = false; regulator.force(m, SystemClock.elapsedRealtime()); mode = regulator.mode }
    fun auto() { regulator.auto = true; automatic = true }

    companion object {
        private fun batteryIntent(c: Context): Intent? =
            androidx.core.content.ContextCompat.registerReceiver(c, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        fun batteryPercent(c: Context): Int? = batteryIntent(c)?.let { i ->
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) l * 100 / s else null }
        fun charging(c: Context): Boolean? = batteryIntent(c)?.let { i ->
            when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null } }

        /** Real measurement only (no permission needed). Unknown values stay null, never invented. */
        fun measure(c: Context): SystemMeasure {
            val i = batteryIntent(c)
            val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
            val mem = ActivityManager.MemoryInfo().also { (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            val thermal = when (c.getSystemService(PowerManager::class.java).currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "normal"; PowerManager.THERMAL_STATUS_LIGHT -> "léger"
                PowerManager.THERMAL_STATUS_MODERATE -> "modéré"; PowerManager.THERMAL_STATUS_SEVERE -> "sévère"
                else -> "critique" }
            return SystemMeasure(batteryPercent(c), charging(c), temp, thermal, mem.availMem / (1 shl 20), mem.totalMem / (1 shl 20),
                runCatching { StatFs(c.filesDir.path).availableBytes / (1 shl 20) }.getOrNull(),
                DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date()))
        }
    }
}
