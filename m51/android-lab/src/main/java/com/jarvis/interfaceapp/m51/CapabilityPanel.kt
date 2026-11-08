package com.jarvis.interfaceapp.m51

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CapabilityPanel() {
    val context=LocalContext.current
    val registry=remember { m51Registry(BuildConfig.SOURCE_COMMIT,uiMounted=true) }
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri != null) scope.launch {
            notice=try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(registry.publicSummary()) } ?: error("NO_OUTPUT") }
                "Résumé technique exporté dans le fichier choisi."
            } catch(_: Exception) { "Export refusé ou fichier inaccessible ; aucune donnée métier exportée." }
        }
    }
    Surface(Modifier.fillMaxWidth().testTag("capabilities-screen"),tonalElevation=2.dp) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Modules & état",style=MaterialTheme.typography.titleLarge)
            Text("Laboratoire M51 · aucun moteur externe raccordé. Les états sont des observations et des références, pas des interrupteurs d’activation.")
            OutlinedTextField(query,{ query=it.take(40) },label={Text("Filtrer les modules")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("capability-filter"))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!blockedOnly,{blockedOnly=false},label={Text("Tous")},modifier=Modifier.testTag("capability-all"))
                FilterChip(blockedOnly,{blockedOnly=true},label={Text("Bloqués")},modifier=Modifier.testTag("capability-blocked"))
            }
            registry.filtered(query,blockedOnly).forEach { row ->
                HorizontalDivider()
                Text("${row.moduleId} · ${row.state}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("capability-${row.moduleId}"))
                Text(row.explanation)
                Text("Raccordé : ${if(row.dimensions.connected) "oui, UI locale" else "non"} · Activé : ${if(row.dimensions.enabled) "oui, laboratoire" else "non"} · Appareil qualifié : non")
                TextButton({detail=row.moduleId},Modifier.heightIn(min=48.dp).testTag("capability-detail-${row.moduleId}")) { Text("Preuves et prochaine action") }
            }
            if(registry.filtered(query,blockedOnly).isEmpty()) Text("Aucun module dans cette sélection.")
            OutlinedButton({export.launch("jarvis-m51-capabilities.txt")},Modifier.heightIn(min=48.dp).testTag("capabilities-export")) { Text("Exporter le résumé technique") }
            if(notice.isNotBlank()) Text(notice,Modifier.testTag("capability-notice"))
        }
    }
    val selected=registry.modules.singleOrNull { it.moduleId == detail }
    if(selected != null) AlertDialog(onDismissRequest={detail=null},title={Text(selected.moduleId)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Version : ${selected.version}\nSource : ${selected.sourceSha}\nContrat : ${selected.contractVersion}")
            Text("Dépendances : ${selected.dependencies.joinToString().ifEmpty { "aucune" }}\nAutorité : ${selected.authority}\nProfil : ${selected.resourceProfile}")
            Text("Dernière preuve : ${selected.evidence.reference}\nCommit : ${selected.evidence.testedCommit}")
            Text("Dernier refus : ${selected.lastError ?: "aucun"}\nProchaine action : ${selected.nextAction}")
            if(canOpenEvidence(selected.evidence.reference)) TextButton({
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(selected.evidence.reference))) }
                catch(_: Exception) { notice="Rapport externe inaccessible. Le cockpit reste utilisable hors ligne." }
            },Modifier.testTag("capability-open-evidence")) { Text("Ouvrir la référence GitHub (action externe)") }
        }
    },confirmButton={TextButton({detail=null},Modifier.testTag("capability-detail-close")){Text("Fermer")}})
}
