package fr.jarvis.v3

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stark.jarvislocal.JarvisSettings
import com.stark.jarvislocal.UnloadResult
import com.stark.jarvislocal.v1b.ConversationHistory
import com.stark.jarvislocal.v1b.SourceDescriptor
import com.stark.jarvislocal.v1b.V1bPolicy
import com.stark.jarvislocal.v1b.VerifiedModel
import fr.jarvis.llm.a02.SealedNativeBridge
import fr.jarvis.v3.conv.ConvLanguageModel
import fr.jarvis.v3.conv.ConversationContext
import fr.jarvis.v3.conv.PersonaSettings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CONVERSATION-01 Android model comparison (owner decision 14/11: prepare a rigorous Android comparison of the
 * 1.7B; no model replacement, no safety-limit change). TEST APK ONLY: the production app keeps loading the
 * verified 0.6B through its own importer. Each model is loaded through the SAME protected A02 path (sealed fd,
 * SHA-256 checked by the engine, context 768, ONE thread, output 128) using the engine's own profile for its
 * size, and answers the published 44-scenario French bench (JARVIS-LLM fr-bench-30 + fr-conv-01) with the
 * app's real instruction (Athéna compact persona + working memory). Measured: first token, total, decode
 * tokens/s, PSS / VmRSS / VmHWM, thermal status and headroom, battery temperature; output in the format of
 * JARVIS-LLM a02/tests/bench_score.py. On an emulator these figures are NOT phone figures.
 *
 * Arguments: model=<path>, sha256=<hex>, label=<text>, small=true|false, benches=<path,path>.
 */
@RunWith(AndroidJUnit4::class)
class ModelStudyTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val ctx get() = inst.targetContext

    private fun proc(key: String): Long = File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }
        ?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: -1
    /** Same rule as the app (ConvApp): available RAM above the low-memory threshold; the engine refuses beyond it. */
    private fun memoryBudgetBytes(): Long {
        val info = android.app.ActivityManager.MemoryInfo(); ctx.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
        return if (info.lowMemory) 0L else (info.availMem - info.threshold).coerceAtLeast(0L)
    }
    private fun thermal(): JSONObject {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return JSONObject().put("status", pm.currentThermalStatus)
            .put("headroom10s", if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10).toDouble().let { if (it.isNaN()) JSONObject.NULL else it } else JSONObject.NULL)
            .put("batteryTempC", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 } ?: JSONObject.NULL)
    }

    @Test fun benchOneModel() {
        val path = args.getString("model"); assumeTrue(path != null)
        val file = File(path!!); val sha = args.getString("sha256")!!; val label = args.getString("label")!!
        val small = args.getString("small") != "false"
        val items = args.getString("benches")!!.split(',').flatMap { f ->
            JSONObject(File(f).readText()).getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }
        val out = JSONObject().put("label", label).put("platform", "Android ${Build.VERSION.SDK_INT} ${Build.SUPPORTED_ABIS.first()} ${Build.MODEL} (emulator figures are NOT phone figures)")
            .put("config", JSONObject().put("small", small).put("context", V1bPolicy.CONTEXT).put("threads", V1bPolicy.THREADS).put("ceiling", V1bPolicy.DEFAULT_OUTPUT)
                .put("instruction", "app ConversationContext(ATHENA compact) = persona + working memory"))
        val bench = JSONArray(); out.put("bench", bench)
        val samples = JSONArray(); out.put("thermalSamples", samples)
        val memory = JSONObject(); out.put("memory", memory)
        val context = ConversationContext(PersonaSettings.ATHENA)
        val model = ConvLanguageModel(SealedNativeBridge.get(), VerifiedModel({
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
        }, file.length(), sha), ::memoryBudgetBytes, instruction = context::instructionFor, small = small)
        // Thermal sampler every 10 s while the model works (status, headroom, battery temperature).
        val running = AtomicBoolean(true); val t0 = SystemClock.elapsedRealtime()
        val sampler = thread(name = "study-thermal") { while (running.get()) { synchronized(samples) { samples.put(thermal().put("tMs", SystemClock.elapsedRealtime() - t0)) }; Thread.sleep(10_000) } }
        try {
            memory.put("budgetBytesAtLoad", memoryBudgetBytes()).put("pssBeforeKb", Debug.getPss()).put("rssBeforeKb", proc("VmRSS"))
            val l0 = SystemClock.elapsedRealtime()
            runBlocking { model.warmUp(JarvisSettings(systemPrompt = V1bPolicy.SYSTEM_PROMPT, contextSize = V1bPolicy.CONTEXT, maxTokens = V1bPolicy.DEFAULT_OUTPUT)) }
            memory.put("loadMs", SystemClock.elapsedRealtime() - l0).put("pssLoadedKb", Debug.getPss()).put("rssLoadedKb", proc("VmRSS"))
            for (item in items) {
                val turns = item.getJSONArray("turns"); val history = ArrayList<ConversationHistory.Turn>(); val res = JSONArray()
                for (k in 0 until turns.length()) {
                    val q = turns.getString(k)
                    val r = runCatching { runBlocking { model.generateTurns(history, q, V1bPolicy.DEFAULT_OUTPUT) { } } }
                    r.onSuccess { t ->
                        res.put(JSONObject().put("question", q).put("answer", t.text).put("firstTokenMs", t.metrics.firstTokenMs).put("totalMs", t.metrics.totalMs)
                            .put("decodeTokensPerSecond", t.metrics.decodeTokensPerSecond).put("promptTokens", t.metrics.promptTokens).put("generatedTokens", t.metrics.generatedTokens))
                        if (t.text.isNotBlank()) history += ConversationHistory.Turn(q, t.text)
                    }.onFailure { e -> res.put(JSONObject().put("question", q).put("answer", "").put("error", e.message ?: e.javaClass.simpleName)
                        .put("firstTokenMs", -1).put("totalMs", -1).put("decodeTokensPerSecond", 0)) }
                }
                bench.put(JSONObject().put("id", item.getString("id")).put("turns", res))
                memory.put("vmHwmKb", proc("VmHWM")).put("pssPeakSampleKb", maxOf(memory.optLong("pssPeakSampleKb", 0), Debug.getPss()))
            }
            memory.put("pssAfterBenchKb", Debug.getPss()).put("rssAfterBenchKb", proc("VmRSS")).put("vmHwmKb", proc("VmHWM"))
        } finally {
            val unloaded = runBlocking { model.destroy() }
            memory.put("unload", unloaded.name).put("pssAfterUnloadKb", Debug.getPss()).put("rssAfterUnloadKb", proc("VmRSS"))
            running.set(false); sampler.join(11_000)
            out.put("thermalEnd", thermal()).put("wallMs", SystemClock.elapsedRealtime() - t0)
            File(ctx.filesDir, "study-$label.json").writeText(out.toString(1))
        }
        assertEquals(UnloadResult.CONFIRMED.name, memory.getString("unload"))
        assertEquals(items.size, bench.length())
    }
}
