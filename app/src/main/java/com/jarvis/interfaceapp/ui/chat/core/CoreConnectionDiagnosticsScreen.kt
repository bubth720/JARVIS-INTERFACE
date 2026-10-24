package com.jarvis.interfaceapp.ui.chat.core

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Separate public protocol bench; never injects fixture text into the real chat thread. */
@Composable
fun CoreConnectionDiagnosticsScreen(onClose:()->Unit) {
    val scope=rememberCoroutineScope()
    var laboratory by remember { mutableStateOf(false) }
    val port=remember(laboratory) { if(laboratory) LaboratoryContentTransport() else UnavailableCoreConversationTransport() }
    val client=remember(port) { CoreConversationClientAdapter(port,scope) }
    val state by client.state.collectAsState()
    DisposableEffect(client) { onDispose { client.close() } }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()).semantics { testTagsAsResourceId=true },verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Raccordement CORE · diagnostic",style=MaterialTheme.typography.titleLarge)
        Text("CORE réel NON CONNECTÉ",Modifier.testTag("core-real-status"))
        Text("Aucun transport de texte approuvé, aucune authentification/signature inter-APK qualifiée. Modèle natif, VOICE, NEXUS et outils non raccordés.")
        Text("Proposition locale distincte des schémas UNIFIED v1. Un ACK ou un événement de métadonnées n’est pas un texte de réponse ni une permission.")
        Text("${state.origin} · ${state.link}",Modifier.testTag("core-protocol-status"))
        Text(state.phase,Modifier.testTag("core-protocol-phase"))
        OutlinedButton(onClick={laboratory=!laboratory},modifier=Modifier.testTag("core-protocol-lab")) {
            Text(if(laboratory) "Fermer le banc RAM" else "Ouvrir volontairement le banc de protocole local")
        }
        if(laboratory) {
            Text("LABORATOIRE UNIQUEMENT · aucun CORE ou LLM exécuté")
            Button(enabled=!state.busy && state.link==CoreTextLink.READY,onClick={client.submit("Scénario public de protocole")},modifier=Modifier.testTag("core-protocol-run")) { Text("Exécuter le scénario public corrélé") }
            OutlinedButton(enabled=state.busy,onClick=client::cancel,modifier=Modifier.testTag("core-protocol-cancel")) { Text("Demander l’arrêt logique") }
            if(state.text.isNotEmpty()) { Text("CONTENU DE TEST · PAS UNE RÉPONSE IA");Text(state.text,Modifier.testTag("core-protocol-text")) }
        }
        Text("Le vrai chat reste indépendant et non connecté. Aucun Binder, service exporté, permission, modèle ou contrat externe n’est activé ici.")
        TextButton(onClick=onClose,modifier=Modifier.testTag("core-protocol-close")) { Text("Retour au chat") }
    }
    }
}
/** Synthetic data source, local to this diagnostic screen; no private user text or external service. */
private class LaboratoryContentTransport:CoreConversationTransport {
    override val origin=CoreTextOrigin.LABORATORY
    override val connection=MutableStateFlow(CoreTextLink.READY).asStateFlow()
    private val stream=MutableSharedFlow<CoreMessageEnvelope>(extraBufferCapacity=8)
    override val updates=stream.asSharedFlow()
    private var key:ContentKey?=null
    override suspend fun submit(value:SubmitUserText):ContentAck {
        val k=ContentKey(UUID.randomUUID().toString(),UUID.randomUUID().toString(),0);key=k
        return ContentAck(value.submissionId,k).also {
            // Emissions before ACK exercise bounded early-event correlation explicitly.
            stream.emit(CoreMessageEnvelope(UUID.randomUUID().toString(),k,1,ContentBody.AssistantTextChunk("Scénario de laboratoire : ")))
            stream.emit(CoreMessageEnvelope(UUID.randomUUID().toString(),k,2,ContentBody.AssistantTextChunk("corrélation et contenu de test vérifiés ; aucun moteur IA.")))
            stream.emit(CoreMessageEnvelope(UUID.randomUUID().toString(),k,3,ContentBody.Completed))
        }
    }
    override suspend fun stop(key:ContentKey):Boolean {
        if(this.key!=key)return false
        stream.emit(CoreMessageEnvelope(UUID.randomUUID().toString(),key,4,ContentBody.Cancelled));return true
    }
}
