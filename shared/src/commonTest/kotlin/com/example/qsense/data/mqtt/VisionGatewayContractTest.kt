package com.example.qsense.data.mqtt

import com.example.qsense.domain.model.VisionRequest
import com.example.qsense.domain.model.VisionResponse
import com.example.qsense.testutil.FakeMqttGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VisionGatewayContractTest {

    @Test
    fun publishRecordsRequest() = runTest {
        val gw = FakeMqttGateway()
        gw.publishVisionRequest(VisionRequest("r1", "M1", "blade", "b64", "t"))
        assertEquals("r1", gw.publishedVisionRequests.single().requestId)
    }

    @Test
    fun collectorReceivesEmittedResponse() = runTest {
        val gw = FakeMqttGateway()
        val expected = VisionResponse("r1", "img", emptyList(), "ok")
        gw.visionResponsesFlow.tryEmit(expected)
        assertEquals(expected, gw.visionResponses.first())
    }
}
