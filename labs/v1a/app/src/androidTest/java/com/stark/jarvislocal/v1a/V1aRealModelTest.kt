package com.stark.jarvislocal.v1a

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.ui.chat.core.CoreConversationClientAdapter
import com.jarvis.interfaceapp.ui.chat.core.CoreTextLink
import com.stark.jarvislocal.JarvisSettings
import com.stark.jarvislocal.UnloadResult
import fr.jarvis.llm.a02.SealedNativeBridge
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL end-to-end on an emulator: authorized GGUF pushed by CI -> ModelStore import (SHA-256)
 * -> A02 sealed load -> real CORE SessionCoordinator -> INTERFACE client. No fake engine.
 * Runs only when CI passes modelPath/modelSha; the workflow asserts it was not skipped.
 * Emulator x86_64 measurements are NOT phone (POCO) measurements.
 */
@RunWith(AndroidJUnit4::class)
class V1aRealModelTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
    private fun waitFor(timeoutMs: Long, what: String, check: () -> Boolean): Long {
        val start = SystemClock.elapsedRealtime()
        while (!main(check)) {
            if (SystemClock.elapsedRealtime() - start > timeoutMs) fail("Timeout: $what")
            Thread.sleep(50)
        }
        return SystemClock.elapsedRealtime() - start
    }
    private fun status(key: String): Long = File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }
        ?.filter(Char::isDigit)?.toLongOrNull() ?: -1

    @Test fun realOfflineConversationThroughCoreAndA02() {
        val path = args.getString("modelPath"); val sha = args.getString("modelSha")
        assumeTrue("modelPath/modelSha not provided", path != null && sha != null)
        val app = context.applicationContext as V1aApplication
        File(context.filesDir, "models").deleteRecursively()
        val importStart = SystemClock.elapsedRealtime()
        val entry = File(path!!).inputStream().use { app.store.import(it, sha!!) }
        val importMs = SystemClock.elapsedRealtime() - importStart
        val rssBefore = status("VmRSS")

        val model = A02LanguageModel(SealedNativeBridge.get(), VerifiedModel({
            val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
        }, entry.sizeBytes, entry.sha256), app::memoryBudgetBytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val core = main { V1aCoreTransport(scope, model, settings = JarvisSettings(contextSize = 768, maxTokens = 48)) }
        val client = main { CoreConversationClientAdapter(core, scope) }
        val turns = JSONArray()
        try {
            val loadMs = waitFor(240_000, "A02 load") { client.state.value.link == CoreTextLink.READY || core.lastError.value != null }
            assertNull("load error: ${core.lastError.value}", core.lastError.value)
            val trace = JSONObject(requireNotNull(model.lastTrace))
            assertTrue(trace.getBoolean("sealedInodeMapped"))
            assertEquals(sha, trace.getString("sealedSha256"))
            val rssLoaded = status("VmRSS"); val shmLoaded = status("RssShmem")

            var streamingObserved = false
            fun turn(q: String): String {
                assertTrue(main { client.submit(q) })
                val started = SystemClock.elapsedRealtime()
                var firstTextMs = -1L
                val ms = waitFor(240_000, "reply to '$q'") {
                    val s = client.state.value
                    if (firstTextMs < 0 && s.text.isNotEmpty()) {
                        firstTextMs = SystemClock.elapsedRealtime() - started
                        if (s.busy) streamingObserved = true // text visible while generation still running
                    }
                    !s.busy
                }
                val text = main { client.state.value.text }
                assertNull("engine error: ${core.lastError.value}", core.lastError.value)
                assertTrue("empty reply", text.isNotBlank())
                turns.put(JSONObject().put("question", q).put("ms", ms).put("firstVisibleTextMs", firstTextMs).put("chars", text.length).put("answer", text.take(300)))
                return text
            }
            turn("Bonjour, présente-toi en une phrase.")
            turn("Je m'appelle Thomas.")
            turn("Comment je m'appelle ?")
            assertEquals(3, core.history.snapshot().size)
            assertTrue("no partial text observed while generating", streamingObserved)

            // Stop during a real generation: CORE drains, the gate reopens, nothing is appended.
            assertTrue(main { client.submit("Compte lentement de un à cinquante, en toutes lettres.") })
            Thread.sleep(1_500)
            main { client.cancel() }
            val stopMs = waitFor(120_000, "stop drain") { !core.coordinator.state.value.busy && !client.state.value.busy }
            assertEquals(3, core.history.snapshot().size)
            assertNotEquals(CoreTextLink.UNKNOWN, main { client.state.value.link })

            val unload = runBlocking { core.releaseEngine() }
            assertEquals(UnloadResult.CONFIRMED, unload)
            val shmAfter = status("RssShmem")
            val report = JSONObject()
                .put("platform", "Android emulator (x86_64, NOT a phone)")
                .put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("model", JSONObject().put("sha256", sha).put("bytes", entry.sizeBytes))
                .put("importMs", importMs).put("loadMs", loadMs).put("trace", trace)
                .put("turns", turns).put("streamingObserved", streamingObserved).put("engine", JSONObject().put("context", 768).put("threads", 1).put("maxTokens", 48)).put("stopDrainMs", stopMs).put("unload", unload.name)
                .put("rssKb", JSONObject().put("beforeLoad", rssBefore).put("loaded", rssLoaded))
                .put("shmemKb", JSONObject().put("loaded", shmLoaded).put("afterUnload", shmAfter))
                .put("memoryBudgetBytesAtEnd", app.memoryBudgetBytes())
            File(context.filesDir, "v1a-real-report.json").writeText(report.toString(2))
        } finally {
            main { client.close() }
            scope.cancel()
            File(context.filesDir, "models").deleteRecursively()
        }
    }
}
