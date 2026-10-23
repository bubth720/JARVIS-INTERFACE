package com.jarvis.interfaceapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.jarvis.interfaceapp.ui.chat.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.interfaceapp.domain.HomeAction
import com.jarvis.interfaceapp.domain.PlacementError

@Composable
fun JarvisApp(viewModel: HomeViewModel,chatViewModel: ChatViewModel=composeViewModel()) {
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    val chat by chatViewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled=chatOpen){if(chat.busy)chatViewModel.cancel();chatOpen=false}
    if(chatOpen){
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            ChatScreen(chat,chatViewModel::send,chatViewModel::cancel,chatViewModel::demonstrate,{if(chat.busy)chatViewModel.cancel();chatOpen=false})
        }
        return
    }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val state = ui.home
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = { confirmReset = false },
        title = { Text("Restaurer la disposition par défaut") },
        text = { Text("Les deux profils et tous les verrouillages seront réinitialisés. La disposition enregistrée sera remplacée. Les autres données restent inchangées.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.resetLayouts() },
            modifier = Modifier.testTag("confirm-reset")) { Text("Restaurer") } },
        dismissButton = { TextButton(onClick = { confirmReset = false },
            modifier = Modifier.testTag("cancel-reset")) { Text("Annuler") } },
    )
    BackHandler(enabled = state.settingsOpen || state.editing) {
        viewModel.dispatch(if (state.settingsOpen) HomeAction.CloseSettings else HomeAction.ExitEditing)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            // Keep one minimum-height panel reachable in a short window.
            // Header overflow scrolls instead of starving the dashboard.
            val headerLimit = (maxHeight - 168.dp - 8.dp).coerceAtLeast(48.dp)
            Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max = headerLimit)
                    .verticalScroll(rememberScrollState()).testTag("app-header"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JARVIS INTERFACE", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("INTERFACE TACTIQUE • 0.1.0", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick={chatOpen=true},modifier=Modifier.testTag("open-conversation")){Text("Conversation — local expérimental")}
                    HorizontalDivider()
                    StorageStatus(ui.storage, viewModel::retryStorage)
                    if (ui.storage.phase != SavePhase.LOADING && !state.settingsOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.dispatch(HomeAction.OpenSettings) },
                                modifier = Modifier.testTag("open-settings")) { Text("Paramètres") }
                            Button(onClick = { viewModel.dispatch(HomeAction.ToggleEditing) },
                                enabled = ui.storage.canEdit,
                                modifier = Modifier.testTag("toggle-edit")) {
                                Text(if (state.editing) "Terminer" else "Modifier")
                            }
                        }
                        if (state.editing) Text("Édition : poignées Déplacer / Taille. ○ libre, ● verrouillé. Défilez pour explorer la grille.",
                            style = MaterialTheme.typography.bodySmall)
                        val message = when (state.placementError) {
                            PlacementError.OVERLAP -> "Position refusée : chevauchement."
                            PlacementError.OUT_OF_BOUNDS -> "Position refusée : limite de la grille."
                            PlacementError.TOO_SMALL -> "Taille refusée : minimum 3×3 cellules."
                            PlacementError.LOCKED -> "Panneau verrouillé."
                            PlacementError.UNKNOWN_PANEL -> "Panneau inconnu."
                            null -> null
                        }
                        if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (ui.storage.phase == SavePhase.LOADING) {
                    CircularProgressIndicator()
                } else if (state.settingsOpen) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
                        Text("Les modifications validées sont enregistrées automatiquement sur cet appareil. Le statut indique quand leur sauvegarde est terminée.")
                        OutlinedButton(onClick = { confirmReset = true },
                            enabled = ui.storage.phase != SavePhase.SAVING,
                            modifier = Modifier.testTag("reset-layouts")) {
                            Text("Restaurer la disposition par défaut")
                        }
                        Button(onClick = { viewModel.dispatch(HomeAction.CloseSettings) },
                            modifier = Modifier.testTag("close-settings")) { Text("Retour au tableau de bord") }
                    }
                } else {
                    DashboardGrid(state, viewModel::dispatch, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StorageStatus(storage: StorageState, retry: () -> Unit) {
    val text = when (storage.phase) {
        SavePhase.LOADING -> "Chargement de la disposition…"
        SavePhase.DEFAULTS -> "Disposition par défaut — aucune sauvegarde."
        SavePhase.SAVING -> "Enregistrement en cours…"
        SavePhase.SAVED -> "Disposition enregistrée."
        SavePhase.PROTECTED -> if (storage.readIssue == com.jarvis.interfaceapp.domain.LayoutReadIssue.CORRUPT)
            "Sauvegarde illisible. Disposition par défaut affichée, fichier conservé."
            else "Sauvegarde incompatible. Disposition par défaut affichée, fichier conservé."
        SavePhase.ERROR -> when (storage.failure) {
            StorageFailure.READ -> "Lecture impossible. La sauvegarde est conservée."
            StorageFailure.RESET -> "Restauration impossible. Disposition inchangée."
            else -> "Échec de l’enregistrement. Les modifications ne sont pas encore sauvegardées."
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = if (storage.phase == SavePhase.ERROR || storage.phase == SavePhase.PROTECTED)
            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("storage-status").semantics { liveRegion = LiveRegionMode.Polite })
    if (storage.phase == SavePhase.ERROR && storage.failure != StorageFailure.RESET) {
        TextButton(onClick = retry, modifier = Modifier.testTag("retry-storage")) { Text("Réessayer") }
    }
}
