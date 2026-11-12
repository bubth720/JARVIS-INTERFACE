package fr.jarvis.v1

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class V1UnitTest {
    private fun block(level: Float, n: Int = 1600) = FloatArray(n) { if (it % 2 == 0) level else -level }

    @Test fun endpointStopsAfterSpeechThenOneSecondOfSilence() {
        val d = EndpointDetector()
        repeat(5) { assertFalse(d.accept(block(0.2f), 1600)) }            // 0.5 s of speech
        repeat(9) { assertFalse(d.accept(block(0.001f), 1600)) }          // 0.9 s silence: continue
        assertTrue(d.accept(block(0.001f), 1600))                          // 1.0 s silence: stop
        assertTrue(d.speechDetected)
    }
    @Test fun endpointGivesUpWithoutSpeechAndCapsDuration() {
        val quiet = EndpointDetector(); var stops = 0
        repeat(60) { if (quiet.accept(block(0.0f), 1600)) stops++ }
        assertTrue(stops > 0); assertFalse(quiet.speechDetected)
        val talker = EndpointDetector(maxMs = 2_000); var at = -1
        for (i in 0 until 40) if (talker.accept(block(0.3f), 1600)) { at = i; break }
        assertEquals(19, at) // 20 blocks x 100 ms = 2 s
    }
    @Test fun speakableTextDropsEmojiAndMarkupButKeepsFrench() {
        assertEquals("Bonjour ! Vous vous appelez Thomas.", PocketSpeech.clean("**Bonjour** ! 😊 Vous vous appelez `Thomas`."))
        assertEquals("D'accord.", PocketSpeech.clean("😊"))
        assertTrue(PocketSpeech.clean("é".repeat(900)).length == 600)
    }
    @Test fun sttImportAcceptsOnlyThePinnedModel() {
        val dir = Files.createTempDirectory("v1-assets").toFile()
        val assets = VoiceAssets(dir) { Long.MAX_VALUE }
        val e = runCatching { assets.importStt(ByteArrayInputStream(ByteArray(1000) { 1 })) }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("refusé"))
        assertNull(assets.sttFile())
        assertTrue(dir.resolve("stt").listFiles().orEmpty().isEmpty())
        assertEquals("Type de fichier non reconnu : notes.txt", runCatching { assets.importAny("notes.txt") { ByteArrayInputStream(ByteArray(1)) } }.exceptionOrNull()?.message)
        dir.deleteRecursively()
    }
}
