package com.jarvis.interfaceapp

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jarvis.interfaceapp.cockpit.LocalWorkspace
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DocumentPickerContractTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun tap(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) { if (current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true; current = current?.parent }
        return false
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun actualAndroidDocumentPickerImportsLocalFileWithoutDeletingOriginal() {
        val context = ui.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = context.contentResolver
        val name = "ui01c-picker-${java.util.UUID.randomUUID()}.txt"
        val original = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        })!!
        resolver.openOutputStream(original)!!.bufferedWriter().use { it.write("TEST LOCAL DOCUMENT") }
        var record: String? = null
        var granted: android.net.Uri? = null
        try {
            ui.onNodeWithTag("logo-home").performClick()
            ui.onNodeWithTag("module-1").performScrollTo().performClick()
            ui.onNodeWithTag("import-document").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            val deadline = android.os.SystemClock.uptimeMillis() + 20000
            var tapped = false
            var openedRoots = false
            var choseDownloads = false
            while (android.os.SystemClock.uptimeMillis() < deadline && !tapped) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val found = find(root, name)
                if (found != null) tapped = tap(found)
                else {
                    // Recent normally includes the newly-created local Downloads row.
                    if (!choseDownloads) find(root, "Downloads")?.let { choseDownloads = tap(it) }
                    if (!choseDownloads && !openedRoots) find(root, "Show roots")?.let { openedRoots = tap(it) }
                }
                if (!tapped) Thread.sleep(200)
            }
            assertTrue("Local Downloads fixture not selectable in system document picker", tapped)
            ui.waitUntil(20000) { LocalWorkspace(context).use { db -> db.entries("Administratif").any { it.title == name } } }
            val entry = LocalWorkspace(context).use { it.entries("Administratif").single { it.title == name } }
            record = entry.id; granted = android.net.Uri.parse(entry.uri)
            assertTrue(resolver.persistedUriPermissions.any { it.uri == granted && it.isReadPermission })
            assertEquals("TEST LOCAL DOCUMENT", resolver.openInputStream(granted!!)!!.bufferedReader().use { it.readText() })
            ui.onNodeWithTag("delete-${entry.id}").performScrollTo().performClick()
            ui.onNodeWithTag("delete-confirm").performClick()
            ui.waitUntil(15000) { LocalWorkspace(context).use { it.entries("Administratif").none { e -> e.id == entry.id } } }
            assertEquals("TEST LOCAL DOCUMENT", resolver.openInputStream(original)!!.bufferedReader().use { it.readText() })
        } finally {
            record?.let { id -> LocalWorkspace(context).use { db -> if (db.entries("Administratif").any { it.id == id }) db.delete(id) } }
            granted?.let { try { resolver.releasePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
            resolver.delete(original, null, null)
        }
    }
}
