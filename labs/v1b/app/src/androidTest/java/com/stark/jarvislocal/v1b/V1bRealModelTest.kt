package com.stark.jarvislocal.v1b

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
 * REAL end-to-end V1-B on an emulator: authorized GGUF pushed by CI -> ModelStore import (SHA-256)
 * -> A02 sealed load -> real CORE SessionCoordinator -> INTERFACE client, airplane mode. No fake
 * engine. Mechanics (sealing, streaming, Stop, unload, reload, errors) are asserted here; the
 * dialogue QUALITY is recorded verbatim and judged by tools/dialogue_gate.py in CI.
 * Emulator x86_64 measurements are NOT phone (POCO) measurements.
 */
@RunWith(AndroidJUnit4::class)
class V1bRealModelTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
    private fun waitFor(timeoutMs: Long, what: String, check: () -> Boolean): Long {
        val start = SystemClock.elapsedRealtime()
        while (!main(check)) {
            if (SystemClock.elapsedRealtime() - start > timeoutMs) fail("Timeout: $what")
            Thread.sleep(25)
        }
        return SystemClock.elapsedRealtime() - start
    }
    private fun status(key: String): Long = File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }
        ?.filter(Char::isDigit)?.toLongOrNull() ?: -1
    private fun metrics(m: TurnMetrics?) = m?.let {
        JSONObject().put("maxTokens", it.maxTokens).put("promptTokens", it.promptTokens).put("generatedTokens", it.generatedTokens)
            .put("stoppedNaturally", it.stoppedNaturally).put("historyPairsSent", it.historyPairsSent).put("firstTokenMs", it.firstTokenMs)
            .put("totalMs", it.totalMs).put("decodeTokensPerSecond", it.decodeTokensPerSecond)
    }

    @Test fun realOfflineV1bConversationThroughCoreAndA02() {
        val path = args.getString("modelPath"); val sha = args.getString("modelSha")
        assumeTrue("modelPath/modelSha not provided", path != null && sha != null)
        val app = context.applicationContext as V1bApplication
        File(context.filesDir, "models").deleteRecursively()
        val importStart = SystemClock.elapsedRealtime()
        val entry = File(path!!).inputStream().use { app.store.import(it, sha!!) }
        val importMs = SystemClock.elapsedRealtime() - importStart
        val verified = VerifiedModel({
            val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
        }, entry.sizeBytes, entry.sha256)
        fun newModel(ceiling: Int) = A02LanguageModel(SealedNativeBridge.get(), verified, app::memoryBudgetBytes, ceiling)
        val settings768 = JarvisSettings(contextSize = 768)
        val rssBefore = status("VmRSS"); val shmBefore = status("RssShmem")
        val report = JSONObject().put("platform", "Android emulator (x86_64, NOT a phone)").put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("model", JSONObject().put("sha256", sha).put("bytes", entry.sizeBytes)).put("importMs", importMs)
            .put("engine", JSONObject().put("context", 768).put("threads", 1).put("defaultOutputCeiling", V1bPolicy.DEFAULT_OUTPUT))

        // ---- 1. Real CORE conversation, default ceiling 128 ----------------------------------
        val model = newModel(V1bPolicy.DEFAULT_OUTPUT)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val core = main { V1bCoreTransport(scope, model) }
        val client = main { CoreConversationClientAdapter(core, scope) }
        try {
            val loadMs = waitFor(240_000, "A02 load") { client.state.value.link == CoreTextLink.READY || core.lastError.value != null }
            assertNull("load error: ${core.lastError.value}", core.lastError.value)
            val trace = JSONObject(requireNotNull(model.lastTrace))
            assertTrue(trace.getBoolean("sealedInodeMapped"))
            assertEquals(sha, trace.getString("sealedSha256"))
            assertTrue(trace.getInt("loaderOpensServedFromSealedFd") > 0)
            assertEquals(1, trace.getInt("readOnlyPrivateMapsOfSealedFd"))
            assertFalse(trace.getBoolean("sourceInodeMapped"))
            assertEquals(96, trace.getInt("protectedOutputCeiling"))
            assertEquals(128, core.settings.maxTokens)
            report.put("loadMs", loadMs).put("trace", trace)
                .put("rssKb", JSONObject().put("beforeLoad", rssBefore).put("loaded", status("VmRSS")))
                .put("shmemKb", JSONObject().put("beforeLoad", shmBefore).put("loaded", status("RssShmem")))

            var streamingObserved = false
            fun turn(q: String, into: JSONArray): String {
                assertTrue("submit refused: $q", main { client.submit(q) })
                val started = SystemClock.elapsedRealtime()
                var firstTextMs = -1L
                val ms = waitFor(240_000, "reply to '$q'") {
                    val s = client.state.value
                    if (firstTextMs < 0 && s.text.isNotEmpty()) {
                        firstTextMs = SystemClock.elapsedRealtime() - started
                        if (s.busy) streamingObserved = true
                    }
                    !s.busy
                }
                val text = main { client.state.value.text }
                assertNull("engine error: ${core.lastError.value}", core.lastError.value)
                assertTrue("empty reply", text.isNotBlank())
                into.put(JSONObject().put("question", q).put("answer", text.take(1200)).put("ms", ms).put("firstVisibleTextMs", firstTextMs)
                    .put("metrics", metrics(core.lastMetrics.value)))
                return text
            }
            val dialogues = JSONArray()
            val scenarios = linkedMapOf(
                "identite-prenom" to listOf("Bonjour, présente-toi.", "Je m'appelle Thomas.", "Comment je m'appelle ?"),
                "correction-prenom" to listOf("Je m'appelle Thomas.", "En fait non, je m'appelle Julien.", "Comment je m'appelle ?"),
                "questions-successives" to listOf("Quelle est la capitale de la France ?", "Et celle de l'Italie ?", "Et celle de l'Espagne ?"),
                "changement-de-sujet" to listOf("Je m'appelle Thomas.", "Donne-moi une idée de dessert simple.", "Au fait, comment je m'appelle ?"),
            )
            for ((name, questions) in scenarios) {
                core.history.clear() // each scenario starts a fresh conversation in the same loaded engine
                val turns = JSONArray()
                questions.forEach { turn(it, turns) }
                assertEquals(3, core.history.snapshot().size)
                dialogues.put(JSONObject().put("scenario", name).put("maxTokens", 128).put("turns", turns))
            }
            assertTrue("no partial text observed while generating", streamingObserved)
            report.put("v1bDialogues", dialogues).put("streamingObserved", streamingObserved)

            // ---- 2. Stop during a real generation ---------------------------------------------
            val before = core.history.snapshot().size
            assertTrue(main { client.submit("Compte lentement de un à cinquante, en toutes lettres.") })
            waitFor(240_000, "first streamed text before stop") { client.state.value.text.isNotEmpty() || !client.state.value.busy }
            assertTrue("generation finished before Stop could be exercised", main { client.state.value.busy })
            val stopAt = SystemClock.elapsedRealtime()
            main { client.cancel() }
            waitFor(120_000, "stop drain") { !core.coordinator.state.value.busy && !client.state.value.busy }
            val stopMs = SystemClock.elapsedRealtime() - stopAt
            assertEquals(before, core.history.snapshot().size)
            assertNotEquals(CoreTextLink.UNKNOWN, main { client.state.value.link })
            report.put("stopDrainMs", stopMs)

            // ---- 3. Visible error, nothing recorded, session still usable ---------------------
            assertTrue(main { client.submit("Peux-tu résumer ce texte : " + "le chat dort au soleil. ".repeat(20)) })
            waitFor(60_000, "budget refusal") { !client.state.value.busy }
            val budgetError = core.lastError.value.orEmpty()
            assertTrue("expected the CORE budget refusal, got: $budgetError", budgetError.contains("BUDGET"))
            assertEquals(before, core.history.snapshot().size)
            core.history.clear()
            val after = JSONArray(); turn("Dis bonjour.", after)
            report.put("errorHandling", JSONObject().put("oversizedQuestion", budgetError).put("nextTurnAfterError", after.getJSONObject(0)))

            // ---- 4. Same configuration, 48 / 128 tokens (ceiling 128 engine) ------------------
            val bench = JSONArray()
            val benchQuestion = "Explique en détail comment fonctionne la photosynthèse."
            fun bench(m: A02LanguageModel, maxTokens: Int, q: String) {
                val r = runBlocking { m.generateTurns(emptyList(), q, maxTokens) { } }
                bench.put(metrics(r.metrics)!!.put("question", q).put("answer", r.text.take(1500)).put("vmHwmKb", status("VmHWM")))
            }
            bench(model, 48, benchQuestion); bench(model, 128, benchQuestion); bench(model, 128, "Dis bonjour.")
            assertEquals(UnloadResult.CONFIRMED, runBlocking { core.releaseEngine() })
            report.put("unloadAfterConversation", JSONObject().put("result", "CONFIRMED").put("shmemKb", status("RssShmem")))
            // ---- 5. Experimental 256 on a fresh engine, same configuration --------------------
            val m256 = newModel(V1bPolicy.EXPERIMENTAL_OUTPUT)
            runBlocking { m256.warmUp(settings768) }
            bench(m256, 256, benchQuestion)
            assertEquals(UnloadResult.CONFIRMED, runBlocking { m256.destroy() })
            report.put("v1bBench", bench)
        } finally {
            main { client.close() }
            runBlocking { model.destroy() }
            scope.cancel()
        }

        // ---- 6. Reload cycles: load -> short answer -> destroy, memory must return ----------
        val cycles = JSONArray()
        repeat(5) { i ->
            val m = newModel(V1bPolicy.DEFAULT_OUTPUT)
            val t0 = SystemClock.elapsedRealtime()
            runBlocking { m.warmUp(settings768) }
            val load = SystemClock.elapsedRealtime() - t0
            val r = runBlocking { m.generateTurns(emptyList(), "Dis oui.", 16) { } }
            val shmLoaded = status("RssShmem")
            assertEquals(UnloadResult.CONFIRMED, runBlocking { m.destroy() })
            cycles.put(JSONObject().put("cycle", i + 1).put("loadMs", load).put("answer", r.text.take(80))
                .put("shmemLoadedKb", shmLoaded).put("shmemAfterDestroyKb", status("RssShmem")).put("rssAfterDestroyKb", status("VmRSS")).put("fds", File("/proc/self/fd").list()!!.size))
        }
        val first = cycles.getJSONObject(0); val last = cycles.getJSONObject(cycles.length() - 1)
        assertTrue("sealed shmem not released after destroy", last.getLong("shmemAfterDestroyKb") - shmBefore < 64 * 1024)
        assertTrue("descriptor leak across reloads", last.getInt("fds") <= first.getInt("fds") + 2)
        report.put("reloadCycles", cycles).put("vmHwmKbAtEnd", status("VmHWM")).put("memoryBudgetBytesAtEnd", app.memoryBudgetBytes())
        File(context.filesDir, "v1b-real-report.json").writeText(report.toString(2))
        File(context.filesDir, "models").deleteRecursively()
    }
}
