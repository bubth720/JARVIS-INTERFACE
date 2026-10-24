package com.jarvis.interfaceapp.ui.identity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.interfaceapp.ui.memory.ConfirmedMemoryPanel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentitySettingsScreen(controller:IdentitySettingsController,onClose:()->Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)
        .semantics { testTagsAsResourceId=true }) {
        val headerLimit=(maxHeight-160.dp).coerceAtLeast(48.dp)
        Column(Modifier.fillMaxSize()) {
        // Header scrolls independently when large fonts would starve the editor.
        Column(Modifier.fillMaxWidth().heightIn(max=headerLimit).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TextButton(onClick=onClose,modifier=Modifier.testTag("identity-close")) { Text("Retour") }
            Text("Identité et personnalité",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        }
        Text(state.message,Modifier.testTag("identity-status").semantics { liveRegion=LiveRegionMode.Polite },style=MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("CORE reste propriétaire des profils et mémoires. INTERFACE ne les active pas et ne les stocke pas.")
            Text(if(state.connection==IdentityConnection.LABORATORY) "LABORATOIRE VOLONTAIRE · données en RAM, non connectées à CORE"
                else if(state.connection==IdentityConnection.UNAVAILABLE) "NON CONNECTÉ · aucune identité chargée" else "Propriétaire autorisé")
            OutlinedButton(enabled=!state.busy,onClick={controller.laboratory(state.connection!=IdentityConnection.LABORATORY)},modifier=Modifier.fillMaxWidth().testTag("identity-lab-toggle")) {
                Text(if(state.connection==IdentityConnection.LABORATORY) "Quitter le laboratoire et effacer sa RAM" else "Ouvrir volontairement le laboratoire local")
            }
            OutlinedButton(enabled=!state.busy,onClick=controller::refresh,modifier=Modifier.testTag("identity-refresh")) { Text("Recharger depuis le propriétaire") }
            if(state.busy) Text("Opération en cours…")
            if(state.snapshot.profiles.isEmpty()) Text("Éditeur indisponible : aucun profil factice n’est chargé par défaut.")
            else {
                Text("Profil à examiner")
                for(profile in state.snapshot.profiles) FilterChip(selected=state.selectedId==profile.reference.id,
                    onClick={controller.selectProfile(profile.reference.id)},enabled=!state.busy,
                    label={Text("${profile.draft.name} · révision ${profile.reference.revision}")},
                    modifier=Modifier.testTag("identity-profile-${profile.draft.preset.name}"))
                state.current?.let { Text("Source : ${state.connection} · révision ${it.reference.revision} · epoch ${state.snapshot.epoch}",style=MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected=state.context==null,onClick={controller.context(null)},label={Text("Défauts")},enabled=!state.busy)
                    for(context in PersonaContext.entries) FilterChip(selected=state.context==context,onClick={controller.context(context)},
                        label={Text(context.name,style=MaterialTheme.typography.labelSmall)},enabled=!state.busy,
                        modifier=Modifier.testTag("identity-context-${context.name}"))
                }
                state.draft?.let { draft ->
                    val context=state.context
                    val inherited=context!=null && context !in draft.overrides
                    if(context!=null) Row {
                        Checkbox(!inherited,{controller.overrideContext(it)},enabled=state.editable,modifier=Modifier.testTag("identity-context-override").semantics { contentDescription="Personnaliser ${context.name}" })
                        Text(if(inherited) "Valeurs héritées des défauts" else "Override explicite pour ${context.name}")
                    }
                    val p=if(context==null) draft.defaults else draft.parameters(context)
                    val editable=state.editable && !inherited
                    ParameterSlider("Franchise",p.frankness,editable,"identity-frankness") { value -> controller.parameters { it.copy(frankness=value) } }
                    ParameterSlider("Humour",p.humour,editable,"identity-humour") { value -> controller.parameters { it.copy(humour=value) } }
                    ParameterSlider("Initiative",p.initiative,editable,"identity-initiative") { value -> controller.parameters { it.copy(initiative=value) } }
                    Row { Checkbox(p.tutoiement,{v->controller.parameters { it.copy(tutoiement=v) }},enabled=editable,modifier=Modifier.testTag("identity-tutoiement").semantics { contentDescription="Tutoiement" });Text("Tutoiement") }
                    Row {
                        for(style in PersonaStyle.entries) FilterChip(selected=p.style==style,onClick={controller.parameters { it.copy(style=style) }},
                            label={Text(style.name)},enabled=editable)
                    }
                    Text("Centres d’intérêt du personnage · SIMULÉS, distincts des préférences confirmées de l’utilisateur")
                    for(interest in SimulatedInterest.entries) Row {
                        Checkbox(interest in draft.interests,{controller.interest(interest,it)},enabled=state.editable,
                            modifier=Modifier.testTag("identity-interest-${interest.name}").semantics { contentDescription="Goût simulé : ${interest.name}" });Text(interest.name)
                    }
                    Text("Aperçu avant/après · modèle de formulation / pas une réponse LLM",style=MaterialTheme.typography.titleSmall)
                    for(c in PersonaContext.entries) {
                        val before=state.current?.draft?.let { ReadOnlyPersonalityPreview.create(it,c) }
                        val after=ReadOnlyPersonalityPreview.create(draft,c)
                        Text("${c.name} · ${if(after.inherited) "hérité" else "override"}")
                        Text("Avant : ${before?.after.orEmpty()}",style=MaterialTheme.typography.bodySmall)
                        Text("Après : ${after.after}",style=MaterialTheme.typography.bodySmall)
                        Text("Paramètres effectifs : franchise ${after.effective.frankness}, humour ${after.effective.humour}, initiative ${after.effective.initiative}",style=MaterialTheme.typography.bodySmall)
                    }
                    Button(enabled=state.editable,onClick={controller.propose()},modifier=Modifier.fillMaxWidth().testTag("identity-propose")) { Text("Proposer et comparer les changements") }
                    OutlinedButton(enabled=state.editable,onClick={controller.propose(1)},modifier=Modifier.testTag("identity-restore")) { Text("Réinitialiser par restauration de la révision 1") }
                    Text("Restauration = nouvelle révision après approbation ; aucun historique ni archive B n’est supprimé.",style=MaterialTheme.typography.bodySmall)
                    for(revision in state.history) TextButton(enabled=state.editable,onClick={controller.propose(revision.reference.revision)}) {
                        Text("Examiner la restauration de ${revision.reference.revision}")
                    }
                }
            }
            HorizontalDivider()
            ConfirmedMemoryPanel(state,controller)
        }
        }
    }
    }
    state.proposal?.let { proposal -> ExplicitApprovalDialog(proposal,state.busy,
        approve={controller.approveProposal(proposal.id,proposal.approvalDigest,proposal.epoch)},cancel=controller::rejectProposal) }
}
@Composable
private fun ParameterSlider(label:String,value:Int,enabled:Boolean,tag:String,onChange:(Int)->Unit) {
    Column {
        Text("$label : $value / 100")
        Slider(value.toFloat(),{onChange(it.roundToInt())},enabled=enabled,valueRange=0f..100f,steps=99,
            modifier=Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription=label })
    }
}
@Composable
fun ExplicitApprovalDialog(proposal:ProfileProposal,busy:Boolean,approve:()->Unit,cancel:()->Unit) {
    var confirmed by remember(proposal.id,proposal.approvalDigest,proposal.epoch) { mutableStateOf(false) }
    AlertDialog(onDismissRequest={if(!busy) cancel()},modifier=Modifier.semantics { testTagsAsResourceId=true },
        title={Text("Confirmer cet aperçu exact")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text("Proposition ${proposal.id.take(8)} · epoch ${proposal.epoch}")
            Text("Digest : ${proposal.approvalDigest}",style=MaterialTheme.typography.bodySmall)
            if(proposal.restoreRevision!=null) Text("Restaurer la révision ${proposal.restoreRevision} en créant une nouvelle révision.")
            if(proposal.differences.isEmpty()) Text("Aucune différence dans cet aperçu.")
            for(d in proposal.differences) Text("${differenceLabel(d.field)}\n${differenceValue(d.before)}\n→ ${differenceValue(d.after)}",style=MaterialTheme.typography.bodySmall)
            for(p in proposal.previews) Text("${p.context} : ${p.after}\n${p.label}\nEffectif : ${differenceValue(p.effective.toString())}",style=MaterialTheme.typography.bodySmall)
            Row { Checkbox(confirmed,{confirmed=it},enabled=!busy,modifier=Modifier.testTag("identity-approve-check").semantics { contentDescription="Approuver cet aperçu exact" });Text("J’approuve cet aperçu et sa révision de base.") }
        }},
        confirmButton={TextButton(onClick=approve,enabled=confirmed && !busy,modifier=Modifier.testTag("identity-approve")) { Text("Approuver") }},
        dismissButton={TextButton(onClick=cancel,enabled=!busy,modifier=Modifier.testTag("identity-reject")) { Text("Annuler / rejeter") }})
}

/** Presentation only: the owner/lab digest remains bound to the original exact values. */
private fun differenceLabel(field:String):String=when(field) {
    "defaults"->"Valeurs de base configurées"
    "name"->"Nom du profil"
    "preset"->"Profil de référence"
    "simulated_interests"->"Centres d’intérêt simulés"
    "language_rules"->"Règles de formulation"
    else->if(field.startsWith("override:")) "Personnalisation ${field.substringAfter(':')}" else "Contexte $field · valeurs configurées"
}
private fun differenceValue(value:String):String {
    if(value=="INHERIT_DEFAULTS")return "Hériter des valeurs de base"
    val p=Regex("PersonaParameters\\(frankness=(\\d+), humour=(\\d+), initiative=(\\d+), tutoiement=(true|false), style=(ELEGANT|PLAIN)\\)").matchEntire(value) ?: return value
    val v=p.groupValues
    return "Franchise ${v[1]}/100 · humour ${v[2]}/100 · initiative ${v[3]}/100 · ${if(v[4]=="true") "tutoiement" else "vouvoiement"} · style ${if(v[5]=="ELEGANT") "élégant" else "simple"}"
}
