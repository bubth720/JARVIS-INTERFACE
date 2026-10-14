package com.jarvis.interfaceapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.interfaceapp.domain.HomeAction

@Composable
fun JarvisApp(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.settingsOpen) { viewModel.dispatch(HomeAction.CloseSettings) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("JARVIS INTERFACE", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("INTERFACE TACTIQUE • 0.1.0", style = MaterialTheme.typography.labelLarge)
            HorizontalDivider()
            if (state.settingsOpen) {
                Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
                Panel("Configuration locale", "Thème sombre provisoire. La personnalisation et le stockage seront ajoutés dans les prochaines missions.")
                Button(onClick = { viewModel.dispatch(HomeAction.CloseSettings) },
                    modifier = Modifier.testTag("close-settings")) { Text("Retour au tableau de bord") }
            } else {
                OutlinedButton(onClick = { viewModel.dispatch(HomeAction.OpenSettings) },
                    modifier = Modifier.testTag("open-settings")) { Text("Paramètres") }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 700.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            DashboardPanel(Modifier.weight(1.5f))
                            AvatarPanel(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            DashboardPanel(Modifier.fillMaxWidth())
                            AvatarPanel(Modifier.fillMaxWidth())
                        }
                    }
                }
                Panel("État local : opérationnel", "Interface autonome • fonctionnement hors ligne\nAucun service JARVIS requis.", Modifier.testTag("local-status"))
            }
        }
    }
}

@Composable
private fun DashboardPanel(modifier: Modifier) {
    Panel("Tableau de bord", "Zone réservée aux futurs panneaux et widgets.",
        modifier.heightIn(min = 220.dp).testTag("dashboard"))
}

@Composable
private fun AvatarPanel(modifier: Modifier) {
    Panel("Avatar", "Emplacement réservé\nSupport futur : vidéo MP4 et/ou animation 2D.",
        modifier.heightIn(min = 220.dp).testTag("avatar-placeholder"))
}

@Composable
private fun Panel(title: String, description: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
