package com.jarvis.interfaceapp.fusion

import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class CapabilityPresentationTest {
    private val epoch=UUID.randomUUID().toString()
    private fun report()=HostCapabilityReport(epoch,2,3,1000,HostOrigin.REAL_HOST,
        listOf(CapabilityEvidence(CapabilityId.CORE,CapabilityStatus.READY,"VERIFIED")))
    private fun project(r:HostCapabilityReport=report(),trusted:Boolean=true,after:Long=2,now:Long=2000)=
        CapabilityProjection.evaluate(r,HostEvidenceVerifier {trusted},epoch,2,after,now)
    @Test fun defaultNeverClaimsReady() {assertEquals(7,CapabilityProjection.disconnected().size);assertTrue(CapabilityProjection.disconnected().all {!it.connected && it.status==CapabilityStatus.UNKNOWN})}
    @Test fun verifiedReportOnlyAttestsExplicitCapabilities() {val p=project();assertEquals(CapabilityStatus.READY,p.first().status);assertTrue(p.drop(1).all {it.status==CapabilityStatus.UNKNOWN})}
    @Test fun absentTrustAndLaboratoryCannotClaimReady() {assertTrue(project(trusted=false).none {it.connected});assertTrue(project(report().copy(origin=HostOrigin.LAB)).none {it.connected})}
    @Test fun expiredFutureAndNegativeClockEvidenceIsRejected() {for(time in listOf(-1L,0L)) assertTrue(project(report().copy(observedAtMs=time),now=31_001).none {it.connected});assertTrue(project(report().copy(observedAtMs=2001)).none {it.connected})}
    @Test fun epochGenerationAndReplayAreRejected() {for(r in listOf(report().copy(epoch=UUID.randomUUID().toString()),report().copy(generation=1),report().copy(sequence=2),report().copy(epoch="bad"),report().copy(version=1)))assertTrue(project(r).none {it.connected})}
    @Test fun duplicateCapabilitiesAreRejected() {assertTrue(project(report().copy(capabilities=report().capabilities+report().capabilities)).none {it.connected})}
    @Test fun hostRefusalAndUnknownRemainVisible() {for(status in CapabilityStatus.entries)assertEquals(status,project(report().copy(capabilities=listOf(CapabilityEvidence(CapabilityId.CORE,status,"HOST_STATUS")))).first().status)}
    @Test fun verifierFailureFailsClosed() {assertTrue(CapabilityProjection.evaluate(report(),HostEvidenceVerifier {error("lost")},epoch,2,2,2000).none {it.connected})}
}
