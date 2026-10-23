package com.jarvis.interfaceapp.avatar.assets

import com.jarvis.interfaceapp.avatar.AvatarAnimationController
import com.jarvis.interfaceapp.avatar.AvatarCommand
import com.jarvis.interfaceapp.avatar.AvatarPose
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AvatarAssetTest {
    private fun bytes() = javaClass.getResourceAsStream("/avatar/manifest.json")!!.use { it.readBytes() }
    private fun manifest() = AssetValidation.decode(bytes())
    private fun changed(change: (JsonObject) -> JsonObject): ByteArray = change(Json.parseToJsonElement(bytes().decodeToString()).jsonObject).toString().toByteArray()
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> for ((name, bytes) in entries) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()
    @Test fun fiveIndependentTransformsReturnToNeutral() {
        val m = manifest(); assertEquals(7, m.layers.size)
        val pose = AvatarPose(1f, .4f, 4f)
        assertEquals(5, m.layers.map { PoseTransform.forLayer(it, pose) }.distinct().size)
        assertTrue(m.layers.all { PoseTransform.forLayer(it, AvatarPose()) == PoseTransform(0f,0f) })
    }
    @Test fun existingControllerStillDrivesAndStopsAllLayers() {
        val controller = AvatarAnimationController { 3000 }
        controller.setVisible(true); controller.setForeground(true); controller.resize(240,320)
        controller.advanceBy(100.0)
        assertTrue(manifest().layers.all { PoseTransform.forLayer(it,controller.pose).dy < 0 })
        controller.execute(AvatarCommand.Reset)
        repeat(4) { controller.advanceBy(100.0) }
        assertEquals(AvatarPose(), controller.pose)
        controller.setForeground(false); val frames=controller.framesAdvanced
        assertFalse(controller.advanceBy(100.0)); assertEquals(frames,controller.framesAdvanced)
    }
    @Test fun versionUnknownFieldDuplicateAndInvalidUtf8AreRejected() {
        val invalid = listOf(
            changed { JsonObject(it + ("version" to JsonPrimitive(2))) },
            changed { JsonObject(it + ("unknown" to JsonPrimitive(true))) },
            ("{\"version\":1," + bytes().decodeToString().drop(1)).toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28), ByteArray(ResourceLimit.MANIFEST_BYTES + 1))
        for (b in invalid) assertThrows(Exception::class.java) { AssetValidation.decode(b) }
    }
    @Test fun duplicateIdsAndOrdersOutOfBoundsAndPathTraversalAreRejected() {
        for ((field,value) in listOf("id" to JsonPrimitive("back-hair"), "order" to JsonPrimitive(0),
            "file" to JsonPrimitive("../a.png"), "width" to JsonPrimitive(241), "depth" to JsonPrimitive(2),
            "sha256" to JsonPrimitive("bad"), "x" to JsonPrimitive("0"))) {
            val b=changed { r -> val layers=r.getValue("layers").jsonArray.toMutableList()
                layers[1]=JsonObject(layers[1].jsonObject + (field to value)); JsonObject(r + ("layers" to JsonArray(layers))) }
            assertThrows(field,Exception::class.java) { AssetValidation.decode(b) }
        }
    }
    @Test fun malformedMasksAreRejected() {
        for (mask in listOf(listOf(0,0,241,320),listOf(3,2,1,0),listOf(1,2))) {
            val b=changed { r -> val layers=r.getValue("layers").jsonArray.toMutableList()
                layers[0]=JsonObject(layers[0].jsonObject + ("mask" to JsonArray(mask.map { JsonPrimitive(it) })))
                JsonObject(r + ("layers" to JsonArray(layers))) }
            assertThrows(Exception::class.java) { AssetValidation.decode(b) }
        }
    }
    @Test fun zipOpensOnlyRootAssetsAndRequiresManifest() {
        val source=ZipAvatarAssetSource(zip("manifest.json" to bytes(),"a.png" to byteArrayOf(1,2)).inputStream())
        assertArrayEquals(bytes(),source.open("manifest.json").readBytes())
        assertThrows(IllegalArgumentException::class.java) { source.open("missing.png") }
        for (name in listOf("../x.png","/x.png","nested/x.png","a.jpg","x\\y.png"))
            assertThrows(IllegalArgumentException::class.java) { ZipAvatarAssetSource(zip("manifest.json" to bytes(),name to byteArrayOf()).inputStream()) }
        assertThrows(IllegalArgumentException::class.java) { ZipAvatarAssetSource(zip("a.png" to byteArrayOf()).inputStream()) }
    }
    @Test fun zipBombAndExtraEntriesAreBoundedBeforeDecode() {
        val compressed=zip("manifest.json" to bytes(),"big.png" to ByteArray(ResourceLimit.FILE_BYTES+1))
        assertTrue(compressed.size<10000)
        assertThrows(IllegalArgumentException::class.java) { ZipAvatarAssetSource(compressed.inputStream()) }
        val entries=(0..ResourceLimit.LAYERS).map { "$it.png" to byteArrayOf() }
        assertThrows(IllegalArgumentException::class.java) { ZipAvatarAssetSource(zip("manifest.json" to bytes(),*entries.toTypedArray()).inputStream()) }
    }
    @Test fun streamBoundAndTruncationInputsCannotBypassLimit() {
        assertArrayEquals(byteArrayOf(1,2),byteArrayOf(1,2).inputStream().readBounded(2))
        assertThrows(IllegalArgumentException::class.java) { byteArrayOf(1,2,3).inputStream().readBounded(2) }
        assertThrows(Exception::class.java) { AssetValidation.decode(bytes().copyOf(30)) }
    }
}
