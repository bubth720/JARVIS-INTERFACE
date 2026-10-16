package com.jarvis.interfaceapp.storage

import com.jarvis.interfaceapp.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

internal fun configuredLayouts(): Layouts = defaultLayouts().mapValues { (profile, panels) ->
    panels.map { panel -> if (panel.id != PanelId.AVATAR) panel else when (profile) {
        DashboardProfile.COMPACT -> panel.copy(y = 1, height = 3, locked = true)
        DashboardProfile.WIDE -> panel.copy(x = 1, y = 1, width = 5)
    } }
}

class LayoutCodecTest {
    private fun root() = LayoutCodec.defaults().root!!
    private fun decode(root: JsonObject) = LayoutCodec.decode(root.toString().toByteArray())
    private fun withPanel(change: (JsonObject) -> JsonObject): JsonObject {
        val r = root(); val profiles = r.getValue("profiles").jsonObject
        val compact = profiles.getValue("compact").jsonArray.toMutableList()
        compact[0] = change(compact[0].jsonObject)
        return JsonObject(r + ("profiles" to JsonObject(profiles + ("compact" to JsonArray(compact)))))
    }
    @Test fun completeSnapshotRoundTripsBothProfilesPositionsSizesAndLocks() {
        val configured = LayoutCodec.withLayouts(LayoutCodec.defaults(), configuredLayouts())
        val loaded = LayoutCodec.decode(LayoutCodec.encode(configured))
        assertNull(loaded.issue)
        assertEquals(configuredLayouts(), loaded.layouts)
    }
    @Test fun onlyPersistentFieldsAreGenerated() {
        val r = root()
        assertEquals(setOf("version", "profiles"), r.keys)
        assertEquals(1, r.getValue("version").jsonPrimitive.int)
        for ((_, records) in r.getValue("profiles").jsonObject) {
            for (record in records.jsonArray) assertEquals(setOf("id", "x", "y", "width", "height", "locked"), record.jsonObject.keys)
        }
    }
    @Test fun malformedEmptyAndNonObjectDocumentsAreProtected() {
        for (bytes in listOf(byteArrayOf(), "{".toByteArray(), "[]".toByteArray(), byteArrayOf(0xC3.toByte(), 0x28))) {
            val doc = LayoutCodec.decode(bytes)
            assertEquals(LayoutReadIssue.CORRUPT, doc.issue)
            assertEquals(defaultLayouts(), doc.layouts)
        }
    }
    @Test fun unknownVersionIsNotMigratedOrSilentlyOverwritten() {
        val doc = decode(JsonObject(root() + ("version" to JsonPrimitive(999))))
        assertEquals(LayoutReadIssue.INCOMPATIBLE, doc.issue)
        assertEquals(defaultLayouts(), doc.layouts)
    }
    @Test fun unknownPanelAndMissingPanelAreProtectedForFutureMigrations() {
        assertEquals(LayoutReadIssue.INCOMPATIBLE, decode(withPanel { JsonObject(it + ("id" to JsonPrimitive("future-widget"))) }).issue)
        val r = root(); val profiles = r.getValue("profiles").jsonObject
        val compact = profiles.getValue("compact").jsonArray.dropLast(1)
        assertEquals(LayoutReadIssue.INCOMPATIBLE,
            decode(JsonObject(r + ("profiles" to JsonObject(profiles + ("compact" to JsonArray(compact)))))).issue)
    }
    @Test fun duplicatePanelIdentifiersAreRefused() {
        assertEquals(LayoutReadIssue.CORRUPT, decode(withPanel { JsonObject(it + ("id" to JsonPrimitive("system"))) }).issue)
    }
    @Test fun missingOrUnknownProfileIsNotDiscarded() {
        val r = root(); val profiles = r.getValue("profiles").jsonObject
        assertEquals(LayoutReadIssue.INCOMPATIBLE, decode(JsonObject(r + ("profiles" to JsonObject(profiles - "wide")))).issue)
        assertEquals(LayoutReadIssue.INCOMPATIBLE, decode(JsonObject(r + ("profiles" to JsonObject(profiles + ("future" to JsonArray(emptyList())))))).issue)
    }
    @Test fun coordinatesAndDimensionsCannotViolateCurrentRules() {
        for ((key, value) in listOf("x" to -1, "y" to 12, "width" to 2, "height" to 2, "height" to 13, "x" to Int.MAX_VALUE)) {
            assertEquals("$key=$value", LayoutReadIssue.CORRUPT,
                decode(withPanel { JsonObject(it + (key to JsonPrimitive(value))) }).issue)
        }
    }
    @Test fun overlappingPanelsAreRefusedEvenIfLocked() {
        assertEquals(LayoutReadIssue.CORRUPT, decode(withPanel {
            JsonObject(it + mapOf("x" to JsonPrimitive(3), "locked" to JsonPrimitive(true)))
        }).issue)
    }
    @Test fun stringsFractionsOverflowAndNullAreNotCoordinates() {
        for (value in listOf(JsonPrimitive("1"), JsonPrimitive(1.5), JsonPrimitive(9_999_999_999L), JsonNull)) {
            assertEquals(LayoutReadIssue.CORRUPT, decode(withPanel { JsonObject(it + ("x" to value)) }).issue)
        }
    }
    @Test fun lockingRequiresActualJsonBoolean() {
        for (value in listOf(JsonPrimitive("true"), JsonPrimitive(1), JsonNull)) {
            assertEquals(LayoutReadIssue.CORRUPT, decode(withPanel { JsonObject(it + ("locked" to value)) }).issue)
        }
    }
    @Test fun missingGeometryAndBadVersionTypesAreRefused() {
        assertEquals(LayoutReadIssue.CORRUPT, decode(withPanel { JsonObject(it - "height") }).issue)
        assertEquals(LayoutReadIssue.CORRUPT, decode(JsonObject(root() + ("version" to JsonPrimitive("1")))).issue)
    }
    @Test fun unknownMetadataIsPreservedDuringSaveAndDefaultReset() {
        val withExtension = withPanel { JsonObject(it + ("futureStyle" to JsonPrimitive("cyan"))) }
        val doc = decode(JsonObject(withExtension + ("extensions" to buildJsonObject { put("future", true) })))
        assertNull(doc.issue)
        for (layouts in listOf(configuredLayouts(), defaultLayouts())) {
            val saved = LayoutCodec.withLayouts(doc, layouts).root!!
            assertEquals(doc.root!!["extensions"], saved["extensions"])
            assertEquals("cyan", saved.getValue("profiles").jsonObject.getValue("compact").jsonArray[0].jsonObject.getValue("futureStyle").jsonPrimitive.content)
        }
    }
    @Test fun duplicateJsonMembersIncludingEscapesAreRejected() {
        val text = root().toString()
        for (extra in listOf("\"version\":1,", "\"\\u0076ersion\":1,")) {
            assertEquals(LayoutReadIssue.CORRUPT, LayoutCodec.decode(("{" + extra + text.drop(1)).toByteArray()).issue)
        }
    }
    @Test fun oversizedAndExcessivelyNestedDataCannotCrashParser() {
        assertEquals(LayoutReadIssue.CORRUPT, LayoutCodec.decode(ByteArray(LayoutCodec.MAX_BYTES + 1) { 32 }).issue)
        assertEquals(LayoutReadIssue.CORRUPT, LayoutCodec.decode(("[".repeat(1000) + "0" + "]".repeat(1000)).toByteArray()).issue)
    }
    @Test fun invalidSnapshotsCannotBeEncodedAsValidLayouts() {
        val invalid = defaultLayouts().toMutableMap().apply { this[DashboardProfile.COMPACT] = emptyList() }
        assertThrows(IllegalArgumentException::class.java) { LayoutCodec.withLayouts(LayoutCodec.defaults(), invalid) }
        assertThrows(IllegalArgumentException::class.java) { LayoutCodec.encode(LayoutCodec.decode("bad".toByteArray())) }
    }
}
