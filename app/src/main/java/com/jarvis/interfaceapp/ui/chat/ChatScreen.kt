package com.jarvis.interfaceapp.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(state: SessionUiState,onSend: (String)->Boolean,onCancel: ()->Unit,onDemo: (Boolean)->Unit,onBack: ()->Unit) {
    var draft by remember { mutableStateOf("") }
    var pageSize by remember { mutableIntStateOf(20) }
    val lastAccepted=state.messages.lastOrNull { it.user }
    LaunchedEffect(lastAccepted?.id){if(lastAccepted?.text==draft)draft=""}
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("chat-screen")) {
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
        if(state.messages.size>pageSize) TextButton(onClick={pageSize=(pageSize+20).coerceAtMost(200)}){Text("Messages précédents (mémoire vive)")}
        state.notifications.lastOrNull()?.let { Text(it,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("chat-notification")) }
    }
        MessageTimeline(state.messages.takeLast(pageSize),Modifier.weight(1f).fillMaxWidth())
    Column(Modifier.fillMaxWidth().heightIn(max=composerLimit).verticalScroll(rememberScrollState())) {
        ComposerBar(draft,{draft=it.take(4000)},!state.busy,
            !state.busy && state.connection in setOf(PortConnection.CONNECTED,PortConnection.DEMONSTRATION),
            {onSend(draft)},onCancel,state.busy)
        Text("Messages temporaires, non enregistrés. Outils et mémoire : aucune action raccordée.",style=MaterialTheme.typography.labelSmall)
    }
    }
    }
}
@Composable
fun MessageTimeline(messages: List<ConversationMessage>,modifier: Modifier=Modifier) {
    LazyColumn(modifier.testTag("chat-timeline"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items(messages,key={it.id}) { message ->
            Surface(Modifier.fillMaxWidth(),tonalElevation=1.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(if(message.user) "Toi${if(message.demo) " • démo" else ""}" else "FIXTURE — PAS UNE RÉPONSE IA",style=MaterialTheme.typography.labelLarge)
                    Text(message.text)
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
