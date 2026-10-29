package com.droid.lucidgravitywatch.protocol

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeAction
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LightAction
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * Pure Kotlin binary Protocol Buffers (proto3) wire codec and gRPC 5-byte framing handler
 * for the Lucid Motors Mobile Gateway API (`package mobilegateway.protos`).
 */
object LucidProtobufCodec {

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    data class DecodedLoginResult(
        val session: LucidSession,
        val vehicles: List<GravityVehicleSnapshot>
    )

    class ProtoFieldMap(
        val varints: Map<Int, List<Long>>,
        val fixed64s: Map<Int, List<Long>>,
        val fixed32s: Map<Int, List<Int>>,
        val bytesList: Map<Int, List<ByteArray>>
    ) {
        fun getVarint(fieldNumber: Int, default: Long = 0L): Long =
            varints[fieldNumber]?.lastOrNull() ?: default

        fun getInt(fieldNumber: Int, default: Int = 0): Int =
            getVarint(fieldNumber, default.toLong()).toInt()

        fun getDouble(fieldNumber: Int, default: Double = 0.0): Double {
            val bits = fixed64s[fieldNumber]?.lastOrNull() ?: return default
            return Double.fromBits(bits)
        }

        fun getString(fieldNumber: Int, default: String = ""): String {
            val bytes = bytesList[fieldNumber]?.lastOrNull() ?: return default
            return bytes.toString(Charsets.UTF_8)
        }

        fun getSubMessage(fieldNumber: Int): ProtoFieldMap? {
            val bytes = bytesList[fieldNumber]?.lastOrNull() ?: return null
            return parseRawFields(bytes)
        }

        fun getSubMessages(fieldNumber: Int): List<ProtoFieldMap> {
            val list = bytesList[fieldNumber] ?: return emptyList()
            return list.map { parseRawFields(it) }
        }
    }

    class ProtoWriter {
        private val out = ByteArrayOutputStream()

        fun writeTag(fieldNumber: Int, wireType: Int) {
            writeRawVarint(((fieldNumber shl 3) or wireType).toLong())
        }

        fun writeRawVarint(value: Long) {
            var v = value
            while (true) {
                if ((v and 0x7FL.inv()) == 0L) {
                    out.write(v.toInt())
                    return
                } else {
                    out.write(((v.toInt() and 0x7F) or 0x80))
                    v = v ushr 7
                }
            }
        }

        fun writeVarintField(fieldNumber: Int, value: Long) {
            if (value == 0L) return
            writeTag(fieldNumber, WIRE_VARINT)
            writeRawVarint(value)
        }

        fun writeVarintFieldAlways(fieldNumber: Int, value: Long) {
            writeTag(fieldNumber, WIRE_VARINT)
            writeRawVarint(value)
        }

        fun writeIntField(fieldNumber: Int, value: Int) {
            writeVarintField(fieldNumber, value.toLong())
        }

        fun writeDoubleField(fieldNumber: Int, value: Double) {
            if (value == 0.0) return
            writeTag(fieldNumber, WIRE_FIXED64)
            val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buf.putLong(value.toRawBits())
            out.write(buf.array())
        }

        fun writeStringField(fieldNumber: Int, value: String) {
            if (value.isEmpty()) return
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeBytesField(fieldNumber, bytes)
        }

        fun writeBytesField(fieldNumber: Int, bytes: ByteArray) {
            if (bytes.isEmpty()) return
            writeTag(fieldNumber, WIRE_LENGTH_DELIMITED)
            writeRawVarint(bytes.size.toLong())
            out.write(bytes)
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    fun encodeGrpcFrame(protobufBytes: ByteArray): ByteArray {
        val len = protobufBytes.size
        val frame = ByteArray(5 + len)
        frame[0] = 0 // Uncompressed flag
        frame[1] = ((len ushr 24) and 0xFF).toByte()
        frame[2] = ((len ushr 16) and 0xFF).toByte()
        frame[3] = ((len ushr 8) and 0xFF).toByte()
        frame[4] = (len and 0xFF).toByte()
        System.arraycopy(protobufBytes, 0, frame, 5, len)
        return frame
    }

    fun decodeGrpcFrame(framedBytes: ByteArray): ByteArray {
        if (framedBytes.isEmpty()) return ByteArray(0)
        if (framedBytes.size < 5) {
            throw IllegalArgumentException("Invalid gRPC frame: size=${framedBytes.size}")
        }
        val compressed = framedBytes[0].toInt() and 0xFF
        val length = ((framedBytes[1].toInt() and 0xFF) shl 24) or
            ((framedBytes[2].toInt() and 0xFF) shl 16) or
            ((framedBytes[3].toInt() and 0xFF) shl 8) or
            (framedBytes[4].toInt() and 0xFF)
        if (length == 0) return ByteArray(0)
        val end = (5 + length).coerceAtMost(framedBytes.size)
        val payload = framedBytes.copyOfRange(5, end)
        return when (compressed) {
            0 -> payload
            1 -> GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            else -> throw IllegalArgumentException("Unsupported gRPC compression flag: $compressed")
        }
    }

    fun parseRawFields(bytes: ByteArray): ProtoFieldMap {
        val varints = mutableMapOf<Int, MutableList<Long>>()
        val fixed64s = mutableMapOf<Int, MutableList<Long>>()
        val fixed32s = mutableMapOf<Int, MutableList<Int>>()
        val bytesList = mutableMapOf<Int, MutableList<ByteArray>>()

        var index = 0
        val limit = bytes.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (index < limit && shift < 64) {
                val b = bytes[index++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        while (index < limit) {
            val tag = readVarint().toInt()
            if (tag == 0) break
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                WIRE_VARINT -> {
                    val v = readVarint()
                    varints.getOrPut(fieldNumber) { mutableListOf() }.add(v)
                }
                WIRE_FIXED64 -> {
                    if (index + 8 <= limit) {
                        val buf = ByteBuffer.wrap(bytes, index, 8).order(ByteOrder.LITTLE_ENDIAN)
                        fixed64s.getOrPut(fieldNumber) { mutableListOf() }.add(buf.long)
                        index += 8
                    } else {
                        break
                    }
                }
                WIRE_LENGTH_DELIMITED -> {
                    val len = readVarint().toInt()
                    if (len >= 0 && index + len <= limit) {
                        val sub = bytes.copyOfRange(index, index + len)
                        bytesList.getOrPut(fieldNumber) { mutableListOf() }.add(sub)
                        index += len
                    } else {
                        break
                    }
                }
                WIRE_FIXED32 -> {
                    if (index + 4 <= limit) {
                        val buf = ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.LITTLE_ENDIAN)
                        fixed32s.getOrPut(fieldNumber) { mutableListOf() }.add(buf.int)
                        index += 4
                    } else {
                        break
                    }
                }
                else -> break
            }
        }

        return ProtoFieldMap(varints, fixed64s, fixed32s, bytesList)
    }

    // --- LoginSession Requests & Responses ---

    fun encodeLoginRequest(
        username: String,
        password: String,
        deviceId: String,
        locale: String = "en_US",
        clientName: String = "LucidGravityWatch/1.0.0"
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, username)
        writer.writeStringField(2, password)
        writer.writeIntField(3, 1) // NOTIFICATION_CHANNEL_FIREBASE = 1
        writer.writeIntField(4, 2) // OS_ANDROID = 2
        writer.writeStringField(5, deviceId)
        writer.writeStringField(6, locale)
        writer.writeStringField(7, deviceId)
        writer.writeStringField(8, clientName)
        return writer.toByteArray()
    }

    fun decodeLoginResponse(bytes: ByteArray, region: LucidRegion): DecodedLoginResult {
        val root = parseRawFields(bytes)
        val uid = root.getString(1)
        val sessionSub = root.getSubMessage(2)
        val profileSub = root.getSubMessage(3)

        val idToken = sessionSub?.getString(1).orEmpty()
        val expiryTimeSec = sessionSub?.getInt(2) ?: 0
        val refreshToken = sessionSub?.getString(3).orEmpty()
        val gigyaJwt = sessionSub?.getString(4).orEmpty()

        val email = profileSub?.getString(1).orEmpty()
        val firstName = profileSub?.getString(5).orEmpty()
        val lastName = profileSub?.getString(6).orEmpty()

        val vehicles = root.getSubMessages(4).map { decodeVehicleMessage(it) }

        return DecodedLoginResult(
            session = LucidSession(
                uid = uid,
                idToken = idToken,
                refreshToken = refreshToken,
                gigyaJwt = gigyaJwt,
                expiryTimeSec = expiryTimeSec,
                userEmail = email,
                firstName = firstName,
                lastName = lastName,
                region = region
            ),
            vehicles = vehicles
        )
    }

    fun encodeGetNewJwtTokenRequest(refreshToken: String): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, refreshToken)
        return writer.toByteArray()
    }

    fun decodeGetNewJwtTokenResponse(bytes: ByteArray, existingSession: LucidSession): LucidSession {
        val root = parseRawFields(bytes)
        val sessionSub = root.getSubMessage(1) ?: return existingSession
        val newIdToken = sessionSub.getString(1).ifEmpty { existingSession.idToken }
        val newExpiry = sessionSub.getInt(2, existingSession.expiryTimeSec)
        val newRefresh = sessionSub.getString(3).ifEmpty { existingSession.refreshToken }
        val newGigya = sessionSub.getString(4).ifEmpty { existingSession.gigyaJwt }
        return existingSession.copy(
            idToken = newIdToken,
            expiryTimeSec = newExpiry,
            refreshToken = newRefresh,
            gigyaJwt = newGigya
        )
    }

    fun encodeGetUserVehiclesRequest(): ByteArray = ByteArray(0)

    fun decodeGetUserVehiclesResponse(bytes: ByteArray): List<GravityVehicleSnapshot> {
        val root = parseRawFields(bytes)
        return root.getSubMessages(1).map { decodeVehicleMessage(it) }
    }

    fun encodeGetVehicleStateRequest(vehicleId: String): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        return writer.toByteArray()
    }

    /**
     * GetVehicleState returns only the state block (field 2, the same VehicleState message as
     * GetUserVehicles' field 4); identity fields such as VIN and nickname are carried over from [vehicle].
     *
     * A waking car's first reports omit whole sections (e.g. no battery block, which would otherwise
     * decode as 0%), so any section missing from the response keeps its value from [vehicle].
     */
    fun decodeGetVehicleStateResponse(bytes: ByteArray, vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
        val state = parseRawFields(bytes).getSubMessage(2) ?: return vehicle
        val decoded = decodeVehicleState(
            state = state,
            vehicleId = vehicle.vehicleId,
            vin = vehicle.vin,
            nickname = vehicle.nickname,
            model = vehicle.model,
            variantName = vehicle.variantName,
            paintColorName = vehicle.paintColorName
        )
        return decoded.keepSectionsMissingFrom(state, previous = vehicle)
    }

    private fun GravityVehicleSnapshot.keepSectionsMissingFrom(
        state: ProtoFieldMap,
        previous: GravityVehicleSnapshot
    ): GravityVehicleSnapshot {
        fun missing(fieldNumber: Int) = state.bytesList[fieldNumber].isNullOrEmpty()
        var merged = this
        if (state.varints[2].isNullOrEmpty()) {
            merged = merged.copy(powerState = previous.powerState)
        }
        // While the car's battery management is still booting after a wake, the battery block
        // carries placeholder values (255% charge, 655 kWh) rather than being omitted.
        val rawBatteryPercent = state.getSubMessage(1)?.fixed64s?.get(2)?.lastOrNull()?.let(Double::fromBits)
        if (missing(1) || rawBatteryPercent == null || rawBatteryPercent !in 0.0..100.0) {
            merged = merged.copy(
                batteryPercent = previous.batteryPercent,
                remainingRangeKm = previous.remainingRangeKm,
                batteryKwh = previous.batteryKwh,
                batteryCapacityKwh = previous.batteryCapacityKwh,
                batteryPreconStatus = previous.batteryPreconStatus
            )
        }
        if (missing(3)) {
            merged = merged.copy(
                interiorTempCelsius = previous.interiorTempCelsius,
                exteriorTempCelsius = previous.exteriorTempCelsius
            )
        }
        if (missing(4)) {
            merged = merged.copy(
                lockState = previous.lockState,
                walkawayState = previous.walkawayState,
                frunkState = previous.frunkState,
                rearTrunkState = previous.rearTrunkState,
                chargePortState = previous.chargePortState,
                frontLeftDoor = previous.frontLeftDoor,
                frontRightDoor = previous.frontRightDoor,
                rearLeftDoor = previous.rearLeftDoor,
                rearRightDoor = previous.rearRightDoor
            )
        }
        if (missing(6)) {
            merged = merged.copy(odometerKm = previous.odometerKm, softwareVersion = previous.softwareVersion)
        }
        if (missing(8)) {
            merged = merged.copy(
                chargeState = previous.chargeState,
                chargeLimitPercent = previous.chargeLimitPercent,
                chargeRateKw = previous.chargeRateKw,
                chargeRateMph = previous.chargeRateMph,
                sessionMinutesRemaining = previous.sessionMinutesRemaining
            )
        }
        if (missing(17)) {
            merged = merged.copy(
                hvacPower = previous.hvacPower,
                defrostState = previous.defrostState,
                maxAcEnabled = previous.maxAcEnabled,
                driverSeatHeatMode = previous.driverSeatHeatMode,
                driverSeatVentMode = previous.driverSeatVentMode,
                passengerSeatHeatMode = previous.passengerSeatHeatMode,
                steeringHeaterLevel = previous.steeringHeaterLevel,
                targetTempCelsius = previous.targetTempCelsius
            )
        }
        if (lastUpdatedMs == 0L) {
            merged = merged.copy(lastUpdatedMs = previous.lastUpdatedMs)
        }
        return merged
    }

    private fun decodeVehicleMessage(veh: ProtoFieldMap): GravityVehicleSnapshot {
        val vehicleId = veh.getString(1)
        val config = veh.getSubMessage(3)

        val vin = config?.getString(1).orEmpty()
        val model = VehicleModel.fromProto(config?.getInt(2) ?: 0)
        val variantName = variantNameFromProto(config?.getInt(3) ?: 0)
        val nickname = config?.getString(5).orEmpty().ifBlank {
            if (model == VehicleModel.GRAVITY) "Lucid Gravity" else "Lucid ${model.displayName}"
        }
        val paintColorName = paintColorNameFromProto(config?.getInt(6) ?: 0)

        return decodeVehicleState(
            state = veh.getSubMessage(4),
            vehicleId = vehicleId,
            vin = vin,
            nickname = nickname,
            model = model,
            variantName = variantName,
            paintColorName = paintColorName
        )
    }

    private fun decodeVehicleState(
        state: ProtoFieldMap?,
        vehicleId: String,
        vin: String,
        nickname: String,
        model: VehicleModel,
        variantName: String,
        paintColorName: String
    ): GravityVehicleSnapshot {
        val battery = state?.getSubMessage(1)
        val powerState = PowerState.fromProto(state?.getInt(2) ?: 0)
        val cabin = state?.getSubMessage(3)
        val body = state?.getSubMessage(4)
        val lastUpdatedMs = state?.getVarint(5) ?: 0L
        val chassis = state?.getSubMessage(6)
        val charging = state?.getSubMessage(8)
        val hvac = state?.getSubMessage(17)

        val remainingRangeKm = battery?.getDouble(1) ?: 0.0
        val batteryPercent = (battery?.getDouble(2) ?: 0.0).let {
            if (it in 0.0..100.0) it else 0.0
        }
        val batteryKwh = battery?.getDouble(3) ?: 0.0
        val batteryCapacityKwh = battery?.getDouble(4) ?: 0.0
        val batteryPreconStatus = BatteryPreconStatus.fromProto(battery?.getInt(9) ?: 0)

        val interiorTempCelsius = cabin?.getDouble(1) ?: 21.0
        val exteriorTempCelsius = cabin?.getDouble(2) ?: 20.0

        val lockState = LockState.fromProto(body?.getInt(1) ?: 0)
        val frunkState = DoorState.fromProto(body?.getInt(2) ?: 0)
        val rearTrunkState = DoorState.fromProto(body?.getInt(3) ?: 0)
        val frontLeftDoor = DoorState.fromProto(body?.getInt(4) ?: 0)
        val frontRightDoor = DoorState.fromProto(body?.getInt(5) ?: 0)
        val rearLeftDoor = DoorState.fromProto(body?.getInt(6) ?: 0)
        val rearRightDoor = DoorState.fromProto(body?.getInt(7) ?: 0)
        val chargePortState = DoorState.fromProto(body?.getInt(8) ?: 0)
        val walkawayState = WalkawayState.fromProto(body?.getInt(9) ?: 0)

        val odometerKm = chassis?.getDouble(1) ?: 0.0
        val softwareVersion = chassis?.getString(16).orEmpty()

        val chargeState = ChargeState.fromProto(charging?.getInt(1) ?: 0)
        val sessionMinutesRemaining = charging?.getInt(7) ?: 0
        val chargeLimitInt = charging?.getInt(8) ?: 0
        val chargeRateKw = charging?.getDouble(19) ?: 0.0
        val chargeRateMph = charging?.getDouble(12) ?: 0.0
        val chargeLimitDouble = charging?.getDouble(14) ?: 0.0
        val resolvedChargeLimit = if (chargeLimitDouble > 0.0) {
            chargeLimitDouble.roundToInt()
        } else if (chargeLimitInt > 0) {
            chargeLimitInt
        } else {
            80
        }

        val hvacPower = HvacPower.fromProto(hvac?.getInt(1) ?: 0)
        val defrostState = DefrostState.fromProto(hvac?.getInt(2) ?: 0)
        val maxAcEnabled = (hvac?.getInt(7) ?: 0) == 2 // MAX_AC_STATE_ON = 2
        val seats = hvac?.getSubMessage(8)
        val driverSeatHeatMode = SeatClimateMode.fromProto(seats?.getInt(1) ?: 2)
        val driverSeatVentMode = SeatClimateMode.fromProto(seats?.getInt(5) ?: 2)
        val passengerSeatHeatMode = SeatClimateMode.fromProto(seats?.getInt(7) ?: 2)
        val steeringHeaterLevel = SteeringWheelHeaterLevel.fromProto(hvac?.getInt(12) ?: 1)
        val targetTempCelsius = (hvac?.getDouble(13) ?: 0.0).let {
            if (it in 15.0..30.0) it else 21.0
        }

        return GravityVehicleSnapshot(
            vehicleId = vehicleId,
            vin = vin,
            nickname = nickname,
            model = model,
            variantName = variantName,
            paintColorName = paintColorName,
            powerState = powerState,
            lockState = lockState,
            walkawayState = walkawayState,
            frunkState = frunkState,
            rearTrunkState = rearTrunkState,
            chargePortState = chargePortState,
            frontLeftDoor = frontLeftDoor,
            frontRightDoor = frontRightDoor,
            rearLeftDoor = rearLeftDoor,
            rearRightDoor = rearRightDoor,
            batteryPercent = batteryPercent,
            remainingRangeKm = remainingRangeKm,
            batteryKwh = batteryKwh,
            batteryCapacityKwh = batteryCapacityKwh,
            batteryPreconStatus = batteryPreconStatus,
            chargeState = chargeState,
            chargeLimitPercent = resolvedChargeLimit,
            chargeRateKw = chargeRateKw,
            chargeRateMph = chargeRateMph,
            sessionMinutesRemaining = sessionMinutesRemaining,
            interiorTempCelsius = interiorTempCelsius,
            exteriorTempCelsius = exteriorTempCelsius,
            targetTempCelsius = targetTempCelsius,
            hvacPower = hvacPower,
            defrostState = defrostState,
            maxAcEnabled = maxAcEnabled,
            driverSeatHeatMode = driverSeatHeatMode,
            driverSeatVentMode = driverSeatVentMode,
            passengerSeatHeatMode = passengerSeatHeatMode,
            steeringHeaterLevel = steeringHeaterLevel,
            odometerKm = odometerKm,
            softwareVersion = softwareVersion,
            lastUpdatedMs = lastUpdatedMs
        )
    }

    private fun variantNameFromProto(code: Int): String = when (code) {
        1 -> "Dream Edition"
        2 -> "Grand Touring"
        3 -> "Touring"
        4 -> "Pure"
        5 -> "Sapphire"
        6 -> "Hyper"
        7 -> "Executive"
        else -> "Grand Touring"
    }

    private fun paintColorNameFromProto(code: Int): String = when (code) {
        1 -> "Eureka Gold"
        2 -> "Stellar White"
        3 -> "Infinite Black"
        4 -> "Cosmos Silver"
        5 -> "Quantum Grey"
        6 -> "Zenith Red"
        7 -> "Fathom Blue"
        9 -> "Sapphire Blue"
        10 -> "Lunar Titanium"
        11 -> "Aurora Green"
        12 -> "Supernova Bronze"
        13 -> "Gloss Black"
        else -> "Lunar Titanium"
    }

    // --- VehicleStateService Control Requests ---

    fun encodeWakeupVehicleRequest(vehicleId: String): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        return writer.toByteArray()
    }

    fun encodeDoorLocksControlRequest(
        vehicleId: String,
        lockState: LockState,
        doors: List<Int> = listOf(1, 2, 3, 4)
    ): ByteArray {
        val writer = ProtoWriter()
        for (door in doors) {
            writer.writeIntField(1, door)
        }
        writer.writeIntField(2, lockState.protoValue)
        writer.writeStringField(3, vehicleId)
        return writer.toByteArray()
    }

    fun encodeFrontCargoControlRequest(vehicleId: String, closureState: DoorState): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, closureState.protoValue)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeRearCargoControlRequest(vehicleId: String, closureState: DoorState): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, closureState.protoValue)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeControlChargePortRequest(vehicleId: String, closureState: DoorState): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, closureState.protoValue)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeSetCabinTemperatureRequest(
        vehicleId: String,
        temperatureCelsius: Double,
        power: HvacPower
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeDoubleField(1, temperatureCelsius)
        writer.writeIntField(2, power.protoValue)
        writer.writeStringField(3, vehicleId)
        return writer.toByteArray()
    }

    fun encodeHvacDefrostControlRequest(vehicleId: String, defrostState: DefrostState): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        writer.writeIntField(2, defrostState.protoValue)
        return writer.toByteArray()
    }

    fun encodeSeatClimateControlRequest(
        vehicleId: String,
        driverHeat: SeatClimateMode,
        driverVent: SeatClimateMode,
        passengerHeat: SeatClimateMode
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        writer.writeIntField(2, driverHeat.protoValue) // driver_heat_backrest_zone1
        writer.writeIntField(3, driverHeat.protoValue) // driver_heat_backrest_zone3
        writer.writeIntField(4, driverHeat.protoValue) // driver_heat_cushion_zone2
        writer.writeIntField(5, driverHeat.protoValue) // driver_heat_cushion_zone4
        writer.writeIntField(6, driverVent.protoValue) // driver_vent_backrest
        writer.writeIntField(7, driverVent.protoValue) // driver_vent_cushion
        writer.writeIntField(8, passengerHeat.protoValue) // front_passenger_heat_backrest_zone1
        writer.writeIntField(9, passengerHeat.protoValue) // front_passenger_heat_backrest_zone3
        writer.writeIntField(10, passengerHeat.protoValue) // front_passenger_heat_cushion_zone2
        writer.writeIntField(11, passengerHeat.protoValue) // front_passenger_heat_cushion_zone4
        return writer.toByteArray()
    }

    fun encodeSteeringWheelHeaterRequest(
        vehicleId: String,
        level: SteeringWheelHeaterLevel
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        writer.writeIntField(2, level.protoValue)
        return writer.toByteArray()
    }

    fun encodeSetBatteryPreconRequest(
        vehicleId: String,
        status: BatteryPreconStatus
    ): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        writer.writeIntField(2, status.protoValue)
        return writer.toByteArray()
    }

    fun encodeSetMaxAcRequest(vehicleId: String, enabled: Boolean): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, if (enabled) 2 else 1)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeSetChargeLimitRequest(vehicleId: String, limitPercent: Int): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, limitPercent.coerceIn(50, 100))
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeChargeControlRequest(vehicleId: String, action: ChargeAction): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, action.protoValue)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeLightsControlRequest(vehicleId: String, action: LightAction): ByteArray {
        val writer = ProtoWriter()
        writer.writeIntField(1, action.protoValue)
        writer.writeStringField(2, vehicleId)
        return writer.toByteArray()
    }

    fun encodeHonkHornRequest(vehicleId: String): ByteArray {
        val writer = ProtoWriter()
        writer.writeStringField(1, vehicleId)
        return writer.toByteArray()
    }

    // --- Test / Verification Builders for Protobuf Messages ---

    fun buildLoginResponseBytesForTest(
        uid: String,
        idToken: String,
        refreshToken: String,
        gigyaJwt: String,
        expiryTimeSec: Int,
        email: String,
        firstName: String,
        lastName: String,
        vehicleId: String,
        vin: String,
        nickname: String,
        modelCode: Int = 2,
        variantCode: Int = 2,
        paintColorCode: Int = 11,
        powerCode: Int = 9,
        lockCode: Int = 2,
        walkawayCode: Int = 2,
        frunkCode: Int = 2,
        rearTrunkCode: Int = 2,
        chargePortCode: Int = 2,
        batteryPercent: Double = 80.0,
        remainingRangeKm: Double = 550.0,
        batteryKwh: Double = 96.0,
        batteryCapacityKwh: Double = 120.0,
        batteryPreconCode: Int = 1,
        chargeStateCode: Int = 1,
        chargeLimitPercent: Double = 80.0,
        chargeRateKw: Double = 0.0,
        chargeRateMph: Double = 0.0,
        sessionMinutesRemaining: Int = 0,
        interiorTempC: Double = 21.0,
        exteriorTempC: Double = 18.0,
        targetTempC: Double = 21.0,
        hvacPowerCode: Int = 2,
        defrostCode: Int = 2,
        maxAcCode: Int = 1,
        driverSeatHeatCode: Int = 2,
        driverSeatVentCode: Int = 2,
        passengerSeatHeatCode: Int = 2,
        steeringHeaterLevelCode: Int = 1,
        odometerKm: Double = 1200.0,
        softwareVersion: String = "2.5.10",
        lastUpdatedMs: Long = 1792113000000L
    ): ByteArray {
        val sessionWriter = ProtoWriter().apply {
            writeStringField(1, idToken)
            writeIntField(2, expiryTimeSec)
            writeStringField(3, refreshToken)
            writeStringField(4, gigyaJwt)
        }
        val profileWriter = ProtoWriter().apply {
            writeStringField(1, email)
            writeStringField(2, "en_US")
            writeStringField(3, email)
            writeStringField(5, firstName)
            writeStringField(6, lastName)
        }
        val vehicleBytes = buildVehicleProtoBytes(
            vehicleId = vehicleId,
            vin = vin,
            nickname = nickname,
            modelCode = modelCode,
            variantCode = variantCode,
            paintColorCode = paintColorCode,
            powerCode = powerCode,
            lockCode = lockCode,
            walkawayCode = walkawayCode,
            frunkCode = frunkCode,
            rearTrunkCode = rearTrunkCode,
            chargePortCode = chargePortCode,
            batteryPercent = batteryPercent,
            remainingRangeKm = remainingRangeKm,
            batteryKwh = batteryKwh,
            batteryCapacityKwh = batteryCapacityKwh,
            batteryPreconCode = batteryPreconCode,
            chargeStateCode = chargeStateCode,
            chargeLimitPercent = chargeLimitPercent,
            chargeRateKw = chargeRateKw,
            chargeRateMph = chargeRateMph,
            sessionMinutesRemaining = sessionMinutesRemaining,
            interiorTempC = interiorTempC,
            exteriorTempC = exteriorTempC,
            targetTempC = targetTempC,
            hvacPowerCode = hvacPowerCode,
            defrostCode = defrostCode,
            maxAcCode = maxAcCode,
            driverSeatHeatCode = driverSeatHeatCode,
            driverSeatVentCode = driverSeatVentCode,
            passengerSeatHeatCode = passengerSeatHeatCode,
            steeringHeaterLevelCode = steeringHeaterLevelCode,
            odometerKm = odometerKm,
            softwareVersion = softwareVersion,
            lastUpdatedMs = lastUpdatedMs
        )
        return ProtoWriter().apply {
            writeStringField(1, uid)
            writeBytesField(2, sessionWriter.toByteArray())
            writeBytesField(3, profileWriter.toByteArray())
            writeBytesField(4, vehicleBytes)
        }.toByteArray()
    }

    fun buildGetUserVehiclesResponseBytesForTest(
        vehicleId: String,
        vin: String,
        nickname: String,
        modelCode: Int = 2,
        powerCode: Int = 9,
        lockCode: Int = 2
    ): ByteArray {
        val vehicleBytes = buildVehicleProtoBytes(
            vehicleId = vehicleId,
            vin = vin,
            nickname = nickname,
            modelCode = modelCode,
            powerCode = powerCode,
            lockCode = lockCode
        )
        return ProtoWriter().apply {
            writeBytesField(1, vehicleBytes)
        }.toByteArray()
    }

    fun buildGetVehicleStateResponseBytesForTest(
        vehicleId: String,
        powerCode: Int = 9,
        lockCode: Int = 2,
        frunkCode: Int = 2,
        batteryPercent: Double = 80.0
    ): ByteArray {
        val vehicleBytes = buildVehicleProtoBytes(
            vehicleId = vehicleId,
            vin = "",
            nickname = "",
            powerCode = powerCode,
            lockCode = lockCode,
            frunkCode = frunkCode,
            batteryPercent = batteryPercent
        )
        val stateBytes = parseRawFields(vehicleBytes).bytesList.getValue(4).last()
        return ProtoWriter().apply {
            writeStringField(1, vehicleId)
            writeBytesField(2, stateBytes)
        }.toByteArray()
    }

    private fun buildVehicleProtoBytes(
        vehicleId: String,
        vin: String,
        nickname: String,
        modelCode: Int = 2,
        variantCode: Int = 2,
        paintColorCode: Int = 11,
        powerCode: Int = 9,
        lockCode: Int = 2,
        walkawayCode: Int = 2,
        frunkCode: Int = 2,
        rearTrunkCode: Int = 2,
        chargePortCode: Int = 2,
        batteryPercent: Double = 80.0,
        remainingRangeKm: Double = 550.0,
        batteryKwh: Double = 96.0,
        batteryCapacityKwh: Double = 120.0,
        batteryPreconCode: Int = 1,
        chargeStateCode: Int = 1,
        chargeLimitPercent: Double = 80.0,
        chargeRateKw: Double = 0.0,
        chargeRateMph: Double = 0.0,
        sessionMinutesRemaining: Int = 0,
        interiorTempC: Double = 21.0,
        exteriorTempC: Double = 18.0,
        targetTempC: Double = 21.0,
        hvacPowerCode: Int = 2,
        defrostCode: Int = 2,
        maxAcCode: Int = 1,
        driverSeatHeatCode: Int = 2,
        driverSeatVentCode: Int = 2,
        passengerSeatHeatCode: Int = 2,
        steeringHeaterLevelCode: Int = 1,
        odometerKm: Double = 1200.0,
        softwareVersion: String = "2.5.10",
        lastUpdatedMs: Long = 1792113000000L
    ): ByteArray {
        val configBytes = ProtoWriter().apply {
            writeStringField(1, vin)
            writeIntField(2, modelCode)
            writeIntField(3, variantCode)
            writeStringField(5, nickname)
            writeIntField(6, paintColorCode)
        }.toByteArray()

        val batteryBytes = ProtoWriter().apply {
            writeDoubleField(1, remainingRangeKm)
            writeDoubleField(2, batteryPercent)
            writeDoubleField(3, batteryKwh)
            writeDoubleField(4, batteryCapacityKwh)
            writeIntField(9, batteryPreconCode)
        }.toByteArray()

        val cabinBytes = ProtoWriter().apply {
            writeDoubleField(1, interiorTempC)
            writeDoubleField(2, exteriorTempC)
        }.toByteArray()

        val bodyBytes = ProtoWriter().apply {
            writeIntField(1, lockCode)
            writeIntField(2, frunkCode)
            writeIntField(3, rearTrunkCode)
            writeIntField(4, 2) // CLOSED
            writeIntField(5, 2)
            writeIntField(6, 2)
            writeIntField(7, 2)
            writeIntField(8, chargePortCode)
            writeIntField(9, walkawayCode)
        }.toByteArray()

        val chassisBytes = ProtoWriter().apply {
            writeDoubleField(1, odometerKm)
            writeStringField(16, softwareVersion)
        }.toByteArray()

        val chargingBytes = ProtoWriter().apply {
            writeIntField(1, chargeStateCode)
            writeIntField(7, sessionMinutesRemaining)
            writeIntField(8, chargeLimitPercent.roundToInt())
            writeDoubleField(19, chargeRateKw)
            writeDoubleField(12, chargeRateMph)
            writeDoubleField(14, chargeLimitPercent)
        }.toByteArray()

        val seatsBytes = ProtoWriter().apply {
            writeIntField(1, driverSeatHeatCode)
            writeIntField(5, driverSeatVentCode)
            writeIntField(7, passengerSeatHeatCode)
        }.toByteArray()

        val hvacBytes = ProtoWriter().apply {
            writeIntField(1, hvacPowerCode)
            writeIntField(2, defrostCode)
            writeIntField(7, maxAcCode)
            writeBytesField(8, seatsBytes)
            writeIntField(12, steeringHeaterLevelCode)
            writeDoubleField(13, targetTempC)
        }.toByteArray()

        val stateBytes = ProtoWriter().apply {
            writeBytesField(1, batteryBytes)
            writeIntField(2, powerCode)
            writeBytesField(3, cabinBytes)
            writeBytesField(4, bodyBytes)
            writeVarintField(5, lastUpdatedMs)
            writeBytesField(6, chassisBytes)
            writeBytesField(8, chargingBytes)
            writeBytesField(17, hvacBytes)
        }.toByteArray()

        return ProtoWriter().apply {
            writeStringField(1, vehicleId)
            writeIntField(2, 2) // ACCESS_LEVEL_PRIMARY_OWNER
            writeBytesField(3, configBytes)
            writeBytesField(4, stateBytes)
        }.toByteArray()
    }
}
