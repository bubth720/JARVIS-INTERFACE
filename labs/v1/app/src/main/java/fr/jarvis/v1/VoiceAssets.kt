package fr.jarvis.v1

import com.jarvis.voice.pocket.ModelPack
import com.stark.jarvislocal.v1b.ModelStore
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * The three local resources of JARVIS V1 (labs/v1/resources.json). Nothing is bundled or downloaded:
 * the owner imports them once; every byte is verified before use (SHA-256, or the pinned per-file
 * Pocket model lock). Imports are atomic and never overwrite a verified file with a bad one.
 */
class VoiceAssets(private val filesDir: File, availableBytes: () -> Long) {
    companion object {
        const val LLM_SHA256 = "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a"
        const val STT_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
        const val STT_BYTES = 59_707_625L
    }
    val llm = ModelStore(File(filesDir, "models"), availableBytes)
    private val sttDir = File(filesDir, "stt")
    private val ttsRoot = File(filesDir, "tts")

    fun llmReady() = llm.current()?.sha256 == LLM_SHA256
    fun sttFile(): File? = File(sttDir, "$STT_SHA256.bin").takeIf { it.isFile && it.length() == STT_BYTES }
    fun ttsDir(): File = File(ttsRoot, "fr")
    /** Full per-file verification of the Pocket pack (pinned lock); throws a readable message if invalid. */
    fun verifyTts(): File { ModelPack.verify(ttsDir()); return ttsDir() }
    fun ttsPresent() = File(ttsDir(), "bundle.json").isFile

    fun importLlm(input: InputStream) = llm.import(input, LLM_SHA256)
    fun importStt(input: InputStream): File {
        sttDir.mkdirs()
        val target = File(sttDir, "$STT_SHA256.bin"); if (target.isFile && target.length() == STT_BYTES) return target
        val partial = File(sttDir, "stt.part"); partial.delete()
        val md = MessageDigest.getInstance("SHA-256"); var total = 0L
        try {
            partial.outputStream().buffered(1 shl 20).use { out ->
                val buf = ByteArray(1 shl 20)
                while (true) { val n = input.read(buf); if (n < 0) break; total += n; require(total <= STT_BYTES) { "Fichier STT trop grand" }; md.update(buf, 0, n); out.write(buf, 0, n) }
            }
            val sha = md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(total == STT_BYTES && sha == STT_SHA256) { "Modèle de reconnaissance vocale refusé (empreinte différente)" }
            check(partial.renameTo(target)) { "Installation atomique impossible" }
            return target
        } finally { partial.delete() }
    }
    fun importTts(zip: InputStream): File = ModelPack.install(zip, ttsRoot)

    /** Imports by content type: GGUF -> LLM, Whisper ggml -> STT, ZIP -> Pocket pack (each verified). */
    fun importAny(name: String, open: () -> InputStream): String = when {
        name.endsWith(".gguf", true) -> { open().use { importLlm(it) }; "Modèle de langage vérifié et installé" }
        name.endsWith(".bin", true) -> { open().use { importStt(it) }; "Reconnaissance vocale vérifiée et installée" }
        name.endsWith(".zip", true) -> { open().use { importTts(it) }; "Voix Pocket vérifiée et installée" }
        else -> error("Type de fichier non reconnu : $name")
    }

    /** Files the owner pushed to the app-specific folder (no storage permission needed) are imported once. */
    fun importFromFolder(dir: File?): List<String> = dir?.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map { f ->
        runCatching { importAny(f.name) { f.inputStream() } }.fold({ "${f.name} : $it" }, { "${f.name} : ${it.message}" })
    }
}
