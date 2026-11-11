package com.stark.jarvislocal.v1a

import com.jarvis.interfaceapp.ui.chat.core.*
import com.stark.jarvislocal.*
import fr.jarvis.llm.a02.A02Native
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** TEST DOUBLES ONLY. Production code has no synthetic engine; these fakes exist solely in src/test. */
private class RecordingModel(private val delayMs: Long = 0, private val replies: (String) -> String = { "réponse:" + it.length }) : LocalLanguageModel {
    val prompts = mutableListOf<Pair<String, String>>()
    var stops = 0; var prepared = false; var unloaded = 0
    var failPrepare: String? = null
    override suspend fun warmUp(settings: JarvisSettings) { failPrepare?.let { error(it) }; prepared = true }
    override suspend fun generate(prompt: String, systemPrompt: String, settings: JarvisSettings): String {
        prompts += prompt to systemPrompt; if (delayMs > 0) delay(delayMs); return replies(prompt)
    }
    override fun isLoaded() = prepared
    override fun requestStop(): StopResult { stops++; return StopResult.REQUESTED }
    override suspend fun unload(): UnloadResult { unloaded++; return UnloadResult.CONFIRMED }
}

private class FakeNative : A02Native {
    val calls = mutableListOf<String>()
    var loadFailure: String? = null
    override fun create() = 7L
    override fun begin(handle: Long) { calls += "begin" }
    override fun cancel(handle: Long) { calls += "cancel" }
    override fun loadSealedFd(handle: Long, sourceFd: Int, size: Long, sha256: ByteArray, budget: Long, context: Int, threads: Int, small: Boolean): ByteArray {
        calls += "load:$sourceFd:$size:${String(sha256)}:$budget:$context"
        loadFailure?.let { throw IllegalStateException(it) }
        return "{\"a02\":\"v1\"}".toByteArray()
    }
    override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray {
        calls += "gen:${roles.map { String(it) }}:$maxTokens:${String(system)}"
        return "  Bonjour Thomas  ".toByteArray()
    }
    override fun unload(handle: Long) { calls += "unload" }
    override fun destroy(handle: Long) { calls += "destroy" }
}

private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

@OptIn(ExperimentalCoroutinesApi::class)
class V1aUnitTest {
    private fun TestScope.wire(model: LocalLanguageModel) = V1aCoreTransport(backgroundScope, model, StandardTestDispatcher(testScheduler))

    @Test fun realCoreCoordinatorDeliversExactlyTheEngineText() = runTest {
        val model = RecordingModel(0) { "Je suis JARVIS." }
        val core = wire(model)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent()
        assertTrue(model.prepared)
        assertEquals(CoreTextLink.READY, core.connection.value)
        assertTrue(client.submit("Qui es-tu ?")); runCurrent()
        assertEquals(1, core.acknowledgements)
        advanceUntilIdle()
        assertEquals("Je suis JARVIS.", client.state.value.text)
        assertFalse(client.state.value.text.contains("SYNTHETIC"))
        assertEquals(1, model.prompts.size)
        assertTrue(model.prompts[0].first.endsWith("UTILISATEUR: Qui es-tu ?\nASSISTANT:"))
        assertEquals(JarvisSettings.DEFAULT_SYSTEM_PROMPT, model.prompts[0].second)
        client.close()
    }

    @Test fun multiTurnCarriesPreviousTurnsThroughCoreContract() = runTest {
        val model = RecordingModel(0) { if (it.contains("Comment je m'appelle")) "Thomas." else "Enchanté." }
        val core = wire(model)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent()
        client.submit("Je m'appelle Thomas."); advanceUntilIdle()
        client.submit("Comment je m'appelle ?"); advanceUntilIdle()
        assertEquals(2, core.history.snapshot().size)
        assertTrue(model.prompts[1].first.startsWith("UTILISATEUR: Je m'appelle Thomas.\nASSISTANT: Enchanté.\n"))
        assertEquals("Thomas.", client.state.value.text)
        client.close()
    }

    @Test fun engineFailureIsVisibleAndNeverReplacedByText() = runTest {
        val model = RecordingModel(0) { error("Calcul natif arrêté") }
        val core = wire(model)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("x"); advanceUntilIdle()
        assertEquals("Calcul natif arrêté", core.lastError.value)
        assertEquals("", client.state.value.text)
        assertTrue(core.history.snapshot().isEmpty())
        client.close()
    }

    @Test fun prepareFailureKeepsLinkClosed() = runTest {
        val model = RecordingModel().apply { failPrepare = "A02_DIGEST_REFUSED" }
        val core = wire(model)
        runCurrent()
        assertNotEquals(CoreTextLink.READY, core.connection.value)
        assertEquals(SessionPhase.ERROR, core.coordinator.state.value.phase)
        assertEquals("A02_DIGEST_REFUSED", core.lastError.value)
    }

    @Test fun stopReachesTheEngineCancel() = runTest {
        val model = RecordingModel(delayMs = 5_000)
        val core = wire(model)
        val client = CoreConversationClientAdapter(core, backgroundScope)
        runCurrent(); client.submit("long"); runCurrent(); advanceTimeBy(100)
        assertTrue(core.coordinator.state.value.busy)
        client.cancel(); runCurrent()
        assertTrue(model.stops >= 1)
        advanceUntilIdle(); client.close()
    }

    @Test fun releaseEngineUnloads() = runTest {
        val model = RecordingModel()
        val core = wire(model); runCurrent()
        assertEquals(UnloadResult.CONFIRMED, core.releaseEngine())
        assertEquals(1, model.unloaded)
    }

    @Test fun historyDropsOldestTurnsToFitCoreBudget() {
        val h = ConversationHistory()
        repeat(30) { h.record("question numéro $it " + "x".repeat(80), "réponse $it " + "y".repeat(80)) }
        val req = h.request("dernière ?", JarvisSettings.DEFAULT_SYSTEM_PROMPT, GenerationLimits(2048, 192), InferenceCapabilities())
        assertTrue(req.context.contains("question numéro 29"))
        assertFalse(req.context.contains("question numéro 0 "))
        validateInference(req, InferenceCapabilities())
    }

    @Test fun a02ModelMapsCoreContractToSealedLoaderOnly() = runBlocking {
        val native = FakeNative()
        var closed = false
        val m = A02LanguageModel(native, VerifiedModel({ object : SourceDescriptor { override val fd = 42; override fun close() { closed = true } } },
            1234L, "a".repeat(64)), { 2_000_000_000L })
        m.warmUp(JarvisSettings(contextSize = 2048))
        assertTrue(closed)
        assertTrue(native.calls.any { it == "load:42:1234:${"a".repeat(64)}:2000000000:2048" })
        val out = m.generate("UTILISATEUR: salut\nASSISTANT:", "Système", JarvisSettings(maxTokens = 999))
        assertEquals("Bonjour Thomas", out)
        assertTrue(native.calls.any { it == "gen:[user]:256:Système" })
        assertEquals(UnloadResult.CONFIRMED, m.unload())
        assertFalse(m.isLoaded())
    }

    @Test fun a02LoadRefusalPropagatesAndStaysUnloaded() = runBlocking {
        val native = FakeNative().apply { loadFailure = "A02_MEMORY_BUDGET_REFUSED" }
        val m = A02LanguageModel(native, VerifiedModel({ object : SourceDescriptor { override val fd = 3; override fun close() {} } }, 10L, "b".repeat(64)), { 1L })
        val e = runCatching { m.warmUp(JarvisSettings()) }.exceptionOrNull()
        assertEquals("A02_MEMORY_BUDGET_REFUSED", e?.message)
        assertFalse(m.isLoaded())
        assertEquals(StopResult.NOT_RUNNING, m.requestStop())
    }

    @Test fun modelStoreAcceptsOnlyMatchingDigestAtomically() {
        val root = Files.createTempDirectory("v1a-store").toFile()
        val store = ModelStore(root) { Long.MAX_VALUE }
        val bytes = ByteArray(3_000_000) { (it % 251).toByte() }
        val wrong = runCatching { store.import(ByteArrayInputStream(bytes), "0".repeat(64)) }.exceptionOrNull()
        assertTrue(wrong?.message!!.contains("Empreinte"))
        assertNull(store.current())
        assertTrue(root.listFiles()!!.isEmpty())
        assertTrue(runCatching { store.import(ByteArrayInputStream(bytes), "xyz") }.isFailure)
        val ok = store.import(ByteArrayInputStream(bytes), sha(bytes).uppercase())
        assertEquals(sha(bytes), store.current()!!.sha256)
        assertEquals(bytes.size.toLong(), ok.sizeBytes)
        val low = ModelStore(Files.createTempDirectory("v1a-low").toFile()) { 10L }
        assertTrue(runCatching { low.import(ByteArrayInputStream(bytes), sha(bytes)) }.exceptionOrNull()!!.message!!.contains("Stockage"))
        assertTrue(runCatching { store.import(ByteArrayInputStream(bytes), sha(bytes)) { true } }.isSuccess) // already installed: no rewrite
    }
}
