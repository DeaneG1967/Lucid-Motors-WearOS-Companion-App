package com.droid.lucidgravitywatch.repository

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.network.LucidGrpcTransport
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import com.droid.lucidgravitywatch.storage.InMemorySessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LucidCloudVehicleRepositoryTest {

    private class FakeGrpcTransport : LucidGrpcTransport {
        val recordedCalls = mutableListOf<Triple<LucidRegion, String, String?>>()
        val recordedPayloads = mutableListOf<ByteArray>()
        var loginResponseBytes: ByteArray = ByteArray(0)
        var vehiclesResponseBytes: ByteArray = ByteArray(0)

        override suspend fun unaryCall(
            region: LucidRegion,
            serviceMethod: String,
            requestProtobuf: ByteArray,
            bearerToken: String?,
            extraHeaders: Map<String, String>
        ): ByteArray {
            recordedCalls.add(Triple(region, serviceMethod, bearerToken))
            recordedPayloads.add(requestProtobuf)
            return when (serviceMethod) {
                "/mobilegateway.protos.LoginSession/Login" -> loginResponseBytes
                "/mobilegateway.protos.LoginSession/GetUserVehicles" -> vehiclesResponseBytes
                else -> ByteArray(0)
            }
        }
    }

    @Test
    fun login_persistsSessionAndSelectsGravityVehicle() = runTest {
        val transport = FakeGrpcTransport()
        val sessionStore = InMemorySessionStore()
        transport.loginResponseBytes = LucidProtobufCodec.buildLoginResponseBytesForTest(
            uid = "user-01",
            idToken = "jwt-token-live",
            refreshToken = "refresh-token-live",
            gigyaJwt = "gigya-token",
            expiryTimeSec = ((System.currentTimeMillis() / 1000L) + 3600L).toInt(),
            email = "driver@lucidmotors.com",
            firstName = "Elena",
            lastName = "Vance",
            vehicleId = "veh-gravity-777",
            vin = "50EA1GBA7RA000777",
            nickname = "Gravity Dream",
            modelCode = 2, // MODEL_GRAVITY
            powerCode = 1, // POWER_STATE_SLEEP
            lockCode = 2 // LOCK_STATE_LOCKED
        )

        val repo = LucidCloudVehicleRepository(transport, sessionStore)
        val vehicles = repo.login(
            email = "driver@lucidmotors.com",
            password = "secretPassword",
            region = LucidRegion.US
        )

        assertEquals(1, vehicles.size)
        assertEquals("veh-gravity-777", vehicles.first().vehicleId)
        assertEquals(VehicleModel.GRAVITY, vehicles.first().model)

        val savedSession = sessionStore.loadSession()
        assertNotNull(savedSession)
        assertEquals("jwt-token-live", savedSession?.idToken)
        assertEquals("veh-gravity-777", sessionStore.getSelectedVehicleId())
    }

    @Test
    fun commandsOnSleepingVehicle_automaticallyWakeUpVehicleFirst() = runTest {
        val transport = FakeGrpcTransport()
        val sessionStore = InMemorySessionStore()
        val futureExpiry = ((System.currentTimeMillis() / 1000L) + 3600L).toInt()
        sessionStore.saveSession(
            LucidSession(
                uid = "user-01",
                idToken = "jwt-token-live",
                refreshToken = "refresh-token-live",
                gigyaJwt = "gigya-token",
                expiryTimeSec = futureExpiry,
                userEmail = "driver@lucidmotors.com",
                firstName = "Elena",
                lastName = "Vance",
                region = LucidRegion.US
            )
        )
        transport.vehiclesResponseBytes = LucidProtobufCodec.buildGetUserVehiclesResponseBytesForTest(
            vehicleId = "veh-gravity-777",
            vin = "50EA1GBA7RA000777",
            nickname = "Gravity Dream",
            modelCode = 2,
            powerCode = 1, // SLEEP -> must trigger WakeupVehicle first!
            lockCode = 2
        )

        val repo = LucidCloudVehicleRepository(transport, sessionStore)
        val initialVehicles = repo.fetchVehicles()
        val sleepingGravity = initialVehicles.first()
        assertEquals(PowerState.SLEEP, sleepingGravity.powerState)

        transport.recordedCalls.clear()
        repo.setDoorLocks(sleepingGravity, LockState.UNLOCKED)

        assertTrue(transport.recordedCalls.size >= 2)
        assertEquals(
            "/mobilegateway.protos.VehicleStateService/WakeupVehicle",
            transport.recordedCalls[0].second
        )
        assertEquals(
            "/mobilegateway.protos.VehicleStateService/DoorLocksControl",
            transport.recordedCalls[1].second
        )
    }

    @Test
    fun allClosureAndClimateCommands_invokeCorrectLucidGrpcEndpoints() = runTest {
        val transport = FakeGrpcTransport()
        val sessionStore = InMemorySessionStore()
        val futureExpiry = ((System.currentTimeMillis() / 1000L) + 3600L).toInt()
        sessionStore.saveSession(
            LucidSession(
                uid = "user-01",
                idToken = "jwt-token-live",
                refreshToken = "refresh-token-live",
                gigyaJwt = "gigya-token",
                expiryTimeSec = futureExpiry,
                userEmail = "driver@lucidmotors.com",
                firstName = "Elena",
                lastName = "Vance",
                region = LucidRegion.EU
            )
        )
        transport.vehiclesResponseBytes = LucidProtobufCodec.buildGetUserVehiclesResponseBytesForTest(
            vehicleId = "veh-gravity-eu",
            vin = "50EA1GBA7RA000999",
            nickname = "Gravity Touring",
            modelCode = 2,
            powerCode = 9, // CLOUD_1 (awake)
            lockCode = 2
        )

        val repo = LucidCloudVehicleRepository(transport, sessionStore)
        val gravity = repo.fetchVehicles().first()
        transport.recordedCalls.clear()

        repo.setFrunkState(gravity, DoorState.OPEN)
        repo.setRearTrunkState(gravity, DoorState.OPEN)
        repo.setChargePortState(gravity, DoorState.OPEN)
        repo.setCabinTemperature(gravity, 21.5)
        repo.setDefrost(gravity, DefrostState.ON)
        repo.setSeatClimate(gravity, SeatClimateMode.HIGH, SeatClimateMode.OFF, SeatClimateMode.MEDIUM)
        repo.setSteeringWheelHeater(gravity, SteeringWheelHeaterLevel.LEVEL_2)
        repo.setBatteryPreconditioning(gravity, BatteryPreconStatus.ON)
        repo.setChargeLimit(gravity, 80)
        repo.setChargingActive(gravity, true)

        val methods = transport.recordedCalls.map { it.second }
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/FrontCargoControl"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/RearCargoControl"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/ControlChargePort"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/SetCabinTemperature"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/HvacDefrostControl"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/SeatClimateControl"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/SteeringWheelHeater"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/SetBatteryPrecon"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/SetChargeLimit"))
        assertTrue(methods.contains("/mobilegateway.protos.VehicleStateService/ChargeControl"))

        repo.logout()
        assertNull(sessionStore.loadSession())
    }
}
