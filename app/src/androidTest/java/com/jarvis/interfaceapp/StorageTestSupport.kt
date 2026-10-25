package com.jarvis.interfaceapp

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jarvis.interfaceapp.domain.defaultLayouts
import com.jarvis.interfaceapp.ui.SavePhase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Isolate each test through the same durable reset transaction as production. */
internal fun AndroidComposeTestRule<*, MainActivity>.resetLayoutFixture() {
    val controller = (activity.application as JarvisApplication).dashboard
    runBlocking {
        withTimeout(30_000) {
            controller.reset()
            controller.startSession()
            controller.awaitIdle()
        }
    }
    check(controller.state.value.home.layouts == defaultLayouts())
    check(controller.state.value.storage.phase == SavePhase.SAVED)
    val pack=runBlocking {withTimeout(30_000) {(activity.application as JarvisApplication).athena.load()}}
    if(pack!=null)waitUntil(30_000) {onAllNodesWithTag("athena-ready").fetchSemanticsNodes().isNotEmpty()}
    waitForIdle()
}
