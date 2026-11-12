package fr.jarvis.v1

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.jarvis.m55d.Avatar25D
import fr.jarvis.m55d.KnowledgeSphere
import fr.jarvis.m55d.Palette
import fr.jarvis.m55d.RenderMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** JARVIS V1 screen: M55-D cockpit (sphere + avatar) driven by the REAL voice session. */
class MainActivity : ComponentActivity() {
    private val app get() = application as V1App
    private var importMessage by mutableStateOf("")
    private var startError by mutableStateOf("")
    private var session by mutableStateOf<V1Session?>(null)
    private var micGranted by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> importUris(uris) }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted; if (granted) session?.voiceTurn() else startError = "Micro refusé : la conversation reste possible au clavier."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        session = app.session?.takeIf { !it.closed }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen() } }
    }

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()

    private fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importMessage = "Vérification des fichiers…"
        app.scope.launch {
            val results = withContext(Dispatchers.IO) {
                uris.map { u -> val name = displayName(u)
                    runCatching { app.assets.importAny(name) { requireNotNull(contentResolver.openInputStream(u)) { "Document inaccessible" } } }
                        .fold({ "$name : $it" }, { "$name : ${it.message}" }) }
            }
            importMessage = results.joinToString("\n"); refresh++
        }
    }

    @Composable
    private fun Screen() {
        val s = session
        Column(Modifier.fillMaxSize().background(Palette.background).padding(12.dp)) {
            Row { Text("JARVIS V1", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("HORS LIGNE · local", color = Palette.of(fr.jarvis.m55d.CockpitState.LISTENING), fontSize = 12.sp) }
            if (s == null) Setup() else Conversation(s)
        }
    }

    @Composable
    private fun Setup() {
        refresh.let { }
        val missing = app.missing()
        Text("Ressources locales (vérifiées par empreinte SHA-256, jamais téléchargées par l'app) :", color = Palette.muted, fontSize = 13.sp)
        listOf("Modèle de langage" to app.assets.llmReady(), "Reconnaissance vocale" to (app.assets.sttFile() != null), "Voix Pocket" to app.assets.ttsPresent()).forEach { (n, ok) ->
            Text((if (ok) "✓ " else "✗ ") + n, color = if (ok) Palette.of(fr.jarvis.m55d.CockpitState.LISTENING) else Palette.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("import")) { Text("Importer des fichiers…") }
            OutlinedButton(onClick = {
                importMessage = "Recherche dans le dossier d'import…"
                app.scope.launch { importMessage = withContext(Dispatchers.IO) { app.assets.importFromFolder(app.importFolder()) }.joinToString("\n").ifBlank { "Aucun fichier dans ${app.importFolder()}" }; refresh++ }
            }) { Text("Rechercher") }
        }
        Text("Dossier d'import : ${app.importFolder()}", color = Palette.muted, fontSize = 11.sp)
        if (importMessage.isNotBlank()) Text(importMessage, color = Palette.text, fontSize = 12.sp)
        Button(onClick = { runCatching { app.startSession() }.onSuccess { session = it; startError = "" }.onFailure { startError = it.message ?: "Erreur" } },
            enabled = missing.isEmpty(), modifier = Modifier.testTag("start")) { Text("Démarrer JARVIS") }
        if (startError.isNotBlank()) Text(startError, color = Palette.of(fr.jarvis.m55d.CockpitState.ERROR))
    }

    @Composable
    private fun Conversation(s: V1Session) {
        val vm = s.cockpit
        Row(Modifier.fillMaxWidth().height(220.dp)) {
            KnowledgeSphere(vm.state, vm.level, vm.regulator.mode, vm.rotation, vm.stats, vm.regulator, vm.thermal, Modifier.weight(0.62f).fillMaxHeight())
            Avatar25D(vm.state, vm.level, vm.atlas, Modifier.weight(0.38f).fillMaxHeight())
        }
        Text(s.status, color = Palette.text, modifier = Modifier.testTag("status"))
        if (startError.isNotBlank()) Text(startError, color = Palette.of(fr.jarvis.m55d.CockpitState.ERROR), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (s.phase == Phase.LISTENING) s.interrupt()
                else if (micGranted) { if (s.busy) s.interrupt(); s.voiceTurn() } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = s.phase == Phase.READY || s.phase == Phase.LISTENING || s.phase == Phase.SPEAKING, modifier = Modifier.testTag("talk")) {
                Text(if (s.phase == Phase.LISTENING) "Terminer" else "🎤 Parler")
            }
            OutlinedButton(onClick = { s.interrupt() }, enabled = s.busy, modifier = Modifier.testTag("interrupt")) { Text("Interrompre") }
            OutlinedButton(onClick = { app.closeSession(); session = null }) { Text("Fermer") }
        }
        var typed by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, Modifier.weight(1f).testTag("input"), singleLine = true, label = { Text("Ou écrivez") })
            Button(onClick = { s.textTurn(typed); typed = "" }, enabled = typed.isNotBlank() && s.phase == Phase.READY) { Text("Envoyer") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RenderMode.entries.forEach { m -> FilterChip(selected = vm.regulator.mode == m, onClick = { vm.forceMode(m) }, label = { Text(m.name.lowercase(), fontSize = 11.sp) }) }
        }
        if (s.partial.isNotBlank() && s.busy) Text("JARVIS : ${s.partial}", color = Palette.text)
        LazyColumn(Modifier.weight(1f)) {
            items(s.exchanges.reversed()) { e ->
                Text("Vous${if (e.viaVoice) " 🎤" else ""} : ${e.user}", color = Palette.muted, fontSize = 13.sp)
                Text("JARVIS : ${e.answer.ifBlank { e.error ?: "(interrompu)" }}", color = Palette.text)
                Text("STT ${e.sttMs} ms · LLM ${e.llmMs} ms (1er texte ${e.firstTextMs} ms) · voix ${"%.1f".format(e.ttsAudioSeconds)} s${if (e.interrupted) " · interrompu" else ""}",
                    color = Palette.muted, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
