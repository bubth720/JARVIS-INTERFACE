package fr.jarvis.v3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import fr.jarvis.v1.Phase
import fr.jarvis.v3.conv.ConvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * JARVIS V3 holographic cockpit. The engine is the qualified V1 chain (CORE → A02 LLM, whisper.cpp STT,
 * Pocket TTS) reused unchanged, driven by the CONVERSATION-01 session ([ConvApp], [fr.jarvis.v3.conv.ConvSession]).
 */
class CockpitActivity : ComponentActivity(), SceneActions {
    private val app get() = application as ConvApp
    private val ui get() = V3Runtime.ui

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> importUris(uris) }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) talk() else ui.message = "Micro refusé : la conversation reste possible au clavier."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val gov = V3Runtime.governor ?: PerformanceGovernor(applicationContext).also { V3Runtime.governor = it }
        if (ui.session == null) app.session?.takeIf { !it.closed }?.let { ui.session = it }
        setContent { HoloTheme { CockpitScene(app, gov, this) } }
    }

    override fun onResume() { super.onResume(); V3Runtime.governor?.refresh() }

    private fun talk() { val s = ui.session ?: return; if (s.busy) s.interrupt(); ui.pending = null; s.voiceTurn() }

    override fun requestMicThenTalk() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) talk()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    override fun importFiles() = picker.launch(arrayOf("*/*"))
    override fun searchImportFolder() {
        ui.message = "Recherche dans le dossier d'import…"
        app.scope.launch {
            ui.message = withContext(Dispatchers.IO) { app.assets.importFromFolder(app.importFolder()) }.joinToString("\n").ifBlank { "Aucun fichier dans ${app.importFolder()}" }
        }
    }
    override fun openGallery() { startActivity(Intent(this, StateGalleryActivity::class.java)) }

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()

    private fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ui.message = "Vérification des fichiers…"
        app.scope.launch {
            ui.message = withContext(Dispatchers.IO) {
                uris.map { u -> val name = displayName(u)
                    if (app.isDocument(name)) runCatching {
                        // A document the user picked goes to the local knowledge base (≤ 2 Mio, read once, never re-opened by URI).
                        app.knowledge.open()
                        val bytes = requireNotNull(contentResolver.openInputStream(u)) { "Document inaccessible" }.use { it.readNBytesCompat(2 * 1024 * 1024 + 1) }
                        if (bytes.size > 2 * 1024 * 1024) "$name : trop volumineux (2 Mio au plus)" else app.knowledge.import(name, bytes)
                    }.getOrElse { "$name : ${it.message}" }
                    else runCatching { app.assets.importAny(name) { requireNotNull(contentResolver.openInputStream(u)) { "Document inaccessible" } } }
                        .fold({ "$name : $it" }, { "$name : ${it.message}" }) }
            }.joinToString("\n")
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
        while (total < max) { val n = read(buf, 0, minOf(buf.size, max - total)); if (n < 0) break; out.write(buf, 0, n); total += n }
        return out.toByteArray()
    }

    /** Used by tests only to know whether the engine is idle. */
    val ready get() = ui.session?.phase == Phase.READY
}
