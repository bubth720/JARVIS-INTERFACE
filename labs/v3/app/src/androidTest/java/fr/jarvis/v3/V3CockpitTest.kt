package fr.jarvis.v3

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import fr.jarvis.m55d.RenderMode
import fr.jarvis.v1.EndpointDetector
import fr.jarvis.v1.Phase
import fr.jarvis.v1.SpeechCapture
import fr.jarvis.v1.V1App
import fr.jarvis.v1.V1Session
import fr.jarvis.v3.model.CardFactory
import fr.jarvis.v3.model.HoloState
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real 16 kHz WAV played in REAL TIME as the microphone source (test only): listening animates on its true levels. */
class RealtimeWav(private val file: File) : SpeechCapture {
    override fun capture(onLevel: (Float) -> Unit, stop: () -> Boolean): FloatArray {
        val b = file.readBytes(); var p = 12; var data = -1; var size = 0
        while (p + 8 <= b.size) {
            val id = String(b, p, 4, Charsets.US_ASCII); val len = (b[p + 4].toInt() and 255) or ((b[p + 5].toInt() and 255) shl 8) or ((b[p + 6].toInt() and 255) shl 16) or ((b[p + 7].toInt() and 255) shl 24)
            if (id == "data") { data = p + 8; size = minOf(len, b.size - data); break }
            p += 8 + len
        }
        check(data > 0) { "WAV sans données" }
        val all = FloatArray(size / 2) { i -> ((b[data + 2 * i].toInt() and 255) or (b[data + 2 * i + 1].toInt() shl 8)).toShort() / 32768f }
        var o = 0
        while (o < all.size && !stop()) { val n = minOf(1600, all.size - o); onLevel((EndpointDetector.rms(all.copyOfRange(o, o + n), n) * 8f).coerceIn(0f, 1f)); o += n; Thread.sleep(100) }
        return all
    }
}

@RunWith(AndroidJUnit4::class)
class V3CockpitTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = inst.targetContext.applicationContext as V1App
    private val ui get() = V3Runtime.ui
    private val dev get() = UiDevice.getInstance(inst)
    private val shots get() = File(inst.targetContext.filesDir, "v3shots").also { it.mkdirs() }
    private fun <T> main(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }
    private fun s(): V1Session? = main { ui.session }
    private fun phase() = main { ui.session?.phase }
    private fun shot(name: String): String { inst.uiAutomation.takeScreenshot()?.let { b -> File(shots, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }; return name }
    private fun waitUntil(ms: Long, what: String, ok: () -> Boolean): Long {
        val t0 = SystemClock.elapsedRealtime()
        while (!main(ok)) { if (SystemClock.elapsedRealtime() - t0 > ms) fail("Timeout: $what (${main { ui.session?.status }})"); Thread.sleep(50) }
        return SystemClock.elapsedRealtime() - t0
    }
    private fun obj(res: String, ms: Long = 20_000): UiObject2 = requireNotNull(dev.wait(Until.findObject(By.res(res)), ms)) { "element '$res' not shown" }
    private fun write(name: String, o: JSONObject) = File(inst.targetContext.filesDir, name).writeText(o.toString(2))

    private fun start(): Long {
        obj("start", 30_000).click()
        val ms = waitUntil(360_000, "session ready") { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }
        assertEquals("load: ${main { ui.session?.status }}", Phase.READY, phase())
        return ms
    }
    private fun type(text: String) { val i = obj("input"); i.click(); i.text = text; Thread.sleep(300); obj("send").click() }
    private fun <A : android.app.Activity> frames(scenario: ActivityScenario<A>, ms: Long): JSONObject {
        val d = Collections.synchronizedList(ArrayList<Double>()); val th = HandlerThread("fm").also { it.start() }
        val l = Window.OnFrameMetricsAvailableListener { _, m, _ -> d += m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6 }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(l, Handler(th.looper)) }
        SystemClock.sleep(ms)
        scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(l) }; th.quitSafely()
        val s = synchronized(d) { d.sorted() }
        if (s.isEmpty()) return JSONObject().put("frames", 0)
        val p99 = s[maxOf(0, (s.size * 99) / 100 - 1)]
        return JSONObject().put("frames", s.size).put("deliveredFps", Math.round(s.size * 10000.0 / ms) / 10.0).put("medianFrameMs", Math.round(s[s.size / 2] * 10) / 10.0)
            .put("p99FrameMs", Math.round(p99 * 10) / 10.0).put("onePercentLowFps", Math.round(10000.0 / p99) / 10.0)
    }

    @Test fun a_manifestSinglePermissionNoNetworkInstallable() {
        val ctx = inst.targetContext
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().filterNot { it == ctx.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), requested)
        assertEquals(0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_TEST_ONLY)
    }

    /** First launch on a fresh install (no resource), then the verified import used by the following tests. */
    @Test fun b_firstLaunchThenVerifiedImport() {
        assumeTrue(args.getString("llmPath") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0)
        val r = JSONObject()
        ActivityScenario.launch(CockpitActivity::class.java).use {
            assertFalse(main { app.missing() }.isEmpty())
            obj("start"); SystemClock.sleep(2_500); r.put("capture", shot("01-premier-lancement"))
        }
        val t0 = SystemClock.elapsedRealtime()
        File(args.getString("llmPath")!!).inputStream().use { app.assets.importLlm(it) }
        File(args.getString("sttPath")!!).inputStream().use { app.assets.importStt(it) }
        File(args.getString("ttsZip")!!).inputStream().use { app.assets.importTts(it) }
        assertTrue(main { app.missing() + app.optionalMissing() }.isEmpty())
        write("v3-first-launch.json", r.put("importMs", SystemClock.elapsedRealtime() - t0))
    }

    /** Every mandatory screen state (§03) on the REAL engine, with CORE → LLM → display non-regression. */
    @Test fun c_realEngineStatesCardsMenuLandscapeBackground() {
        assumeTrue(args.getString("afterImport") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0)
        val r = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("platform", "Android emulator x86_64 (NOT a phone)")
        val captures = JSONArray(); r.put("captures", captures)
        try { ActivityScenario.launch(CockpitActivity::class.java).use { sc ->
            r.put("loadMs", start()); SystemClock.sleep(2_000); captures.put(shot("02-repos"))
            r.put("idleFrames", frames(sc, 5_000)).put("governor", JSONObject(main { V3Runtime.governor!!.stats.summary() }).put("mode", main { V3Runtime.governor!!.effectiveMode.name }))
            // Sphere gesture: real system swipe, inertial rotation.
            val sphere = obj("sphere").visibleBounds; val yaw0 = V3Runtime.motion.yaw
            dev.swipe(sphere.left + sphere.width() * 3 / 4, sphere.centerY(), sphere.left + sphere.width() / 4, sphere.centerY(), 20)
            SystemClock.sleep(600); r.put("swipe", JSONObject().put("dragEvents", V3Runtime.motion.dragEvents).put("yawDelta", V3Runtime.motion.yaw - yaw0))
            assertTrue("sphere received no drag", V3Runtime.motion.dragEvents > 0)
            // Keyboard shown.
            obj("input").click(); SystemClock.sleep(1_200); captures.put(shot("03-clavier")); dev.pressBack(); SystemClock.sleep(500)
            // Typed question: thinking, streaming, speaking, answer, error accent (emulator speaker), back to stable.
            val n0 = main { ui.session!!.exchanges.size }
            type("Quelle est la capitale de la France ?")
            val seen = HashSet<String>(); val t0 = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - t0 < 420_000) {
                val ph = phase(); val partial = main { ui.session!!.partial }
                if (ph == Phase.THINKING && partial.isBlank() && "think" !in seen && SystemClock.elapsedRealtime() - t0 > 1_500) { seen += "think"; captures.put(shot("04-reflexion")) }
                if (ph == Phase.THINKING && partial.isNotBlank() && "stream" !in seen) { seen += "stream"; captures.put(shot("05-diffusion-texte")) }
                if (ph == Phase.SPEAKING && "speak" !in seen) { seen += "speak"; SystemClock.sleep(700); captures.put(shot("06-parole")) }
                if (ph == Phase.READY && main { ui.session!!.exchanges.size } > n0) break
                Thread.sleep(40)
            }
            val e = main { ui.session!!.exchanges.last() }
            assertEquals("Quelle est la capitale de la France ?", e.user); assertTrue(e.answer.isNotBlank())
            val errorShown = main { ui.errorSince != null }
            if (errorShown) captures.put(shot("08-erreur"))
            r.put("typed", JSONObject().put("answer", e.answer).put("llmMs", e.llmMs).put("firstTextMs", e.firstTextMs).put("error", e.error ?: JSONObject.NULL)
                .put("statesCaptured", JSONArray(seen.toList())).put("errorAccentShown", errorShown))
            SystemClock.sleep(SphereStateAdapterTestAccess.errorAccentMs + 500)
            assertNotNull("answer not displayed", dev.wait(Until.findObject(By.res("answer").textContains(e.answer.take(20))), 20_000))
            assertEquals("visual state back to stable after the temporary error accent", HoloState.IDLE,
                main { fr.jarvis.v3.model.SphereStateAdapter.resolve(ui.session?.phase?.name, false, ui.errorSince?.let { SystemClock.elapsedRealtime() - it }) })
            captures.put(shot("07-reponse"))
            // Card from a conversation keyword (only real words of the exchange).
            val chip = dev.wait(Until.findObject(By.res("keyword-0")), 5_000)
            if (chip != null) { val kw = chip.text; chip.click(); SystemClock.sleep(900); obj("card"); captures.put(shot("09-fiche-sujet")); r.put("keywordCard", kw)
                obj("card-expand").click(); SystemClock.sleep(700); captures.put(shot("10-fiche-agrandie")); obj("card-close").click(); SystemClock.sleep(600) }
            else { obj("card-open-latest").click(); SystemClock.sleep(900); obj("card"); captures.put(shot("09-fiche-reponse")); obj("card-close").click(); SystemClock.sleep(600); r.put("keywordCard", JSONObject.NULL) }
            // Radial menu, unavailable module, settings.
            obj("radial").click(); SystemClock.sleep(900); captures.put(shot("11-menu-radial"))
            obj("dest-documents").click(); SystemClock.sleep(900)
            assertTrue(obj("card-provenance").text.contains("non connecté")); captures.put(shot("12-module-indisponible"))
            dev.pressBack(); SystemClock.sleep(600); assertNull("Android back must close the card", dev.findObject(By.res("card")))
            obj("radial").click(); SystemClock.sleep(700); obj("dest-settings").click(); SystemClock.sleep(900); captures.put(shot("13-reglages"))
            obj("panel-settings-close").click(); SystemClock.sleep(600)
            // Device question: answered from an Android measurement, the LLM is NOT called.
            val n1 = main { ui.session!!.exchanges.size }
            type("Quelle est la batterie ?"); SystemClock.sleep(1_200)
            val summary = obj("card-summary").text; val prov = obj("card-provenance").text
            assertEquals(CardFactory.ANDROID_PROVENANCE, prov); assertTrue(summary, summary.startsWith("Batterie : "))
            assertEquals("the language model must not answer a device question", n1, main { ui.session!!.exchanges.size })
            captures.put(shot("14-fiche-systeme")); r.put("systemCard", summary)
            obj("card-close").click(); SystemClock.sleep(600)
            // Voice turn: real-time WAV as microphone source → real listening levels, real STT, real LLM.
            val wav = args.getString("wav1")
            if (wav != null) {
                val job = main { ui.session!!.voiceTurn(RealtimeWav(File(wav))) }; assertNotNull(job)
                waitUntil(10_000, "listening") { ui.session!!.phase == Phase.LISTENING }; SystemClock.sleep(900); captures.put(shot("15-ecoute"))
                r.put("listeningLevel", main { ui.session!!.cockpit.level })
                waitUntil(30_000, "transcribing") { ui.session!!.phase == Phase.TRANSCRIBING }; SystemClock.sleep(1_500); captures.put(shot("16-transcription"))
                runBlocking { kotlinx.coroutines.withTimeout(420_000) { job!!.join() } }
                val v = main { ui.session!!.exchanges.last() }
                r.put("voice", JSONObject().put("transcript", v.user).put("answer", v.answer).put("sttMs", v.sttMs).put("llmMs", v.llmMs).put("error", v.error ?: JSONObject.NULL))
            }
            // Landscape: same session, three regions, card in the right column.
            obj("card-open-latest").click(); SystemClock.sleep(700)
            dev.setOrientationLeft(); SystemClock.sleep(2_000); captures.put(shot("17-paysage"))
            r.put("landscapeSameSession", main { ui.session != null && !ui.session!!.closed })
            dev.setOrientationNatural(); SystemClock.sleep(1_200); dev.unfreezeRotation(); main { ui.openCard = null }
            // Background: no frame is drawn while the app is not visible.
            val f0 = V3Runtime.motion.drawnFrames; dev.pressHome(); SystemClock.sleep(3_000); val f1 = V3Runtime.motion.drawnFrames
            inst.targetContext.startActivity(Intent(inst.targetContext, CockpitActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); SystemClock.sleep(2_500); val f2 = V3Runtime.motion.drawnFrames
            r.put("background", JSONObject().put("framesWhileHidden", f1 - f0).put("framesAfterReturn", f2 - f1))
            assertTrue("animations must pause in background (${f1 - f0} frames)", f1 - f0 <= 3)
        } } finally { write("v3-states.json", r) }
    }

    /** Android "reduced motion" (animator scale 0, set by the script): still sphere; first text measured without cockpit animation. */
    @Test fun d_reducedMotionStaticSphereAndFirstText() {
        assumeTrue(args.getString("afterImport") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0)
        val r = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT)
        try { ActivityScenario.launch(CockpitActivity::class.java).use {
            start(); SystemClock.sleep(1_500)
            assertTrue(main { V3Runtime.governor!!.reducedMotion })
            val f0 = V3Runtime.motion.drawnFrames; SystemClock.sleep(2_000); val f1 = V3Runtime.motion.drawnFrames
            r.put("framesIn2sStatic", f1 - f0); assertTrue("static sphere must not animate", f1 - f0 <= 3)
            type("Quelle est la capitale de la France ?")
            waitUntil(420_000, "answer") { ui.session!!.phase == Phase.READY && ui.session!!.exchanges.isNotEmpty() }
            val e = main { ui.session!!.exchanges.last() }
            r.put("answer", e.answer).put("llmMs", e.llmMs).put("firstTextMs", e.firstTextMs)
            shot("18-mouvement-reduit")
        } } finally { write("v3-reduced.json", r) }
    }

    /** Prototype captures of every state (labelled EXEMPLE) and before/after rendering cost, same box and state. */
    @Test fun e_galleryCapturesAndBeforeAfterBench() {
        val r = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("note", "Emulator x86_64, software GPU (swiftshader): NOT representative of a phone GPU")
        val bench = JSONArray(); r.put("bench", bench)
        try {
            for (st in HoloState.entries) {
                val i = Intent(inst.targetContext, StateGalleryActivity::class.java).putExtra("state", st.name).putExtra("renderer", "v3")
                ActivityScenario.launch<StateGalleryActivity>(i).use { SystemClock.sleep(1_800); shot("g-${st.name.lowercase()}") }
            }
            ActivityScenario.launch<StateGalleryActivity>(Intent(inst.targetContext, StateGalleryActivity::class.java).putExtra("renderer", "m55d")).use { SystemClock.sleep(1_800); shot("g-avant-v1-m55d") }
            for (mode in RenderMode.entries) for (st in listOf(HoloState.IDLE, HoloState.THINKING)) for (renderer in listOf("m55d", "v3")) {
                val i = Intent(inst.targetContext, StateGalleryActivity::class.java).putExtra("bench", true).putExtra("renderer", renderer)
                    .putExtra("state", st.name).putExtra("mode", mode.name)
                ActivityScenario.launch<StateGalleryActivity>(i).use { sc -> SystemClock.sleep(1_500)
                    bench.put(frames(sc, 5_000).put("renderer", if (renderer == "v3") "V3" else "V1 (M55-D)").put("mode", mode.name).put("state", st.name)) }
            }
        } finally { write("v3-bench.json", r) }
    }
}

/** Keeps the test independent from the private constant name. */
object SphereStateAdapterTestAccess { val errorAccentMs get() = fr.jarvis.v3.model.SphereStateAdapter.ERROR_ACCENT_MS }
