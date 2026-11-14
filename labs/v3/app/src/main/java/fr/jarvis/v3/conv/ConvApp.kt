package fr.jarvis.v3.conv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.StatFs
import com.stark.jarvislocal.v1b.SourceDescriptor
import com.stark.jarvislocal.v1b.VerifiedModel
import fr.jarvis.llm.a02.SealedNativeBridge
import fr.jarvis.v1.VoiceAssets
import java.io.File
import kotlinx.coroutines.*

/**
 * CONVERSATION-01 application, derived from [fr.jarvis.v1.V1App] (unchanged): one session and ONE native
 * engine per process, resources verified before any load, same model (Qwen3-0.6B, SHA-verified) and same
 * protections. Adds the local conversation journal, the persona the owner chose, and resume.
 */
class ConvApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val assets by lazy { VoiceAssets(filesDir) { StatFs(filesDir.path).availableBytes } }
    /** App-private conversation history (never exported; excluded from backup: allowBackup=false). */
    val journal by lazy { ConversationJournal(File(filesDir, "conversations")) }
    val personas by lazy { PersonaStore(getSharedPreferences("persona", Context.MODE_PRIVATE)) }
    /** Local documents (NEXUS), app-private; opened off the main thread on first use. */
    val knowledge by lazy { LocalKnowledge(File(filesDir, "nexus")) }
    /** Extensions routed to the knowledge base on import (the model / voice files keep their verified importer). */
    fun isDocument(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "csv", "tsv", "docx", "xml")
    var session: ConvSession? = null; private set
    @Volatile var previousReleased = true; private set

    fun thermal(): Int = getSystemService(PowerManager::class.java).currentThermalStatus
    fun lowMemory(): Boolean = ActivityManager.MemoryInfo().also { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.lowMemory
    fun memoryBudgetBytes(): Long {
        val info = ActivityManager.MemoryInfo(); (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.lowMemory) 0L else (info.availMem - info.threshold).coerceAtLeast(0L)
    }
    fun importFolder() = getExternalFilesDir("import")

    fun missing(): List<String> = buildList { if (!assets.llmReady()) add("Modèle de langage (Qwen3-0.6B-Q4_K_M.gguf)") }
    fun optionalMissing(): List<String> = buildList {
        if (assets.sttFile() == null) add("Reconnaissance vocale (ggml-base-q5_1.bin)")
        if (!assets.ttsPresent()) add("Voix Pocket (pocket-fr-6l-alba.zip)")
    }

    /**
     * Starts a session. [resumeId] = a journal conversation to continue (null = new conversation).
     * The visible exchanges come back, and so does the engine context (last exchanges + stated facts).
     */
    fun startSession(resumeId: String? = null): ConvSession {
        session?.let { if (!it.closed) return it }
        check(previousReleased) { "Libération de la session précédente en cours : réessaie dans un instant" }
        check(missing().isEmpty()) { "Ressources manquantes : " + missing().joinToString() }
        val entry = checkNotNull(assets.llm.current())
        val context = ConversationContext(personas.load())
        val model = ConvLanguageModel(SealedNativeBridge.get(), VerifiedModel({
            val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
        }, entry.sizeBytes, entry.sha256), ::memoryBudgetBytes, instruction = context::instructionFor)
        val resume = resumeId?.let { runCatching { journal.read(it) }.getOrNull() }
        return ConvSession(scope, model, context, journal, assets.sttFile(), assets.ttsDir().takeIf { assets.ttsPresent() }, ::thermal, ::lowMemory, resume,
            knowledge = knowledge).also { session = it; scope.launch(Dispatchers.IO) { runCatching { knowledge.open() } }; it.start() }
    }

    fun closeSession() {
        val s = session ?: return
        previousReleased = false
        s.close { previousReleased = true }
    }

    /** Owner-chosen persona: saved, and applied from the next turn of the running session. */
    fun applyPersona(p: PersonaSettings) { personas.save(p); session?.context?.persona = p }
}

/** Persists the owner's persona parameters (CORE identity profile values), app-private. */
class PersonaStore(private val prefs: android.content.SharedPreferences) {
    fun load(): PersonaSettings = runCatching {
        if (!prefs.contains("profile")) return PersonaSettings.ATHENA
        PersonaSettings(prefs.getString("profile", "Athéna")!!, prefs.getInt("frankness", 75), prefs.getInt("humour", 60), prefs.getInt("initiative", 100),
            prefs.getBoolean("tutoiement", true), prefs.getBoolean("elegant", true),
            rendering = PersonaSettings.Rendering.valueOf(prefs.getString("rendering", "COMPACT")!!))
    }.getOrDefault(PersonaSettings.ATHENA)

    fun save(p: PersonaSettings) {
        prefs.edit().putString("profile", p.profileName).putInt("frankness", p.frankness).putInt("humour", p.humour).putInt("initiative", p.initiative)
            .putBoolean("tutoiement", p.tutoiement).putBoolean("elegant", p.elegant).putString("rendering", p.rendering.name).apply()
    }
}
