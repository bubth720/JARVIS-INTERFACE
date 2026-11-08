package com.jarvis.interfaceapp.m51

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Emulator UI measurements only. No LLM, user content, physical thermals or energy inference. */
class M51FrameProbe {
    @Test fun captureBoundedWindow() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val args=InstrumentationRegistry.getArguments();val mode=requireNotNull(args.getString("benchMode"))
        require(mode in setOf("ANIMATED","ECO"))
        val duration=(args.getString("benchMillis") ?: "20000").toLong();require(duration in 15000..60000)
        require(context.packageName in setOf("fr.jarvis.v1.lab","fr.jarvis.v1.lab.baseline"))
        require(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
        val id=requireNotNull(args.getString("benchId"));require(id.matches(Regex("[a-z0-9-]{1,48}")))
        context.getSharedPreferences("ui-01",0).edit().putString("route","Accueil").putString("navHistory","")
            .putBoolean("economy",mode=="ECO").putInt("performance",if(mode=="ECO")0 else 1)
            .putFloat("intensity",.35f).putBoolean("glow",true).putBoolean("demo",true).putFloat("demoHour",12f)
            .putString("demoWeather","CLEAR").putBoolean("reduceMotion",false).putBoolean("secure",false).commit()
        val thread=HandlerThread("m51-frame-observer").apply { start() }
        val records=ArrayList<LongArray>();var overflow=false
        val listener=Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            val row=longArrayOf(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION),metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME),dropped.toLong(),
                if(Build.VERSION.SDK_INT>=31)metrics.getMetric(FrameMetrics.GPU_DURATION)else -1L)
            synchronized(records) { if(records.size<8192)records.add(row)else overflow=true }
        }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val dir=File(context.filesDir,"m51-evidence").apply { mkdirs() }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            inst.waitForIdleSync();SystemClock.sleep(2000)
            File(dir,"$id-reset.txt").writeText(shell("dumpsys gfxinfo ${context.packageName} reset"))
            scenario.onActivity { assertTrue(it.window.decorView.isShown);it.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper)) }
            val start=SystemClock.elapsedRealtimeNanos();SystemClock.sleep(duration);val end=SystemClock.elapsedRealtimeNanos()
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely();thread.join(3000);assertFalse("Frame observer terminated",thread.isAlive)
            val values=JSONArray();synchronized(records) { records.forEach { values.put(JSONArray(it.toList())) } }
            val result=JSONObject().put("schema","jarvis.m51.frame-metrics/1").put("package",context.packageName)
                .put("mode",mode).put("api",Build.VERSION.SDK_INT).put("window_elapsed_start_nanos",start).put("window_elapsed_end_nanos",end)
                .put("duration_requested_ms",duration).put("clock_domain","ANDROID_FRAME_METRICS_REPORTED")
                .put("observer_window_clock","ANDROID_BOOTTIME").put("cross_clock_subtraction",false)
                .put("columns",JSONArray(listOf("intended_vsync_ns","vsync_ns","total_duration_ns","first_draw","dropped_callbacks","gpu_duration_ns")))
                .put("rows",values).put("overflow",overflow).put("llm_generation","NOT_RUN").put("physical_android","NOT_RUN")
            File(dir,"$id.json").writeText(result.toString(2))
            File(dir,"$id-gfxinfo.txt").writeText(shell("dumpsys gfxinfo ${context.packageName} framestats"))
            File(dir,"$id-memory.txt").writeText(shell("dumpsys meminfo ${context.packageName}"))
            inst.uiAutomation.takeScreenshot()?.also { bitmap -> File(dir,"$id.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
            assertFalse("Bounded observer did not overflow",overflow)
        } } finally { thread.quitSafely();thread.join(3000) }
    }
}
