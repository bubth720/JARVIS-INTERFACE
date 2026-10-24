package com.jarvis.interfaceapp.avatar.review

/** Independent user grants. Local preview is never consent to publish or activate permanently. */
data class ArtApprovalReceipt(
    val manifestSha256: String,
    val reviewEpoch: Long,
    val aestheticApproved: Boolean = false,
    val publicationApproved: Boolean = false,
    val permanentActivationApproved: Boolean = false,
) {
    fun matches(digest: String, epoch: Long): Boolean = manifestSha256.matches(Regex("[0-9a-f]{64}")) &&
        digest == manifestSha256 && epoch >= 0 && epoch == reviewEpoch
    fun mayPublish(digest: String, epoch: Long) = matches(digest, epoch) && aestheticApproved && publicationApproved
    fun mayActivatePermanently(digest: String, epoch: Long) = matches(digest, epoch) && aestheticApproved && permanentActivationApproved
}
