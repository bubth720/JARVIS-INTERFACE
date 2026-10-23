package com.jarvis.interfaceapp.avatar.assets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.avatar.AvatarDemo
import com.jarvis.interfaceapp.avatar.drawStaticAvatarFallback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit local preview. Imported assets remain in RAM and never replace dashboard settings. */
@Composable
fun AvatarPreviewPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<LayeredAvatarAsset?>(null) }
    var issue by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pivots by remember { mutableStateOf(false) }
    var variants by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    suspend fun load(source: () -> AvatarAssetSource) {
        busy = true; issue = null; result = null
        try {
            result = withContext(Dispatchers.IO) { AvatarAssetLoader().load(source()) }
            variants = result!!.manifest.layers.groupBy { it.group }.mapValues { it.value.first().variant }
        } catch (_: Exception) { issue = "Profil invalide ou ressource indisponible · secours vectoriel statique" }
        finally { busy = false }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            load { require(uri.scheme == "content"); ZipAvatarAssetSource(requireNotNull(context.contentResolver.openInputStream(uri))) }
        }
    }
    LaunchedEffect(Unit) { load { AvatarAssetSource { context.assets.open("avatar-demo/$it") } } }
    Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ressources 2.5D locales", style = MaterialTheme.typography.titleMedium)
        Text("Démonstration synthétique. Import ZIP : manifest.json et PNG à la racine. Aucun enregistrement du profil.")
        if (busy) Text("Chargement borné…")
        issue?.let { Text(it) }
        val loaded = result
        if (loaded != null) {
            val artwork = remember(loaded, variants, pivots) {
                LayeredAvatarArtwork(loaded).also { it.variants = variants; it.showPivots = pivots }
            }
            Text("${loaded.manifest.id} · ${loaded.manifest.layers.size} calques")
            // Existing foreground/visibility clock, controller, controls and release lifecycle.
            AvatarDemo(Modifier.fillMaxWidth(), artwork = artwork, showAssetButton = false)
            Row { Checkbox(pivots, { pivots = it }); Text("Afficher les pivots") }
            for ((group, layers) in loaded.manifest.layers.groupBy { it.group }) {
                Row {
                    for (variant in layers.map { it.variant }.distinct()) TextButton(onClick = {
                        variants = variants + (group to variant)
                    }) { Text("$group : $variant") }
                }
            }
        } else if (!busy) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val factor = minOf(size.width / 240f, size.height / 320f)
                withTransform({ scale(factor, factor, Offset.Zero) }) { drawStaticAvatarFallback() }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) { Text("Importer un ZIP local") }
        TextButton(onClick = onClose) { Text("Fermer") }
    }
}
