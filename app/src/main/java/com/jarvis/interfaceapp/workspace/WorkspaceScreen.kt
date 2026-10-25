package com.jarvis.interfaceapp.workspace

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.ui.identity.*
import kotlinx.coroutines.*

@Composable
fun WorkspaceScreen(controller:WorkspaceController,layouts:Layouts,restore:(Layouts)->Unit,onBack:()->Unit,onChat:()->Unit,initialTab:String="Accueil") {
    val ui by controller.state.collectAsStateWithLifecycle();val w=ui.value
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
        .verticalScroll(rememberScrollState()).semantics { testTagsAsResourceId=true },verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("JARVIS / STUDIO LOCAL",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
        Text("Autonome · aucun module externe requis",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick=onBack,modifier=Modifier.testTag("studio-back")) { Text("Retour au dashboard") }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Accueil","Avatar","Dispositions","Personnalité","Modules","Apparence","Historique").forEach { name ->
                FilterChip(selected=tab==name,onClick={tab=name;notice=null},label={Text(name)},modifier=Modifier.testTag("studio-tab-$name"))
            }
        }
        Text(ui.error ?: if(!ui.ready) "Chargement…" else if(ui.saving) "Enregistrement en cours…" else if(ui.durable) "Préférences locales prêtes" else "Modification non durable",
            Modifier.testTag("workspace-status").semantics { liveRegion=LiveRegionMode.Polite })
        notice?.let { Text(it,Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
        if(ui.ready) Column(Modifier.fillMaxWidth().animateContentSize(if(w.visual.reduceMotion || !w.visual.transitions)snap() else tween(180)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                "Accueil" -> {
                    Text("Votre espace tactique",style=MaterialTheme.typography.titleLarge)
                    Text("Quatre panneaux tactiles, des dispositions nommées et des réglages locaux. Les modules absents restent indisponibles.")
                    Button(onClick=onChat) { Text("Ouvrir la conversation") }
                    panelCatalog.forEach { d -> OutlinedCard(onClick={when(d.destination){
                        "chat"->onChat();"dashboard"->if(d.id=="avatar")tab="Avatar" else onBack();"tasks"->{tab="Modules";notice="Tâches indisponibles : aucun adaptateur raccordé."};else->tab="Modules"
                    }},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(d.title,color=MaterialTheme.colorScheme.primary)
                        Text(when(d.id) {
                            "avatar"->"Retrouver le panneau et ses prévisualisations."
                            "conversation"->"Ouvrir votre session locale."
                            "tasks"->"Gestion des tâches indisponible pour le moment."
                            "knowledge"->"Sources documentaires : adaptateur indisponible."
                            "commands"->"Retrouver vos commandes locales."
                            "applications"->"Configurer les accès aux modules."
                            else->"Consulter les disponibilités et les erreurs connues."
                        })
                    } } }
                }
                "Avatar" -> com.jarvis.interfaceapp.avatar.athena.AthenaAvatar(Modifier.fillMaxWidth(),portraitHeight=360.dp,studio=true)
                "Dispositions" -> LayoutLibrary(w,controller,layouts,restore) {notice=it}
                "Personnalité" -> LocalPersonaEditor(w.persona,{next->controller.change { require(it.persona==w.persona) {"Préférences changées depuis l’aperçu"};it.copy(persona=next) }})
                "Modules" -> ModuleManager(w.packages,controller)
                "Apparence" -> AppearanceEditor(w.visual) { next->controller.change { it.copy(visual=mergeVisual(it.visual,w.visual,next)) } }
                "Historique" -> HistoryPanel(w,controller)
            }
        }
    }
    }
}

@Composable
private fun LayoutLibrary(w:Workspace,c:WorkspaceController,current:Layouts,restore:(Layouts)->Unit,notice:(String)->Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String,Layouts>?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var exportSnapshot by remember { mutableStateOf<ByteArray?>(null) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri->
        val bytes=exportSnapshot;exportSnapshot=null
        if(uri!=null && bytes!=null)scope.launch {
            notice("Export en cours…")
            try { withContext(Dispatchers.IO) {
                // Only a newly empty destination may receive an explicit layout export.
                require(context.contentResolver.openInputStream(uri)?.use { it.read()==-1 }==true)
                context.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }
                require(context.contentResolver.openInputStream(uri)!!.use { input->
                    val read=java.io.ByteArrayOutputStream();val chunk=ByteArray(4096)
                    while(read.size()<=bytes.size) {val n=input.read(chunk,0,minOf(chunk.size,bytes.size+1-read.size()));if(n<0)break;read.write(chunk,0,n)}
                    read.toByteArray().contentEquals(bytes)
                })
            };notice("Disposition exportée et relue. Aucun profil ni historique exporté.") }
            catch(e:Exception) { if(e is CancellationException)throw e;notice("Export échoué ou destination non vide ; résultat non confirmé.") }
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
        if(uri!=null)scope.launch {
            try { val imported=withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { input->
                    val out=java.io.ByteArrayOutputStream();val buf=ByteArray(4096)
                    while(out.size()<=65_536) { val n=input.read(buf,0,minOf(buf.size,65_537-out.size()));if(n<0)break;out.write(buf,0,n) }
                    WorkspaceCodec.importLayouts(out.toByteArray())
                }
            };pending="Import local" to imported }
            catch(e:Exception) { if(e is CancellationException)throw e;notice("Import refusé : version, taille ou placement invalide. Aucune modification.") }
        }
    }
    Text("Dispositions / deux profils indépendants",style=MaterialTheme.typography.titleLarge)
    Text("Jusqu’à12 dispositions. Chaque copie contient positions, dimensions et verrouillages des deux profils. L’avatar et les données personnelles ne sont pas exportés.")
    OutlinedTextField(name,{name=it.take(40)},label={Text("Nom de la disposition")},modifier=Modifier.fillMaxWidth().testTag("layout-name"))
    Button(enabled=validLayoutName(name) && w.layouts.size<12 && name !in w.layouts,onClick={
        val frozen=current.mapValues { it.value.toList() };val n=name;c.change { it.copy(layouts=it.layouts+(n to frozen)) };name=""
    },modifier=Modifier.testTag("layout-save")) { Text("Enregistrer une copie") }
    w.layouts.forEach { (n,l) -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text(n,style=MaterialTheme.typography.titleMedium)
        FlowRow { TextButton(onClick={pending=n to l},modifier=Modifier.testTag("layout-restore-$n")) { Text("Restaurer") }
            TextButton(onClick={delete=n}) { Text("Supprimer la copie") } }
    } } }
    OutlinedButton(onClick={import.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.testTag("layout-import")) { Text("Importer une disposition…") }
    OutlinedButton(onClick={exportSnapshot=WorkspaceCodec.exportLayouts(current);export.launch("jarvis-disposition-v1.json")}) { Text("Exporter les deux profils…") }
    pending?.let { (n,l)->AlertDialog(onDismissRequest={pending=null},title={Text("Restaurer $n ?")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Les deux profils seront remplacés après écriture effective. Vérifiez les coordonnées et verrouillages :")
        l.forEach { (p,panels)->Text(p.name);panels.forEach { Text("${it.id}: ${it.x},${it.y} / ${it.width}×${it.height} / verrouillé=${it.locked}") } }
    } },confirmButton={TextButton(onClick={restore(l);pending=null;notice("Restauration demandée : consulter le statut de sauvegarde du dashboard.")},modifier=Modifier.testTag("layout-confirm-restore")) {Text("Confirmer")}},dismissButton={TextButton(onClick={pending=null}){Text("Annuler")}}) }
    delete?.let { n->AlertDialog(onDismissRequest={delete=null},title={Text("Supprimer la copie $n ?")},text={Text("La disposition active reste inchangée.")},
        confirmButton={TextButton(onClick={c.change{it.copy(layouts=it.layouts-n)};delete=null}){Text("Supprimer")}},dismissButton={TextButton(onClick={delete=null}){Text("Annuler")}}) }
}

@Composable
private fun AppearanceEditor(v:VisualPreferences,save:(VisualPreferences)->Unit) {
    Text("Apparence et accessibilité",style=MaterialTheme.typography.titleLarge)
    LocalTheme.entries.forEach { theme->FilterChip(v.theme==theme,{save(v.copy(theme=theme))},label={Text(theme.name)}) }
    Text("Texte : ${v.textPercent}% (en plus du réglage Android)")
    IntPreferenceSlider("Taille du texte",v.textPercent,100..150){save(v.copy(textPercent=it))}
    Text("Éléments : ${v.elementPercent}%")
    IntPreferenceSlider("Taille des éléments",v.elementPercent,100..125){save(v.copy(elementPercent=it))}
    Toggle("Réduire les animations",v.reduceMotion){save(v.copy(reduceMotion=it))}
    Toggle("Transitions de navigation",v.transitions){save(v.copy(transitions=it))}
    DisplayMode.entries.forEach { mode->FilterChip(v.mode==mode,{save(v.copy(mode=mode))},label={Text(mode.name)}) }
    Text("Panneaux visibles — au moins un ; les panneaux masqués gardent leurs coordonnées et réservent leurs cellules.")
    PanelId.entries.forEach { id->Toggle(id.name,id in v.visible){on->if(on || v.visible.size>1)save(v.copy(visible=if(on)v.visible+id else v.visible-id))} }
    Text("TalkBack : titres, boutons nommés, sliders réglables et actions personnalisées Déplacer/Taille. Respect du texte Android ; validation physique à effectuer.")
}
@Composable
internal fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,Modifier.weight(1f));Switch(value,change,Modifier.semantics { contentDescription=label })
    }
}

@Composable
private fun HistoryPanel(w:Workspace,c:WorkspaceController) {
    var confirm by remember { mutableStateOf(false) }
    Text("Historique local",style=MaterialTheme.typography.titleLarge)
    Text("Opt-in : jusqu’à200 messages terminés sur cet appareil, jamais envoyés à CORE. Les échanges restent étiquetés simulation. Désactiver efface la copie locale. Les fichiers privés sont exclus des sauvegardes Android.")
    Toggle("Conserver la session actuelle et les prochains échanges localement",w.keepHistory){on->if(on)c.change { it.copy(keepHistory=true) } else confirm=true}
    Text("${w.history.size} messages enregistrés")
    OutlinedButton(onClick={confirm=true},modifier=Modifier.testTag("history-clear")){Text("Supprimer l’historique local…")}
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { items(w.history.asReversed(),key={it.id}) { m->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
        Text(if(m.user) "Vous" else if(m.simulation) "SIMULATION · aucun LLM" else "Réponse archivée")
        Text(m.text)
        m.sources.forEach {source->Text("Source : ${source.title}");Text(source.excerpt)}
    } } } }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Effacer l’historique local ?")},text={Text("Suppression volontaire des messages archivés et désactivation de la conservation. La session RAM active reste disponible.")},
        confirmButton={TextButton(onClick={c.change {it.copy(keepHistory=false,history=emptyList())};confirm=false},modifier=Modifier.testTag("history-confirm-clear")){Text("Effacer")}},dismissButton={TextButton(onClick={confirm=false}){Text("Annuler")}})
}

@Composable
private fun ModuleManager(packages:Map<ModuleId,String>,c:WorkspaceController) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var observed by remember {mutableStateOf<Map<ModuleId,Pair<String,ModuleObservation>>>(emptyMap())}
    Text("Modules / état vérifiable",style=MaterialTheme.typography.titleLarge)
    Text("Détection Android limitée à la visibilité autorisée. Application installée ≠ service opérationnel. Aucune connexion Binder/réseau, capacité ni permission n’est activée.")
    ModuleId.entries.forEach { id->
        var pkg by remember(id,packages[id]) {mutableStateOf(packages[id].orEmpty())}
        val o=observed[id]?.takeIf {it.first==pkg}?.second
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(id.name,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text("Contrat UI v1 · ${o?.health ?: if(packages[id]==null)ModuleHealth.UNCONFIGURED else ModuleHealth.CONFIGURED_UNVERIFIED}",Modifier.testTag("module-state-$id"))
            Text("Version : ${o?.version ?: "non vérifiée"} · capacités : ${o?.declaredCapabilities?.joinToString()?.ifBlank {"aucune attestée"} ?: "non vérifiées"}")
            Text("Dernière erreur observée : ${o?.lastError ?: "aucune observation"}")
            OutlinedTextField(pkg,{pkg=it.take(200)},label={Text("Identifiant du paquet Android (facultatif)")},modifier=Modifier.fillMaxWidth())
            Button(enabled=validPackage(pkg),onClick={
                val target=pkg;c.change {it.copy(packages=it.packages+(id to target))}
                scope.launch {
                    val result=withContext(Dispatchers.IO) { try {
                        @Suppress("DEPRECATION") val info=context.packageManager.getPackageInfo(target,0)
                        ModuleObservation(id,ModuleHealth.INSTALLED_NOT_CONNECTED,info.versionName,lastError="Transport non autorisé/non raccordé")
                    } catch(_:android.content.pm.PackageManager.NameNotFoundException) { ModuleObservation(id,ModuleHealth.UNOBSERVABLE,lastError="Paquet absent ou non visible ; aucune capacité vérifiée") }
                    catch(_:Exception) { ModuleObservation(id,ModuleHealth.ERROR,lastError="Inspection Android refusée") } }
                    observed=observed+(id to (target to result))
                }
            }){Text("Enregistrer et vérifier localement")}
            if(o?.health==ModuleHealth.INSTALLED_NOT_CONNECTED && packages[id]==pkg) TextButton(onClick={
                try {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:$pkg".toUri()))}catch(_:Exception){observed=observed+(id to (pkg to o.copy(lastError="Réglages Android indisponibles")))}
            }){Text("Réglages Android du module")}
        } }
    }
}
