package com.jarvis.interfaceapp.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.ui.identity.*

private val personaSaver=Saver<LocalPersona,String>(
    save={WorkspaceCodec.encode(Workspace(persona=it)).toString(Charsets.UTF_8)},
    restore={runCatching {WorkspaceCodec.decode(it.toByteArray()).persona}.getOrNull()})

@Composable
internal fun LocalPersonaEditor(saved:LocalPersona,save:(LocalPersona)->Unit) {
    var draft by rememberSaveable(saved,stateSaver=personaSaver) {mutableStateOf(saved)}
    var context by rememberSaveable {mutableStateOf("DEFAULT")}
    var confirm by remember(saved) {mutableStateOf(false)}
    val p=draft.profile
    val key=PersonaContext.entries.find {it.name==context}
    val values=if(key==null)p.defaults else p.parameters(key)
    fun update(next:PersonaParameters) {
        draft=draft.copy(profile=if(key==null)p.copy(defaults=next) else p.copy(overrides=p.overrides+(key to next)))
    }
    Text("Athéna / formulation locale",style=MaterialTheme.typography.titleLarge)
    Text("Préférences de présentation autorisées sur cet appareil. Aucun profil propriétaire CORE modifié, aucune personnalité distante activée. Prévisualisations déterministes, sans IA.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("DEFAULT","DAILY","WORK","URGENT").forEach { value->FilterChip(context==value,{context=value},label={Text(when(value){"DEFAULT"->"Défauts";"DAILY"->"Quotidien";"WORK"->"Travail";else->"Urgence"})}) }
    }
    if(key!=null)Toggle("Surcharge explicite de ce contexte",key in p.overrides){enabled->
        draft=draft.copy(profile=p.copy(overrides=if(enabled)p.overrides+(key to p.defaults) else p.overrides-key))
    }
    Toggle("Tutoiement",values.tutoiement){update(values.copy(tutoiement=it))}
    IntPreferenceSlider("Franchise",values.frankness,0..100){update(values.copy(frankness=it))}
    IntPreferenceSlider("Humour",values.humour,0..100){update(values.copy(humour=it))}
    IntPreferenceSlider("Initiative",values.initiative,0..100){update(values.copy(initiative=it))}
    IntPreferenceSlider("Niveau de détail (présentation locale)",draft.detail,0..100){draft=draft.copy(detail=it)}
    Toggle("Ton élégant",values.style==PersonaStyle.ELEGANT){update(values.copy(style=if(it)PersonaStyle.ELEGANT else PersonaStyle.PLAIN))}
    Text("Goûts simulés du personnage · jamais des préférences humaines supposées")
    SimulatedInterest.entries.forEach { interest->Toggle(interest.name,interest in p.interests){enabled->draft=draft.copy(profile=p.copy(interests=if(enabled)p.interests+interest else p.interests-interest))} }
    Toggle("Particularité de langage : Bonjour → Salut",p.rules.any {it.source==LexicalToken.BONJOUR}){enabled->
        draft=draft.copy(profile=p.copy(rules=if(enabled)listOf(LexicalRule(LexicalToken.BONJOUR,LexicalToken.SALUT)) else emptyList()))
    }
    val ctx=key?:PersonaContext.DAILY
    val before=ReadOnlyPersonalityPreview.create(saved.profile,ctx);val after=ReadOnlyPersonalityPreview.create(p,ctx)
    Text("Aperçu avant / après — gabarit sans LLM",style=MaterialTheme.typography.titleMedium)
    Text("Avant : ${preview(saved,ctx)}\nAprès : ${preview(draft,ctx)}",Modifier.testTag("local-persona-preview"))
    Text("Effectifs : franchise=${after.effective.frankness}, humour=${after.effective.humour}, initiative=${after.effective.initiative}. Travail plafonne l’humour à30 ; urgence le neutralise et limite l’initiative à40.")
    Button(enabled=draft!=saved,onClick={confirm=true},modifier=Modifier.testTag("local-persona-propose")){Text("Comparer et confirmer…")}
    OutlinedButton(onClick={draft=saved},modifier=Modifier.testTag("local-persona-cancel")){Text("Annuler les modifications")}
    OutlinedButton(onClick={draft=LocalPersona()},modifier=Modifier.testTag("local-persona-restore")){Text("Préparer les valeurs par défaut")}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Confirmer les préférences locales ?")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Enregistrement local versionné uniquement. Aucun envoi à CORE.")
        ProfileDiffUi.compare(saved.profile,p).forEach {Text("${localDifferenceLabel(it.field)}\n${localDifferenceValue(it.before)} → ${localDifferenceValue(it.after)}")}
        if(saved.detail!=draft.detail)Text("Détail : ${saved.detail} → ${draft.detail}")
        Text("Avant : ${preview(saved,ctx)}\nAprès : ${preview(draft,ctx)}")
    }},confirmButton={TextButton(onClick={save(draft);confirm=false},modifier=Modifier.testTag("local-persona-confirm")){Text("Confirmer localement")}},dismissButton={TextButton(onClick={confirm=false}){Text("Annuler")}})
}
internal fun preview(value:LocalPersona,context:PersonaContext):String {
    val p=ReadOnlyPersonalityPreview.create(value.profile,context)
    val address=if(p.effective.tutoiement)"Tu peux choisir la prochaine étape." else "Vous pouvez choisir la prochaine étape."
    val detail=if(value.detail>65)" Les faits, limites et étapes seront présentés séparément." else ""
    val frank=if(p.effective.frankness>=70) "Il manque des faits pour conclure." else "Quelques précisions aideraient à mieux conclure."
    val humour=if(p.effective.humour>=60) " Même les circuits apprécient un peu de méthode." else ""
    val initiative=if(p.effective.initiative>=60) " Je peux proposer une prochaine étape, à valider." else " J’attends le choix de la prochaine étape."
    val tone=if(p.effective.style==PersonaStyle.ELEGANT) "Volontiers." else "D’accord."
    return p.after+" "+tone+" "+address+" "+frank+humour+initiative+detail
}
@Composable
internal fun IntPreferenceSlider(label:String,value:Int,range:IntRange,save:(Int)->Unit) {
    var pending by remember(value) {mutableFloatStateOf(value.toFloat())}
    Text("$label : ${pending.toInt()}")
    Slider(pending,{pending=it},onValueChangeFinished={save(pending.toInt())},valueRange=range.first.toFloat()..range.last.toFloat(),
        modifier=Modifier.semantics {contentDescription=label})
}

private fun localDifferenceLabel(field:String)=when(field) {
    "defaults"->"Valeurs par défaut";"DAILY"->"Quotidien";"WORK"->"Travail";"URGENT"->"Urgence"
    "simulated_interests"->"Goûts simulés";"language_rules"->"Particularités de langage"
    "name"->"Nom";"preset"->"Profil"
    else->field.replace("override:","Surcharge : ").replace("DAILY","quotidien").replace("WORK","travail").replace("URGENT","urgence")
}
private fun localDifferenceValue(value:String):String {
    if(value=="INHERIT_DEFAULTS")return "Hériter des valeurs par défaut"
    val match=Regex("PersonaParameters\\(frankness=(\\d+), humour=(\\d+), initiative=(\\d+), tutoiement=(true|false), style=(ELEGANT|PLAIN)\\)").matchEntire(value)?:return value
    val p=match.groupValues
    return "Franchise ${p[1]}/100 · humour ${p[2]}/100 · initiative ${p[3]}/100 · ${if(p[4]=="true") "tutoiement" else "vouvoiement"} · ton ${if(p[5]=="ELEGANT") "élégant" else "simple"}"
}
