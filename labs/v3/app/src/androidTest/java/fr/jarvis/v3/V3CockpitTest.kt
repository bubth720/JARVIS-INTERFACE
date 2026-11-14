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
import fr.jarvis.v3.conv.ConvApp
import fr.jarvis.v3.conv.ConvSession
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
    private val app get() = inst.targetContext.applicationContext as ConvApp
    private val ui get() = V3Runtime.ui
    private val dev get() = UiDevice.getInstance(inst)
    private val shots get() = File(inst.targetContext.filesDir, "v3shots").also { it.mkdirs() }
    private fun <T> main(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }
    private fun s(): ConvSession? = main { ui.session }
    private fun phase() = main { ui.session?.phase }
    private fun shot(name: String): String { inst.uiAutomation.takeScreenshot()?.let { b -> File(shots, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }; return name }
    private fun waitUntil(ms: Long, what: String, ok: () -> Boolean): Long {
        val t0 = SystemClock.elapsedRealtime()
        while (!main(ok)) { if (SystemClock.elapsedRealtime() - t0 > ms) fail("Timeout: $what (${main { ui.session?.status }})"); Thread.sleep(50) }
        return SystemClock.elapsedRealtime() - t0
    }
    private fun obj(res: String, ms: Long = 20_000): UiObject2 = requireNotNull(dev.wait(Until.findObject(By.res(res)), ms)) { "element '$res' not shown" }
    /** True when the soft keyboard is really on screen (InputMethodManagerService state). */
    private fun imeShown(): Boolean {
        val pfd = inst.uiAutomation.executeShellCommand("dumpsys input_method")
        val out = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return out.contains("mInputShown=true") || out.contains("isInputViewShown=true")
    }
    /** Frames actually drawn by the sphere during [ms] (V3-FIX-01: 0–1 when the sphere must be still). */
    private fun drawnDuring(ms: Long): Long { val f0 = V3Runtime.motion.drawnFrames; SystemClock.sleep(ms); return V3Runtime.motion.drawnFrames - f0 }
    /** V3-FIX-01: no interface text overflows (labels, buttons, chips, meta lines). */
    private fun assertTextsFit(where: String) { SystemClock.sleep(300); assertTrue("truncated texts $where: ${TextFit.overflowing}", TextFit.overflowing.isEmpty()) }
    private fun write(name: String, o: JSONObject) = File(inst.targetContext.filesDir, name).writeText(o.toString(2))

    private fun start(): Long {
        obj("start", 30_000).click()
        val ms = waitUntil(360_000, "session ready") { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }
        assertEquals("load: ${main { ui.session?.status }}", Phase.READY, phase())
        return ms
    }
    /** Real system key input (ASCII questions), checked against the UI state; accessibility setText only as a fallback. */
    private fun type(text: String): String {
        val i = obj("input"); i.click(); Thread.sleep(400)
        inst.uiAutomation.executeShellCommand("input text " + text.replace(" ", "%s").replace("'", "")).close()
        val t0 = SystemClock.elapsedRealtime(); var how = "system-keys"
        // The loaded emulator can need well over 4 s to deliver every key: wait for the whole text, then for it to be stable.
        while (main { ui.typed } != text && SystemClock.elapsedRealtime() - t0 < 30_000) Thread.sleep(150)
        if (main { ui.typed } != text) {
            var last = ""; while (main { ui.typed } != last) { last = main { ui.typed }; Thread.sleep(1_000) }   // no key still in flight
            how = "accessibility-setText"; main { ui.typed = "" }; obj("input").text = text; Thread.sleep(600)
        }
        assertEquals("typed text did not reach the input", text, main { ui.typed })
        requireNotNull(dev.wait(Until.findObject(By.res("send").enabled(true)), 10_000)) { "Send not enabled" }.click()
        return how
    }
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
            // V3-FIX-01: still at rest (animations ON) and NOT manipulable: a real swipe changes nothing.
            val restFrames = drawnDuring(2_000); r.put("restFrames2s", restFrames)
            assertTrue("sphere must be still at rest ($restFrames frames)", restFrames <= 1)
            val sphere = obj("sphere").visibleBounds; val yaw0 = V3Runtime.motion.yaw
            dev.swipe(sphere.left + sphere.width() * 3 / 4, sphere.centerY(), sphere.left + sphere.width() / 4, sphere.centerY(), 20)
            SystemClock.sleep(600); r.put("swipe", JSONObject().put("dragEvents", V3Runtime.motion.dragEvents).put("yawDelta", V3Runtime.motion.yaw - yaw0))
            assertEquals("the sphere must not react to gestures", 0, V3Runtime.motion.dragEvents)
            assertEquals(yaw0, V3Runtime.motion.yaw, 0f)
            assertTextsFit("at rest")
            // Keyboard shown.
            obj("input").click()
            val kt = SystemClock.elapsedRealtime(); while (!imeShown() && SystemClock.elapsedRealtime() - kt < 4_000) SystemClock.sleep(200)
            val keyboard = imeShown(); SystemClock.sleep(600); r.put("keyboardShown", keyboard)
            captures.put(shot(if (keyboard) "03-clavier" else "03-clavier-NON-AFFICHE"))
            if (keyboard) { dev.pressBack(); SystemClock.sleep(600) }   // Back only closes the keyboard; never the app
            // Typed question: thinking, streaming, speaking, answer, error accent (emulator speaker), back to stable.
            val n0 = main { ui.session!!.exchanges.size }
            r.put("inputMethod", type("Quelle est la capitale de la France ?"))
            val seen = HashSet<String>(); val t0 = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - t0 < 420_000) {
                val ph = phase(); val partial = main { ui.session!!.partial }
                if (ph == Phase.THINKING && partial.isBlank() && "think" !in seen && SystemClock.elapsedRealtime() - t0 > 1_500) { seen += "think"; captures.put(shot("04-reflexion"))
                    val f = drawnDuring(1_500); if (phase() == Phase.THINKING && main { ui.session!!.partial }.isBlank()) { r.put("thinkingFrames1500ms", f)
                        assertTrue("sphere must be still while thinking ($f frames)", f <= 1) } }
                if (ph == Phase.THINKING && partial.isNotBlank() && "stream" !in seen) { seen += "stream"; captures.put(shot("05-diffusion-texte")) }
                if (ph == Phase.SPEAKING && "speak" !in seen) { seen += "speak"; SystemClock.sleep(700); captures.put(shot("06-parole"))
                    val f = drawnDuring(1_500); r.put("speakingFrames1500ms", f); assertTrue("sphere must animate while JARVIS speaks ($f frames)", f >= 4) }
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
            val after = drawnDuring(2_000); r.put("afterAnswerFrames2s", after)
            assertTrue("sphere must stop after the answer ($after frames)", after <= 1)
            // Card from a conversation keyword (only real words of the exchange).
            val chip = dev.wait(Until.findObject(By.res("keyword-0")), 5_000)
            if (chip != null) { val kw = chip.text; chip.click(); SystemClock.sleep(900); obj("card"); captures.put(shot("09-fiche-sujet")); r.put("keywordCard", kw)
                obj("card-expand").click(); SystemClock.sleep(700); captures.put(shot("10-fiche-agrandie")); assertTextsFit("expanded card")
                obj("card-close").click(); SystemClock.sleep(600) }
            else { obj("card-open-latest").click(); SystemClock.sleep(900); obj("card"); captures.put(shot("09-fiche-reponse")); obj("card-close").click(); SystemClock.sleep(600); r.put("keywordCard", JSONObject.NULL) }
            // Radial menu, unavailable module, settings.
            // The open menu is modal (the dock leaves the accessibility tree): measure the input field before opening it.
            val inputBox = obj("input").visibleBounds
            obj("radial").click(); SystemClock.sleep(900); captures.put(shot("11-menu-radial")); assertTextsFit("radial menu")
            for (d in listOf("cockpit", "knowledge", "documents", "services", "settings")) {
                val b = obj("dest-$d").visibleBounds
                assertFalse("radial item '$d' $b covers the input field $inputBox", android.graphics.Rect.intersects(b, inputBox))
            }
            obj("dest-documents").click(); SystemClock.sleep(900)
            assertTrue(obj("card-provenance").text.contains("non connecté")); captures.put(shot("12-module-indisponible"))
            // The card leaves with an exit animation: wait for it to be gone (slow API 35 emulator), the assertion is unchanged.
            dev.pressBack(); dev.wait(Until.gone(By.res("card")), 3_000); assertNull("Android back must close the card", dev.findObject(By.res("card")))
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
                // V3-FIX-01: after the previous voice error, the previous answer is shown ONCE (history), never again as "in progress".
                val shownTimes = dev.findObjects(By.text(e.answer)).size
                r.put("previousAnswerShownWhileListening", shownTimes).put("liveNodeWhileListening", dev.findObject(By.res("partial")) != null)
                assertEquals("previous answer duplicated after a voice error", 1, shownTimes)
                assertNull("stale partial shown while listening", dev.findObject(By.res("partial")))
                val lf = drawnDuring(800); r.put("listeningFrames800ms", lf); assertTrue("sphere must be still while listening ($lf frames)", lf <= 1)
                waitUntil(30_000, "transcribing") { ui.session!!.phase == Phase.TRANSCRIBING }; SystemClock.sleep(1_500); captures.put(shot("16-transcription"))
                runBlocking { kotlinx.coroutines.withTimeout(420_000) { job!!.join() } }
                val v = main { ui.session!!.exchanges.last() }
                r.put("voice", JSONObject().put("transcript", v.user).put("answer", v.answer).put("sttMs", v.sttMs).put("llmMs", v.llmMs).put("error", v.error ?: JSONObject.NULL))
            }
            // Landscape: same session, three regions, card in the right column.
            obj("card-open-latest").click(); SystemClock.sleep(700)
            dev.setOrientationLeft(); SystemClock.sleep(2_000); captures.put(shot("17-paysage")); assertTextsFit("landscape")
            r.put("landscapeSameSession", main { ui.session != null && !ui.session!!.closed })
            dev.setOrientationNatural(); SystemClock.sleep(1_200); dev.unfreezeRotation(); main { ui.openCard = null }
            // Background: no frame is drawn while the app is not visible.
            dev.pressHome(); SystemClock.sleep(1_500)   // let the window transition finish, then count
            val f0 = V3Runtime.motion.drawnFrames; SystemClock.sleep(3_000); val f1 = V3Runtime.motion.drawnFrames
            inst.targetContext.startActivity(Intent(inst.targetContext, CockpitActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); SystemClock.sleep(2_500); val f2 = V3Runtime.motion.drawnFrames
            r.put("background", JSONObject().put("framesWhileHidden", f1 - f0).put("framesAfterReturn", f2 - f1))
            assertTrue("animations must pause in background (${f1 - f0} frames)", f1 - f0 <= 1)
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

    /**
     * CONVERSATION-01 acceptance 1–4 on the REAL engine (emulator, airplane mode): multi-turn context beyond the
     * protected 3-exchange window, topic change and return, persona consistency, close then resume, persona switch,
     * deletion. Hard assertions = deterministic properties (instruction content, journal, restored context, no
     * error). Answer QUALITY of the 0.6B is measured and recorded (never asserted as if it were guaranteed).
     */
    @Test fun f_conversationContextPersonaResume() {
        assumeTrue(args.getString("afterImport") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0)
        val r = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("platform", "Android emulator x86_64 (NOT a phone), airplane mode")
        val turns = JSONArray(); r.put("turns", turns)
        val quality = JSONObject(); r.put("quality", quality)
        fun ready() = waitUntil(360_000, "session ready") { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }.also {
            assertEquals("load: ${main { ui.session?.status }}", Phase.READY, phase()) }
        fun released() = waitUntil(60_000, "engine released") { app.previousReleased }
        fun ask(q: String, check: String? = null, ok: (String) -> Boolean = { true }): String {
            val n = main { ui.session!!.exchanges.size }
            assertNotNull("turn refused: ${main { ui.session!!.status }}", main { ui.session!!.textTurn(q) })
            waitUntil(420_000, "answer to '$q'") { ui.session!!.phase == Phase.READY && ui.session!!.exchanges.size > n }
            val e = main { ui.session!!.exchanges.last() }
            assertNull("engine error on '$q': ${e.error}", e.error); assertTrue("empty answer to '$q'", e.answer.isNotBlank())
            val instr = main { ui.session!!.model.lastInstruction }.orEmpty()
            val o = JSONObject().put("q", q).put("a", e.answer).put("llmMs", e.llmMs).put("firstTextMs", e.firstTextMs).put("instructionChars", instr.length)
            if (check != null) { val pass = ok(e.answer); o.put("check", check).put("pass", pass); quality.put(check, pass) }
            turns.put(o); return instr
        }
        fun Regex.has(s: String) = containsMatchIn(s)
        try {
            main { app.closeSession(); ui.session = null }; released()
            main { app.applyPersona(fr.jarvis.v3.conv.PersonaSettings.ATHENA) }
            val s0 = main { app.startSession().also { ui.session = it; it.voiceOutputEnabled = false } }; ready()
            val id = s0.conversationId; r.put("conversationId", id)
            // 1. Multi-turn context + correction; 2. topic change then return (older than the 3 exchanges the engine re-reads).
            ask("Je m'appelle Marc.")
            ask("Non pardon, je me suis trompé : je m'appelle Léo.")
            ask("Mon frère s'appelle Paul et il a douze ans.")
            ask("Quelle est la capitale de l'Italie ?", "capitale-italie") { Regex("(?i)rome").has(it) }
            ask("Combien font 12 fois 12 ?", "calcul-144") { it.contains("144") }
            ask("Et si on divise ce résultat par 4 ?", "reference-resultat-36") { it.contains("36") }
            val brother = ask("Revenons à mon frère : quel âge a-t-il ?", "retour-sujet-age-frere") { Regex("(?i)douze|12").has(it) }
            assertTrue("brother fact must reach the instruction beyond the 3-exchange window: $brother", brother.contains("son frère s'appelle Paul (douze ans)"))
            val name = ask("Comment je m'appelle ?", "prenom-corrige") { it.contains("Léo") && !it.contains("Marc") }
            assertTrue("corrected name must reach the instruction: $name", name.contains("l'utilisateur s'appelle Léo") && !name.contains("Marc"))
            // Owner rule: never claim an action that was not executed (instruction + system notice if the model still claims it).
            val act = ask("Allume la lumière du salon.", "refus-action-sans-pretention") { !fr.jarvis.v3.conv.ActionGuard.claimsDone("Allume la lumière du salon.", it) }
            assertTrue("action request must reach the instruction: $act", act.contains("Tu ne peux pas l'exécuter"))
            val ai = main { ui.session!!.exchanges.size - 1 }; val la = main { ui.session!!.exchanges.last().answer }
            if (fr.jarvis.v3.conv.ActionGuard.claimsDone("Allume la lumière du salon.", la))
                assertEquals("false claim must carry the system notice", fr.jarvis.v3.conv.ActionGuard.NOTICE, main { ui.session!!.notices[ai] })
            // 3. Persona consistency (Athéna = tutoiement): answers addressing the user with "vous" are counted.
            val answers = main { ui.session!!.exchanges.map { it.answer } }
            val vous = answers.count { Regex("(?i)\\b(vous|votre|vos)\\b").has(it) }
            r.put("personaAthena", JSONObject().put("answers", answers.size).put("withVous", vous).put("introducesAsJarvisNotAthena", answers.none { it.contains("Athéna") }))
            assertTrue("JARVIS must never call itself Athéna", answers.none { Regex("(?i)je (suis|m'appelle) ath[ée]na").has(it) })
            main { shot("c-conv-01-contexte") }
            // 4. Close, release, resume: exchanges and engine context come back.
            val before = main { ui.session!!.exchanges.size }
            main { app.closeSession(); ui.session = null }; released()
            assertEquals(before, app.journal.read(id)!!.entries.size)
            val s1 = main { app.startSession(id).also { ui.session = it; it.voiceOutputEnabled = false } }; ready()
            assertEquals(before, main { s1.restoredCount }); assertEquals(id, s1.conversationId)
            val resumed = ask("Tu te souviens de mon prénom ?", "reprise-prenom") { it.contains("Léo") }
            assertTrue("resumed context must contain the stated facts: $resumed", resumed.contains("l'utilisateur s'appelle Léo"))
            // Persona switch from the next turn (owner setting, persisted).
            main { app.applyPersona(fr.jarvis.v3.conv.PersonaSettings.NEUTRAL.copy(tutoiement = false)) }
            val neutral = ask("Quelle est la capitale de l'Espagne ?", "neutre-vouvoiement-madrid") { Regex("(?i)madrid").has(it) }
            assertTrue("persona switch must apply to the next turn: $neutral", neutral.startsWith("Vouvoie"))
            assertEquals("Neutre", app.personas.load().profileName)
            main { app.applyPersona(fr.jarvis.v3.conv.PersonaSettings.ATHENA) }
            // Resume from the cockpit itself (UI path).
            main { app.closeSession(); ui.session = null }; released()
            ActivityScenario.launch(CockpitActivity::class.java).use {
                obj("resume", 30_000).click(); ready()
                assertNotNull("restored exchanges shown", dev.wait(Until.findObject(By.textContains("échange précédent")), 20_000))
                r.put("uiResume", main { ui.session!!.conversationId == id && ui.session!!.restoredCount > 0 })
                assertTrue(r.getBoolean("uiResume")); shot("c-conv-02-reprise")
                obj("radial").click(); SystemClock.sleep(700); obj("dest-settings").click(); SystemClock.sleep(900)
                dev.findObject(By.res("panel-settings"))?.scroll(androidx.test.uiautomator.Direction.DOWN, 1f); SystemClock.sleep(500)
                shot("c-conv-03-personnalite-historique"); assertTextsFit("settings: persona and history")
                obj("panel-settings-close").click(); SystemClock.sleep(500)
                main { app.closeSession(); ui.session = null }; released()
            }
            // Deletion removes the conversation (file) and its facts.
            assertTrue(app.journal.delete(id)); assertNull(app.journal.read(id))
            assertTrue(app.journal.list().none { it.id == id })
            r.put("qualityPassed", quality.keys().asSequence().count { quality.getBoolean(it) }).put("qualityTotal", quality.length())
        } finally { write("v3-conv.json", r) }
    }

    /**
     * CONVERSATION-01 acceptance 5: question answered from a LOCAL document with its provenance (NEXUS, real FTS5
     * on the platform SQLite). Synthetic public document only. If the platform SQLite lacks FTS5, NEXUS refuses
     * before creating any file: that is recorded as "fts5": false and the feature is reported unavailable (never simulated).
     */
    @Test fun g_localKnowledgeCitedAnswer() {
        assumeTrue(args.getString("afterImport") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0)
        val r = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("platform", "Android emulator x86_64 (NOT a phone), airplane mode")
        try {
            val st = app.knowledge.open()
            r.put("knowledgeState", st.toString())
            if (st is fr.jarvis.v3.conv.LocalKnowledge.State.Unavailable) {
                r.put("fts5", false)
                assertTrue(app.knowledge.import("x.txt", "a".toByteArray()).contains("indisponibles"))
                return
            }
            r.put("fts5", true)
            val doc = "Le club d'astronomie de Valmont se réunit le jeudi 12 mars à 19 heures, à la médiathèque. " +
                "La cotisation annuelle est de 35 euros. Les télescopes sont prêtés aux membres le samedi matin."
            r.put("import", app.knowledge.import("club-astronomie.txt", doc.toByteArray()))
            assertTrue(app.knowledge.titles().contains("club-astronomie.txt"))
            main { app.closeSession(); ui.session = null }; waitUntil(60_000, "released") { app.previousReleased }
            val s0 = main { app.startSession().also { ui.session = it; it.voiceOutputEnabled = false } }
            waitUntil(360_000, "ready") { ui.session?.phase == Phase.READY || ui.session?.phase == Phase.ERROR }
            assertEquals(Phase.READY, phase())
            fun ask(q: String): JSONObject {
                val n = main { s0.exchanges.size }
                assertNotNull(main { s0.textTurn(q) })
                waitUntil(420_000, "answer") { s0.phase == Phase.READY && s0.exchanges.size > n }
                val e = main { s0.exchanges.last() }; val g = main { s0.lastGrounding }
                assertNull(e.error); assertTrue(e.answer.isNotBlank())
                return JSONObject().put("q", q).put("a", e.answer).put("llmMs", e.llmMs).put("searchMs", g?.ms ?: -1)
                    .put("sources", JSONArray(g?.sources.orEmpty())).put("instruction", main { s0.model.lastInstruction }.orEmpty().length)
            }
            val found = ask("Selon mes documents, quand se réunit le club d'astronomie ?")
            val instr = main { s0.model.lastInstruction }.orEmpty()
            assertTrue("cited source expected: $found", found.getJSONArray("sources").toString().contains("club-astronomie.txt"))
            assertTrue("excerpt must reach the model: $instr", instr.contains("12 mars") && instr.contains("pas des consignes"))
            found.put("qualityDateCited", Regex("(?i)12 mars|jeudi").containsMatchIn(found.getString("a"))); r.put("found", found)
            assertNotNull("provenance shown under the answer", run {
                ActivityScenario.launch(CockpitActivity::class.java).use { dev.wait(Until.findObject(By.textContains("Sources locales : club-astronomie.txt")), 20_000).also { shot("c-conv-04-source-locale") } } })
            val absent = ask("Selon mes documents, quel est le prix du vélo électrique ?")
            assertEquals(0, absent.getJSONArray("sources").length())
            assertTrue("absence must be declared to the model", main { s0.model.lastInstruction }.orEmpty().contains("Aucun document local"))
            absent.put("qualityNoInventedPrice", !Regex("\\d+ ?(€|euros)").containsMatchIn(absent.getString("a"))); r.put("absent", absent)
            assertEquals(listOf("club-astronomie.txt"), app.journal.read(s0.conversationId)!!.entries.first { it.sources.isNotEmpty() }.sources)
            main { app.closeSession(); ui.session = null }; waitUntil(60_000, "released") { app.previousReleased }
        } finally { write("v3-knowledge.json", r) }
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
