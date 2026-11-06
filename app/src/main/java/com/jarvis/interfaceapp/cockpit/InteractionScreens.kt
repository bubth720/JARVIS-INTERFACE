package com.jarvis.interfaceapp.cockpit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun EntryEditor(entry: WorkspaceEntry, save: (WorkspaceEntry) -> Unit, close: () -> Unit) {
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var body by remember(entry.id) { mutableStateOf(entry.body) }
    var date by remember(entry.id) { mutableStateOf(entry.due) }
    var amount by remember(entry.id) { mutableStateOf(BigDecimal.valueOf(entry.amountCents).abs().movePointLeft(2).toPlainString()) }
    var income by remember(entry.id) { mutableStateOf(entry.amountCents >= 0) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Modifier l’enregistrement") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it.take(160) }, label = { Text("Titre") }, modifier = Modifier.testTag("edit-title"))
            OutlinedTextField(body, { body = it.take(100_000) }, label = { Text("Texte / description") }, modifier = Modifier.testTag("edit-body"))
            if (entry.module in listOf("Administratif", "Projets", "Finance")) OutlinedTextField(date, { date = it }, label = { Text("Date · AAAA-MM-JJ") }, modifier = Modifier.testTag("edit-date"))
            if (entry.module == "Finance") {
                OutlinedTextField(amount, { amount = it }, label = { Text("Montant EUR") }, modifier = Modifier.testTag("edit-amount"))
                Row { FilterChip(!income, { income = false }, label = { Text("Dépense") }, modifier = Modifier.testTag("edit-expense")); FilterChip(income, { income = true }, label = { Text("Revenu") }, modifier = Modifier.testTag("edit-income")) }
            }
            if (error.isNotEmpty()) Text(error, Modifier.testTag("edit-error"))
        }
    }, confirmButton = { Button(onClick = {
        val cents = if (entry.module == "Finance") parseEuroCents(amount) else entry.amountCents
        if (title.isBlank() || !validDueDate(date) || cents == null) error = "Titre, date ou montant invalide."
        else { save(entry.copy(title = title, body = body, due = date, amountCents = if (entry.module != "Finance" || income) cents else -cents)); close() }
    }, modifier = Modifier.testTag("edit-save")) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = close, modifier = Modifier.testTag("edit-cancel")) { Text("Annuler") } })
}

@Composable
fun FinanceChart(entries: List<WorkspaceEntry>) {
    val month = YearMonth.now().toString()
    val rows = entries.filter { !it.done && it.due.startsWith(month) }
    val income = rows.filter { it.amountCents > 0 }.fold(BigDecimal.ZERO) { a, e -> a.add(BigDecimal.valueOf(e.amountCents)) }
    val expense = rows.filter { it.amountCents < 0 }.fold(BigDecimal.ZERO) { a, e -> a.add(BigDecimal.valueOf(e.amountCents).abs()) }
    val maximum = income.max(expense).max(BigDecimal.ONE)
    val primary = MaterialTheme.colorScheme.primary
    Text("Graphique du mois $month · revenus / dépenses · saisies locales")
    Canvas(Modifier.fillMaxWidth().height(90.dp).testTag("finance-chart").semantics { contentDescription = "Revenus ${income.movePointLeft(2).toPlainString()} EUR ; dépenses ${expense.movePointLeft(2).toPlainString()} EUR" }) {
        listOf(income, expense).forEachIndexed { i, value ->
            val fraction = value.divide(maximum, 6, java.math.RoundingMode.HALF_UP).toFloat()
            drawRect(primary.copy(alpha = if (i == 0) .9f else .5f), Offset(size.width * .1f, size.height * (.1f + i * .45f)), Size(size.width * .8f * fraction, size.height * .3f))
        }
    }
}

@Composable
fun CollectionScreen(route: String, workspace: LocalWorkspace, go: (String) -> Unit, openFile: (String) -> Unit) {
    var entries by remember(route) { mutableStateOf(emptyList<WorkspaceEntry>()) }
    var error by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf("") }
    LaunchedEffect(route) {
        try { entries = withContext(Dispatchers.IO) { workspace.allEntries() } }
        catch (_: Exception) { error = "Lecture locale impossible." }
    }
    Pane(route, Modifier.testTag("collection-screen")) {
        if (error.isNotEmpty()) Text(error)
        when (route) {
            "Documents" -> {
                Text("Références choisies localement. Supprimer une référence ne supprime jamais son fichier original.")
                Button(onClick = { go("Administratif") }, modifier = Modifier.testTag("documents-import")) { Text("Référencer un document") }
                val documents = entries.filter { it.uri.isNotEmpty() }
                if (documents.isEmpty()) Text("Aucun document référencé.")
                documents.forEach { entry -> Text(entry.title); Button(onClick = { openFile(entry.uri) }, modifier = Modifier.testTag("document-open-${entry.id}")) { Text("Ouvrir le document") } }
            }
            "Calendrier" -> {
                Text(month.toString(), Modifier.testTag("calendar-month"))
                Row {
                    TextButton({ month = month.minusMonths(1); selectedDay = "" }, Modifier.testTag("month-previous")) { Text("←") }
                    TextButton({ month = YearMonth.now(); selectedDay = "" }, Modifier.testTag("month-today")) { Text("Aujourd’hui") }
                    TextButton({ month = month.plusMonths(1); selectedDay = "" }, Modifier.testTag("month-next")) { Text("→") }
                }
                val deadlines = entries.filter { it.module != "Finance" && it.due.startsWith(month.toString()) }
                (1..month.lengthOfMonth()).toList().chunked(7).forEach { days ->
                    Row {
                        days.forEach { day -> val date = month.atDay(day).toString()
                            TextButton({ selectedDay = if (selectedDay == date) "" else date }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("day-$day"), contentPadding = PaddingValues(0.dp)) { Text("$day${if (deadlines.any { it.due == date }) "•" else ""}") }
                        }
                    }
                }
                Text(if (selectedDay.isEmpty()) "Toutes les échéances du mois" else selectedDay)
                val shown = deadlines.filter { selectedDay.isEmpty() || it.due == selectedDay }.sortedBy { it.due }
                if (shown.isEmpty()) Text("Aucune échéance dans cette sélection.")
                shown.forEach { entry -> Text("${entry.due} · ${entry.title}${if (entry.done) " · terminé" else ""}"); TextButton({ go(entry.module) }, Modifier.testTag("deadline-open-${entry.id}")) { Text("Ouvrir ${entry.module}") } }
            }
            "Statistiques" -> {
                val projects = entries.filter { it.module in listOf("Projets", "Création") }
                Text("${projects.size} projets · ${entries.count { it.module == "Administratif" && it.done }} tâches terminées", Modifier.testTag("statistics-summary"))
                projects.forEach { entry ->
                    Text("${entry.title} · ${entry.progress} %")
                    LinearProgressIndicator(progress = { entry.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    TextButton({ go(entry.module) }, Modifier.testTag("stats-open-${entry.id}")) { Text("Ouvrir ${entry.module}") }
                }
                if (projects.isEmpty()) Text("Aucun projet enregistré.")
                FinanceChart(entries.filter { it.module == "Finance" })
            }
        }
    }
}
