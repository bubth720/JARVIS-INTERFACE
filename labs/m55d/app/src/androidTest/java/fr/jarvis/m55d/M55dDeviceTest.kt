package fr.jarvis.m55d

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Emulator benchmark + functional checks of the M55-D cockpit (NOT a phone measurement). Uses real
 * activity frames (the sphere animates continuously, so no Compose idling) and real touch events.
 */
@RunWith(AndroidJUnit4::class)
class M55dDeviceTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun main(block: () -> Unit) = inst.runOnMainSync(block)

    @Test fun manifestHasNoPermissionAndIsTestOnly() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        // androidx adds its own signature-level "<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
        // the requirement is: no network, microphone, storage, camera, location or other sensitive permission.
        val unexpected = requested.filterNot { it == ctx.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
        assertTrue("unexpected permissions: $unexpected", unexpected.isEmpty())
        assertNotEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_TEST_ONLY)
    }

    /** Real system input path (same as a finger through InputManager), not an in-process synthetic dispatch. */
    private fun swipe(fromX: Float, toX: Float, y: Float) {
        inst.uiAutomation.executeShellCommand("input swipe ${fromX.toInt()} ${y.toInt()} ${toX.toInt()} ${y.toInt()} 400").close()
        SystemClock.sleep(900)
    }

    @Test fun cockpitBenchmarkRotationModesStatesAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2_000)
            val vm = Lab.current()!!
            val report = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("platform", "Android emulator x86_64 (NOT a phone)")
            // Real touch rotation on the sphere area (left part of the middle row).
            val b = requireNotNull(vm.rotation.screenBounds) { "sphere not laid out" }
            var yaw0 = 0f; main { yaw0 = vm.rotation.yaw }
            swipe(b.left + b.width * 0.80f, b.left + b.width * 0.20f, b.top + b.height / 2)
            var yaw1 = 0f; main { yaw1 = vm.rotation.yaw }
            report.put("sphereBoundsPx", b.toString()).put("swipeYawDelta", yaw1 - yaw0).put("dragEvents", vm.rotation.dragEvents)
            assertTrue("no drag event received (bounds $b)", vm.rotation.dragEvents > 0)
            assertTrue("swipe did not rotate the sphere (delta ${yaw1 - yaw0})", kotlin.math.abs(yaw1 - yaw0) > 0.2f)
            // 5 s of real frames per manual mode, then automatic regulation.
            val modes = JSONObject()
            for (m in RenderMode.entries) {
                main { vm.forceMode(m) }; SystemClock.sleep(500); vm.stats.reset(); SystemClock.sleep(5_000)
                modes.put(m.name, JSONObject(vm.stats.summary()))
            }
            main { vm.forceMode(RenderMode.CINEMATIC); vm.automatic() }; vm.stats.reset(); SystemClock.sleep(8_000)
            modes.put("AUTO_FROM_CINEMATIC", JSONObject(vm.stats.summary()).put("finalMode", vm.regulator.mode.name).put("changes", vm.regulator.changes))
            // State animation path (DEMO source, labelled, no answer text).
            main { vm.forceMode(RenderMode.BALANCED); vm.demoSubmit() }; vm.stats.reset(); SystemClock.sleep(800)
            var s1 = CockpitState.IDLE; var label = ""; main { s1 = vm.state; label = vm.sourceLabel }
            assertEquals(CockpitState.THINKING, s1); assertTrue(label.startsWith("DÉMO"))
            SystemClock.sleep(1_600)
            var s2 = CockpitState.IDLE; main { s2 = vm.state }; assertEquals(CockpitState.SPEAKING, s2)
            modes.put("BALANCED_ANIMATED_STATES", JSONObject(vm.stats.summary()))
            main { vm.stop() }; var s3 = CockpitState.SPEAKING; main { s3 = vm.state }; assertEquals(CockpitState.IDLE, s3)
            // Recreation (rotation-equivalent): process state kept, no crash.
            scenario.recreate(); SystemClock.sleep(1_000)
            scenario.onActivity { assertSame(vm, Lab.current()) }
            report.put("modes", modes).put("thermalStatus", vm.thermal()).put("recreated", true)
            File(ctx.filesDir, "m55d-report.json").writeText(report.toString(2))
        }
    }
}
