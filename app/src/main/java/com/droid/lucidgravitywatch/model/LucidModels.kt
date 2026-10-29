package com.droid.lucidgravitywatch.model

import kotlin.math.roundToInt

enum class LucidRegion(val code: String, val label: String, val apiHost: String) {
    US("us", "United States", "mobile.deneb.prod.infotainment.pdx.atieva.com"),
    EU("eu", "Europe", "mobile.do.prod.eu.lcid.io"),
    SA("sa", "Saudi Arabia", "mobile.ksag.prod.do.lucidcars.io"),
    ME("me", "Middle East", "mobile.ksap.prod.do.lucidcars.io");

    companion object {
        fun fromCode(code: String?): LucidRegion =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: US
    }
}

enum class VehicleModel(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Lucid"),
    AIR(1, "Air"),
    GRAVITY(2, "Gravity");

    companion object {
        fun fromProto(value: Int): VehicleModel =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class LockState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    UNLOCKED(1, "Unlocked"),
    LOCKED(2, "Locked");

    companion object {
        fun fromProto(value: Int): LockState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class DoorState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    OPEN(1, "Open"),
    CLOSED(2, "Closed"),
    AJAR(3, "Ajar"),
    CLOSE_ERROR(4, "Close Error");

    val isOpen: Boolean
        get() = this == OPEN || this == AJAR

    companion object {
        fun fromProto(value: Int): DoorState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class WalkawayState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    IDLE(1, "Idle"),
    ACTIVE(2, "Walk-Away Active"),
    DISABLE(3, "Walk-Away Off");

    companion object {
        fun fromProto(value: Int): WalkawayState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class PowerState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    SLEEP(1, "Sleep"),
    WINK(2, "Wink"),
    ACCESSORY(3, "Accessory"),
    DRIVE(4, "Drive"),
    LIVE_CHARGE(5, "Charging"),
    SLEEP_CHARGE(6, "Sleep Charge"),
    LIVE_UPDATE(7, "Updating"),
    SLEEP_UPDATE(8, "Sleep Update"),
    CLOUD_1(9, "Awake"),
    CLOUD_2(10, "Awake"),
    MONITOR(11, "Monitor"),
    DROWSY(12, "Drowsy"),
    SLEEP_PENDING(13, "Sleeping");

    val isSleeping: Boolean
        get() = this == SLEEP || this == DROWSY || this == SLEEP_PENDING

    /** True when the car is known to be awake enough to act on remote commands. */
    val isAwake: Boolean
        get() = !isSleeping && this != UNKNOWN && this != SLEEP_CHARGE && this != SLEEP_UPDATE

    companion object {
        fun fromProto(value: Int): PowerState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class ChargeState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    NOT_CONNECTED(1, "Unplugged"),
    CABLE_CONNECTED(2, "Plugged In"),
    ESTABLISHING_SESSION(3, "Connecting"),
    AUTHORIZING_PNC(4, "Authorizing PnC"),
    AUTHORIZING_EXTERNAL(5, "Authorizing"),
    AUTHORIZED(6, "Authorized"),
    CHARGER_PREPARATION(7, "Preparing"),
    CHARGING(8, "Charging"),
    CHARGING_END_OK(9, "Complete"),
    CHARGING_END_ERROR(10, "Charge Error"),
    CHARGING_STOPPED(13, "Stopped"),
    CHARGING_PAUSED(14, "Paused"),
    DISCHARGING(19, "Discharging"),
    EVSE_NOT_READY(25, "Plugged In"),
    CHARGING_SCHEDULED(30, "Scheduled");

    val isActivelyCharging: Boolean
        get() = this == CHARGING

    val isCablePluggedIn: Boolean
        get() = this != UNKNOWN && this != NOT_CONNECTED

    companion object {
        fun fromProto(value: Int): ChargeState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class ChargeAction(val protoValue: Int) {
    UNKNOWN(0),
    START(1),
    STOP(2)
}

enum class HvacPower(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    ON(1, "On"),
    OFF(2, "Off"),
    PRECONDITION(3, "Preconditioning"),
    RESIDUAL_HEATING(5, "Residual Heat"),
    KEEP_TEMP(6, "Keep Climate"),
    HEATSTROKE_PREVENTION(7, "Cabin Overheat Prot");

    val isActive: Boolean
        get() = this == ON || this == PRECONDITION || this == KEEP_TEMP

    companion object {
        fun fromProto(value: Int): HvacPower =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class DefrostState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    ON(1, "Defrost On"),
    OFF(2, "Defrost Off");

    val isEnabled: Boolean
        get() = this == ON

    companion object {
        fun fromProto(value: Int): DefrostState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class BatteryPreconStatus(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    OFF(1, "Off"),
    ON(2, "Preconditioning"),
    UNAVAILABLE(3, "Unavailable");

    val isEnabled: Boolean
        get() = this == ON

    companion object {
        fun fromProto(value: Int): BatteryPreconStatus =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class SeatClimateMode(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Off"),
    OFF(2, "Off"),
    LOW(3, "Low"),
    MEDIUM(4, "Med"),
    HIGH(5, "High");

    fun nextCycle(): SeatClimateMode = when (this) {
        UNKNOWN, OFF -> LOW
        LOW -> MEDIUM
        MEDIUM -> HIGH
        HIGH -> OFF
    }

    companion object {
        fun fromProto(value: Int): SeatClimateMode =
            entries.firstOrNull { it.protoValue == value } ?: OFF
    }
}

enum class SteeringWheelHeaterLevel(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Off"),
    OFF(1, "Off"),
    LEVEL_1(2, "L1"),
    LEVEL_2(3, "L2"),
    LEVEL_3(4, "L3");

    fun nextCycle(): SteeringWheelHeaterLevel = when (this) {
        UNKNOWN, OFF -> LEVEL_1
        LEVEL_1 -> LEVEL_2
        LEVEL_2 -> LEVEL_3
        LEVEL_3 -> OFF
    }

    companion object {
        fun fromProto(value: Int): SteeringWheelHeaterLevel =
            entries.firstOrNull { it.protoValue == value } ?: OFF
    }
}

enum class LightAction(val protoValue: Int) {
    UNKNOWN(0),
    FLASH(1),
    ON(2),
    OFF(3)
}

data class LucidSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val gigyaJwt: String,
    val expiryTimeSec: Int,
    val userEmail: String,
    val firstName: String,
    val lastName: String,
    val region: LucidRegion
) {
    fun isExpired(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        return expiryTimeSec > 0 && nowEpochSec >= (expiryTimeSec - 60L)
    }
}

data class GravityVehicleSnapshot(
    val vehicleId: String,
    val vin: String,
    val nickname: String,
    val model: VehicleModel,
    val variantName: String,
    val paintColorName: String,
    val powerState: PowerState,
    val lockState: LockState,
    val walkawayState: WalkawayState,
    val frunkState: DoorState,
    val rearTrunkState: DoorState,
    val chargePortState: DoorState,
    val frontLeftDoor: DoorState,
    val frontRightDoor: DoorState,
    val rearLeftDoor: DoorState,
    val rearRightDoor: DoorState,
    val batteryPercent: Double,
    val remainingRangeKm: Double,
    val batteryKwh: Double,
    val batteryCapacityKwh: Double,
    val batteryPreconStatus: BatteryPreconStatus,
    val chargeState: ChargeState,
    val chargeLimitPercent: Int,
    val chargeRateKw: Double,
    val chargeRateMph: Double,
    val sessionMinutesRemaining: Int,
    val interiorTempCelsius: Double,
    val exteriorTempCelsius: Double,
    val targetTempCelsius: Double,
    val hvacPower: HvacPower,
    val defrostState: DefrostState,
    val maxAcEnabled: Boolean,
    val driverSeatHeatMode: SeatClimateMode,
    val driverSeatVentMode: SeatClimateMode,
    val passengerSeatHeatMode: SeatClimateMode,
    val steeringHeaterLevel: SteeringWheelHeaterLevel,
    val odometerKm: Double,
    val softwareVersion: String,
    val lastUpdatedMs: Long
) {
    val remainingRangeMiles: Int
        get() = (remainingRangeKm * 0.621371).roundToInt()

    val odometerMiles: Int
        get() = (odometerKm * 0.621371).roundToInt()

    fun formatTemperature(celsius: Double, useFahrenheit: Boolean): String {
        return if (useFahrenheit) {
            val f = (celsius * 9.0 / 5.0 + 32.0).roundToInt()
            "${f}°F"
        } else {
            val rounded = (celsius * 2.0).roundToInt() / 2.0
            "${rounded}°C"
        }
    }

    fun formatChargingPowerKw(): String {
        return "${chargeRateKw.roundToInt()} kW"
    }
}
