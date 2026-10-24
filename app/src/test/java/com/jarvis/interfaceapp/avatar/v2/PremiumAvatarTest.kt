package com.jarvis.interfaceapp.avatar.v2

import com.jarvis.interfaceapp.avatar.assets.ResourceLimit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PremiumAvatarTest {
    private val fixture = javaClass.getResourceAsStream("/avatar/v2/manifest.json")!!.readBytes()
    private fun manifest() = PremiumManifestCodec.decode(fixture)
    private fun mutate(edit: (MutableMap<String, JsonElement>) -> Unit): ByteArray {
        val m = Json.parseToJsonElement(fixture.toString(Charsets.UTF_8)).jsonObject.toMutableMap(); edit(m)
        return JsonObject(m).toString().toByteArray()
    }
    private fun refuse(bytes: ByteArray) { assertThrows(Exception::class.java) { PremiumManifestCodec.decode(bytes) } }
    @Test fun demoHasRealInventoryAndDoesNotClaimPremiumCompleteness() {
        val m=manifest();assertEquals(5,m.resources.size);assertEquals(5,m.layers.size)
        assertEquals(40_000,m.resources.sumOf { it.width * it.height });assertFalse(m.completeCandidate)
        assertEquals(ArtGrade.DEMONSTRATION,m.artGrade);assertEquals(1,m.frames.size)
    }
    @Test fun oldVersionIsNotSilentlyMigrated() { refuse(mutate { it["version"]=JsonPrimitive(1) }) }
    @Test fun futureVersionRefused() { refuse(mutate { it["version"]=JsonPrimitive(3) }) }
    @Test fun fractionalVersionRefused() { refuse(mutate { it["version"]=JsonPrimitive(2.5) }) }
    @Test fun quotedNumbersRefused() { refuse(mutate { it["width"]=JsonPrimitive("240") }) }
    @Test fun unknownFieldRefused() { refuse(mutate { it["future"]=JsonPrimitive(1) }) }
    @Test fun escapedDuplicateKeysRefused() {
        refuse(fixture.toString(Charsets.UTF_8).replaceFirst("\"version\": 2", "\"version\": 2, \"ver\\u0073ion\":2").toByteArray())
    }
    @Test fun deepJsonRefusedBeforeRecursion() { refuse(("[".repeat(5000)+"]".repeat(5000)).toByteArray()) }
    @Test fun invalidUtf8Refused() { refuse(byteArrayOf(0xc3.toByte(),0x28)) }
    @Test fun oversizedManifestRefused() { refuse(ByteArray(ResourceLimit.MANIFEST_BYTES+1)) }
    @Test fun unsafeAndCaseAliasedFilesRefused() {
        for (name in listOf("../face.png","nested/face.png","face.PNG","/face.png")) {
            refuse(fixture.toString(Charsets.UTF_8).replace("back-hair.png",name).toByteArray())
        }
    }
    @Test fun duplicateStatesRefused() { refuse(mutate { val f=it.getValue("frames").jsonArray;it["frames"]=JsonArray(f+f) }) }
    @Test fun missingNeutralRefused() { refuse(fixture.toString(Charsets.UTF_8).replace("\"IDLE\"","\"ERROR\"").toByteArray()) }
    @Test fun outOfBoundsLayerRefused() { refuse(fixture.toString(Charsets.UTF_8).replace("\"x\": 0","\"x\": 241").toByteArray()) }
    @Test fun invalidPivotRefused() { refuse(fixture.toString(Charsets.UTF_8).replace("\"pivotX\": 120","\"pivotX\": -1").toByteArray()) }
    @Test fun textureAndPixelBudgetsUnchanged() {
        refuse(fixture.toString(Charsets.UTF_8).replace("\"width\": 80","\"width\": 2049").toByteArray())
        refuse(fixture.toString(Charsets.UTF_8).replace("\"width\": 80","\"width\": 2048").replace("\"height\": 100","\"height\": 2048").toByteArray())
        assertEquals(16,ResourceLimit.LAYERS);assertEquals(2*1024*1024,ResourceLimit.FILE_BYTES)
        assertEquals(8*1024*1024,ResourceLimit.BUNDLE_BYTES);assertEquals(2*1024*1024,ResourceLimit.PIXELS)
    }
    @Test fun incompleteEyesAndMouthPairsRefused() {
        for (channel in listOf("EYES_OPEN","MOUTH_OPEN")) refuse(fixture.toString(Charsets.UTF_8).replaceFirst("\"channel\": \"HEAD\"", "\"channel\": \"$channel\"").toByteArray())
    }
    @Test fun mismatchedHeadAnchorsRefused() { refuse(fixture.toString(Charsets.UTF_8).replaceFirst("\"pivotX\": 120", "\"pivotX\": 100").replaceFirst("\"BACK_HAIR\"","\"HEAD\"").toByteArray()) }
    @Test fun flattenedImageCannotClaimLayeredArticulation() {
        refuse(fixture.toString(Charsets.UTF_8).replace("\"LAYERS\"","\"IMAGE_REPLACEMENT\"").toByteArray())
    }
    @Test fun absentStateExpressionAndPoseLeaveCurrentUnchanged() {
        val machine=AvatarExpressionStateMachine(manifest())
        for(e in listOf(ExpressionEvent.State(PremiumState.SPEAKING),ExpressionEvent.Expression(PremiumExpression.SMILE),ExpressionEvent.Perspective(PremiumPerspective.LEFT))) {
            val r=machine.dispatch(e);assertFalse(r.accepted);assertEquals("ART_ASSET_UNAVAILABLE",r.issue);assertEquals(PremiumKey(),machine.current)
        }
    }
    @Test fun allEightStatesAndResetAreTypedWhenMappingsExist() {
        val m=manifest();val frames=PremiumState.entries.map { m.frames.single().copy(key=PremiumKey(state=it)) }
        val machine=AvatarExpressionStateMachine(m.copy(frames=frames))
        for(s in PremiumState.entries) { assertTrue(machine.dispatch(ExpressionEvent.State(s)).accepted);assertEquals(s,machine.current.state) }
        assertTrue(machine.dispatch(ExpressionEvent.Reset).accepted);assertEquals(PremiumKey(),machine.current)
    }
    @Test fun sessionSelectionRequiresValidatedPackAndExplicitVisualConfirmation() {
        val r=AvatarPackRepository();val m=manifest();assertNull(r.selectedId)
        assertFalse(r.select(m.id,true));assertTrue(r.register(ValidatedPremiumPack(m,emptyMap())))
        assertFalse(r.select(m.id,false));assertNull(r.selectedId)
        assertTrue(r.select(m.id,true));assertEquals(m.id,r.selectedId);r.rollback();assertNull(r.selectedId)
        r.rollback();assertEquals(m.id,r.selectedId);assertTrue(r.select(null,true))
    }
    @Test fun failedRegistrationOrIdCollisionCannotReplacePreviousPack() {
        val r=AvatarPackRepository();val m=manifest();r.register(ValidatedPremiumPack(m,emptyMap()));r.select(m.id,true)
        assertFalse(r.register(ValidatedPremiumPack(m.copy(author="Other"),emptyMap())))
        assertEquals(m,r.inventory().single());assertEquals(m.id,r.selectedId)
    }
    @Test fun globalResidentTextureBudgetIncludesPreviouslySelectedPacks() {
        val r=AvatarPackRepository();val m=manifest();r.register(ValidatedPremiumPack(m,emptyMap()))
        val large=m.copy(id="large",resources=listOf(m.resources.first().copy(width=2048,height=1024)))
        assertFalse(r.canRegister(large));assertEquals(40000L,r.retainedPixels)
    }
    private fun zip(vararg files: Pair<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (n,b) -> z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry() } }
    }.toByteArray()
    @Test fun zipTraversalAndFoldersRefusedWithoutExtraction() {
        for(n in listOf("../face.png","nested/face.png","folder/","image.zip","/face.png")) assertThrows(Exception::class.java) { PremiumZipSource(zip("manifest.json" to fixture,n to byteArrayOf(1)).inputStream()) }
    }
    @Test fun zipCaseAliasesAndMissingManifestRefused() {
        assertThrows(Exception::class.java) { PremiumZipSource(zip("manifest.json" to fixture,"a.png" to byteArrayOf(),"A.png" to byteArrayOf()).inputStream()) }
        assertThrows(Exception::class.java) { PremiumZipSource(zip("a.png" to byteArrayOf()).inputStream()) }
    }
    @Test fun zipBombIsBoundedByExpandedBytes() {
        assertThrows(Exception::class.java) { PremiumZipSource(zip("manifest.json" to fixture,"bomb.png" to ByteArray(ResourceLimit.FILE_BYTES+1)).inputStream()) }
    }
    @Test fun undeclaredAndMissingZipFilesRefused() {
        val z=PremiumZipSource(zip("manifest.json" to fixture,"extra.png" to byteArrayOf()).inputStream())
        assertThrows(Exception::class.java) { z.requireExactFiles(manifest().resources.map { it.file }.toSet()) }
    }
    private fun png(): ByteArray {
        val out=ByteArrayOutputStream();out.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
        fun chunk(n:String,b:ByteArray) { val name=n.toByteArray();out.write(ByteBuffer.allocate(4).putInt(b.size).array());out.write(name);out.write(b)
            out.write(ByteBuffer.allocate(4).putInt(CRC32().apply { update(name);update(b) }.value.toInt()).array()) }
        chunk("IHDR",ByteBuffer.allocate(13).putInt(1).putInt(1).put(8).put(6).put(0).put(0).put(0).array())
        chunk("IDAT",byteArrayOf(1,2));chunk("IEND",byteArrayOf());return out.toByteArray()
    }
    private fun tiny(bytes:ByteArray):PremiumAvatarManifest = manifest().let { m -> m.copy(resources=m.resources.map {
        it.copy(width=1,height=1,sha256=PremiumAssetValidator.sha256(bytes))
    }) }
    @Test fun hashesCheckedBeforeInspectorAndCorruptionRejected() {
        var inspections=0;val validator=PremiumAssetValidator { inspections++;PngFacts(1,1,true) }
        assertThrows(Exception::class.java) { validator.validate(manifest()) { png() } };assertEquals(0,inspections)
        val b=png();b[30]=(b[30].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { validator.validate(tiny(b)) { b } };assertEquals(0,inspections)
    }
    @Test fun transparentRgbaAndActualDimensionsRequired() {
        val b=png()
        assertThrows(Exception::class.java) { PremiumAssetValidator { PngFacts(1,1,false) }.validate(tiny(b)) { b } }
        assertThrows(Exception::class.java) { PremiumAssetValidator { PngFacts(2,1,true) }.validate(tiny(b)) { b } }
        assertEquals(5,PremiumAssetValidator { PngFacts(1,1,true) }.validate(tiny(b)) { b }.bytes.size)
    }
    @Test fun truncatedAndTrailingPngDataRejectedEvenWithMatchingHash() {
        for(b in listOf(png().dropLast(5).toByteArray(),png()+byteArrayOf(1)))
            assertThrows(Exception::class.java) { PremiumAssetValidator { PngFacts(1,1,true) }.validate(tiny(b)) { b } }
    }
}
