package com.jarvis.interfaceapp.cockpit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryDialog(entry: WorkspaceEntry, workspace: LocalWorkspace, close: () -> Unit) {
    var rows by remember { mutableStateOf(listOf("Lecture de l’historique…")) }
    LaunchedEffect(entry.id) {
        rows = try { withContext(Dispatchers.IO) { workspace.history(entry.id) } }
        catch (_: Exception) { listOf("Historique indisponible.") }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Historique local · ${entry.title}") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).testTag("history-content")) {
            Text("200 dernières opérations au maximum.")
            rows.forEach { Text(it) }
        }
    }, confirmButton = { TextButton(close, Modifier.testTag("history-close")) { Text("Fermer") } })
}

@Composable
fun StepPanel(entry: WorkspaceEntry, workspace: LocalWorkspace, changed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var steps by remember(entry.id) { mutableStateOf(emptyList<WorkspaceStep>()) }
    var text by remember(entry.id) { mutableStateOf("") }
    var error by remember(entry.id) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id, revision) {
        try { steps = withContext(Dispatchers.IO) { workspace.steps(entry.id) } }
        catch (_: Exception) { error = "Lecture des étapes impossible." }
    }
    fun save(step: WorkspaceStep) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { workspace.saveStep(step) }; revision++; changed(); text = "" }
            catch (_: Exception) { error = "Enregistrement de l’étape impossible." }
            finally { busy = false }
        }
    }
    Text("Étapes · la progression générale reste réglée manuellement")
    OutlinedTextField(text, { text = it.take(160) }, label = { Text("Nouvelle étape") }, modifier = Modifier.fillMaxWidth().testTag("step-title-${entry.id}"))
    Button({ if (text.isBlank()) error = "Un titre d’étape est nécessaire." else save(WorkspaceStep(parent = entry.id, title = text)) }, Modifier.testTag("step-add-${entry.id}"), enabled = !busy) { Text("Ajouter l’étape") }
    steps.forEach { step ->
        Row { Checkbox(step.done, { save(step.copy(done = it)) }, Modifier.testTag("step-toggle-${step.id}"), enabled = !busy); Text(step.title, Modifier.weight(1f)) }
    }
    if (error.isNotEmpty()) Text(error)
}
