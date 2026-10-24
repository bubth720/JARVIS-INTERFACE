package com.jarvis.interfaceapp.avatar.v2

import com.jarvis.interfaceapp.avatar.assets.Pivot
import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class PremiumState { IDLE, LISTENING, THINKING, SPEAKING, WAITING, WORKING, REACTION, ERROR }
enum class PremiumExpression { NEUTRAL, SMILE, SURPRISE, LAUGH, WINK, ATTENTIVE, THOUGHTFUL, CONCERNED, TALKING, WAITING, FOCUSED }
enum class PremiumPerspective { FACE, THREE_QUARTER_LEFT, THREE_QUARTER_RIGHT, LEFT, RIGHT, BACK }
enum class RenderMode { LAYERS, IMAGE_REPLACEMENT }
enum class LayerChannel { STATIC, BODY, BACK_HAIR, HEAD, EYES_OPEN, EYES_CLOSED, MOUTH_NEUTRAL, MOUTH_OPEN }
enum class ArtGrade { DEMONSTRATION, PREMIUM_CANDIDATE }
data class PremiumKey(val state: PremiumState = PremiumState.IDLE,
    val expression: PremiumExpression = PremiumExpression.NEUTRAL,
    val perspective: PremiumPerspective = PremiumPerspective.FACE)
data class PremiumResource(val id: String, val file: String, val sha256: String, val width: Int, val height: Int)
data class PremiumLayer(val id: String, val resource: String, val order: Int, val x: Float, val y: Float,
    val width: Float, val height: Float, val pivot: Pivot, val depth: Float, val channel: LayerChannel)
data class PremiumFrame(val key: PremiumKey, val layers: List<String>, val mode: RenderMode)
data class PremiumAvatarManifest(val id: String, val author: String, val license: String, val artGrade: ArtGrade,
    val resources: List<PremiumResource>, val layers: List<PremiumLayer>, val frames: List<PremiumFrame>) {
    fun resolve(key: PremiumKey): PremiumFrame? = frames.singleOrNull { it.key == key }
    val completeCandidate: Boolean get() = artGrade == ArtGrade.PREMIUM_CANDIDATE &&
        PremiumState.entries.all { state -> frames.any { it.key.state == state } } &&
        frames.map { it.key.expression }.distinct().size >= 8 && frames.size >= 8 &&
        setOf(PremiumPerspective.FACE, PremiumPerspective.THREE_QUARTER_LEFT,
            PremiumPerspective.THREE_QUARTER_RIGHT).all { p -> frames.any { it.key.perspective == p } } &&
        frames.any { f -> f.mode == RenderMode.LAYERS && f.layers.map { id -> layers.single { it.id == id }.channel }
            .containsAll(listOf(LayerChannel.EYES_OPEN, LayerChannel.EYES_CLOSED, LayerChannel.MOUTH_NEUTRAL, LayerChannel.MOUTH_OPEN)) }
}

/** Strict local V2 only. The established V1 codec and resource budgets are unchanged. */
object PremiumManifestCodec {
    private val json = Json { isLenient = false }
    private fun checkThat(ok: Boolean, code: String) = require(ok) { code }
    fun decode(bytes: ByteArray): PremiumAvatarManifest {
        checkThat(bytes.size <= ResourceLimit.MANIFEST_BYTES, "MANIFEST_TOO_LARGE")
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        // Reject duplicate keys (including escaped equivalents) before last-key-wins parsing.
        val objects = ArrayDeque<MutableSet<String>>(); var depth = 0; var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> { checkThat(++depth <= 8, "JSON_DEPTH"); objects.addLast(mutableSetOf()) }
                '[' -> checkThat(++depth <= 8, "JSON_DEPTH")
                '}' -> { checkThat(objects.isNotEmpty(), "INVALID_JSON"); objects.removeLast(); depth-- }
                ']' -> depth--
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') { if (text[i] == '\\') i++; i++ }
                    checkThat(i < text.length, "INVALID_JSON")
                    val end = ++i
                    while (i < text.length && text[i].isWhitespace()) i++
                    if (i < text.length && text[i] == ':') checkThat(objects.isNotEmpty() && objects.last().add(
                        json.parseToJsonElement(text.substring(start, end)).jsonPrimitive.content), "DUPLICATE_KEY")
                    continue
                }
            }; i++
        }
        val root = json.parseToJsonElement(text).jsonObject
        fun JsonObject.fields(vararg names: String) = checkThat(keys == names.toSet(), "UNKNOWN_OR_MISSING_FIELD")
        fun JsonObject.str(k: String): String = getValue(k).jsonPrimitive.also { checkThat(it.isString, "STRING_REQUIRED") }.content
        fun JsonObject.num(k: String): Float = getValue(k).jsonPrimitive.also { checkThat(!it.isString, "NUMBER_REQUIRED") }
            .float.also { checkThat(it.isFinite(), "FINITE_REQUIRED") }
        fun JsonObject.int(k: String): Int = getValue(k).jsonPrimitive.also { checkThat(!it.isString, "INTEGER_REQUIRED") }.intOrNull?.also { checkThat(it >= 0, "INTEGER_REQUIRED") } ?: error("INTEGER_REQUIRED")
        fun token(s: String) = checkThat(s.matches(Regex("[a-zA-Z0-9._-]{1,80}")), "INVALID_ID")
        root.fields("version", "id", "author", "license", "artGrade", "width", "height", "resources", "layers", "frames")
        checkThat(root.int("version") == 2, "UNKNOWN_VERSION")
        checkThat(root.int("width") == 240 && root.int("height") == 320, "CANVAS_SIZE")
        val id = root.str("id").also(::token)
        val author = root.str("author"); val license = root.str("license")
        checkThat(author.isNotBlank() && author.length <= 160 && license.isNotBlank() && license.length <= 240, "PROVENANCE_REQUIRED")
        val grade = ArtGrade.valueOf(root.str("artGrade"))
        val resourceRows = root.getValue("resources").jsonArray
        checkThat(resourceRows.size in 1..ResourceLimit.LAYERS, "RESOURCE_COUNT")
        val resources = resourceRows.map { row ->
            val r = row.jsonObject; r.fields("id", "file", "sha256", "width", "height")
            PremiumResource(r.str("id").also(::token), r.str("file"), r.str("sha256"), r.int("width"), r.int("height")).also {
                checkThat(it.file.matches(Regex("[A-Za-z0-9_-]{1,64}\\.png")), "UNSAFE_PATH")
                checkThat(it.sha256.matches(Regex("[0-9a-f]{64}")), "INVALID_HASH")
                checkThat(it.width in 1..ResourceLimit.TEXTURE_SIDE && it.height in 1..ResourceLimit.TEXTURE_SIDE, "TEXTURE_SIZE")
            }
        }
        checkThat(resources.map { it.id }.distinct().size == resources.size &&
            resources.map { it.file.lowercase() }.distinct().size == resources.size, "DUPLICATE_RESOURCE")
        checkThat(resources.sumOf { it.width.toLong() * it.height } <= ResourceLimit.PIXELS, "PIXEL_BUDGET")
        val layerRows = root.getValue("layers").jsonArray
        checkThat(layerRows.size in 1..ResourceLimit.LAYERS, "LAYER_COUNT")
        val layers = layerRows.map { row ->
            val r = row.jsonObject; r.fields("id", "resource", "order", "x", "y", "width", "height", "pivotX", "pivotY", "depth", "channel")
            PremiumLayer(r.str("id").also(::token), r.str("resource"), r.int("order"), r.num("x"), r.num("y"),
                r.num("width"), r.num("height"), Pivot(r.num("pivotX"), r.num("pivotY")), r.num("depth"), LayerChannel.valueOf(r.str("channel"))).also {
                checkThat(resources.any { a -> a.id == it.resource } && it.order <= 100, "LAYER_RESOURCE")
                checkThat(it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0 &&
                    it.x + it.width <= 240 && it.y + it.height <= 320, "LAYER_BOUNDS")
                checkThat(it.pivot.x in 0f..240f && it.pivot.y in 0f..320f && it.depth in 0f..1f, "PIVOT_OR_DEPTH")
            }
        }.sortedBy { it.order }
        checkThat(layers.map { it.id }.distinct().size == layers.size && layers.map { it.order }.distinct().size == layers.size, "DUPLICATE_LAYER")
        checkThat(layers.map { it.resource }.toSet() == resources.map { it.id }.toSet(), "UNUSED_RESOURCE")
        val frameRows = root.getValue("frames").jsonArray
        checkThat(frameRows.size in 1..64, "FRAME_COUNT")
        val frames = frameRows.map { row ->
            val r = row.jsonObject; r.fields("state", "expression", "perspective", "layers", "mode")
            val ids = r.getValue("layers").jsonArray.map { it.jsonPrimitive.also { v -> checkThat(v.isString, "LAYER_ID") }.content }
            checkThat(ids.isNotEmpty() && ids.distinct().size == ids.size && ids.all { id -> layers.any { it.id == id } }, "FRAME_LAYERS")
            val mode = RenderMode.valueOf(r.str("mode"))
            val channels = ids.map { id -> layers.single { it.id == id }.channel }
            if (mode == RenderMode.IMAGE_REPLACEMENT) checkThat(ids.size == 1 && channels.single() == LayerChannel.STATIC, "REPLACEMENT_MODE")
            for ((a,b) in listOf(LayerChannel.EYES_OPEN to LayerChannel.EYES_CLOSED, LayerChannel.MOUTH_NEUTRAL to LayerChannel.MOUTH_OPEN))
                checkThat((a in channels) == (b in channels), "INCOMPLETE_ARTICULATION")
            val headPivots = ids.map { id -> layers.single { it.id == id } }.filter { it.channel in setOf(
                LayerChannel.HEAD, LayerChannel.EYES_OPEN, LayerChannel.EYES_CLOSED, LayerChannel.MOUTH_NEUTRAL, LayerChannel.MOUTH_OPEN) }.map { it.pivot }.distinct()
            checkThat(headPivots.size <= 1, "MISALIGNED_PIVOTS")
            PremiumFrame(PremiumKey(PremiumState.valueOf(r.str("state")), PremiumExpression.valueOf(r.str("expression")),
                PremiumPerspective.valueOf(r.str("perspective"))), ids, mode)
        }
        checkThat(frames.map { it.key }.distinct().size == frames.size, "STATE_CONFLICT")
        checkThat(frames.any { it.key == PremiumKey() }, "NEUTRAL_REQUIRED")
        checkThat(frames.flatMap { it.layers }.toSet() == layers.map { it.id }.toSet(), "UNUSED_LAYER")
        return PremiumAvatarManifest(id, author, license, grade, resources, layers, frames)
    }
}
