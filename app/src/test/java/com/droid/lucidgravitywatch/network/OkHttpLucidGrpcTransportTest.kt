package com.droid.lucidgravitywatch.network

import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpLucidGrpcTransportTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun unaryCall_sendsGrpcFramedProtobufWithoutContentLengthHeaderAndParsesResponse() = runTest {
        val expectedReplyProto = LucidProtobufCodec.encodeWakeupVehicleRequest("veh-ack-01")
        val framedReply = LucidProtobufCodec.encodeGrpcFrame(expectedReplyProto)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("content-type", "application/grpc")
                .addHeader("grpc-status", "0")
                .setBody(Buffer().write(framedReply))
        )

        val transport = OkHttpLucidGrpcTransport(
            baseUrlOverride = server.url("/").toString()
        )
        val reqProto = LucidProtobufCodec.encodeDoorLocksControlRequest(
            vehicleId = "veh-gravity-01",
            lockState = LockState.LOCKED
        )

        val actualReply = transport.unaryCall(
            region = LucidRegion.US,
            serviceMethod = "/mobilegateway.protos.VehicleStateService/DoorLocksControl",
            requestProtobuf = reqProto,
            bearerToken = "live-jwt-token-123"
        )

        assertArrayEquals(expectedReplyProto, actualReply)

        val recorded = server.takeRequest()
        assertEquals("/mobilegateway.protos.VehicleStateService/DoorLocksControl", recorded.path)
        assertEquals("Bearer live-jwt-token-123", recorded.getHeader("authorization"))
        assertEquals("application/grpc", recorded.getHeader("content-type"))
        assertEquals("trailers", recorded.getHeader("te"))
        assertNull(recorded.getHeader("Content-Length"))
        assertNull(recorded.getHeader("Accept-Encoding"))

        val sentBytes = recorded.body.readByteArray()
        val unwrappedReq = LucidProtobufCodec.decodeGrpcFrame(sentBytes)
        assertArrayEquals(reqProto, unwrappedReq)
    }

    @Test(expected = LucidApiException::class)
    fun unaryCall_nonZeroGrpcStatus_throwsLucidApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("content-type", "application/grpc+proto")
                .addHeader("grpc-status", "16")
                .addHeader("grpc-message", "UNAUTHENTICATED%3A%20Invalid%20token")
                .setBody(Buffer().write(LucidProtobufCodec.encodeGrpcFrame(ByteArray(0))))
        )

        val transport = OkHttpLucidGrpcTransport(
            baseUrlOverride = server.url("/").toString()
        )
        transport.unaryCall(
            region = LucidRegion.US,
            serviceMethod = "/mobilegateway.protos.LoginSession/GetUserVehicles",
            requestProtobuf = ByteArray(0),
            bearerToken = "expired-token"
        )
    }
}
