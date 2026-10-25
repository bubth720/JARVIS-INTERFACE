package com.jarvis.interfaceapp.avatar.athena

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.JarvisApplication
import com.jarvis.interfaceapp.avatar.AvatarDemo
import com.jarvis.interfaceapp.avatar.v2.*
import kotlinx.coroutines.launch

/** The primary avatar boundary: no procedural demo while loading or without art. */
@Composable
fun AthenaAvatar(modifier:Modifier=Modifier,portraitHeight:Dp=160.dp,studio:Boolean=false,resources:AthenaResources?=null) {
    val context=LocalContext.current
    val repository=resources ?: (context.applicationContext as JarvisApplication).athena
    val scope=rememberCoroutineScope()
    val motionEnabled=com.jarvis.interfaceapp.avatar.LocalAvatarMotionEnabled.current
    var loaded by remember {mutableStateOf<LoadedPremiumPack?>(null)}
    var loading by remember {mutableStateOf(true)}
    var issue by remember {mutableStateOf<String?>(null)}
    var technical by remember {mutableStateOf(false)}
    LaunchedEffect(repository) { loaded=repository.load();loading=false }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            loading=true;issue=null
            try {loaded=repository.importPack(requireNotNull(context.contentResolver.openInputStream(uri)));technical=false}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){issue="Pack refusé. Le portrait précédent est conservé."}
            finally {loading=false}
        }
    }
    Column(modifier.semantics {testTagsAsResourceId=true},verticalArrangement=Arrangement.spacedBy(4.dp)) {
        val pack=loaded
        if(pack!=null) {
            val renderer=remember(pack) {PremiumAvatarArtwork(pack)}
            Column(Modifier.testTag("athena-ready").semantics {stateDescription=pack.manifest.id}) {
                AvatarDemo(artwork=renderer,portraitHeight=portraitHeight,artworkLabel="Athéna · portrait premium",modifier=Modifier.fillMaxWidth())
            }
            if(studio) {
                Text("Athéna · aperçu premium privé",style=MaterialTheme.typography.titleMedium)
                Text("Portraits de haute qualité. Le rig articulé et le lip-sync restent un chantier distinct.",style=MaterialTheme.typography.bodySmall)
                FlowRow {
                    pack.manifest.frames.forEach {frame->TextButton(onClick={renderer.dispatch(ExpressionEvent.Select(frame.key));if(!motionEnabled)repeat(3){renderer.advanceBy(100.0)}}) {
                        Text(frame.key.expression.name,style=MaterialTheme.typography.labelSmall)
                    } }
                }
            }
        } else if(loading) {
            CircularProgressIndicator(Modifier.testTag("athena-loading"))
        } else {
            Text("Athéna : ressource premium à charger",Modifier.testTag("athena-unavailable"))
            Text("Le portrait privé n’est pas inclus dans cette compilation.",style=MaterialTheme.typography.bodySmall)
        }
        issue?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(studio || loaded==null) OutlinedButton(enabled=!loading,onClick={importer.launch(arrayOf("application/zip","application/x-zip-compressed"))},modifier=Modifier.testTag("athena-import")) {Text("Importer un portrait Athéna V2")}
        if(studio && loaded==null) TextButton(onClick={technical=!technical},modifier=Modifier.testTag("athena-technical-fallback")) {Text("Afficher le secours technique")}
        if(technical && loaded==null) AvatarDemo()
    }
}
