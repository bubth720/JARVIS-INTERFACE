package com.jarvis.interfaceapp.avatar.v2

/** Selection is session-only and opt-in. Layout DataStore V1 is never accessed. */
class AvatarPackRepository {
    private val validated = linkedMapOf<String, PremiumAvatarManifest>()
    var selectedId: String? = null; private set // null = existing V1 vector avatar
    var previousId: String? = null; private set
    val retainedPixels: Long get() = validated.values.sumOf { m -> m.resources.sumOf { it.width.toLong() * it.height } }
    fun canRegister(manifest: PremiumAvatarManifest): Boolean {
        val existing = validated[manifest.id]
        if (existing != null) return existing == manifest
        return retainedPixels + manifest.resources.sumOf { it.width.toLong() * it.height } <= com.jarvis.interfaceapp.avatar.assets.ResourceLimit.PIXELS
    }
    fun register(pack: ValidatedPremiumPack): Boolean {
        if (!canRegister(pack.manifest)) return false
        validated[pack.manifest.id] = pack.manifest
        return true
    }
    fun inventory(): List<PremiumAvatarManifest> = validated.values.toList()
    fun select(id: String?, visualConfirmation: Boolean): Boolean {
        if (id != null && (!visualConfirmation || id !in validated)) return false
        if (id == selectedId) return true
        previousId = selectedId; selectedId = id
        return true
    }
    fun rollback() { val old = selectedId; selectedId = previousId; previousId = old }
}
