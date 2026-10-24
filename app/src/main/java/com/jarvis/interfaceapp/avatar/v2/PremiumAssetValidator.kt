package com.jarvis.interfaceapp.avatar.v2

import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import com.jarvis.interfaceapp.avatar.assets.readBounded
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** No extraction, URI persistence, filesystem paths or ambient network. */
class PremiumZipSource(input: InputStream) {
    private val entries = linkedMapOf<String, ByteArray>()
    init {
        ZipInputStream(input).use { zip ->
            var total = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && (entry.name == "manifest.json" || entry.name.matches(Regex("[A-Za-z0-9_-]{1,64}\\.png")))) { "UNSAFE_ZIP_PATH" }
                require(entries.size < ResourceLimit.LAYERS + 1 && entries.keys.none { it.equals(entry.name, ignoreCase = true) }) { "DUPLICATE_OR_EXCESS_ENTRY" }
                val limit = if (entry.name == "manifest.json") ResourceLimit.MANIFEST_BYTES else ResourceLimit.FILE_BYTES
                val bytes = zip.readBounded(minOf(limit, ResourceLimit.BUNDLE_BYTES - total))
                total += bytes.size; entries[entry.name] = bytes
            }
        }
        require("manifest.json" in entries) { "MISSING_MANIFEST" }
    }
    fun manifest(): ByteArray = entries.getValue("manifest.json")
    fun read(file: String): ByteArray = requireNotNull(entries[file]) { "MISSING_ASSET" }
    fun requireExactFiles(files: Set<String>) = require(entries.keys == files + "manifest.json") { "UNDECLARED_OR_MISSING_ASSET" }
}
data class PngFacts(val width: Int, val height: Int, val transparentPixels: Boolean)
fun interface PngInspector { fun inspect(bytes: ByteArray): PngFacts }
class ValidatedPremiumPack internal constructor(val manifest: PremiumAvatarManifest, internal val bytes: Map<String, ByteArray>)

class PremiumAssetValidator(private val inspector: PngInspector) {
    fun validate(zip: PremiumZipSource): ValidatedPremiumPack {
        val manifest = PremiumManifestCodec.decode(zip.manifest())
        zip.requireExactFiles(manifest.resources.map { it.file }.toSet())
        return validate(manifest) { zip.read(it) }
    }
    fun validate(manifest: PremiumAvatarManifest, read: (String) -> ByteArray): ValidatedPremiumPack {
        val data = linkedMapOf<String, ByteArray>(); var total = 0
        for (resource in manifest.resources) {
            val bytes = read(resource.file)
            require(bytes.size <= ResourceLimit.FILE_BYTES) { "FILE_BUDGET" }
            total += bytes.size; require(total <= ResourceLimit.BUNDLE_BYTES) { "BUNDLE_BUDGET" }
            require(sha256(bytes) == resource.sha256) { "HASH_MISMATCH" }
            // Require non-paletted RGBA PNG and validate advertised dimensions before decode.
            require(bytes.size >= 33 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() } &&
                bytes.copyOfRange(12,16).toString(Charsets.US_ASCII) == "IHDR" && bytes[24] == 8.toByte() && bytes[25] == 6.toByte()) { "RGBA_PNG_REQUIRED" }
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            require(buffer.getInt(8) == 13 && buffer.getInt(16) == resource.width && buffer.getInt(20) == resource.height) { "PNG_DIMENSIONS" }
            checkContainer(bytes)
            val facts = inspector.inspect(bytes)
            require(facts.width == resource.width && facts.height == resource.height && facts.transparentPixels) { "TRANSPARENCY_OR_DIMENSIONS" }
            data[resource.id] = bytes
        }
        return ValidatedPremiumPack(manifest, data)
    }
    companion object {
        private fun checkContainer(bytes: ByteArray) {
            var at = 8; var chunks = 0; var idat = false; var ended = false
            val b = java.nio.ByteBuffer.wrap(bytes)
            while (at < bytes.size) {
                require(bytes.size - at >= 12 && ++chunks <= 4096) { "CORRUPT_PNG" }
                val length = b.getInt(at)
                require(length >= 0 && length <= bytes.size - at - 12) { "CORRUPT_PNG" }
                val name = bytes.copyOfRange(at + 4, at + 8).toString(Charsets.US_ASCII)
                require(name != "acTL" && (at != 8 || name == "IHDR") && (at == 8 || name != "IHDR")) { "UNSUPPORTED_PNG" }
                val crc = java.util.zip.CRC32().apply { update(bytes, at + 4, length + 4) }.value
                require(crc == (b.getInt(at + 8 + length).toLong() and 0xffffffffL)) { "PNG_CRC" }
                if (name == "IDAT") idat = true
                at += length + 12
                if (name == "IEND") { require(length == 0 && at == bytes.size); ended = true; break }
            }
            require(ended && idat) { "TRUNCATED_PNG" }
        }
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
