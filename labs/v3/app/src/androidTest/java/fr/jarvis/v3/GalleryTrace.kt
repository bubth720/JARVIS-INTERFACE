package fr.jarvis.v3

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Evidence written by a watchdog, including when the instrumentation or Android main thread is stuck. */
internal class GalleryTrace(private val file: File) : AutoCloseable {
    private data class Event(val name: String, val atMs: Long)

    private val start = SystemClock.elapsedRealtime()
    private val events = mutableListOf(Event("test-entered", start))
    private val testThread = Thread.currentThread()
    private val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "s04-gallery-watchdog").apply { isDaemon = true }
    }
    private var longestWait = 0L
    private var stall: JSONObject? = null

    init {
        watchdog.scheduleWithFixedDelay({
            try { write() }
            catch (e: Exception) { Log.w("JarvisS04", "Could not write gallery trace", e) }
        }, 1, 10, TimeUnit.SECONDS)
    }

    fun mark(name: String) {
        synchronized(events) { events.add(Event(name, SystemClock.elapsedRealtime())) }
        Log.i("JarvisS04", name)
    }

    @Synchronized
    private fun write() {
        val entries = synchronized(events) { events.toList() }
        val age = SystemClock.elapsedRealtime() - entries.last().atMs
        val report = JSONObject()
            .put("lastEvent", entries.last().name).put("eventAgeMs", age)
            .put("events", JSONArray(entries.map { JSONObject().put("event", it.name).put("elapsedMs", it.atMs - start) }))
            .put("coroutineSignals", JSONObject(RenderDiagnostics.active?.counts().orEmpty()))
        if (age >= 10_000 && age > longestWait) {
            longestWait = age
            val threads = Thread.getAllStackTraces().entries
                .sortedBy { (t, _) -> if (t == testThread || t.name == "main") "" else t.name }
                .take(48)
            stall = JSONObject().put("event", entries.last().name).put("ageMs", age)
                .put("threads", JSONArray(threads.map { (thread, stack) ->
                    JSONObject().put("name", thread.name).put("state", thread.state.name)
                        .put("stack", JSONArray(stack.take(24).map { it.toString() }))
                }))
        }
        stall?.let { report.put("longestWait", it) }
        val pending = File(file.parentFile, "${file.name}.tmp")
        pending.writeText(report.toString(2))
        check(pending.renameTo(file)) { "Cannot replace gallery trace" }
    }

    override fun close() {
        watchdog.shutdownNow()
        write()
    }
}
