package com.jarvis.interfaceapp.avatar.png

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.jarvis.interfaceapp.avatar.AvatarArtwork
import com.jarvis.interfaceapp.avatar.AvatarPose
import kotlinx.serialization.json.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Distinct from the V2 layered-asset codec. No cloud, IPC, or DataStore mutation. */
data class PngPose(
    val id: String, val file: String, val state: String, val pose: String,
    val label: String, val width: Int, val height: Int, val bytes: Int,
    val sha256: String, val quality: String
)
data class PngPoseManifest(val id: String, val defaultId: String, val frames: List<PngPose>)
data class PngPosePack(val digest: String, val manifest: PngPoseManifest)

object PngPoseCodec {
    private val json = Json { isLenient = false }
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val namePattern = Regex("JARVIS_[0-9]{3}_[A-Z0-9_]{1,55}[.]png")
    private val states = setOf("IDLE", "LISTENING", "THINKING", "EXPLAINING", "SUCCESS",
        "ERROR", "SURPRISED", "HAPPY", "PLAYFUL", "GREETING", "COMPASSION", "CALM",
        "CONFIDENT", "WARM")
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun decode(bytes: ByteArray): PngPoseManifest {
        require(bytes.size in 1..65536) { "MANIFEST_SIZE" }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        val root = json.parseToJsonElement(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()).jsonObject
        require(root.keys == setOf("format", "pack_id", "default_id", "local_only",
            "source_description", "frames")) { "MANIFEST_FIELDS" }
        require(root.getValue("format").jsonPrimitive.content == "jarvis.avatar.png/1") { "FORMAT" }
        require(root.getValue("local_only").jsonPrimitive.boolean) { "LOCAL_ONLY" }
        val id = root.getValue("pack_id").jsonPrimitive.content
        require(id.matches(Regex("[a-z0-9._-]{1,80}"))) { "PACK_ID" }
        val defaultId = root.getValue("default_id").jsonPrimitive.content
        require(root.getValue("source_description").jsonPrimitive.content.length <= 220)
        val rows = root.getValue("frames").jsonArray
        require(rows.size == 36) { "COUNT_36" }
        val frames = rows.map { row ->
            val r = row.jsonObject
            require(r.keys == setOf("id", "file", "state", "pose", "label",
                "width", "height", "bytes", "sha256", "quality")) { "FRAME_FIELDS" }
            fun str(k: String) = r.getValue(k).jsonPrimitive.also { require(it.isString) }.content
            fun num(k: String) = r.getValue(k).jsonPrimitive.also { require(!it.isString) }.int
            val pose = PngPose(str("id"), str("file"), str("state"), str("pose"), str("label"),
                num("width"), num("height"), num("bytes"), str("sha256"), str("quality"))
            require(pose.id.matches(Regex("[0-9]{3}")) && pose.file.matches(namePattern))
            require(pose.file.startsWith("JARVIS_" + pose.id + "_"))
            require(pose.width == 640 && pose.height == 960)
            require(pose.bytes in 1..3_000_000 && pose.sha256.matches(shaPattern))
            require(pose.state in states && pose.pose.matches(Regex("[a-z_]{1,35}")))
            require(pose.label.length in 1..150 && pose.quality in setOf("reviewed", "alpha_cleaned"))
            pose
        }
        require(frames.map { it.id } == (1..36).map { "%03d".format(it) }) { "ORDER_OR_DUPLICATE" }
        require(frames.map { it.file }.distinct().size == 36)
        require(frames.any { it.id == defaultId }) { "DEFAULT_MISSING" }
        return PngPoseManifest(id, defaultId, frames)
    }
}

/** Files in filesDir/avatar-png only. Originals are never copied to a public repository. */
class PngPoseStore(private val context: Context) {
    private val root = File(context.filesDir, "avatar-png")
    private val selectedFile get() = File(root, "selected.txt")
    private fun dir(digest: String): File {
        require(digest.matches(Regex("[0-9a-f]{64}")))
        return File(root, "pack-" + digest)
    }
    private fun readPack(digest: String): PngPosePack? = runCatching {
        val bytes = File(dir(digest), "manifest.json").readBytes()
        require(PngPoseCodec.sha256(bytes) == digest)
        PngPosePack(digest, PngPoseCodec.decode(bytes))
    }.getOrNull()

    fun current(): Pair<PngPosePack, String>? = runCatching {
        val lines = selectedFile.readLines(Charsets.UTF_8)
        require(lines.size == 2)
        val pack = requireNotNull(readPack(lines[0]))
        require(pack.manifest.frames.any { it.id == lines[1] })
        pack to lines[1]
    }.getOrNull()

    fun importArchive(input: InputStream): PngPosePack {
        require(root.exists() || root.mkdirs())
        val stage = File(root, "stage-" + UUID.randomUUID().toString())
        require(stage.mkdir()) { "STAGING_FAILED" }
        try {
            val seen = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    val path = item.name
                    val allowed = path == "manifest.json" ||
                        (path.startsWith("poses/") && path.removePrefix("poses/").matches(
                            Regex("JARVIS_[0-9]{3}_[A-Z0-9_]{1,55}[.]png")))
                    require(!item.isDirectory && allowed && seen.add(path) && seen.size <= 37) { "ZIP_PATH_OR_COUNT" }
                    val file = File(stage, if (path == "manifest.json") path else path.removePrefix("poses/"))
                    val cap = if (path == "manifest.json") 65536L else 3_000_000L
                    var bytes = 0L
                    file.outputStream().buffered().use { dest ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n == -1) break
                            bytes += n; total += n
                            require(bytes <= cap && total <= 64L * 1024 * 1024) { "SIZE_LIMIT" }
                            dest.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifestBytes = File(stage, "manifest.json").readBytes()
            val manifest = PngPoseCodec.decode(manifestBytes)
            require(seen == manifest.frames.map { "poses/" + it.file }.toSet() + "manifest.json") {
                "MISSING_OR_UNDECLARED"
            }
            for (pose in manifest.frames) {
                val file = File(stage, pose.file)
                val bytes = file.readBytes()
                require(bytes.size == pose.bytes && PngPoseCodec.sha256(bytes) == pose.sha256) { "HASH" }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                require(opts.outWidth == 640 && opts.outHeight == 960 && opts.outMimeType == "image/png") {
                    "INVALID_PNG"
                }
            }
            val digest = PngPoseCodec.sha256(manifestBytes)
            val target = dir(digest)
            if (!target.exists()) require(stage.renameTo(target)) { "INSTALL_FAILED" }
            return PngPosePack(digest, manifest)
        } finally {
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    fun artwork(pack: PngPosePack, id: String): PngPoseArtwork {
        val pose = requireNotNull(pack.manifest.frames.singleOrNull { it.id == id })
        val file = File(dir(pack.digest), pose.file)
        val bytes = file.readBytes()
        require(bytes.size == pose.bytes && PngPoseCodec.sha256(bytes) == pose.sha256) { "ASSET_CHANGED" }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)) { "DECODE" }
        return PngPoseArtwork(pack.digest, id, bitmap.asImageBitmap())
    }

    fun activeArtwork(): PngPoseArtwork? = current()?.let { (pack, id) ->
        runCatching { artwork(pack, id) }.getOrNull()
    }

    fun activate(pack: PngPosePack, id: String) {
        require(pack.manifest.frames.any { it.id == id })
        requireNotNull(readPack(pack.digest)) { "PACK_MISSING" }
        val atomic = AtomicFile(selectedFile)
        val stream = atomic.startWrite()
        try {
            stream.write((pack.digest + "\n" + id + "\n").toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    fun disable() { if (selectedFile.exists()) require(selectedFile.delete()) { "RESET_FAILED" } }
}

class PngPoseArtwork(digest: String, id: String, val image: ImageBitmap) : AvatarArtwork {
    override val resourceId: String = "jarvis.png." + digest.take(12) + "." + id
    override fun DrawScope.render(pose: AvatarPose) {
        // 2:3 image fitted within the legacy 240x320 canvas, without face deformation.
        drawImage(image, dstOffset = IntOffset(13, 0), dstSize = IntSize(213, 320))
    }
}