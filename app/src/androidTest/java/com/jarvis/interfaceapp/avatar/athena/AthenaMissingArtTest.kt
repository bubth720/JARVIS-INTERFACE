package com.jarvis.interfaceapp.avatar.athena

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jarvis.interfaceapp.ui.JarvisTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class AthenaMissingArtTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun absentPremiumNeverDisplaysProceduralDemoAsMain() {
        val context=object:ContextWrapper(compose.activity) {override fun getFilesDir()=File(super.getFilesDir(),"athena-empty-test")}
        context.filesDir.deleteRecursively()
        val repo=AthenaResources(context) {throw java.io.FileNotFoundException()}
        compose.setContent {JarvisTheme {AthenaAvatar(resources=repo)}}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("athena-unavailable").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("avatar-renderer").assertDoesNotExist()
        compose.onNodeWithTag("athena-import").assertExists()
        compose.onNodeWithTag("athena-technical-fallback").assertDoesNotExist()
    }
}
