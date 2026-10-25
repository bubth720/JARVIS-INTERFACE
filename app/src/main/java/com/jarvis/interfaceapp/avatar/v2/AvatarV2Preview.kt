package com.jarvis.interfaceapp.avatar.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.avatar.AvatarDemo
import com.jarvis.interfaceapp.avatar.assets.readBounded
import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Private SAF import in RAM. Neither reference images nor URI grants are persisted. */
@Composable
fun AvatarV2Preview(repository: AvatarPackRepository, packs: MutableMap<String, LoadedPremiumPack>,
    onApply: (LoadedPremiumPack?) -> Unit, onClose: () -> Unit,primaryArtworkLabel:String?=null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var gallery by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<LoadedPremiumPack?>(null) }
    var issue by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var compareV1 by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(repository.selectedId) }
    var revision by remember { mutableIntStateOf(0) }
    suspend fun load(zip: PremiumZipSource? = null) {
        if (busy) return
        busy = true; issue = null; confirmed = false
        try {
            val loaded = withContext(Dispatchers.IO) {
                val bytes = zip?.manifest() ?: context.assets.open("avatar-v2-demo/manifest.json").use { it.readBounded(ResourceLimit.MANIFEST_BYTES) }
                val manifest = PremiumManifestCodec.decode(bytes)
                require(repository.canRegister(manifest)) { "GLOBAL_PIXEL_BUDGET_OR_ID_CONFLICT" }
                val validated = if (zip != null) PremiumPackLoader.validate(zip) else PremiumPackLoader.validate(bytes) { file ->
                    context.assets.open("avatar-demo/$file").use { it.readBounded(ResourceLimit.FILE_BYTES) }
                }
                val decoded = packs[manifest.id] ?: PremiumPackLoader.decode(validated)
                validated to decoded
            }
            check(repository.register(loaded.first))
            packs[loaded.second.manifest.id] = loaded.second
            candidate = loaded.second; compareV1 = false; revision++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { issue = "PACK_REFUSED · ZIP, manifeste, PNG, budget ou intégrité invalide. Pack précédent conservé." }
        finally { busy = false }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val zip = withContext(Dispatchers.IO) {
                    require(uri.scheme == "content")
                    PremiumZipSource(requireNotNull(context.contentResolver.openInputStream(uri)))
                }
                busy = false; load(zip)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { issue = "PACK_REFUSED · Archive illisible. Pack précédent conservé." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(issue) { if (issue != null) scroll.scrollTo(0) }
    if (gallery && candidate != null) {
        com.jarvis.interfaceapp.avatar.review.AthenaGalleryScreen(candidate!!) { gallery = false }
        return
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).padding(16.dp).verticalScroll(scroll)
        .testTag("avatar-v2-preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Athéna V2 · prévisualisation locale", style = MaterialTheme.typography.titleMedium)
        Text("PARTIAL · ART_ASSETS_MISSING", Modifier.testTag("avatar-v2-art-status"))
        Text("Le pack premium officiel n’est pas fourni. Les formes et PNG de démonstration ne représentent pas Athéna finale.")
        Text("Pack du panneau : ${selection ?: primaryArtworkLabel ?: "V1 vectoriel"} · sélection temporaire, sans sauvegarde")
        if (busy) Text("Validation et décodage bornés…")
        issue?.let { Text(it, Modifier.testTag("avatar-v2-issue").semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(enabled = !busy, onClick = { compareV1 = true }, modifier = Modifier.weight(1f).testTag("avatar-v2-compare-v1")) { Text("Comparer V1") }
            OutlinedButton(enabled = !busy && candidate != null, onClick = { compareV1 = false }, modifier = Modifier.weight(1f).testTag("avatar-v2-compare-v2")) { Text("Voir V2") }
        }
        if (compareV1) {
            Text("V1 · secours vectoriel existant")
            AvatarDemo(artwork = remember { com.jarvis.interfaceapp.avatar.DemoAvatarArtwork() }, showAssetButton = false)
        } else candidate?.let { pack -> key(pack, revision) {
            val renderer = remember { PremiumAvatarArtwork(pack) }
            var current by remember { mutableStateOf(renderer.current) }
            var simulated by remember { mutableStateOf(false) }
            fun dispatch(event: ExpressionEvent) {
                val result = renderer.dispatch(event)
                current = result.key
                issue = result.issue?.let { if (it == "TRANSITION_IN_PROGRESS") "Transition en cours · réessayer après le fondu." else "ART_ASSET_UNAVAILABLE · demande refusée, état conservé." }
            }
            Text("${pack.manifest.id} · ${pack.manifest.artGrade}")
            OutlinedButton(onClick = { gallery = true }, modifier = Modifier.testTag("athena-open-gallery")) { Text("Galerie de revue du pack") }
            Text("${pack.manifest.resources.size} textures · ${pack.decodedBytes} octets RGBA théoriques")
            AvatarDemo(artwork = renderer, showAssetButton = false)
            Text("${current.state} · ${current.expression} · ${current.perspective}", Modifier.testTag("avatar-v2-current"))
            Text(if (pack.manifest.resolve(current)?.mode == RenderMode.IMAGE_REPLACEMENT)
                "Remplacement d’image pleine pose · pas un rig articulé" else "Calques indépendants · ressources disponibles uniquement")
            Text("États (une demande sans asset est refusée)")
            for (row in PremiumState.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (state in row) OutlinedButton(onClick = { dispatch(ExpressionEvent.State(state)) }, modifier = Modifier.weight(1f).testTag("avatar-v2-state-${state.name}")) { Text(state.name, style = MaterialTheme.typography.labelSmall) }
            }
            Text("Expressions disponibles")
            for (expression in pack.manifest.frames.map { it.key.expression }.distinct()) TextButton(onClick = {
                dispatch(ExpressionEvent.Expression(expression))
            }) { Text(expression.name) }
            Text("Perspectives (aucune vue artificiellement déduite d’une autre)")
            for (row in PremiumPerspective.entries.chunked(2)) Row {
                for (perspective in row) TextButton(onClick = { dispatch(ExpressionEvent.Perspective(perspective)) }, modifier = Modifier.weight(1f)) { Text(perspective.name, style = MaterialTheme.typography.labelSmall) }
            }
            Text("Correspondances du pack :")
            for (frame in pack.manifest.frames) TextButton(onClick = { dispatch(ExpressionEvent.Select(frame.key)) }) {
                Text("${frame.key.state} / ${frame.key.expression} / ${frame.key.perspective}", style = MaterialTheme.typography.labelSmall)
            }
            val mouthAvailable = pack.manifest.resolve(current)?.layers?.any { id ->
                pack.manifest.layers.any { it.id == id && it.channel == LayerChannel.MOUTH_OPEN }
            } == true
            OutlinedButton(enabled = mouthAvailable, onClick = {
                if (renderer.simulateMouth(!simulated)) simulated = !simulated
            }, modifier = Modifier.testTag("avatar-v2-mouth")) { Text("Bouche simulée · sans synchronisation phonétique") }
            OutlinedButton(onClick = { dispatch(ExpressionEvent.Reset); renderer.simulateMouth(false); simulated = false }, modifier = Modifier.testTag("avatar-v2-reset")) { Text("Revenir au neutre") }
        } }
        HorizontalDivider()
        OutlinedButton(enabled = false, onClick = {}) { Text("Athéna premium · assets manquants") }
        OutlinedButton(enabled = !busy, onClick = { scope.launch { load() } }) { Text("Charger la démo technique V2") }
        OutlinedButton(enabled = !busy && primaryArtworkLabel==null, onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed")) }, modifier = Modifier.testTag("avatar-v2-import")) { Text("Importer volontairement un pack V2") }
        Text("Import privé, en mémoire seulement. Aucun pack ni image n’est publié par l’application.")
        Row {
            Checkbox(confirmed, { confirmed = it }, enabled = candidate != null && !busy, modifier = Modifier.testTag("avatar-v2-confirm"))
            Text("J’ai vérifié visuellement ce pack pour cette session.")
        }
        Button(enabled = confirmed && candidate != null && !busy, onClick = {
            val pack = candidate!!
            if(primaryArtworkLabel!=null && pack.manifest.artGrade!=ArtGrade.PREMIUM_CANDIDATE) {onClose()}
            else if (repository.select(pack.manifest.id, confirmed)) { selection = repository.selectedId; onApply(pack); onClose() }
        }, modifier = Modifier.fillMaxWidth().testTag("avatar-v2-apply")) { Text(if(primaryArtworkLabel!=null && candidate?.manifest?.artGrade!=ArtGrade.PREMIUM_CANDIDATE) "Fermer la comparaison et conserver Athéna" else "Utiliser le pack prévisualisé dans le panneau") }
        OutlinedButton(enabled = !busy, onClick = {
            repository.rollback(); selection = repository.selectedId; onApply(selection?.let { packs[it] }); onClose()
        }, modifier = Modifier.testTag("avatar-v2-rollback")) { Text("Revenir au pack précédent") }
        OutlinedButton(enabled = !busy, onClick = {
            repository.select(null, true); selection = null; onApply(null); onClose()
        }) { Text("Restaurer le rendu V1") }
        TextButton(onClick = onClose) { Text("Fermer sans appliquer") }
    }
}
