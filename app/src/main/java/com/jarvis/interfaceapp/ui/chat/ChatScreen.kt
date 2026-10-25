package com.jarvis.interfaceapp.ui.chat

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(state: SessionUiState,onSend: (String)->Boolean,onCancel: ()->Unit,onDemo: (Boolean)->Unit,onBack: ()->Unit,onClear:()->Unit={}) {
    var diagnosticsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled=diagnosticsOpen) { diagnosticsOpen=false }
    if(diagnosticsOpen) {
        com.jarvis.interfaceapp.ui.chat.core.CoreConnectionDiagnosticsScreen { diagnosticsOpen=false }
        return
    }
    var draft by rememberSaveable { mutableStateOf("") }
    var pageSize by rememberSaveable { mutableIntStateOf(20) }
    var confirmClear by remember {mutableStateOf(false)}
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Effacer cette session RAM ?")},text={Text("L’archive locale éventuelle se gère séparément dans le Studio. Aucune session CORE n’est supprimée.")},
        confirmButton={TextButton(onClick={onClear();confirmClear=false}){Text("Effacer")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Annuler")}})
    val lastAccepted=state.messages.lastOrNull { it.user }
    LaunchedEffect(lastAccepted?.id){if(lastAccepted?.text==draft)draft=""}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("chat-screen").semantics { testTagsAsResourceId=true }) {
    val headerLimit=minOf(180.dp,maxHeight*0.3f).coerceAtLeast(48.dp)
    val composerLimit=(maxHeight*0.5f).coerceAtLeast(48.dp)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Column(Modifier.fillMaxWidth().heightIn(max=headerLimit).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            Text("Conversation • écran V1",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onBack,modifier=Modifier.testTag("chat-back")){Text("Tableau de bord")}
        }
        CapabilityStatusPanel(state)
        Row(Modifier.fillMaxWidth()) {
            Text("Démo locale, sans CORE ni IA",Modifier.weight(1f))
            Switch(checked=state.connection==PortConnection.DEMONSTRATION,onCheckedChange=onDemo,enabled=!state.busy,
                modifier=Modifier.testTag("chat-demo").semantics { contentDescription="Activer la démonstration logicielle sans IA" })
        }
        Text(state.phase,modifier=Modifier.testTag("chat-phase").semantics { liveRegion=LiveRegionMode.Polite })
        OutlinedButton(enabled=!state.busy,onClick={diagnosticsOpen=true},modifier=Modifier.testTag("open-core-diagnostics")) { Text("Diagnostic du raccordement CORE") }
        TextButton(enabled=!state.busy && state.messages.isNotEmpty(),onClick={confirmClear=true}){Text("Effacer cette session…")}
        if(state.messages.size>pageSize) TextButton(onClick={pageSize=(pageSize+20).coerceAtMost(200)}){Text("Messages précédents (mémoire vive)")}
        state.notifications.lastOrNull()?.let { Text(it,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("chat-notification")) }
    }
        MessageTimeline(state.messages.takeLast(pageSize),Modifier.weight(1f).fillMaxWidth(),canRetry=!state.busy && state.connection==PortConnection.DEMONSTRATION,onRetry={onSend(it)})
    Column(Modifier.fillMaxWidth().heightIn(max=composerLimit).verticalScroll(rememberScrollState())) {
        ComposerBar(draft,{draft=it.take(4000)},!state.busy,
            !state.busy && state.connection in setOf(PortConnection.CONNECTED,PortConnection.DEMONSTRATION),
            {onSend(draft)},onCancel,state.busy)
        Text("Messages temporaires, non enregistrés. Outils et mémoire : aucune action raccordée.",style=MaterialTheme.typography.labelSmall)
    }
    }
    }
    }
}
@Composable
fun MessageTimeline(messages: List<ConversationMessage>,modifier: Modifier=Modifier,canRetry:Boolean=false,onRetry:(String)->Unit={}) {
    val list=rememberLazyListState()
    val clipboard=LocalClipboardManager.current
    var details by remember {mutableStateOf<ConversationMessage?>(null)}
    LaunchedEffect(messages.size) {
        if(messages.isNotEmpty() && (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:0)>=messages.size-4)list.animateScrollToItem(messages.lastIndex)
    }
    details?.let {m->AlertDialog(onDismissRequest={details=null},title={Text("Détails du message")},text={Text("Identifiant : ${m.id}\nOrigine : ${if(m.demo) "SIMULATION locale" else "contenu reçu"}\n${m.text.length} caractères\nSources documentaires : ${m.sources.size}. Aucun outil ou moteur IA attesté.")},confirmButton={TextButton(onClick={details=null}){Text("Fermer")}})}
    LazyColumn(modifier.testTag("chat-timeline"),state=list,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(messages,key={it.id}) { message ->
            Surface(Modifier.fillMaxWidth(),tonalElevation=1.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(if(message.user) "Toi${if(message.demo) " • démo" else ""}" else "FIXTURE — PAS UNE RÉPONSE IA",style=MaterialTheme.typography.labelLarge)
                    SelectionContainer {Text(message.text)}
                    if(message.sources.isNotEmpty()) {
                        Text("Sources documentaires fournies",style=MaterialTheme.typography.labelLarge)
                        message.sources.forEach {source->Text(source.title);if(source.excerpt.isNotBlank())Text(source.excerpt,style=MaterialTheme.typography.bodySmall)}
                    }
                    FlowRow {
                        TextButton(onClick={clipboard.setText(AnnotatedString(message.text))},modifier=Modifier.testTag("copy-${message.id}")){Text("Copier")}
                        TextButton(onClick={details=message}){Text("Détails / sources")}
                        val index=messages.indexOfFirst {it.id==message.id}
                        val question=messages.take(index+1).lastOrNull {it.user}?.text
                        if(message.demo && question!=null)TextButton(enabled=canRetry,onClick={onRetry(question)},modifier=Modifier.testTag("retry-${message.id}")){Text("Relancer en simulation")}
                    }
                }
            }
        }
    }
}
@Composable
fun ComposerBar(value: String,onValue: (String)->Unit,editable: Boolean,sendEnabled: Boolean,onSend: ()->Unit,onCancel: ()->Unit,busy: Boolean) {
    OutlinedTextField(value=value,onValueChange=onValue,enabled=editable,label={Text("Message (4 000 caractères maximum)")},maxLines=4,
        modifier=Modifier.fillMaxWidth().testTag("chat-input"))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick=onSend,enabled=sendEnabled && value.isNotBlank(),modifier=Modifier.testTag("chat-send")){Text("Envoyer")}
        OutlinedButton(onClick=onCancel,enabled=busy,modifier=Modifier.testTag("chat-cancel")){Text("Annuler")}
    }
}
@Composable
fun CapabilityStatusPanel(state: SessionUiState) {
    val status=when(state.connection){PortConnection.DEMONSTRATION->"DÉMONSTRATION LOCALE — aucun CORE ou LLM connecté"
        PortConnection.CONNECTED->"Port local CORE connecté — contenu de réponse non raccordé"
        else->"CORE NON CONNECTÉ — aucun envoi possible"}
    Text("$status\nHors ligne • aucun transport réseau ajouté • VOICE désactivée",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("chat-connection"))
}
