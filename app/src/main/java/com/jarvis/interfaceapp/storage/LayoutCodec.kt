package com.jarvis.interfaceapp.storage

import com.jarvis.interfaceapp.domain.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Immutable document; unknown fields are retained, never interpreted as UI state. */
data class LayoutDocument(
    val root: JsonObject?,
    val layouts: Layouts,
    val issue: LayoutReadIssue? = null,
    val fresh: Boolean = false,
)

object LayoutCodec {
    const val VERSION = 1
    const val MAX_BYTES = 65_536
    private val json = Json { isLenient = false }
    private val fields = setOf("id", "x", "y", "width", "height", "locked")
    fun defaults(fresh: Boolean = false): LayoutDocument =
        LayoutDocument(null, defaultLayouts(), fresh = fresh).let { withLayouts(it, it.layouts).copy(fresh = fresh) }

    fun decode(bytes: ByteArray): LayoutDocument {
        if (bytes.size > MAX_BYTES) return invalid(LayoutReadIssue.CORRUPT)
        return try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            checkDepth(text)
            val root = json.parseToJsonElement(text) as? JsonObject ?: error("Object required")
            rejectDuplicateKeys(text)
            val version = integer(root.getValue("version"))
            if (version != VERSION) return invalid(LayoutReadIssue.INCOMPATIBLE, root)
            val profiles = root.getValue("profiles") as? JsonObject ?: error("Profiles required")
            if (profiles.keys != DashboardProfile.entries.map { it.name.lowercase() }.toSet()) {
                return invalid(LayoutReadIssue.INCOMPATIBLE, root)
            }
            val layouts = DashboardProfile.entries.associateWith { profile ->
                val records = profiles.getValue(profile.name.lowercase()) as? JsonArray ?: error("Array required")
                val ids = records.map { record -> string((record as JsonObject).getValue("id")) }
                if (ids.toSet().size != ids.size) error("Duplicate panel")
                if (ids.toSet() != PanelId.entries.map { it.name.lowercase() }.toSet()) {
                    return invalid(LayoutReadIssue.INCOMPATIBLE, root)
                }
                val panels = records.map { record ->
                    val obj = record.jsonObject
                    require(obj.keys.containsAll(fields))
                    val id = PanelId.entries.single { it.name.lowercase() == string(obj.getValue("id")) }
                    val locked = obj.getValue("locked").jsonPrimitive
                    require(!locked.isString)
                    DashboardPanel(id, integer(obj.getValue("x")), integer(obj.getValue("y")),
                        integer(obj.getValue("width")), integer(obj.getValue("height")),
                        locked = locked.booleanOrNull ?: error("Boolean required"))
                }
                require(panels.all { placementError(profile.grid, panels, it) == null })
                PanelId.entries.map { id -> panels.single { it.id == id } }
            }
            LayoutDocument(root, layouts)
        } catch (_: Exception) {
            invalid(LayoutReadIssue.CORRUPT)
        }
    }

    fun encode(document: LayoutDocument): ByteArray {
        require(document.issue == null && document.root != null)
        return document.root.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }

    /** Merge only geometry/locks; retain unrecognised fields on supported documents. */
    fun withLayouts(document: LayoutDocument, layouts: Layouts): LayoutDocument {
        require(document.issue == null)
        require(layouts.keys == DashboardProfile.entries.toSet())
        val oldProfiles = document.root?.get("profiles") as? JsonObject
        val profiles = DashboardProfile.entries.associate { profile ->
            val panels = layouts.getValue(profile)
            require(panels.size == PanelId.entries.size && panels.map { it.id }.toSet() == PanelId.entries.toSet())
            require(panels.all { placementError(profile.grid, panels, it) == null })
            val old = (oldProfiles?.get(profile.name.lowercase()) as? JsonArray).orEmpty()
            val records = PanelId.entries.map { id ->
                val panel = panels.single { it.id == id }
                val previous = old.mapNotNull { it as? JsonObject }.find {
                    (it["id"] as? JsonPrimitive)?.content == id.name.lowercase()
                }.orEmpty()
                JsonObject(previous + mapOf(
                    "id" to JsonPrimitive(id.name.lowercase()), "x" to JsonPrimitive(panel.x),
                    "y" to JsonPrimitive(panel.y), "width" to JsonPrimitive(panel.width),
                    "height" to JsonPrimitive(panel.height), "locked" to JsonPrimitive(panel.locked),
                ))
            }
            profile.name.lowercase() to JsonArray(records)
        }
        val root = JsonObject(document.root.orEmpty() + mapOf("version" to JsonPrimitive(VERSION), "profiles" to JsonObject(profiles)))
        val result = LayoutDocument(root, layouts)
        require(encode(result).size <= MAX_BYTES)
        return result
    }

    private fun invalid(issue: LayoutReadIssue, root: JsonObject? = null) = LayoutDocument(root, defaultLayouts(), issue)
    private fun integer(value: JsonElement): Int {
        val p = value.jsonPrimitive
        require(!p.isString && Regex("-?(0|[1-9][0-9]*)").matches(p.content))
        return p.intOrNull ?: error("Integer overflow")
    }
    private fun string(value: JsonElement): String = value.jsonPrimitive.also { require(it.isString) }.content

    // Bound nesting before parsing, including unknown extension data, to avoid stack exhaustion.
    private fun checkDepth(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) }
                '}', ']' -> depth--
            }
        }
    }
    // JSON has already parsed successfully. Reject ambiguous duplicate members, including escaped keys.
    private fun rejectDuplicateKeys(text: String) {
        val objects = ArrayDeque<MutableSet<String>>()
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> objects.addLast(mutableSetOf())
                '}' -> objects.removeLast()
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') { if (text[i] == '\\') i++; i++ }
                    val end = ++i
                    while (i < text.length && text[i].isWhitespace()) i++
                    if (i < text.length && text[i] == ':') {
                        val key = json.parseToJsonElement(text.substring(start, end)).jsonPrimitive.content
                        require(objects.last().add(key))
                    }
                    continue
                }
            }
            i++
        }
    }
}
