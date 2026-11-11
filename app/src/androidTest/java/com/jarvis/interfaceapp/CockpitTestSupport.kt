package com.jarvis.interfaceapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Bring both scroll viewports into view before targeting a child of the knowledge card. */
internal fun ComposeTestRule.reachCockpit(tag: String): SemanticsNodeInteraction {
    fun bring(node: SemanticsNodeInteraction) {
        try { node.performScrollTo() }
        catch (failure: AssertionError) { if (!node.isDisplayed()) throw failure }
    }
    if (tag in setOf("v2-demo-close", "v2-source-status")) bring(onNodeWithTag("v2-knowledge-card"))
    val node = onNodeWithTag(tag)
    bring(node)
    try { return node.assertIsDisplayed() }
    catch (failure: AssertionError) { throw AssertionError("Control not visible after scrolling: $tag", failure) }
}
