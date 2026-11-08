package com.jarvis.interfaceapp.r1

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** TestAPK only. Observes Java stacks and draw callback identity; never invokes Compose drawing. */
class DiagnosticListener : RunListener() {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val directory get()=File(inst.targetContext.filesDir,"m51-r1").apply { mkdirs() }
    private val lock=Any()
    private val running=AtomicBoolean(false)
    private val draws=AtomicInteger(0)
    private val offMainDraws=AtomicInteger(0)
    private var sampler: Thread?=null
    private var bytesWritten=0L
    private var truncated=false
    private var testThreadId=0L
    private var active=""
    private val callback=ActivityLifecycleCallback { activity: Activity, stage: Stage ->
        if(stage==Stage.RESUMED) {
            activity.window.decorView.viewTreeObserver.addOnDrawListener {
                draws.incrementAndGet()
                if(Looper.myLooper()!=Looper.getMainLooper()) offMainDraws.incrementAndGet()
            }
        }
    }
    private fun append(file: String, row: JSONObject) = synchronized(lock) {
        val line=row.toString()+"\n"
        if(bytesWritten+line.toByteArray(Charsets.UTF_8).size>262144) { truncated=true;return@synchronized }
        File(directory,file).appendText(line);bytesWritten+=line.toByteArray(Charsets.UTF_8).size
    }
    private fun event(kind: String) = JSONObject().put("event",kind).put("test",active)
        .put("elapsed_nanos",SystemClock.elapsedRealtimeNanos()).put("thread_id",Thread.currentThread().id)
        .put("thread_name",Thread.currentThread().name).put("main_thread_id",Looper.getMainLooper().thread.id)
        .put("draw_callbacks",draws.get()).put("off_main_draw_callbacks",offMainDraws.get())
    override fun testRunStarted(description: Description) {
        check(inst.targetContext.packageName in setOf("fr.jarvis.v1.lab","fr.jarvis.v1.lab.baseline"))
        inst.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
    }
    override fun testStarted(description: Description) {
        active=description.className+"#"+description.methodName;testThreadId=Thread.currentThread().id
        append("events.jsonl",event("TEST_STARTED"));running.set(true)
        sampler=Thread({
            while(running.get() && !truncated) {
                val all=Thread.getAllStackTraces();val rows=JSONArray()
                all.entries.filter { (t,frames) -> t.id==testThreadId || t==Looper.getMainLooper().thread || frames.any { it.className.contains("SnapshotStateObserver") } }.take(8).forEach { (t,frames) ->
                    rows.put(JSONObject().put("id",t.id).put("name",t.name).put("state",t.state.toString())
                        .put("stack",JSONArray(frames.take(14).map { it.toString() })))
                }
                append("threads.jsonl",event("STACK_SAMPLE").put("threads",rows))
                try { Thread.sleep(100) } catch(_: InterruptedException) { break }
            }
        },"m51r1-stack-observer").apply { isDaemon=true;start() }
    }
    override fun testFailure(failure: Failure) {
        append("events.jsonl",event("TEST_FAILURE").put("exception_class",failure.exception.javaClass.name))
        // Shell/UiAutomation capture is performed by the host after the invocation. No draw or
        // snapshot read is injected here, including while the target process is failing.
    }
    override fun testFinished(description: Description) {
        stopSampler();append("events.jsonl",event("TEST_FINISHED").put("truncated",truncated))
    }
    private fun stopSampler() { running.set(false);sampler?.interrupt();sampler?.join(1000);check(sampler?.isAlive!=true);sampler=null }
    override fun testRunFinished(result: Result) {
        stopSampler();inst.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        append("events.jsonl",event("RUN_FINISHED").put("run_count",result.runCount).put("failures",result.failureCount).put("truncated",truncated))
    }
}
