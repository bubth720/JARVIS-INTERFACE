package com.jarvis.interfaceapp.ui.memory

import android.provider.OpenableColumns
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.ui.identity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only explicit owner projections or opt-in lab RAM; never reads historical chats or private databases. */
@Composable
fun ConfirmedMemoryPanel(state:IdentitySettingsUiState,controller:IdentitySettingsController) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var selected by remember { mutableStateOf<Set<UiMemoryReference>>(emptySet()) }
    var preference by remember { mutableStateOf(false) }
    var pendingExportToken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.memories,state.memoryAllowed) {
        selected=if(!state.memoryAllowed) emptySet() else selected intersect state.memories.filter { !it.suspended && it.scope!=UiMemoryScope.SENSITIVE }.map { it.reference }.toSet()
    }
    val exportChooser=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val token=pendingExportToken
        val current=controller.state.value
        if(token!=null && token==current.exportToken) {
            val bytes=current.exportReady?.copyOf()
            if(uri==null || bytes==null) controller.exportResult(token,false,cancelled=true)
            else scope.launch {
                val verified=withContext(Dispatchers.IO) {
                    try {
                        require(uri.scheme=="content" && bytes.size<=65536)
                        fun checkGrant() { check(controller.state.value.exportToken==token && controller.state.value.memoryAllowed) }
                        checkGrant()
                        val size=context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                            if(!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
                        }
                        require(size==0L) { "DESTINATION_NOT_EMPTY_OR_UNKNOWN" }
                        context.contentResolver.openOutputStream(uri,"w")!!.use { out ->
                            var at=0
                            while(at<bytes.size) { checkGrant();val n=minOf(4096,bytes.size-at);out.write(bytes,at,n);at+=n }
                            out.flush()
                        }
                        checkGrant()
                        val copied=context.contentResolver.openInputStream(uri)!!.use { input ->
                            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                            while(true) { checkGrant();val n=input.read(buffer);if(n<0) break
                                require(out.size()+n<=bytes.size);out.write(buffer,0,n) }
                            out.toByteArray()
                        }
                        checkGrant();copied.contentEquals(bytes)
                    } catch(_:Exception) { false }
                }
                controller.exportResult(token,verified)
            }
        }
        pendingExportToken=null
    }
    LaunchedEffect(state.exportToken) {
        if(state.exportToken!=null && state.exportReady!=null && state.exportToken!=pendingExportToken) {
            pendingExportToken=state.exportToken
            exportChooser.launch("jarvis-memory-laboratory.json")
        }
    }
    val connected=state.connection!=IdentityConnection.UNAVAILABLE
    Text("Mémoire confirmée et consentements",style=MaterialTheme.typography.titleMedium)
    Text("Goûts fictifs du personnage et préférences réelles sont deux catégories distinctes. Les données sensibles ne sont pas exposées par ce port.")
    Row {
        Checkbox(state.memoryAllowed,controller::allowMemory,enabled=connected && !state.busy,
            modifier=Modifier.testTag("memory-projection").semantics { contentDescription="Autoriser la projection mémoire pour cette session" })
        Text("Autoriser cette projection mémoire pour la session")
    }
    if(!state.memoryAllowed) Text("Vue mémoire fermée · aucune donnée projetée")
    else {
        if(state.connection==IdentityConnection.LABORATORY) Text("LABORATOIRE : consentements simulés en RAM ; aucun enregistrement dans CORE.")
        OutlinedTextField(state.memoryText,controller::memoryText,label={Text("Contenu exact à proposer")},
            supportingText={Text("1500 caractères maximum ; proposition seule ≠ mémoire confirmée")},
            enabled=!state.busy && !state.requiresRefresh,modifier=Modifier.fillMaxWidth().testTag("memory-input"))
        TextButton(enabled=!state.busy,onClick={controller.memoryText("Préférence fictive de laboratoire : des exemples logiciels courts.")}) { Text("Insérer un exemple public fictif") }
        Row { Checkbox(preference,{preference=it},enabled=!state.busy,modifier=Modifier.semantics { contentDescription="Catégoriser comme préférence confirmable" });Text("Proposer comme préférence utilisateur confirmable") }
        Button(enabled=state.memoryText.isNotBlank() && !state.busy && !state.requiresRefresh,
            onClick={controller.proposeMemory(state.memoryText,if(preference) UiMemoryScope.PREFERENCE else UiMemoryScope.GENERAL)},
            modifier=Modifier.testTag("memory-propose")) { Text("Examiner une proposition mémoire") }
        Text("Mémoires confirmées projetées :")
        val rows=state.memories.filter { it.scope!=UiMemoryScope.SENSITIVE }
        if(rows.isEmpty()) Text("Aucune mémoire confirmée visible.")
        for(row in rows) {
            Surface(tonalElevation=2.dp,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text(row.content)
                    Text("${row.scope} · source ${row.source} · révision ${row.reference.revision} · confirmation ${row.confirmedAtMs}",style=MaterialTheme.typography.bodySmall)
                    Text("Digest ${row.reference.digest}",style=MaterialTheme.typography.bodySmall)
                    Row {
                        Checkbox(row.reference in selected,{include -> selected=if(include && selected.size<10) selected+row.reference else selected-row.reference },enabled=!row.suspended && !state.busy,modifier=Modifier.semantics { contentDescription="Exporter la mémoire ${row.reference.id.take(8)}" })
                        Text("Inclure dans l’export volontaire")
                    }
                    TextButton(enabled=!state.busy,onClick={controller.suspendMemory(row.reference,!row.suspended)}) { Text(if(row.suspended) "Reprendre cette mémoire" else "Suspendre cette mémoire") }
                    TextButton(enabled=!state.busy,onClick={controller.requestDelete(row.reference)}) { Text("Demander la suppression") }
                }
            }
        }
        OutlinedButton(enabled=selected.isNotEmpty() && !state.busy && !state.requiresRefresh,
            onClick={controller.previewExport(selected)},modifier=Modifier.testTag("memory-export-preview")) { Text("Prévisualiser l’export sélectionné") }
        Text("Aucun import ne crée de mémoire. L’application n’a pas d’API d’application automatique d’un fichier importé.",style=MaterialTheme.typography.bodySmall)
    }
    state.memoryCandidate?.let { p ->
        var confirmed by remember(p.id,p.digest,p.epoch) { mutableStateOf(false) }
        AlertDialog(onDismissRequest={if(!state.busy) controller.decideMemory(p,false)},modifier=Modifier.semantics { testTagsAsResourceId=true },
            title={Text("Consentement au contenu exact")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                Text(p.content);Text("${p.scope} · epoch ${p.epoch}",style=MaterialTheme.typography.bodySmall)
                Text("Digest ${p.digest}",style=MaterialTheme.typography.bodySmall)
                Row { Checkbox(confirmed,{confirmed=it},enabled=!state.busy,modifier=Modifier.testTag("memory-consent-check").semantics { contentDescription="Approuver explicitement le contenu mémoire affiché" });Text("J’approuve explicitement ce contenu affiché.") }
            }},confirmButton={TextButton(enabled=confirmed && !state.busy,onClick={controller.decideMemory(p,true)},modifier=Modifier.testTag("memory-consent-approve")) { Text("Confirmer") }},
            dismissButton={TextButton(enabled=!state.busy,onClick={controller.decideMemory(p,false)}) { Text("Rejeter") }})
    }
    state.deleteCandidate?.let { ref -> AlertDialog(onDismissRequest=controller::cancelDelete,
        title={Text("Confirmer la suppression")},text={Text("Supprimer seulement la mémoire ${ref.id.take(8)} révision ${ref.revision}. Les autres données ne sont pas concernées.")},
        confirmButton={TextButton(enabled=!state.busy,onClick={controller.confirmDelete(ref)},modifier=Modifier.testTag("memory-delete-confirm")) { Text("Supprimer") }},
        dismissButton={TextButton(onClick=controller::cancelDelete) { Text("Annuler") }}) }
    state.exportPreview?.let { preview ->
        var confirmed by remember(preview.id,preview.digest) { mutableStateOf(false) }
        AlertDialog(onDismissRequest=controller::cancelExport,title={Text("Exporter cette sélection exacte")},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                Text(preview.json,style=MaterialTheme.typography.bodySmall);Text("Digest ${preview.digest}",style=MaterialTheme.typography.bodySmall)
                Row { Checkbox(confirmed,{confirmed=it},enabled=!state.busy,modifier=Modifier.semantics { contentDescription="Autoriser cet export vers une destination vide" });Text("J’autorise cet export vers une nouvelle destination vide.") }
            }},confirmButton={TextButton(enabled=confirmed && !state.busy,onClick={controller.approveExport(preview)},modifier=Modifier.testTag("memory-export-approve")) { Text("Choisir une destination") }},
            dismissButton={TextButton(onClick=controller::cancelExport) { Text("Annuler") }})
    }
}
