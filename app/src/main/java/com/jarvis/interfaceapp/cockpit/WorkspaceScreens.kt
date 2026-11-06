package com.jarvis.interfaceapp.cockpit

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Debug
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.YearMonth

private fun euros(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString() + " €"

@Composable
private fun draft(key: String, label: String, multiline: Boolean = false): Pair<String, @Composable () -> Unit> {
    val preferences = LocalContext.current.getSharedPreferences("ui01b-drafts", Context.MODE_PRIVATE)
    var value by remember(key) { mutableStateOf(preferences.getString(key, "") ?: "") }
    return value to {
        OutlinedTextField(value, {
            value = it.take(if (multiline) 100_000 else 160)
            preferences.edit().putString(key, value).apply()
        }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(key), singleLine = !multiline,
            minLines = if (multiline) 4 else 1)
    }
}

@Composable
fun WorkspaceScreen(module: String, workspace: LocalWorkspace, secure: Boolean, setSecure: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember(module) { mutableStateOf(emptyList<WorkspaceEntry>()) }
    var message by remember(module) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(module, revision) {
        if (module in listOf("Administratif", "Finance", "Projets", "Écriture", "Création")) {
            try { entries = withContext(Dispatchers.IO) { workspace.entries(module) } }
            catch (_: Exception) { message = "Lecture locale impossible. Les données sont conservées." }
        }
    }
    fun save(entry: WorkspaceEntry) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { workspace.save(entry) }
                revision++; message = "Enregistré sur cet appareil."
            } catch (_: Exception) { message = "Échec de l’enregistrement local. Réessayez." }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Ressource locale"
                        workspace.save(WorkspaceEntry(module = module, title = name.take(160), uri = uri.toString(), body = "Accès local choisi par l’utilisateur"))
                    }
                    revision++; message = "Référence du document conservée. Le fichier n’a pas été copié."
                } catch (_: Exception) { message = "Import impossible : le fournisseur doit autoriser un accès durable en lecture." }
                finally { busy = false }
            }
        }
    }
    Pane(module, Modifier.testTag("module-screen")) {
        Text("Services JARVIS : Non connecté", style = MaterialTheme.typography.labelMedium)
        when (module) {
            "Système", "Technique" -> Diagnostics(module, workspace)
            "Sécurité" -> {
                Text("Stockage privé Android · sauvegarde distante désactivée · aucune permission Internet.")
                Text("La base locale n’utilise pas de chiffrement applicatif supplémentaire. La protection du stockage dépend d’Android et du verrouillage de l’appareil.")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(secure, setSecure, Modifier.testTag("privacy-switch"))
                    Text("Masquer les captures et aperçus", Modifier.weight(1f).padding(start = 8.dp))
                }
                Text("L’activation bloque les captures de cette application. Elle n’active aucun service de surveillance.")
                Button(onClick = {
                    try { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                    catch (_: Exception) { message = "Paramètres Android indisponibles." }
                }) { Text("Permissions dans Android") }
            }
            else -> {
                if (module == "Finance") {
                    val preferences = context.getSharedPreferences("ui01b-finance", Context.MODE_PRIVATE)
                    val month = YearMonth.now().toString()
                    val (budget, budgetField) = draft("budget-$month", "Budget mensuel en EUR · $month")
                    budgetField()
                    var budgetCents by remember(month) { mutableLongStateOf(preferences.getLong(month, 0L)) }
                    Button(onClick = {
                        val parsed = parseEuroCents(budget)
                        if (parsed == null) message = "Montant positif, deux décimales maximum."
                        else { budgetCents = parsed; preferences.edit().putLong(month, parsed).apply(); message = "Budget enregistré." }
                    }, modifier = Modifier.testTag("save-budget")) { Text("Enregistrer le budget") }
                    val totals = entries.filter { !it.done && it.due.startsWith(month) }.map { BigDecimal.valueOf(it.amountCents) }
                    val income = totals.filter { it.signum() > 0 }.fold(BigDecimal.ZERO, BigDecimal::add)
                    val expenses = totals.filter { it.signum() < 0 }.fold(BigDecimal.ZERO, BigDecimal::add).abs()
                    Text("Mois $month · saisie manuelle\nRevenus : ${income.movePointLeft(2).toPlainString()} €\nDépenses : ${expenses.movePointLeft(2).toPlainString()} €\nBudget : ${euros(budgetCents)}\nBudget restant : ${BigDecimal.valueOf(budgetCents).subtract(expenses).movePointLeft(2).toPlainString()} €", Modifier.testTag("finance-summary"))
                }
                val (title, titleField) = draft("$module-title", when (module) {
                    "Administratif" -> "Tâche ou document"
                    "Finance" -> "Libellé du mouvement"
                    "Projets" -> "Nom du projet"
                    "Écriture" -> "Titre de la note ou de l’œuvre"
                    else -> "Nom du travail créatif"
                })
                titleField()
                val (body, bodyField) = draft("$module-body", if (module == "Écriture") "Texte · brouillon conservé automatiquement" else "Description · brouillon conservé", true)
                if (module != "Finance") bodyField()
                val (due, dueField) = draft("$module-date", "Échéance facultative · AAAA-MM-JJ")
                if (module in listOf("Administratif", "Projets")) dueField()
                val (amount, amountField) = draft("Finance-amount", "Montant en EUR")
                var income by remember { mutableStateOf(false) }
                if (module == "Finance") {
                    amountField()
                    Row {
                        FilterChip(!income, { income = false }, label = { Text("Dépense") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(income, { income = true }, label = { Text("Revenu") })
                    }
                }
                Button(enabled = !busy, onClick = {
                    val cents = if (module == "Finance") parseEuroCents(amount) else 0L
                    if (title.isBlank()) message = "Un titre est nécessaire."
                    else if (module in listOf("Administratif", "Projets") && !validDueDate(due)) message = "Échéance invalide : utilisez AAAA-MM-JJ."
                    else if (cents == null) message = "Montant positif, deux décimales maximum."
                    else save(WorkspaceEntry(module = module, title = title, body = if (module == "Finance") "Saisie manuelle EUR" else body,
                        due = if (module == "Finance") java.time.LocalDate.now().toString() else if (module in listOf("Administratif", "Projets")) due else "",
                        amountCents = if (income) cents else -cents))
                }, modifier = Modifier.testTag("save-entry")) { Text(if (busy) "Enregistrement…" else "Ajouter localement") }
                if (module in listOf("Administratif", "Création")) OutlinedButton(enabled = !busy, onClick = { import.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("import-document")) { Text("Référencer un fichier local") }
                if (entries.isEmpty()) Text("Aucun enregistrement. Ajoutez vos propres données.")
                entries.forEach { entry ->
                    HorizontalDivider()
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("entry-${entry.id}"))
                    if (entry.body.isNotEmpty()) Text(if (entry.body.length > 1200) entry.body.take(1200) + "\n[Aperçu — ouvrir l’éditeur pour le texte complet]" else entry.body)
                    if (entry.due.isNotBlank()) Text("${if (module == "Finance") "Date" else "Échéance"} : ${entry.due}")
                    if (module == "Finance") Text("${euros(entry.amountCents)} · ${if (entry.done) "Exclu des totaux" else "Inclus dans les totaux"}")
                    if (module == "Projets") {
                        var progress by remember(entry.id, entry.progress) { mutableFloatStateOf(entry.progress.toFloat()) }
                        Text("Progression : ${progress.toInt()} %")
                        Slider(progress, { progress = it }, valueRange = 0f..100f, steps = 99,
                            onValueChangeFinished = { save(entry.copy(progress = progress.toInt())) }, modifier = Modifier.testTag("project-progress"))
                    }
                    if (module == "Écriture") {
                        var edit by remember(entry.id) { mutableStateOf(false) }
                        if (edit) {
                            val prefs = context.getSharedPreferences("ui01b-drafts", Context.MODE_PRIVATE)
                            var edited by remember(entry.id) { mutableStateOf(prefs.getString("edit-${entry.id}", entry.body) ?: entry.body) }
                            OutlinedTextField(edited, { edited = it.take(100_000); prefs.edit().putString("edit-${entry.id}", edited).apply() }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Texte modifié") })
                            Button(onClick = { save(entry.copy(body = edited)) }, enabled = !busy) { Text("Enregistrer la modification") }
                        }
                        TextButton(onClick = { edit = !edit }) { Text(if (edit) "Fermer l’éditeur" else "Modifier le texte") }
                    }
                    if (entry.uri.isNotEmpty()) TextButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(entry.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (_: Exception) { message = "Fichier inaccessible ou aucune application de lecture disponible." }
                    }) { Text("Ouvrir le fichier") }
                    if (module != "Écriture") TextButton(enabled = !busy, onClick = { save(entry.copy(done = !entry.done)) }) {
                        Text(if (module == "Finance") { if (entry.done) "Réinclure dans les totaux" else "Exclure des totaux" }
                        else if (entry.done) "Rouvrir" else "Marquer terminé")
                    }
                    if (entry.done && module != "Finance") Text("Terminé")
                }
            }
        }
        if (message.isNotEmpty()) Text(message, modifier = Modifier.testTag("workspace-message"))
    }
}

@Composable
private fun Diagnostics(module: String, workspace: LocalWorkspace) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    fun refresh() { scope.launch {
        try { report = withContext(Dispatchers.IO) { diagnosticReport(context, workspace, module == "Technique") } }
        catch (_: Exception) { message = "Diagnostic local indisponible." }
    } }
    LaunchedEffect(module) { refresh() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) } ?: error("No output") }
                message = "Diagnostic exporté dans le fichier choisi."
            } catch (_: Exception) { message = "Échec de l’export." }
        }
    }
    Text(report.ifEmpty { "Lecture des sources Android locales…" }, modifier = Modifier.testTag("diagnostics"))
    Button(onClick = { refresh() }) { Text("Actualiser le diagnostic") }
    OutlinedButton(enabled = report.isNotEmpty(), onClick = { export.launch("jarvis-diagnostic.txt") }) { Text("Exporter sans contenu personnel") }
    if (message.isNotEmpty()) Text(message)
}

fun diagnosticReport(context: Context, workspace: LocalWorkspace, includeEvents: Boolean): String {
    val memory = ActivityManager.MemoryInfo()
    context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
    val process = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
    val disk = StatFs(context.filesDir.path)
    return buildString {
        appendLine("JARVIS INTERFACE · diagnostic local · ${java.time.Instant.now()}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT} (Build)")
        appendLine("PSS de ce processus : ${process.totalPss} Ko (Debug.MemoryInfo)")
        appendLine("RAM disponible système : ${memory.availMem / 1048576} Mio (ActivityManager)")
        appendLine("Stockage disponible : ${disk.availableBytes / 1048576} Mio (StatFs)")
        appendLine("Économie Android : ${context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode}")
        appendLine("Aucune mesure thermique ou de consommation batterie réalisée.")
        appendLine("CORE / LLM / VOICE : Non connecté")
        if (includeEvents) { appendLine("Journal local (100 événements maximum, sans contenu des enregistrements) :"); workspace.events().forEach { appendLine(it) } }
    }
}
