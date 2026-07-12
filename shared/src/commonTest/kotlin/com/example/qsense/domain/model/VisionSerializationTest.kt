package com.example.qsense.domain.model

import com.example.qsense.data.serialization.JsonProviders
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class VisionSerializationTest {

    @Test
    fun responseDecodesFromServerPayload() {
        val json = """
            {"requestId":"a","annotatedImageB64":"x",
             "detections":[{"cls":"blade","score":1.0,"box":[1,2,3,4]}],
             "diagnosis":"Detected: 1 blade."}
        """.trimIndent()
        val r = JsonProviders.strict.decodeFromString<VisionResponse>(json)
        assertEquals("a", r.requestId)
        assertEquals("Detected: 1 blade.", r.diagnosis)
        assertEquals("blade", r.detections.single().cls)
        assertEquals(listOf(1, 2, 3, 4), r.detections.single().box)
    }

    @Test
    fun requestEncodesFieldsExpectedByServer() {
        val req = VisionRequest("r1", "M1", "blade-1", "b64data", "2026-07-12T00:00:00Z")
        val json = JsonProviders.strict.encodeToString(req)
        val back = JsonProviders.strict.decodeFromString<VisionRequest>(json)
        assertEquals(req, back)
        // field names the Python handler reads
        listOf("requestId", "machineNo", "partNo", "imageB64", "timestamp").forEach {
            assertEquals(true, json.contains("\"$it\""), "missing field $it")
        }
    }
}
