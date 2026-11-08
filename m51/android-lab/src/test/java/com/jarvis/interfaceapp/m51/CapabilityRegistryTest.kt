package com.jarvis.interfaceapp.m51
import org.junit.Assert.*
import org.junit.Test
class CapabilityRegistryTest {
    private val sha="a".repeat(40)
    @Test fun absentRuntimeNeverBecomesEnabledFromSourcePresence() {
        val registry=m51Registry(sha,true)
        registry.modules.filter { it.moduleId != "INTERFACE" }.forEach {
            assertFalse(it.dimensions.codePresent);assertFalse(it.dimensions.connected);assertFalse(it.dimensions.enabled)
            assertFalse(it.dimensions.runtimeReady);assertFalse(it.dimensions.userApproved);assertFalse(it.dimensions.deviceQualified)
            assertTrue(registry.access(it.moduleId) is CapabilityAccess.Refused)
        }
    }
    @Test fun interfaceIsLabOnlyAndDeviceQualificationRemainsFalse() {
        val registry=m51Registry(sha,true);val row=registry.modules.single { it.moduleId=="INTERFACE" }
        assertEquals(CapabilityState.LAB_ONLY,row.state);assertTrue(row.dimensions.enabled);assertFalse(row.dimensions.deviceQualified)
        assertEquals(CapabilityAccess.LocalUi,registry.access("INTERFACE"))
    }
    @Test fun unavailableInterfaceIsNotReady() {
        val registry=m51Registry(sha,false);assertTrue(registry.access("INTERFACE") is CapabilityAccess.Refused)
    }
    @Test fun missingModuleIsControlledRefusal() { assertTrue(m51Registry(sha,true).access("IMAGINARY") is CapabilityAccess.Refused) }
    @Test fun activePromotionNeedsAllEvidenceDimensions() {
        val row=m51Registry(sha,true).modules.first()
        assertTrue(runCatching { row.copy(state=CapabilityState.ACTIVE_VERIFIED) }.isFailure)
        assertTrue(runCatching { row.copy(dimensions=row.dimensions.copy(connected=false)) }.isFailure)
    }
    @Test fun registryRejectsDuplicatesAndMissingDependencies() {
        val rows=m51Registry(sha,true).modules
        assertTrue(runCatching { CapabilityRegistry(rows+rows.first()) }.isFailure)
        assertTrue(runCatching { CapabilityRegistry(rows.filter { it.moduleId!="CORE" }) }.isFailure)
    }
    @Test fun publicSummaryIsFixedTechnicalMetadataOnly() {
        val summary=m51Registry(sha,true).publicSummary()
        assertTrue(summary.contains("jarvis.capabilities/1"));assertFalse(summary.contains("content://"))
        assertFalse(summary.contains("/data/"));assertFalse(summary.contains("coreMandateSha256"))
        assertTrue(summary.contains("LLM: BLOCKED"));assertTrue(summary.contains("device_qualified=false"))
    }
    @Test fun optionalEvidenceLinksCannotBecomeArbitraryUris() {
        assertTrue(canOpenEvidence("https://github.com/bubth720/JARVIS-LLM/tree/"+sha))
        for(uri in listOf("file:///data/private","content://private/document","https://evil.test/bubth720/JARVIS-LLM/tree/$sha","https://github.com/bubth720/JARVIS-LLM/tree/$sha?token=SECRET","https://github.com/other/JARVIS-LLM/tree/$sha")) assertFalse(canOpenEvidence(uri))
    }
    @Test fun filteringIsDeterministicAndBounded() {
        val r=m51Registry(sha,true);assertEquals(listOf("LLM"),r.filtered("ll",true).map { it.moduleId })
        assertTrue(r.filtered("x".repeat(41)).isEmpty());assertEquals(9,r.filtered("").size)
    }
}
