package com.jarvis.interfaceapp.avatar.athena

import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.jarvis.interfaceapp.MainActivity
import com.jarvis.interfaceapp.JarvisApplication
import com.jarvis.interfaceapp.resetLayoutFixture
import com.jarvis.interfaceapp.workspace.Workspace
import com.jarvis.interfaceapp.avatar.v2.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@OptIn(ExperimentalTestApi::class)
class AthenaPrimaryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun setup() {
        compose.resetLayoutFixture()
        val app=compose.activity.application as JarvisApplication
        app.workspace.change {Workspace()};runBlocking {withTimeout(30000){app.workspace.awaitIdle()}}
    }
    private fun ready() {compose.waitUntil(30000) {compose.onAllNodesWithTag("athena-ready").fetchSemanticsNodes().isNotEmpty()}}
    private fun capture(name:String) {
        if(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("privateAthenaCapture")!="true")return
        require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir,"athena-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun premiumIsPrimaryInDashboardAndStudioWithoutManualImport() {
        ready()
        compose.onNodeWithTag("avatar-renderer").assertContentDescriptionEquals("Athéna · portrait premium")
        val p=runBlocking {(compose.activity.application as JarvisApplication).athena.load()!!}
        assertEquals(ArtGrade.PREMIUM_CANDIDATE,p.manifest.artGrade)
        assertNotEquals("jarvis.demo.adult-vector.v1",p.manifest.id)
        compose.onNodeWithTag("athena-technical-fallback").assertDoesNotExist()
        capture("dashboard")
        compose.onNodeWithTag("open-studio").performScrollTo().performClick()
        compose.onNodeWithTag("studio-tab-Avatar").performScrollTo().performClick();ready()
        compose.onNodeWithTag("avatar-renderer").performScrollTo().assertContentDescriptionEquals("Athéna · portrait premium")
        capture("studio")
        if(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("privateAthenaCapture")=="true") {
            val b=compose.onNodeWithTag("avatar-renderer").captureToImage().asAndroidBitmap()
            File(compose.activity.filesDir,"athena-portrait.png").outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun recreationReloadsPremiumAsPrimary() {
        ready();compose.activityRule.scenario.recreate();ready()
        compose.onNodeWithTag("avatar-renderer").assertContentDescriptionEquals("Athéna · portrait premium")
    }
    @Test fun installedPackSurvivesRepositoryRecreationAndRejectsDemo()=runBlocking {
        val context=object:ContextWrapper(compose.activity) {override fun getFilesDir()=File(super.getFilesDir(),"athena-test-isolated").apply {mkdirs()}}
        try {
            val r=AthenaResources(context)
            val first=r.importPack(context.assets.open("athena-private/athena-review.zip"))
            assertEquals(first.manifest.id,AthenaResources(context).load()!!.manifest.id)
            val manifest=context.assets.open("avatar-v2-demo/manifest.json").use {it.readBytes()}
            val m=PremiumManifestCodec.decode(manifest);val out=java.io.ByteArrayOutputStream()
            ZipOutputStream(out).use {zip->
                zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest);zip.closeEntry()
                for(resource in m.resources) {zip.putNextEntry(ZipEntry(resource.file));context.assets.open("avatar-demo/${resource.file}").use {it.copyTo(zip)};zip.closeEntry()}
            }
            try {r.importPack(out.toByteArray().inputStream());fail("Demo accepted as primary")}catch(_:IllegalArgumentException){}
            assertEquals(first.manifest.id,AthenaResources(context).load()!!.manifest.id)
        } finally {context.filesDir.deleteRecursively()}
    }
}
