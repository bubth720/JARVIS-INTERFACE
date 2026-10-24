package com.jarvis.interfaceapp.avatar.review

import org.junit.Assert.*
import org.junit.Test
class ArtApprovalReceiptTest {
    private val digest="a".repeat(64)
    @Test fun noGrantByDefaultOrPreview() {
        val r=ArtApprovalReceipt(digest,1)
        assertFalse(r.mayPublish(digest,1));assertFalse(r.mayActivatePermanently(digest,1))
    }
    @Test fun publicationAndActivationRemainSeparate() {
        val r=ArtApprovalReceipt(digest,1,true,true,false)
        assertTrue(r.mayPublish(digest,1));assertFalse(r.mayActivatePermanently(digest,1))
        assertFalse(r.copy(publicationApproved=false,permanentActivationApproved=true).mayPublish(digest,1))
    }
    @Test fun oldApprovalDoesNotAuthorizeModifiedAssetsOrEpoch() {
        val r=ArtApprovalReceipt(digest,1,true,true,true)
        assertFalse(r.mayPublish("b".repeat(64),1));assertFalse(r.mayPublish(digest,2))
        assertFalse(r.mayActivatePermanently(digest,-1));assertFalse(r.copy(manifestSha256="broken").mayPublish("broken",1))
    }
    @Test fun aestheticsAloneNeverAllowsPublication() {
        val r=ArtApprovalReceipt(digest,1,true,false,false)
        assertFalse(r.mayPublish(digest,1));assertFalse(r.mayActivatePermanently(digest,1))
    }
}
