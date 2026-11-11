package com.stark.jarvislocal.v1a

import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

/**
 * Private model store. A GGUF enters only through an explicit user import with a trusted
 * SHA-256 typed by the user; the digest is computed on the copied bytes, the copy is atomic
 * and never overwrites. A02 re-verifies the same bytes again at every load.
 * Nothing is bundled in the APK and nothing is downloaded.
 */
class ModelStore(private val root: File, private val availableBytes: () -> Long) {
    init { root.mkdirs() }

    data class Entry(val file: File, val sha256: String) { val sizeBytes: Long get() = file.length() }

    fun current(): Entry? = root.listFiles { f -> f.isFile && f.name.matches(NAME) }
        ?.maxByOrNull { it.lastModified() }?.let { Entry(it, it.name.removeSuffix(".gguf")) }

    fun import(input: InputStream, expectedSha256: String, cancelled: () -> Boolean = { false }): Entry {
        val sha = expectedSha256.trim().lowercase()
        require(sha.matches(Regex("[a-f0-9]{64}"))) { "SHA-256 attendu invalide (64 caractères hexadécimaux)" }
        val target = File(root, "$sha.gguf")
        if (target.isFile) return Entry(target, sha) // A02 re-hashes before any load.
        val partial = File(root, "$sha.part")
        partial.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            partial.outputStream().buffered(1 shl 20).use { out ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    if (cancelled()) throw InterruptedIOException("Import annulé")
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "Fichier supérieur à 2 Gio" }
                    require(availableBytes() > (n.toLong() + RESERVE_BYTES)) { "Stockage insuffisant" }
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                }
                out.flush()
            }
            require(total > 0) { "Fichier vide" }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(actual == sha) { "Empreinte différente : fichier refusé" }
            check(partial.renameTo(target)) { "Installation atomique impossible" }
            return Entry(target, sha)
        } finally { partial.delete() }
    }

    fun delete(entry: Entry): Boolean = entry.file.delete()

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        const val RESERVE_BYTES = 64L * 1024 * 1024
        private val NAME = Regex("[a-f0-9]{64}\\.gguf")
    }
}
