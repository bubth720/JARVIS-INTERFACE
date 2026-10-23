package com.jarvis.interfaceapp.avatar.assets

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

fun interface AvatarAssetSource { fun open(name: String): InputStream }
/** Entirely in-memory, bounded ZIP. No extraction, paths, nested archives or persisted URI grants. */
class ZipAvatarAssetSource(input: InputStream) : AvatarAssetSource {
    private val entries = mutableMapOf<String, ByteArray>()
    init {
        ZipInputStream(input).use { zip ->
            var total = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && (entry.name == "manifest.json" || AssetValidation.safeFile(entry.name)))
                require(entries.size < ResourceLimit.LAYERS + 1 && !entries.containsKey(entry.name))
                val limit = minOf(ResourceLimit.FILE_BYTES, ResourceLimit.BUNDLE_BYTES - total)
                val bytes = zip.readBounded(limit); total += bytes.size
                entries[entry.name] = bytes
            }
        }
        require(entries.containsKey("manifest.json"))
    }
    override fun open(name: String): InputStream = requireNotNull(entries[name]) { "MISSING_ASSET" }.inputStream()
}
fun InputStream.readBounded(limit: Int): ByteArray {
    require(limit >= 0)
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break
        require(out.size() + count <= limit) { "ASSET_TOO_LARGE" }; out.write(buffer, 0, count) }
    return out.toByteArray()
}
class LayeredAvatarAsset(val manifest: AvatarAssetManifest, val textures: Map<String, ImageBitmap>)
class AvatarAssetLoader {
    /** One load, no process-global cache. Pixel budget includes every decoded unique texture. */
    fun load(source: AvatarAssetSource): LayeredAvatarAsset {
        val manifest = source.open("manifest.json").use { AssetValidation.decode(it.readBounded(ResourceLimit.MANIFEST_BYTES)) }
        val images = linkedMapOf<String, ImageBitmap>(); var pixels = 0L; var bytesTotal = 0
        for (layer in manifest.layers) {
            if (images.containsKey(layer.file)) {
                require(manifest.layers.first { it.file == layer.file }.sha256 == layer.sha256)
                continue
            }
            val bytes = source.open(layer.file).use { it.readBounded(ResourceLimit.FILE_BYTES) }
            bytesTotal += bytes.size; require(bytesTotal <= ResourceLimit.BUNDLE_BYTES)
            require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == layer.sha256) { "HASH_MISMATCH" }
            require(bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }) { "PNG_REQUIRED" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..ResourceLimit.TEXTURE_SIDE && bounds.outHeight in 1..ResourceLimit.TEXTURE_SIDE)
            pixels += bounds.outWidth.toLong() * bounds.outHeight
            require(pixels <= ResourceLimit.PIXELS) { "PIXEL_BUDGET" }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "CORRUPT_PNG" }
            images[layer.file] = bitmap.asImageBitmap()
        }
        return LayeredAvatarAsset(manifest, images.toMap())
    }
}
