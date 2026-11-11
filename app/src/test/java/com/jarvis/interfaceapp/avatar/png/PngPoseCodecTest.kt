package com.jarvis.interfaceapp.avatar.png

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PngPoseCodecTest {
    private fun valid(): ByteArray = buildJsonObject {
        put("format", "jarvis.avatar.png/1")
        put("pack_id", "jarvis-avatar-expressions-36-v1")
        put("default_id", "031")
        put("local_only", true)
        put("source_description", "Synthetic fixture; no private portraits")
        putJsonArray("frames") {
            for (i in 1..36) add(buildJsonObject {
                val id = "%03d".format(i)
                put("id", id)
                put("file", "JARVIS_" + id + "_TEST.png")
                put("state", if (i == 31) "IDLE" else "GREETING")
                put("pose", "front")
                put("label", "Test " + id)
                put("width", 640)
                put("height", 960)
                put("bytes", 125)
                put("sha256", "0".repeat(64))
                put("quality", "reviewed")
            })
        }
    }.toString().toByteArray(Charsets.UTF_8)

    @Test fun catalogueHasThirtySixOrderedPoses() {
        val parsed = PngPoseCodec.decode(valid())
        assertEquals(36, parsed.frames.size)
        assertEquals("031", parsed.defaultId)
        assertEquals("SUCCESS", "SUCCESS") // No external runtime or CORE needed.
        assertEquals("JARVIS_031_TEST.png", parsed.frames[30].file)
    }

    @Test fun refusesUnknownFormat() {
        val bad = valid().toString(Charsets.UTF_8).replace("jarvis.avatar.png/1", "jarvis.avatar.png/9")
        try { PngPoseCodec.decode(bad.toByteArray()); fail("FORMAT must be refused") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun refusesUnknownState() {
        val bad = valid().toString(Charsets.UTF_8).replace("\"GREETING\"", "\"SUPERUSER\"")
        try { PngPoseCodec.decode(bad.toByteArray()); fail("STATE must be refused") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun refusesDuplicateId() {
        val bad = valid().toString(Charsets.UTF_8).replace("\"id\":\"030\"", "\"id\":\"029\"")
        try { PngPoseCodec.decode(bad.toByteArray()); fail("DUPLICATE must be refused") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun refusesTraversalFilename() {
        val bad = valid().toString(Charsets.UTF_8).replace("JARVIS_001_TEST.png", "../secret.png")
        try { PngPoseCodec.decode(bad.toByteArray()); fail("TRAVERSAL must be refused") }
        catch (_: IllegalArgumentException) {}
    }
}