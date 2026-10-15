package com.jarvis.interfaceapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.interfaceapp.domain.HomeAction
import com.jarvis.interfaceapp.domain.PlacementError

@Composable
fun JarvisApp(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.settingsOpen || state.editing) {
        viewModel.dispatch(if (state.settingsOpen) HomeAction.CloseSettings else HomeAction.ExitEditing)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("JARVIS INTERFACE", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("INTERFACE TACTIQUE • 0.1.0", style = MaterialTheme.typography.labelLarge)
            HorizontalDivider()
            if (state.settingsOpen) {
                Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
                Text("Configuration locale. Thème sombre provisoire. Les dispositions sont conservées en mémoire durant la session ; le stockage sera ajouté ultérieurement.")
                Button(onClick = { viewModel.dispatch(HomeAction.CloseSettings) },
                    modifier = Modifier.testTag("close-settings")) { Text("Retour au tableau de bord") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.dispatch(HomeAction.OpenSettings) },
                        modifier = Modifier.testTag("open-settings")) { Text("Paramètres") }
                    Button(onClick = { viewModel.dispatch(HomeAction.ToggleEditing) },
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
                DashboardGrid(state, viewModel::dispatch, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
