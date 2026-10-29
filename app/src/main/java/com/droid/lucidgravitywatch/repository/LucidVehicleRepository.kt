package com.droid.lucidgravitywatch.repository

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
import com.droid.lucidgravitywatch.network.LucidApiException
import com.droid.lucidgravitywatch.network.LucidGrpcTransport
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import com.droid.lucidgravitywatch.storage.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LucidVehicleRepository {
    fun getActiveSession(): LucidSession?
    fun isFahrenheitPreferred(): Boolean
    fun setFahrenheitPreferred(fahrenheit: Boolean)
    fun getSelectedVehicleId(): String?
    fun setSelectedVehicleId(vehicleId: String)

    suspend fun login(
        email: String,
        password: String,
        region: LucidRegion
    ): List<GravityVehicleSnapshot>

    suspend fun fetchVehicles(): List<GravityVehicleSnapshot>

    /**
     * Current state of one vehicle via GetVehicleState. Unlike [fetchVehicles] (GetUserVehicles,
     * ~5 calls per server minute) this call had no rate limit at 1 call/second, so it is the one
     * to poll. Identity fields come from [vehicle].
     */
    suspend fun fetchVehicleState(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot
    suspend fun wakeVehicle(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot
    suspend fun setDoorLocks(vehicle: GravityVehicleSnapshot, lockState: LockState): GravityVehicleSnapshot
    suspend fun setFrunkState(vehicle: GravityVehicleSnapshot, doorState: DoorState): GravityVehicleSnapshot
    suspend fun setRearTrunkState(vehicle: GravityVehicleSnapshot, doorState: DoorState): GravityVehicleSnapshot
    suspend fun setChargePortState(vehicle: GravityVehicleSnapshot, doorState: DoorState): GravityVehicleSnapshot
    suspend fun setCabinTemperature(vehicle: GravityVehicleSnapshot, temperatureCelsius: Double?): GravityVehicleSnapshot
    suspend fun setDefrost(vehicle: GravityVehicleSnapshot, defrostState: DefrostState): GravityVehicleSnapshot
    suspend fun setSeatClimate(
        vehicle: GravityVehicleSnapshot,
        driverHeat: SeatClimateMode,
        driverVent: SeatClimateMode,
        passengerHeat: SeatClimateMode
    ): GravityVehicleSnapshot

    suspend fun setSteeringWheelHeater(
        vehicle: GravityVehicleSnapshot,
        level: SteeringWheelHeaterLevel
    ): GravityVehicleSnapshot

    suspend fun setBatteryPreconditioning(
        vehicle: GravityVehicleSnapshot,
        status: BatteryPreconStatus
    ): GravityVehicleSnapshot

    suspend fun setChargeLimit(
        vehicle: GravityVehicleSnapshot,
        limitPercent: Int
    ): GravityVehicleSnapshot

    suspend fun setChargingActive(
        vehicle: GravityVehicleSnapshot,
        active: Boolean
    ): GravityVehicleSnapshot

    suspend fun flashLights(vehicle: GravityVehicleSnapshot)
    suspend fun honkHorn(vehicle: GravityVehicleSnapshot)
    fun logout()
}

class LucidCloudVehicleRepository(
    private val transport: LucidGrpcTransport,
    private val sessionStore: SessionStore,
    private val autoWakeSleepingVehicle: Boolean = true
) : LucidVehicleRepository {

    private var demoVehicleSnapshot: GravityVehicleSnapshot = createDefaultDemoVehicle()

    /** Serializes token refreshes so concurrent calls share one GetNewJWTToken. */
    private val sessionRefreshMutex = Mutex()

    private fun isDemoSession(session: LucidSession?): Boolean =
        session?.idToken == DEMO_REVIEWER_TOKEN ||
            session?.userEmail.equals(DEMO_REVIEWER_EMAIL, ignoreCase = true)

    override fun getActiveSession(): LucidSession? = sessionStore.loadSession()

    override fun isFahrenheitPreferred(): Boolean = sessionStore.isFahrenheitPreferred()

    override fun setFahrenheitPreferred(fahrenheit: Boolean) {
        sessionStore.setFahrenheitPreferred(fahrenheit)
    }

    override fun getSelectedVehicleId(): String? = sessionStore.getSelectedVehicleId()

    override fun setSelectedVehicleId(vehicleId: String) {
        sessionStore.setSelectedVehicleId(vehicleId)
    }

    override suspend fun login(
        email: String,
        password: String,
        region: LucidRegion
    ): List<GravityVehicleSnapshot> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.equals(DEMO_REVIEWER_EMAIL, ignoreCase = true) &&
            password == DEMO_REVIEWER_PASSWORD
        ) {
            val demoSession = LucidSession(
                uid = "demo-reviewer-uid",
                idToken = DEMO_REVIEWER_TOKEN,
                refreshToken = "demo-refresh-token",
                gigyaJwt = "demo-gigya-jwt",
                expiryTimeSec = Int.MAX_VALUE,
                userEmail = DEMO_REVIEWER_EMAIL,
                firstName = "Play",
                lastName = "Reviewer",
                region = region
            )
            demoVehicleSnapshot = createDefaultDemoVehicle()
            sessionStore.saveSession(demoSession)
            sessionStore.setSelectedVehicleId(demoVehicleSnapshot.vehicleId)
            return listOf(demoVehicleSnapshot)
        }

        val requestBytes = LucidProtobufCodec.encodeLoginRequest(
            username = trimmedEmail,
            password = password,
            deviceId = sessionStore.getDeviceId()
        )
        val responseBytes = transport.unaryCall(
            region = region,
            serviceMethod = METHOD_LOGIN,
            requestProtobuf = requestBytes,
            bearerToken = null
        )
        val decoded = LucidProtobufCodec.decodeLoginResponse(responseBytes, region)
        if (decoded.session.idToken.isBlank()) {
            throw LucidApiException(
                grpcStatus = 16,
                message = "Authentication failed: Lucid Cloud did not return an ID token."
            )
        }
        sessionStore.saveSession(decoded.session)

        val vehicles = decoded.vehicles.ifEmpty {
            fetchVehiclesInternal(decoded.session)
        }
        autoSelectOnlyVehicle(vehicles)
        return vehicles
    }

    private suspend fun ensureValidSession(): LucidSession {
        sessionRefreshMutex.withLock {
            // Re-read inside the lock: a caller that waited here usually finds a session another
            // caller has just refreshed.
            val current = sessionStore.loadSession()
                ?: throw LucidApiException(16, "Not signed in to Lucid account.")
            if (isDemoSession(current)) {
                return current
            }
            if (current.isExpired() && current.refreshToken.isNotBlank()) {
                val req = LucidProtobufCodec.encodeGetNewJwtTokenRequest(current.refreshToken)
                val resp = transport.unaryCall(
                    region = current.region,
                    serviceMethod = METHOD_GET_NEW_JWT_TOKEN,
                    requestProtobuf = req,
                    bearerToken = current.idToken
                )
                val refreshed = LucidProtobufCodec.decodeGetNewJwtTokenResponse(resp, current)
                sessionStore.saveSession(refreshed)
                return refreshed
            }
            return current
        }
    }

    override suspend fun fetchVehicles(): List<GravityVehicleSnapshot> {
        val session = ensureValidSession()
        if (isDemoSession(session)) {
            return listOf(demoVehicleSnapshot)
        }
        return fetchVehiclesInternal(session)
    }

    override suspend fun fetchVehicleState(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (isDemoSession(session)) {
            return demoVehicleSnapshot
        }
        val responseBytes = transport.unaryCall(
            region = session.region,
            serviceMethod = METHOD_GET_VEHICLE_STATE,
            requestProtobuf = LucidProtobufCodec.encodeGetVehicleStateRequest(vehicle.vehicleId),
            bearerToken = session.idToken
        )
        return LucidProtobufCodec.decodeGetVehicleStateResponse(responseBytes, vehicle)
    }

    private suspend fun fetchVehiclesInternal(session: LucidSession): List<GravityVehicleSnapshot> {
        val responseBytes = transport.unaryCall(
            region = session.region,
            serviceMethod = METHOD_GET_USER_VEHICLES,
            requestProtobuf = LucidProtobufCodec.encodeGetUserVehiclesRequest(),
            bearerToken = session.idToken
        )
        val vehicles = LucidProtobufCodec.decodeGetUserVehiclesResponse(responseBytes)
        autoSelectOnlyVehicle(vehicles)
        return vehicles
    }

    /**
     * Saves the selection only when the account has a single vehicle; with several vehicles the
     * user picks one in the vehicle selector instead of being handed an arbitrary one.
     */
    private fun autoSelectOnlyVehicle(vehicles: List<GravityVehicleSnapshot>) {
        val onlyVehicle = vehicles.singleOrNull() ?: return
        if (sessionStore.getSelectedVehicleId().isNullOrBlank()) {
            sessionStore.setSelectedVehicleId(onlyVehicle.vehicleId)
        }
    }

    private suspend fun ensureAwakeIfRequired(
        session: LucidSession,
        vehicle: GravityVehicleSnapshot
    ) {
        if (isDemoSession(session)) return
        if (autoWakeSleepingVehicle && vehicle.powerState.isSleeping) {
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_WAKEUP_VEHICLE,
                requestProtobuf = LucidProtobufCodec.encodeWakeupVehicleRequest(vehicle.vehicleId),
                bearerToken = session.idToken
            )
        }
    }

    override suspend fun wakeVehicle(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_WAKEUP_VEHICLE,
                requestProtobuf = LucidProtobufCodec.encodeWakeupVehicleRequest(vehicle.vehicleId),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            powerState = PowerState.CLOUD_1,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setDoorLocks(
        vehicle: GravityVehicleSnapshot,
        lockState: LockState
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_DOOR_LOCKS_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeDoorLocksControlRequest(
                    vehicleId = vehicle.vehicleId,
                    lockState = lockState
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            lockState = lockState,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setFrunkState(
        vehicle: GravityVehicleSnapshot,
        doorState: DoorState
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_FRONT_CARGO_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeFrontCargoControlRequest(
                    vehicleId = vehicle.vehicleId,
                    closureState = doorState
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            frunkState = doorState,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setRearTrunkState(
        vehicle: GravityVehicleSnapshot,
        doorState: DoorState
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_REAR_CARGO_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeRearCargoControlRequest(
                    vehicleId = vehicle.vehicleId,
                    closureState = doorState
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            rearTrunkState = doorState,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setChargePortState(
        vehicle: GravityVehicleSnapshot,
        doorState: DoorState
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_CONTROL_CHARGE_PORT,
                requestProtobuf = LucidProtobufCodec.encodeControlChargePortRequest(
                    vehicleId = vehicle.vehicleId,
                    closureState = doorState
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            chargePortState = doorState,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setCabinTemperature(
        vehicle: GravityVehicleSnapshot,
        temperatureCelsius: Double?
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        val power = if (temperatureCelsius == null) HvacPower.OFF else HvacPower.PRECONDITION
        val targetTemp = (temperatureCelsius ?: 0.0).let {
            if (temperatureCelsius == null) 0.0 else it.coerceIn(15.5, 29.5)
        }
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_SET_CABIN_TEMPERATURE,
                requestProtobuf = LucidProtobufCodec.encodeSetCabinTemperatureRequest(
                    vehicleId = vehicle.vehicleId,
                    temperatureCelsius = targetTemp,
                    power = power
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            hvacPower = power,
            targetTempCelsius = if (temperatureCelsius != null) targetTemp else vehicle.targetTempCelsius,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setDefrost(
        vehicle: GravityVehicleSnapshot,
        defrostState: DefrostState
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_HVAC_DEFROST_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeHvacDefrostControlRequest(
                    vehicleId = vehicle.vehicleId,
                    defrostState = defrostState
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            defrostState = defrostState,
            hvacPower = if (defrostState == DefrostState.ON) HvacPower.PRECONDITION else vehicle.hvacPower,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setSeatClimate(
        vehicle: GravityVehicleSnapshot,
        driverHeat: SeatClimateMode,
        driverVent: SeatClimateMode,
        passengerHeat: SeatClimateMode
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_SEAT_CLIMATE_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeSeatClimateControlRequest(
                    vehicleId = vehicle.vehicleId,
                    driverHeat = driverHeat,
                    driverVent = driverVent,
                    passengerHeat = passengerHeat
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            driverSeatHeatMode = driverHeat,
            driverSeatVentMode = driverVent,
            passengerSeatHeatMode = passengerHeat,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setSteeringWheelHeater(
        vehicle: GravityVehicleSnapshot,
        level: SteeringWheelHeaterLevel
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_STEERING_WHEEL_HEATER,
                requestProtobuf = LucidProtobufCodec.encodeSteeringWheelHeaterRequest(
                    vehicleId = vehicle.vehicleId,
                    level = level
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            steeringHeaterLevel = level,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setBatteryPreconditioning(
        vehicle: GravityVehicleSnapshot,
        status: BatteryPreconStatus
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_SET_BATTERY_PRECON,
                requestProtobuf = LucidProtobufCodec.encodeSetBatteryPreconRequest(
                    vehicleId = vehicle.vehicleId,
                    status = status
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            batteryPreconStatus = status,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setChargeLimit(
        vehicle: GravityVehicleSnapshot,
        limitPercent: Int
    ): GravityVehicleSnapshot {
        val clamped = limitPercent.coerceIn(50, 100)
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_SET_CHARGE_LIMIT,
                requestProtobuf = LucidProtobufCodec.encodeSetChargeLimitRequest(
                    vehicleId = vehicle.vehicleId,
                    limitPercent = clamped
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            chargeLimitPercent = clamped,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun setChargingActive(
        vehicle: GravityVehicleSnapshot,
        active: Boolean
    ): GravityVehicleSnapshot {
        val session = ensureValidSession()
        if (!isDemoSession(session)) {
            ensureAwakeIfRequired(session, vehicle)
            val action = if (active) ChargeAction.START else ChargeAction.STOP
            transport.unaryCall(
                region = session.region,
                serviceMethod = METHOD_CHARGE_CONTROL,
                requestProtobuf = LucidProtobufCodec.encodeChargeControlRequest(
                    vehicleId = vehicle.vehicleId,
                    action = action
                ),
                bearerToken = session.idToken
            )
        }
        val updated = vehicle.copy(
            chargeState = if (active) ChargeState.CHARGING else ChargeState.CHARGING_STOPPED,
            chargeRateKw = if (active) 19.2 else 0.0,
            powerState = if (vehicle.powerState.isSleeping) PowerState.CLOUD_1 else vehicle.powerState,
            lastUpdatedMs = System.currentTimeMillis()
        )
        if (isDemoSession(session)) demoVehicleSnapshot = updated
        return updated
    }

    override suspend fun flashLights(vehicle: GravityVehicleSnapshot) {
        val session = ensureValidSession()
        if (isDemoSession(session)) return
        ensureAwakeIfRequired(session, vehicle)
        transport.unaryCall(
            region = session.region,
            serviceMethod = METHOD_LIGHTS_CONTROL,
            requestProtobuf = LucidProtobufCodec.encodeLightsControlRequest(
                vehicleId = vehicle.vehicleId,
                action = LightAction.FLASH
            ),
            bearerToken = session.idToken
        )
    }

    override suspend fun honkHorn(vehicle: GravityVehicleSnapshot) {
        val session = ensureValidSession()
        if (isDemoSession(session)) return
        ensureAwakeIfRequired(session, vehicle)
        transport.unaryCall(
            region = session.region,
            serviceMethod = METHOD_HONK_HORN,
            requestProtobuf = LucidProtobufCodec.encodeHonkHornRequest(vehicle.vehicleId),
            bearerToken = session.idToken
        )
    }

    override fun logout() {
        sessionStore.clearSession()
    }

    companion object {
        const val DEMO_REVIEWER_EMAIL = "demo@lucidcars.app"
        const val DEMO_REVIEWER_PASSWORD = "GooglePlayReview2026"
        const val DEMO_REVIEWER_TOKEN = "demo-play-reviewer-token"

        private fun createDefaultDemoVehicle(): GravityVehicleSnapshot = GravityVehicleSnapshot(
            vehicleId = "veh-demo-gravity-01",
            vin = "50EA1GBA7RA000999",
            nickname = "Gravity GT",
            model = VehicleModel.GRAVITY,
            variantName = "Grand Touring",
            paintColorName = "Aurora Green",
            powerState = PowerState.CLOUD_1,
            lockState = LockState.LOCKED,
            walkawayState = WalkawayState.ACTIVE,
            frunkState = DoorState.CLOSED,
            rearTrunkState = DoorState.CLOSED,
            chargePortState = DoorState.CLOSED,
            frontLeftDoor = DoorState.CLOSED,
            frontRightDoor = DoorState.CLOSED,
            rearLeftDoor = DoorState.CLOSED,
            rearRightDoor = DoorState.CLOSED,
            batteryPercent = 84.0,
            remainingRangeKm = 580.0,
            batteryKwh = 100.8,
            batteryCapacityKwh = 120.0,
            batteryPreconStatus = BatteryPreconStatus.OFF,
            chargeState = ChargeState.NOT_CONNECTED,
            chargeLimitPercent = 85,
            chargeRateKw = 0.0,
            chargeRateMph = 0.0,
            sessionMinutesRemaining = 0,
            interiorTempCelsius = 21.0,
            exteriorTempCelsius = 18.0,
            targetTempCelsius = 21.0,
            hvacPower = HvacPower.OFF,
            defrostState = DefrostState.OFF,
            maxAcEnabled = false,
            driverSeatHeatMode = SeatClimateMode.OFF,
            driverSeatVentMode = SeatClimateMode.OFF,
            passengerSeatHeatMode = SeatClimateMode.OFF,
            steeringHeaterLevel = SteeringWheelHeaterLevel.OFF,
            odometerKm = 2400.0,
            softwareVersion = "2.5.12",
            lastUpdatedMs = System.currentTimeMillis()
        )

        const val METHOD_LOGIN = "/mobilegateway.protos.LoginSession/Login"
        const val METHOD_GET_NEW_JWT_TOKEN = "/mobilegateway.protos.LoginSession/GetNewJWTToken"
        const val METHOD_GET_USER_VEHICLES = "/mobilegateway.protos.LoginSession/GetUserVehicles"
        const val METHOD_GET_VEHICLE_STATE = "/mobilegateway.protos.VehicleStateService/GetVehicleState"
        const val METHOD_WAKEUP_VEHICLE = "/mobilegateway.protos.VehicleStateService/WakeupVehicle"
        const val METHOD_DOOR_LOCKS_CONTROL = "/mobilegateway.protos.VehicleStateService/DoorLocksControl"
        const val METHOD_FRONT_CARGO_CONTROL = "/mobilegateway.protos.VehicleStateService/FrontCargoControl"
        const val METHOD_REAR_CARGO_CONTROL = "/mobilegateway.protos.VehicleStateService/RearCargoControl"
        const val METHOD_CONTROL_CHARGE_PORT = "/mobilegateway.protos.VehicleStateService/ControlChargePort"
        const val METHOD_SET_CABIN_TEMPERATURE = "/mobilegateway.protos.VehicleStateService/SetCabinTemperature"
        const val METHOD_HVAC_DEFROST_CONTROL = "/mobilegateway.protos.VehicleStateService/HvacDefrostControl"
        const val METHOD_SEAT_CLIMATE_CONTROL = "/mobilegateway.protos.VehicleStateService/SeatClimateControl"
        const val METHOD_STEERING_WHEEL_HEATER = "/mobilegateway.protos.VehicleStateService/SteeringWheelHeater"
        const val METHOD_SET_BATTERY_PRECON = "/mobilegateway.protos.VehicleStateService/SetBatteryPrecon"
        const val METHOD_SET_CHARGE_LIMIT = "/mobilegateway.protos.VehicleStateService/SetChargeLimit"
        const val METHOD_CHARGE_CONTROL = "/mobilegateway.protos.VehicleStateService/ChargeControl"
        const val METHOD_LIGHTS_CONTROL = "/mobilegateway.protos.VehicleStateService/LightsControl"
        const val METHOD_HONK_HORN = "/mobilegateway.protos.VehicleStateService/HonkHorn"
    }
}

