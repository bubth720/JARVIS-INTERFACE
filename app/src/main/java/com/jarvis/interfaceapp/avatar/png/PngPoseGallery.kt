package com.jarvis.interfaceapp.avatar.png

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.avatar.AvatarArtwork
import com.jarvis.interfaceapp.avatar.DemoAvatarArtwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A private offline gallery. Import is user-initiated; activation requires confirmation. */
@Composable
fun PngPoseGallery(onApply: (AvatarArtwork) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PngPoseStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var pack by remember { mutableStateOf<PngPosePack?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PngPoseArtwork?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val active = withContext(Dispatchers.IO) { store.current() }
        if (active != null) { pack = active.first; chosen = active.second }
    }
    LaunchedEffect(pack, chosen) {
        preview = null
        val p = pack
        val id = chosen
        if (p != null && id != null) {
            preview = withContext(Dispatchers.IO) {
                runCatching { store.artwork(p, id) }.getOrNull()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true; error = null; confirm = false
            try {
                val imported = withContext(Dispatchers.IO) {
                    require(uri.scheme == "content") { "URI_REJECTED" }
                    val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                    stream.use { store.importArchive(it) }
                }
                pack = imported; chosen = imported.manifest.defaultId
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Pack refusé : archive, PNG, catalogue ou empreintes invalides. Avatar précédent conservé." }
            finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(16.dp)
            .verticalScroll(rememberScrollState()).testTag("png-gallery"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("JARVIS AVATAR — 36 portraits PNG", style = MaterialTheme.typography.titleMedium)
        Text("Bibliothèque locale : poses fixes, pas de vidéo ni synchronisation labiale.")
        Text("Le pack privé n'est pas intégré à l'APK et n'est jamais envoyé sur GitHub ou à CORE.")
        OutlinedButton(
            enabled = !busy,
            onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().testTag("png-import")
        ) { Text("Importer le pack PNG depuis Fichiers") }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("png-error").semantics { liveRegion = LiveRegionMode.Polite }) }
        val selected = pack
        if (selected == null) Text("Aucun pack PNG sélectionné. Le rendu existant reste disponible.")
        else {
            Text("Pack : " + selected.manifest.id + " · " + selected.manifest.frames.size + " expressions")
            val shown = preview
            if (shown != null) {
                Image(
                    bitmap = shown.image,
                    contentDescription = "Aperçu de l'avatar, portrait " + (chosen ?: ""),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).testTag("png-image"),
                    contentScale = ContentScale.Fit
                )
            } else Text("Aperçu en cours ou indisponible.")
            Text("Choisir une pose — l'aperçu ne modifie pas l'avatar actif.")
            for (entry in selected.manifest.frames) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { chosen = entry.id; confirm = false },
                    modifier = Modifier.fillMaxWidth().testTag("png-pose-" + entry.id)
                ) {
                    Text(entry.id + " · " + entry.label + " [" + entry.state + "]",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(confirm, { confirm = it }, enabled = !busy && preview != null,
                    modifier = Modifier.testTag("png-confirm"))
                Text("J'ai vérifié cette image et souhaite l'utiliser localement.")
            }
            Button(
                enabled = !busy && confirm && preview != null && chosen != null,
                onClick = {
                    try {
                        store.activate(selected, chosen!!)
                        onApply(requireNotNull(preview))
                        onClose()
                    } catch (_: Exception) { error = "Activation locale impossible. Avatar précédent conservé." }
                },
                modifier = Modifier.fillMaxWidth().testTag("png-apply")
            ) { Text("Activer cette pose dans l'avatar") }
        }
        OutlinedButton(
            onClick = {
                try { store.disable(); onApply(DemoAvatarArtwork()); onClose() }
                catch (_: Exception) { error = "Le retour au rendu initial a échoué." }
            },
            modifier = Modifier.fillMaxWidth().testTag("png-disable")
        ) { Text("Revenir au rendu vectoriel V1") }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer sans changer") }
    }
}