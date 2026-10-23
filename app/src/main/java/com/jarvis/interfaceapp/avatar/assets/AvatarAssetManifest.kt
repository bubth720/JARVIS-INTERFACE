package com.jarvis.interfaceapp.avatar.assets

import com.jarvis.interfaceapp.avatar.AvatarPose
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Local asset contract, unrelated to frozen inter-module contracts. Coordinates are 240 x 320. */
data class Pivot(val x: Float, val y: Float)
data class LayerDescriptor(
    val id: String, val file: String, val sha256: String, val order: Int,
    val x: Float, val y: Float, val width: Float, val height: Float, val pivot: Pivot,
    val depth: Float, val group: String, val variant: String,
    val mask: List<Float>?,
)
data class AvatarAssetManifest(val version: Int, val id: String, val layers: List<LayerDescriptor>)
object ResourceLimit {
    const val MANIFEST_BYTES = 64 * 1024
    const val FILE_BYTES = 2 * 1024 * 1024
    const val BUNDLE_BYTES = 8 * 1024 * 1024
    const val PIXELS = 2 * 1024 * 1024
    const val TEXTURE_SIDE = 2048
    const val LAYERS = 16
}
object AssetValidation {
    private val json = Json { isLenient = false }
    fun safeFile(name: String): Boolean = name.matches(Regex("[A-Za-z0-9_-]{1,64}\\.png"))
    fun decode(bytes: ByteArray): AvatarAssetManifest {
        require(bytes.size <= ResourceLimit.MANIFEST_BYTES) { "MANIFEST_TOO_LARGE" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth = 0; var quoted = false; var escape = false
        for (c in text) if (quoted) {
            if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') quoted = false
        } else when (c) { '"' -> quoted = true; '{', '[' -> require(++depth <= 8); '}', ']' -> depth-- }
        val root = json.parseToJsonElement(text).jsonObject
        // Refuse duplicate keys, including JSON-escaped spellings, rather than last-value-wins.
        val objects = ArrayDeque<MutableSet<String>>(); var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> objects.addLast(mutableSetOf())
                '}' -> objects.removeLast()
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') { if (text[i] == '\\') i++; i++ }
                    val end = ++i
                    while (i < text.length && text[i].isWhitespace()) i++
                    if (i < text.length && text[i] == ':') require(objects.last().add(
                        json.parseToJsonElement(text.substring(start, end)).jsonPrimitive.content))
                    continue
                }
            }; i++
        }
        fun JsonObject.keysExactly(vararg fields: String) = require(keys == fields.toSet()) { "UNKNOWN_OR_MISSING_FIELD" }
        fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.also { require(it.isString) }.content
        fun JsonObject.number(key: String): Float = getValue(key).jsonPrimitive.also { require(!it.isString) }
            .float.also { require(it.isFinite()) }
        root.keysExactly("version", "id", "layers")
        val version = root.number("version"); require(version == 1f) { "INCOMPATIBLE_VERSION" }
        val id = root.string("id"); require(id.matches(Regex("[a-zA-Z0-9._-]{1,80}")))
        val records = root.getValue("layers").jsonArray
        require(records.size in 1..ResourceLimit.LAYERS)
        val layers = records.map { entry ->
            val r = entry.jsonObject
            r.keysExactly("id", "file", "sha256", "order", "x", "y", "width", "height", "pivotX", "pivotY", "depth", "group", "variant", "mask")
            val order = r.number("order"); require(order % 1f == 0f && order in 0f..100f)
            val layer = LayerDescriptor(r.string("id"), r.string("file"), r.string("sha256"), order.toInt(),
                r.number("x"), r.number("y"), r.number("width"), r.number("height"),
                Pivot(r.number("pivotX"), r.number("pivotY")), r.number("depth"), r.string("group"), r.string("variant"),
                r.getValue("mask").let { v -> if (v == JsonNull) null else v.jsonArray.map { n ->
                    n.jsonPrimitive.also { require(!it.isString) }.float.also { require(it.isFinite()) }
                } })
            require(layer.id.matches(Regex("[a-zA-Z0-9_-]{1,40}")) && safeFile(layer.file))
            require(layer.sha256.matches(Regex("[0-9a-f]{64}")))
            require(layer.x >= 0 && layer.y >= 0 && layer.width > 0 && layer.height > 0 &&
                layer.x + layer.width <= 240 && layer.y + layer.height <= 320)
            require(layer.pivot.x in 0f..240f && layer.pivot.y in 0f..320f && layer.depth in 0f..1f)
            require(layer.group in setOf("body", "hair", "expression") && layer.variant.matches(Regex("[a-zA-Z0-9_-]{1,40}")))
            layer.mask?.let { require(it.size == 4 && it[0] >= 0 && it[1] >= 0 && it[2] > it[0] && it[3] > it[1] && it[2] <= 240 && it[3] <= 320) }
            layer
        }
        require(layers.map { it.id }.distinct().size == layers.size)
        require(layers.map { it.order }.distinct().size == layers.size)
        return AvatarAssetManifest(1, id, layers.sortedBy { it.order })
    }
}
data class PoseTransform(val dy: Float, val rotation: Float) {
    companion object {
        fun forLayer(layer: LayerDescriptor, pose: AvatarPose) = PoseTransform(if (pose.breath == 0f) 0f else -pose.breath * layer.depth * 2f,
            pose.headTilt * layer.depth)
    }
}
