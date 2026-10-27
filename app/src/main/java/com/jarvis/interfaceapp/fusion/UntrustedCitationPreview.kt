package com.jarvis.interfaceapp.fusion

import java.util.UUID

/** Local preview only. A page/offset/digest is a claim, not source authorization. */
data class CitationClaim(val sourceId: String, val page: Int, val startUtf16: Int,
    val endUtf16: Int, val revisionSha256: String)
data class CitationPreview(val status: String, val caption: String, val clickable: Boolean = false)

/** Never reveals a private source path or constructs a navigation action. */
object UntrustedCitationPreview {
    fun project(claim: CitationClaim): CitationPreview {
        val validId = runCatching { UUID.fromString(claim.sourceId).toString() == claim.sourceId }.getOrDefault(false)
        val valid = validId && claim.page in 1..100000 && claim.startUtf16 >= 0 &&
            claim.endUtf16 >= claim.startUtf16 && claim.endUtf16.toLong() - claim.startUtf16 <= 4096 &&
            claim.revisionSha256.matches(Regex("[a-f0-9]{64}"))
        return if (valid) CitationPreview("REVIEW_ONLY", "Page ${claim.page} · source à vérifier")
            else CitationPreview("UNKNOWN", "Citation indisponible")
    }
}
