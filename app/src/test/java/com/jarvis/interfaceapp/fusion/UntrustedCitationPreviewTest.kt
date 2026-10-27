package com.jarvis.interfaceapp.fusion

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class UntrustedCitationPreviewTest {
    private val base = CitationClaim(UUID.randomUUID().toString(), 2, 0, 27, "a".repeat(64))

    @Test fun evenWellFormedCitationRequiresSourceVerification() {
        val view = UntrustedCitationPreview.project(base)
        assertEquals("REVIEW_ONLY", view.status)
        assertFalse(view.clickable)
        assertTrue(view.caption.contains("Page 2"))
        assertFalse(view.caption.contains(base.sourceId))
        assertFalse(view.caption.contains(base.revisionSha256))
    }

    @Test fun malformedOrDangerousOffsetsAreInert() {
        val cases = listOf(
            base.copy(startUtf16 = -1), base.copy(endUtf16 = -1),
            base.copy(startUtf16 = 0, endUtf16 = 4097), base.copy(endUtf16 = -2),
            base.copy(page = 0), base.copy(page = -1), base.copy(page = 100001),
            base.copy(sourceId = "file:///private/source.pdf"),
            base.copy(sourceId = "not-a-uuid"),
            base.copy(revisionSha256 = "bad"),
            base.copy(revisionSha256 = "F".repeat(64)))
        for (claim in cases) {
            val view = UntrustedCitationPreview.project(claim)
            assertFalse(view.clickable)
            assertEquals("UNKNOWN", view.status)
        }
    }

    @Test fun validCitationIsNeverAnActionOrGrant() {
        val edge = base.copy(page = 100000, startUtf16 = 4096, endUtf16 = 8192)
        assertEquals("REVIEW_ONLY", UntrustedCitationPreview.project(edge).status)
        assertFalse(UntrustedCitationPreview.project(edge).clickable)
        assertEquals("UNKNOWN", UntrustedCitationPreview.project(edge.copy(page = 100001)).status)
    }
}
