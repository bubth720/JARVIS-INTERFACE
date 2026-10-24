package com.jarvis.interfaceapp.avatar.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.interfaceapp.avatar.AvatarPose
import com.jarvis.interfaceapp.avatar.v2.*

/** Review only, uses already decoded session textures. No copy/export, approval or activation. */
@Composable
fun AthenaGalleryScreen(pack: LoadedPremiumPack, onClose: () -> Unit) {
    Surface(Modifier.fillMaxWidth().heightIn(max=700.dp), color=MaterialTheme.colorScheme.background,
        contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().padding(16.dp)
        .verticalScroll(rememberScrollState()).testTag("athena-review-gallery"),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Athéna · galerie de revue privée",style=MaterialTheme.typography.titleLarge)
        Text("ART_REVIEW_PENDING · ni publication ni activation définitive autorisées.",Modifier.testTag("athena-review-status"))
        Text("${pack.manifest.id} · ${pack.manifest.frames.size} correspondances")
        Text(if(pack.manifest.frames.all { it.mode==RenderMode.IMAGE_REPLACEMENT })
            "Portraits pleine pose : remplacement d’image. Ce pack ne fournit pas encore de calques articulés yeux/bouche."
            else "Calques selon le manifeste fourni ; vérifier leur alignement et les expressions avant approbation.")
        BoxWithConstraints {
            val columns=if(maxWidth>=600.dp) 3 else 2
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                for(row in pack.manifest.frames.chunked(columns)) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(frame in row) key(frame.key) {
                        val art=remember(pack,frame.key) { PremiumAvatarArtwork(pack).also {
                            it.dispatch(ExpressionEvent.Select(frame.key));repeat(3) { _ -> it.advanceBy(100.0) }
                        } }
                        DisposableEffect(art) { onDispose { art.release() } }
                        Surface(Modifier.weight(1f),shape=MaterialTheme.shapes.medium,color=Color(0xFF122337),contentColor=MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.padding(6.dp)) {
                                Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("athena-review-${frame.key.state}-${frame.key.expression}-${frame.key.perspective}")
                                    .semantics { contentDescription="${frame.key.state} ${frame.key.expression} ${frame.key.perspective}" }) {
                                    val factor=minOf(size.width/240f,size.height/320f)
                                    withTransform({ translate((size.width-240f*factor)/2,(size.height-320f*factor)/2);scale(factor,factor,Offset.Zero) }) {
                                        clipRect(0f,0f,240f,320f) { with(art) { render(AvatarPose()) } }
                                    }
                                }
                                Text("${frame.key.state} · ${frame.key.expression}",style=MaterialTheme.typography.labelSmall)
                                Text(frame.key.perspective.name,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text("La confirmation temporaire du sélecteur V2 ne vaut pas accord de diffusion. Les dispositions du dashboard restent indépendantes.")
        Button(onClick=onClose,modifier=Modifier.testTag("athena-review-close")) { Text("Retour à la prévisualisation") }
    }
}
}
