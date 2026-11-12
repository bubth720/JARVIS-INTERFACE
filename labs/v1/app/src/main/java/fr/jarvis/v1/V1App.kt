package fr.jarvis.v1

import android.app.ActivityManager
import android.app.Application
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.StatFs
import com.stark.jarvislocal.v1b.A02LanguageModel
import com.stark.jarvislocal.v1b.SourceDescriptor
import com.stark.jarvislocal.v1b.VerifiedModel
import fr.jarvis.llm.a02.SealedNativeBridge
import kotlinx.coroutines.*

/** One JARVIS session and ONE native LLM engine per process; resources verified before any load. */
class V1App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val assets by lazy { VoiceAssets(filesDir) { StatFs(filesDir.path).availableBytes } }
    var session: V1Session? = null; private set
    @Volatile var previousReleased = true; private set

    fun thermal(): Int = getSystemService(PowerManager::class.java).currentThermalStatus
    fun lowMemory(): Boolean = ActivityManager.MemoryInfo().also { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.lowMemory
    /** Bytes the A02 snapshot may use now: available RAM above the system low-memory threshold. */
    fun memoryBudgetBytes(): Long {
        val info = ActivityManager.MemoryInfo(); (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.lowMemory) 0L else (info.availMem - info.threshold).coerceAtLeast(0L)
    }
    fun importFolder() = getExternalFilesDir("import")

    /** Required to start: the language model only. Voice input and voice output are optional and isolated. */
    fun missing(): List<String> = buildList {
        if (!assets.llmReady()) add("Modèle de langage (Qwen3-0.6B-Q4_K_M.gguf)")
    }
    /** Optional resources not installed: the matching voice feature is disabled, the text conversation works. */
    fun optionalMissing(): List<String> = buildList {
        if (assets.sttFile() == null) add("Reconnaissance vocale (ggml-base-q5_1.bin)")
        if (!assets.ttsPresent()) add("Voix Pocket (pocket-fr-6l-alba.zip)")
    }

    fun startSession(): V1Session {
        session?.let { if (!it.closed) return it }
        check(previousReleased) { "Libération de la session précédente en cours : réessayez dans un instant" }
        check(missing().isEmpty()) { "Ressources manquantes : " + missing().joinToString() }
        val entry = checkNotNull(assets.llm.current())
        val model = A02LanguageModel(SealedNativeBridge.get(), VerifiedModel({
            val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            object : SourceDescriptor { override val fd = pfd.fd; override fun close() = pfd.close() }
        }, entry.sizeBytes, entry.sha256), ::memoryBudgetBytes)
        return V1Session(scope, model, assets.sttFile(), assets.ttsDir().takeIf { assets.ttsPresent() }, ::thermal, ::lowMemory)
            .also { session = it; it.start() }
    }

    fun closeSession() {
        val s = session ?: return
        previousReleased = false
        s.close { previousReleased = true }
    }
}
