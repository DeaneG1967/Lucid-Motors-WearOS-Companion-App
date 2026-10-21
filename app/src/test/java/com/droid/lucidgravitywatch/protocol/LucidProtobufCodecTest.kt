package com.droid.lucidgravitywatch.protocol

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeAction
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LightAction
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class LucidProtobufCodecTest {

    @Test
    fun grpcFrame_roundTrip_encodesAndDecodes5ByteHeaderAndGzipFrame() {
        val payload = byteArrayOf(0x08, 0x01, 0x12, 0x04, 0x74, 0x65, 0x73, 0x74)
        val framed = LucidProtobufCodec.encodeGrpcFrame(payload)
        assertEquals(payload.size + 5, framed.size)
        assertEquals(0.toByte(), framed[0])
        assertEquals(0.toByte(), framed[1])
        assertEquals(0.toByte(), framed[2])
        assertEquals(0.toByte(), framed[3])
        assertEquals(payload.size.toByte(), framed[4])

        val unwrapped = LucidProtobufCodec.decodeGrpcFrame(framed)
        assertArrayEquals(payload, unwrapped)

        val gzipBytes = ByteArrayOutputStream().also { baos ->
            GZIPOutputStream(baos).use { it.write(payload) }
        }.toByteArray()
        val gzipFrame = ByteArray(5 + gzipBytes.size).also { frame ->
            frame[0] = 1
            frame[4] = gzipBytes.size.toByte()
            System.arraycopy(gzipBytes, 0, frame, 5, gzipBytes.size)
        }
        assertArrayEquals(payload, LucidProtobufCodec.decodeGrpcFrame(gzipFrame))
    }

    @Test
    fun loginRequest_encodesRequiredLucidFields() {
        val encoded = LucidProtobufCodec.encodeLoginRequest(
            username = "owner@lucidmotors.com",
            password = "gravity-secret-password",
            deviceId = "a1b2c3d4e5f6",
            locale = "en_US",
            clientName = "LucidGravityWatch/1.0.0"
        )
        val fields = LucidProtobufCodec.parseRawFields(encoded)
        assertEquals("owner@lucidmotors.com", fields.getString(1))
        assertEquals("gravity-secret-password", fields.getString(2))
        assertEquals(1L, fields.getVarint(3)) // NOTIFICATION_CHANNEL_FIREBASE
        assertEquals(2L, fields.getVarint(4)) // OS_ANDROID
        assertEquals("a1b2c3d4e5f6", fields.getString(5))
        assertEquals("en_US", fields.getString(6))
        assertEquals("a1b2c3d4e5f6", fields.getString(7))
        assertEquals("LucidGravityWatch/1.0.0", fields.getString(8))
    }

    @Test
    fun decodeLoginResponse_parsesSessionProfileAndGravityVehicleTelemetry() {
        val sampleResponseBytes = LucidProtobufCodec.buildLoginResponseBytesForTest(
            uid = "uid-gravity-001",
            idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.gravity",
            refreshToken = "refresh-token-gravity-99",
            gigyaJwt = "gigya-jwt-01",
            expiryTimeSec = 1890000000,
            email = "owner@lucidmotors.com",
            firstName = "Alex",
            lastName = "Rivera",
            vehicleId = "veh-gravity-gt-01",
            vin = "50EA1GBA7RA000123",
            nickname = "Gravity GT",
            modelCode = 2, // MODEL_GRAVITY
            variantCode = 2, // MODEL_VARIANT_GRAND_TOURING
            paintColorCode = 11, // PAINT_COLOR_AURORA_GREEN
            powerCode = 9, // POWER_STATE_CLOUD_1
            lockCode = 2, // LOCK_STATE_LOCKED
            walkawayCode = 2, // WALKAWAY_ACTIVE
            frunkCode = 2, // DOOR_STATE_CLOSED
            rearTrunkCode = 2, // DOOR_STATE_CLOSED
            chargePortCode = 1, // DOOR_STATE_OPEN
            batteryPercent = 82.5,
            remainingRangeKm = 560.0,
            batteryKwh = 98.4,
            batteryCapacityKwh = 120.0,
            batteryPreconCode = 1, // BATTERY_PRECON_OFF
            chargeStateCode = 8, // CHARGE_STATE_CHARGING
            chargeLimitPercent = 85.0,
            chargeRateKw = 19.2,
            chargeRateMph = 58.0,
            sessionMinutesRemaining = 24,
            interiorTempC = 21.5,
            exteriorTempC = 14.0,
            targetTempC = 21.0,
            hvacPowerCode = 3, // HVAC_PRECONDITION
            defrostCode = 2, // DEFROST_OFF
            maxAcCode = 1, // MAX_AC_STATE_OFF
            driverSeatHeatCode = 4, // SEAT_CLIMATE_MODE_MEDIUM
            driverSeatVentCode = 2, // SEAT_CLIMATE_MODE_OFF
            passengerSeatHeatCode = 3, // SEAT_CLIMATE_MODE_LOW
            steeringHeaterLevelCode = 3, // STEERING_WHEEL_HEATER_LEVEL_2
            odometerKm = 4120.5,
            softwareVersion = "2.5.12",
            lastUpdatedMs = 1792113000000L
        )

        val result = LucidProtobufCodec.decodeLoginResponse(sampleResponseBytes, LucidRegion.US)
        assertEquals("uid-gravity-001", result.session.uid)
        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.gravity", result.session.idToken)
        assertEquals("refresh-token-gravity-99", result.session.refreshToken)
        assertEquals("owner@lucidmotors.com", result.session.userEmail)
        assertEquals("Alex", result.session.firstName)
        assertEquals("Rivera", result.session.lastName)
        assertEquals(1, result.vehicles.size)

        val vehicle = result.vehicles.first()
        assertEquals("veh-gravity-gt-01", vehicle.vehicleId)
        assertEquals("50EA1GBA7RA000123", vehicle.vin)
        assertEquals("Gravity GT", vehicle.nickname)
        assertEquals(VehicleModel.GRAVITY, vehicle.model)
        assertEquals("Grand Touring", vehicle.variantName)
        assertEquals("Aurora Green", vehicle.paintColorName)
        assertEquals(PowerState.CLOUD_1, vehicle.powerState)
        assertEquals(LockState.LOCKED, vehicle.lockState)
        assertEquals(WalkawayState.ACTIVE, vehicle.walkawayState)
        assertEquals(DoorState.CLOSED, vehicle.frunkState)
        assertEquals(DoorState.CLOSED, vehicle.rearTrunkState)
        assertEquals(DoorState.OPEN, vehicle.chargePortState)
        assertEquals(82.5, vehicle.batteryPercent, 0.01)
        assertEquals(560.0, vehicle.remainingRangeKm, 0.1)
        assertEquals(348, vehicle.remainingRangeMiles)
        assertEquals(ChargeState.CHARGING, vehicle.chargeState)
        assertEquals(85, vehicle.chargeLimitPercent)
        assertEquals(19.2, vehicle.chargeRateKw, 0.01)
        assertEquals(24, vehicle.sessionMinutesRemaining)
        assertEquals(21.5, vehicle.interiorTempCelsius, 0.01)
        assertEquals(14.0, vehicle.exteriorTempCelsius, 0.01)
        assertEquals(21.0, vehicle.targetTempCelsius, 0.01)
        assertEquals(HvacPower.PRECONDITION, vehicle.hvacPower)
        assertEquals(DefrostState.OFF, vehicle.defrostState)
        assertEquals(SeatClimateMode.MEDIUM, vehicle.driverSeatHeatMode)
        assertEquals(SteeringWheelHeaterLevel.LEVEL_2, vehicle.steeringHeaterLevel)
        assertEquals("2.5.12", vehicle.softwareVersion)
    }

    @Test
    fun controlRequests_encodeExactFieldNumbersAndValues() {
        val lockReq = LucidProtobufCodec.encodeDoorLocksControlRequest(
            vehicleId = "veh-gravity-1",
            lockState = LockState.LOCKED,
            doors = listOf(1, 2, 3, 4)
        )
        val lockFields = LucidProtobufCodec.parseRawFields(lockReq)
        assertEquals(2L, lockFields.getVarint(2))
        assertEquals("veh-gravity-1", lockFields.getString(3))

        val frunkReq = LucidProtobufCodec.encodeFrontCargoControlRequest(
            vehicleId = "veh-gravity-1",
            closureState = DoorState.OPEN
        )
        val frunkFields = LucidProtobufCodec.parseRawFields(frunkReq)
        assertEquals(1L, frunkFields.getVarint(1))
        assertEquals("veh-gravity-1", frunkFields.getString(2))

        val trunkReq = LucidProtobufCodec.encodeRearCargoControlRequest(
            vehicleId = "veh-gravity-1",
            closureState = DoorState.OPEN
        )
        val trunkFields = LucidProtobufCodec.parseRawFields(trunkReq)
        assertEquals(1L, trunkFields.getVarint(1))
        assertEquals("veh-gravity-1", trunkFields.getString(2))

        val chargePortReq = LucidProtobufCodec.encodeControlChargePortRequest(
            vehicleId = "veh-gravity-1",
            closureState = DoorState.CLOSED
        )
        val chargePortFields = LucidProtobufCodec.parseRawFields(chargePortReq)
        assertEquals(2L, chargePortFields.getVarint(1))
        assertEquals("veh-gravity-1", chargePortFields.getString(2))

        val cabinTempReq = LucidProtobufCodec.encodeSetCabinTemperatureRequest(
            vehicleId = "veh-gravity-1",
            temperatureCelsius = 22.0,
            power = HvacPower.PRECONDITION
        )
        val cabinFields = LucidProtobufCodec.parseRawFields(cabinTempReq)
        assertEquals(22.0, cabinFields.getDouble(1), 0.001)
        assertEquals(3L, cabinFields.getVarint(2))
        assertEquals("veh-gravity-1", cabinFields.getString(3))

        val defrostReq = LucidProtobufCodec.encodeHvacDefrostControlRequest(
            vehicleId = "veh-gravity-1",
            defrostState = DefrostState.ON
        )
        val defrostFields = LucidProtobufCodec.parseRawFields(defrostReq)
        assertEquals("veh-gravity-1", defrostFields.getString(1))
        assertEquals(1L, defrostFields.getVarint(2))

        val seatReq = LucidProtobufCodec.encodeSeatClimateControlRequest(
            vehicleId = "veh-gravity-1",
            driverHeat = SeatClimateMode.HIGH,
            driverVent = SeatClimateMode.OFF,
            passengerHeat = SeatClimateMode.LOW
        )
        val seatFields = LucidProtobufCodec.parseRawFields(seatReq)
        assertEquals("veh-gravity-1", seatFields.getString(1))
        assertEquals(5L, seatFields.getVarint(2))
        assertEquals(5L, seatFields.getVarint(4))
        assertEquals(2L, seatFields.getVarint(6))
        assertEquals(3L, seatFields.getVarint(8))

        val steeringReq = LucidProtobufCodec.encodeSteeringWheelHeaterRequest(
            vehicleId = "veh-gravity-1",
            level = SteeringWheelHeaterLevel.LEVEL_3
        )
        val steeringFields = LucidProtobufCodec.parseRawFields(steeringReq)
        assertEquals("veh-gravity-1", steeringFields.getString(1))
        assertEquals(4L, steeringFields.getVarint(2))

        val chargeLimitReq = LucidProtobufCodec.encodeSetChargeLimitRequest(
            vehicleId = "veh-gravity-1",
            limitPercent = 90
        )
        val limitFields = LucidProtobufCodec.parseRawFields(chargeLimitReq)
        assertEquals(90L, limitFields.getVarint(1))
        assertEquals("veh-gravity-1", limitFields.getString(2))

        val chargeActionReq = LucidProtobufCodec.encodeChargeControlRequest(
            vehicleId = "veh-gravity-1",
            action = ChargeAction.START
        )
        val actionFields = LucidProtobufCodec.parseRawFields(chargeActionReq)
        assertEquals(1L, actionFields.getVarint(1))
        assertEquals("veh-gravity-1", actionFields.getString(2))

        val batteryPreconReq = LucidProtobufCodec.encodeSetBatteryPreconRequest(
            vehicleId = "veh-gravity-1",
            status = BatteryPreconStatus.ON
        )
        val preconFields = LucidProtobufCodec.parseRawFields(batteryPreconReq)
        assertEquals("veh-gravity-1", preconFields.getString(1))
        assertEquals(2L, preconFields.getVarint(2))

        val lightsReq = LucidProtobufCodec.encodeLightsControlRequest(
            vehicleId = "veh-gravity-1",
            action = LightAction.FLASH
        )
        val lightsFields = LucidProtobufCodec.parseRawFields(lightsReq)
        assertEquals(1L, lightsFields.getVarint(1))
        assertEquals("veh-gravity-1", lightsFields.getString(2))

        val hornReq = LucidProtobufCodec.encodeHonkHornRequest("veh-gravity-1")
        val hornFields = LucidProtobufCodec.parseRawFields(hornReq)
        assertEquals("veh-gravity-1", hornFields.getString(1))
        assertTrue(hornReq.isNotEmpty())
    }
}
