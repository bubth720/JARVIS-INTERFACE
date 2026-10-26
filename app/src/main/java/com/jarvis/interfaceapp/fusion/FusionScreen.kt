package com.jarvis.interfaceapp.fusion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class FusionViewModel:ViewModel() {
    val laboratory=DocumentToolLaboratory()
    override fun onCleared() {laboratory.close()}
}

@Composable
fun CapabilityCards(items:List<CapabilityPresentation>,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.forEach {item->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            Text(item.id.name,style=MaterialTheme.typography.titleMedium)
            Text(if(!item.connected) "NON_CONNECTÉ · ${item.status}" else item.status.name,
                Modifier.testTag("fusion-capability-${item.id}").semantics {liveRegion=LiveRegionMode.Polite})
            Text(item.detail,style=MaterialTheme.typography.bodySmall)
        }}}
    }
}

/** Embedded in the existing scrollable Studio. No artwork is drawn on this public synthetic page. */
@Composable
fun FusionScreen(model:FusionViewModel=viewModel()) {
    val lab=model.laboratory;val state by lab.state.collectAsStateWithLifecycle()
    var loseAck by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().testTag("fusion-screen"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("FUSION-02 / capacités",style=MaterialTheme.typography.titleLarge)
        Text("CORE réel NON CONNECTÉ · aucun transport authentifié ni capacité distante attestée.",Modifier.testTag("fusion-real-status"))
        Text("Athéna : portraits privés locaux conservés ; rig articulé et lip-sync non qualifiés. AVATAR ci-dessous désigne une capacité hôte distante, pas le portrait local.")
        OutlinedButton(onClick={if(state.enabled)lab.close() else lab.open()},modifier=Modifier.testTag("fusion-lab-toggle")) {Text(if(state.enabled) "Fermer le laboratoire" else "Ouvrir le laboratoire synthétique")}
        if(state.enabled) {
            Text("LAB UNIQUEMENT · aucune réponse IA, aucun SAF réel, aucun export réel",Modifier.testTag("fusion-lab-label"))
            Text("${state.phase} · ${state.detail}",Modifier.testTag("fusion-lab-status").semantics {liveRegion=LiveRegionMode.Polite})
            Button(enabled=state.phase==LabPhase.EMPTY,onClick={lab.chooseSyntheticSource()},modifier=Modifier.testTag("fusion-source")) {Text("Choisir la source synthétique (SAF simulé)")}
            state.citation?.let {CitationCard(it)}
            Button(enabled=state.phase==LabPhase.SOURCE,onClick={lab.preview()},modifier=Modifier.testTag("fusion-preview")) {Text("Aperçu CSV synthétique : 0.10 + 0.20")}
            state.total?.let {Text("LAB · somme exacte locale : $it",Modifier.testTag("fusion-total"))}
            Row {Checkbox(checked=loseAck,onCheckedChange={loseAck=it},modifier=Modifier.semantics {contentDescription="Simuler un ACK perdu"});Text("Simuler un ACK perdu")}
            Button(enabled=state.phase==LabPhase.PREVIEW,onClick={lab.prepare()},modifier=Modifier.testTag("fusion-prepare")) {Text("Examiner la confirmation exacte…")}
            OutlinedButton(onClick={lab.revoke()},modifier=Modifier.testTag("fusion-revoke")) {Text("Simuler la révocation du grant")}
            state.receiptId?.let {Text("Reçu LAB : $it · aucun effet externe",Modifier.testTag("fusion-receipt"))}
            Text("Les choix du laboratoire survivent à une rotation, pas à la mort du processus. Aucun effet en cours à reprendre ; une réouverture repart fermée, sans réexécution.")
        }
        CapabilityCards(CapabilityProjection.disconnected())
    }
    state.review?.let {review->AlertDialog(onDismissRequest=lab::cancelReview,
        title={Text("LAB · confirmer cette simulation unique ?")},
        text={Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Opération : lab.csv.sum/2 · génération ${review.generation}")
            Text("Destination neuve fictive : ${review.destination}")
            Text("Octets UTF-8 proposés :\n${review.payload}")
            Text("SHA-256 de la décision : ${review.digest}")
            Text("Validité 30 secondes. Aucun fichier ne sera écrit et aucun consentement CORE n’est accordé.")
        }},confirmButton={TextButton(onClick={lab.confirm(review,loseAck)},modifier=Modifier.testTag("fusion-confirm")){Text("Confirmer la simulation")}},
        dismissButton={TextButton(onClick=lab::cancelReview,modifier=Modifier.testTag("fusion-cancel")){Text("Annuler")}})}
}

@Composable
fun CitationCard(citation:LabCitation) {
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
        Text("LAB · citation synthétique, URI non ouvrable",style=MaterialTheme.typography.titleSmall)
        Text(citation.uri)
        Text("Version ${citation.version} · page ${citation.page} · offsets ${citation.start}–${citation.end}")
        Text("SHA-256 : ${citation.hash}")
    }}
}
