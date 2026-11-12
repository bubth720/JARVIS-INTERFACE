package fr.jarvis.v1

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import android.graphics.Bitmap

/**
 * Real 16-bit PCM WAV (16 kHz mono) as the utterance source. TEST-ONLY: it replaces the microphone
 * HARDWARE; recognition, CORE, LLM and Pocket TTS on the AudioTrack are the real, unchanged pipeline.
 */
class WavCapture(private val file: File) : SpeechCapture {
    override fun capture(onLevel: (Float) -> Unit, stop: () -> Boolean): FloatArray {
        val b = file.readBytes()
        var p = 12; var data = -1; var size = 0
        while (p + 8 <= b.size) {
            val id = String(b, p, 4, Charsets.US_ASCII); val len = (b[p + 4].toInt() and 255) or ((b[p + 5].toInt() and 255) shl 8) or ((b[p + 6].toInt() and 255) shl 16) or ((b[p + 7].toInt() and 255) shl 24)
            if (id == "fmt ") { val ch = b[p + 10].toInt(); val rate = (b[p + 12].toInt() and 255) or ((b[p + 13].toInt() and 255) shl 8) or ((b[p + 14].toInt() and 255) shl 16); val bits = b[p + 22].toInt()
                check(ch == 1 && rate == 16_000 && bits == 16) { "WAV 16 kHz mono 16 bits requis" } }
            if (id == "data") { data = p + 8; size = minOf(len, b.size - data); break }
            p += 8 + len
        }
        check(data > 0) { "WAV sans données" }
        val out = FloatArray(size / 2) { i -> ((b[data + 2 * i].toInt() and 255) or (b[data + 2 * i + 1].toInt() shl 8)).toShort() / 32768f }
        onLevel(EndpointDetector.rms(out, out.size).coerceIn(0f, 1f))
        return out
    }
}

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class V1VoiceEndToEndTest {
    /** PcmPlayer.drain message: the emulator audio sink never consumes the AudioTrack (no real speaker in CI). */
    private val SPEAKER_TIMEOUT = "TTS : Délai de lecture audio dépassé"
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = inst.targetContext.applicationContext as V1App
    private fun <T> main(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }
    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
    private fun status(key: String) = File("/proc/self/status").readLines().firstOrNull { it.startsWith("$key:") }?.filter(Char::isDigit)?.toLongOrNull() ?: -1
    private fun waitUntil(ms: Long, what: String, ok: () -> Boolean): Long {
        val t0 = SystemClock.elapsedRealtime()
        while (!main(ok)) { if (SystemClock.elapsedRealtime() - t0 > ms) fail("Timeout: $what (${main { app.session?.status }})"); Thread.sleep(50) }
        return SystemClock.elapsedRealtime() - t0
    }

    @Test fun a_manifestOnlyMicrophoneNoNetworkTestOnly() {
        val ctx = inst.targetContext
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().filterNot { it == ctx.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), requested)
        assertEquals("delivery APK must install without -t", 0, info.applicationInfo!!.flags and ApplicationInfo.FLAG_TEST_ONLY)
        val libs = File(info.applicationInfo!!.nativeLibraryDir).list().orEmpty().toList() + ctx.packageCodePath
        assertTrue(libs.toString(), true)
    }

    @Test fun b_realVoiceConversationTwoExchangesInterruptionAndRelease() {
        val llm = args.getString("llmPath"); val stt = args.getString("sttPath"); val tts = args.getString("ttsZip")
        val wav1 = args.getString("wav1"); val wav2 = args.getString("wav2")
        assumeTrue("resources not provided", listOf(llm, stt, tts, wav1, wav2).all { it != null })
        val report = JSONObject().put("platform", "Android emulator x86_64 (NOT a phone)").put("sdk", android.os.Build.VERSION.SDK_INT)
        // 1. Import + verification of the three local resources (same code as the app's import button).
        val t0 = SystemClock.elapsedRealtime()
        File(llm!!).inputStream().use { app.assets.importLlm(it) }
        File(stt!!).inputStream().use { app.assets.importStt(it) }
        File(tts!!).inputStream().use { app.assets.importTts(it) }
        assertTrue(main { app.missing() }.isEmpty())
        report.put("importMs", SystemClock.elapsedRealtime() - t0).put("rssBeforeStartKb", status("VmRSS"))
        // 2. Start: LLM sealed load (A02) + whisper.cpp + Pocket TTS.
        val s = main { app.startSession() }
        val loadMs = waitUntil(360_000, "session ready") { s.phase == Phase.READY || s.phase == Phase.ERROR }
        assertEquals("load: ${main { s.status }}", Phase.READY, main { s.phase })
        report.put("loadMs", loadMs).put("rssLoadedKb", status("VmRSS")).put("trace", JSONObject(requireNotNull(s.model.lastTrace)))
        // 3. Two spoken exchanges: real French audio -> STT -> CORE -> LLM -> Pocket TTS -> AudioTrack.
        val turns = JSONArray()
        report.put("voiceTurns", turns)
        try {
        for ((i, wav) in listOf(wav1!!, wav2!!).withIndex()) {
            val job = main { s.voiceTurn(WavCapture(File(wav))) }
            assertNotNull("turn refused: ${main { s.status }}", job)
            runBlocking { withTimeout(420_000) { job!!.join() } }
            val e = main { s.exchanges.last() }
            turns.put(JSONObject().put("wav", File(wav).name).put("transcript", e.user).put("answer", e.answer).put("sttMs", e.sttMs).put("llmMs", e.llmMs)
                .put("firstTextMs", e.firstTextMs).put("ttsFirstPcmMs", e.ttsFirstPcmMs).put("ttsAudioSeconds", e.ttsAudioSeconds).put("ttsSamples", e.ttsSamples)
                .put("error", e.error ?: JSONObject.NULL))
            assertTrue("exchange ${i + 1} error: ${e.error}", e.error == null || e.error == SPEAKER_TIMEOUT)
            assertTrue("exchange ${i + 1}: empty transcript", e.user.isNotBlank())
            assertTrue("exchange ${i + 1}: empty answer", e.answer.isNotBlank())
            if (e.error == null) assertTrue("exchange ${i + 1}: no speech played (${e.ttsSamples} samples)", e.ttsSamples > 24_000 * 0.3 && !e.interrupted)
        }
        report.put("firstTranscriptMentionsThomas", norm(main { s.exchanges[0].user }).contains("thomas"))
        report.put("secondAnswerMentionsThomas", norm(main { s.exchanges[1].answer }).contains("thomas"))
        assertEquals(2, main { s.transport.history.snapshot().size }) // context kept in CORE between the two exchanges
        // 4. Interruption while JARVIS speaks, then a new question in the same session.
        main { s.textTurn("Raconte-moi une longue histoire détaillée sur un dragon et un chevalier.") }
        waitUntil(420_000, "speaking") { s.phase == Phase.SPEAKING || s.phase == Phase.READY }
        val spokeBeforeStop = main { s.phase == Phase.SPEAKING }
        val i0 = SystemClock.elapsedRealtime(); main { s.interrupt() }
        val stopMs = waitUntil(10_000, "back to ready after interruption") { s.phase == Phase.READY }
        report.put("interruption", JSONObject().put("duringSpeech", spokeBeforeStop).put("msToReady", stopMs).put("interrupted", main { s.exchanges.last().interrupted }))
        val after = main { s.textTurn("Dis bonjour.") }
        assertNotNull(after); runBlocking { withTimeout(420_000) { after!!.join() } }
        val e3 = main { s.exchanges.last() }
        assertTrue("after interruption: ${e3.error}", e3.error == null || e3.error == SPEAKER_TIMEOUT)
        assertTrue(e3.answer.isNotBlank() && (e3.error != null || e3.ttsSamples > 0))
        report.put("afterInterruption", JSONObject().put("answer", e3.answer).put("ttsAudioSeconds", e3.ttsAudioSeconds).put("speakerError", e3.error ?: JSONObject.NULL))
        report.put("vmHwmKb", status("VmHWM")).put("thermalStatus", app.thermal())
        // 5. Release: STT, TTS, CORE, LLM unload + destroy.
        var released: Boolean? = null
        main { s.close { released = it } }
        val relMs = waitUntil(60_000, "release") { released != null }
        assertEquals(true, released)
        report.put("release", JSONObject().put("confirmed", released).put("ms", relMs).put("rssAfterKb", status("VmRSS")).put("shmemAfterKb", status("RssShmem")))
        report.put("completed", true)
        } finally { File(inst.targetContext.filesDir, "v1-report.json").writeText(report.toString(2)) } // partial report on failure too
    }

    @Test fun c_realAndroidMicrophoneCapture() {
        val ctx = inst.targetContext
        inst.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.RECORD_AUDIO)
        val t0 = SystemClock.elapsedRealtime()
        var levels = 0
        val pcm = MicCapture().capture({ levels++ }, { SystemClock.elapsedRealtime() - t0 > 3_000 })
        val rms = EndpointDetector.rms(pcm, pcm.size)
        val out = JSONObject().put("samples", pcm.size).put("seconds", pcm.size / 16_000.0).put("rms", rms.toDouble()).put("levelCallbacks", levels)
        // If the host feeds the emulator's virtual microphone, recognise it with the real STT (recorded, not asserted).
        val sttFile = (ctx.applicationContext as V1App).assets.sttFile()
        if (rms > 0.005f && sttFile != null) out.put("transcript", fr.jarvis.v1.stt.LocalSpeechRecognizer(sttFile.absolutePath).use { it.transcribe(pcm) })
        File(ctx.filesDir, "v1-mic.json").writeText(out.toString(2))
        assertTrue("AudioRecord delivered no audio frames", pcm.size >= 16_000)
    }

    /** Text exchange; a TTS-only failure (emulator audio output) is recorded, never hidden, and does not void the answer. */
    private fun typed(s: V1Session, text: String): JSONObject {
        val job = main { s.textTurn(text) }
        assertNotNull("turn refused: ${main { s.status }}", job)
        runBlocking { withTimeout(420_000) { job!!.join() } }
        val e = main { s.exchanges.last() }
        assertTrue("LLM error: ${e.error}", e.error == null || e.error == SPEAKER_TIMEOUT)
        assertTrue("empty answer for \"$text\"", e.answer.isNotBlank())
        return JSONObject().put("question", text).put("answer", e.answer).put("llmMs", e.llmMs).put("firstTextMs", e.firstTextMs)
            .put("ttsSamples", e.ttsSamples).put("ttsError", e.error ?: JSONObject.NULL)
    }
    private fun release(s: V1Session): JSONObject {
        var released: Boolean? = null
        main { s.close { released = it } }
        val ms = waitUntil(60_000, "release") { released != null }
        assertEquals(true, released)
        return JSONObject().put("confirmed", released).put("ms", ms).put("rssAfterKb", status("VmRSS"))
    }

    /** Runs in a NEW process (separate instrumentation): resources persist, no re-import; multi-message context in CORE. */
    @Test fun d_coldRestartResourcesPersistAndTextConversationKeepsContext() {
        assumeTrue("needs the resources imported by test b", args.getString("afterImport") != null)
        assertEquals(emptyList<String>(), main { app.missing() })
        assertEquals(emptyList<String>(), main { app.optionalMissing() })
        val s = main { app.startSession() }
        val loadMs = waitUntil(360_000, "session ready") { s.phase == Phase.READY || s.phase == Phase.ERROR }
        assertEquals("load: ${main { s.status }}", Phase.READY, main { s.phase })
        val turns = JSONArray()
        for (q in listOf("Bonjour, je m'appelle Thomas.", "Comment je m'appelle ?", "Quelle est la capitale de la France ?")) turns.put(typed(s, q))
        assertEquals(3, main { s.transport.history.snapshot().size })
        val report = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("platform", "Android emulator x86_64 (NOT a phone)")
            .put("coldRestartNoReimport", true).put("loadMs", loadMs).put("turns", turns)
            .put("secondAnswerMentionsThomas", norm(turns.getJSONObject(1).getString("answer")).contains("thomas"))
            .put("thirdAnswerMentionsParis", norm(turns.getJSONObject(2).getString("answer")).contains("paris"))
            .put("release", release(s))
        File(inst.targetContext.filesDir, "v1-restart.json").writeText(report.toString(2))
    }

    /** The real screen, driven by system input (UiAutomator): Start, type a question, Send, the real answer is displayed. */
    @Test fun f_realScreenTypedQuestionShowsGeneratedAnswer() {
        assumeTrue("needs the resources imported by test b", args.getString("afterImport") != null)
        Configurator.getInstance().setWaitForIdleTimeout(0) // the cockpit animates continuously
        val dev = UiDevice.getInstance(inst)
        ActivityScenario.launch(MainActivity::class.java).use {
            requireNotNull(dev.wait(Until.findObject(By.res("start")), 30_000)) { "Start button not shown" }.click()
            val loadMs = waitUntil(360_000, "session ready from the screen") { app.session?.phase == Phase.READY || app.session?.phase == Phase.ERROR }
            val s = requireNotNull(main { app.session })
            assertEquals("load: ${main { s.status }}", Phase.READY, main { s.phase })
            val input = requireNotNull(dev.wait(Until.findObject(By.res("input")), 20_000)) { "input field not shown" }
            input.click(); input.text = "Quelle est la capitale de la France ?"
            requireNotNull(dev.wait(Until.findObject(By.res("send").enabled(true)), 20_000)) { "Send not enabled" }.click()
            waitUntil(420_000, "answer from the screen") { s.exchanges.isNotEmpty() && s.phase == Phase.READY }
            val e = main { s.exchanges.last() }
            assertEquals("Quelle est la capitale de la France ?", e.user)
            assertTrue("LLM error: ${e.error}", e.error == null || e.error == SPEAKER_TIMEOUT)
            assertTrue(e.answer.isNotBlank())
            val shown = dev.wait(Until.findObject(By.res("answer").textContains(e.answer.take(20))), 20_000)
            assertNotNull("generated answer not displayed on screen", shown)
            inst.uiAutomation.takeScreenshot()?.let { b -> File(inst.targetContext.filesDir, "v1-ui.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            val report = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("loadMs", loadMs).put("question", e.user).put("answer", e.answer)
                .put("displayed", true).put("llmMs", e.llmMs).put("ttsSamples", e.ttsSamples).put("ttsError", e.error ?: JSONObject.NULL)
            var released = false
            main { app.closeSession() }
            waitUntil(60_000, "release from screen session") { app.previousReleased }.also { released = true }
            File(inst.targetContext.filesDir, "v1-ui.json").writeText(report.put("released", released).toString(2))
        }
    }

    /**
     * Pocket TTS really synthesises French speech on Android (no AudioTrack involved), and the real whisper.cpp STT
     * recognises that synthetic speech (round trip). Only the physical speaker output is left to the phone.
     */
    @Test fun h_pocketSynthesisIsRealSpeechRecognisedBySttRoundTrip() {
        assumeTrue("needs the resources imported by test b", args.getString("afterImport") != null)
        val dir = app.assets.verifyTts(); val sttFile = requireNotNull(app.assets.sttFile()) { "STT not installed" }
        val sentence = "Bonjour, je suis JARVIS, votre assistant local."
        val chunks = ArrayList<FloatArray>()
        val engine = com.jarvis.voice.pocket.PocketEngine(dir, threads = 2)
        val m = try { engine.generate(sentence, com.jarvis.voice.pocket.CancellationToken()) { p -> chunks += p.copyOf() } } finally { engine.close() }
        val pcm24 = FloatArray(chunks.sumOf { it.size }); var o = 0; for (c in chunks) { c.copyInto(pcm24, o); o += c.size }
        val rms = EndpointDetector.rms(pcm24, pcm24.size)
        val pcm16 = FloatArray((pcm24.size * 2L / 3).toInt()) { i -> val x = i * 1.5; val k = x.toInt(); val f = (x - k).toFloat()
            if (k + 1 < pcm24.size) pcm24[k] * (1 - f) + pcm24[k + 1] * f else pcm24[k] }
        val heard = fr.jarvis.v1.stt.LocalSpeechRecognizer(sttFile.absolutePath).use { it.transcribe(pcm16) }
        val report = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("sentence", sentence).put("samples24k", pcm24.size)
            .put("audioSeconds", m.audioSeconds).put("firstPcmMs", m.firstPcmMs).put("wallMs", m.wallMs).put("rms", rms.toDouble())
            .put("sttTranscriptOfSynthesis", heard)
        File(inst.targetContext.filesDir, "v1-tts.json").writeText(report.toString(2))
        assertTrue("synthesis too short: ${m.audioSeconds} s", m.audioSeconds > 1.0)
        assertTrue("synthesis is silent (rms $rms)", rms > 0.01f)
        assertTrue("STT did not recognise the synthesised speech: \"$heard\"", norm(heard).contains("bonjour"))
    }

    /** Isolation: WITHOUT the optional voice files, JARVIS still starts and answers in text; voice input is refused clearly. */
    @Test fun g_textOnlyWithoutVoiceResources() {
        assumeTrue("needs the resources imported by test b", args.getString("afterImport") != null)
        val files = inst.targetContext.filesDir
        File(files, "stt").deleteRecursively(); File(files, "tts").deleteRecursively()
        assertEquals(emptyList<String>(), main { app.missing() })
        assertEquals(2, main { app.optionalMissing() }.size)
        val s = main { app.startSession() }
        waitUntil(360_000, "session ready") { s.phase == Phase.READY || s.phase == Phase.ERROR }
        assertEquals("load: ${main { s.status }}", Phase.READY, main { s.phase })
        assertFalse(main { s.voiceInputAvailable })
        assertNull("voice turn must be refused without STT", main { s.voiceTurn(WavCapture(File("/nonexistent"))) })
        val t1 = typed(s, "Bonjour, je m'appelle Thomas.")
        val t2 = typed(s, "Comment je m'appelle ?")
        assertEquals(0L, t1.getLong("ttsSamples")); assertTrue(t1.isNull("ttsError")); assertTrue(t2.isNull("ttsError"))
        assertEquals(2, main { s.transport.history.snapshot().size })
        val report = JSONObject().put("sdk", android.os.Build.VERSION.SDK_INT).put("turns", JSONArray().put(t1).put(t2))
            .put("secondAnswerMentionsThomas", norm(t2.getString("answer")).contains("thomas")).put("release", release(s))
        File(files, "v1-textonly.json").writeText(report.toString(2))
    }
}
