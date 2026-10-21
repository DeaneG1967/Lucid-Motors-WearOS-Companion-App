package com.droid.lucidgravitywatch.viewmodel

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import com.droid.lucidgravitywatch.repository.LucidVehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GravityWatchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeLucidVehicleRepository : LucidVehicleRepository {
        var currentSession: LucidSession? = null
        var useFahrenheit: Boolean = true
        var shouldFailRequests: Boolean = false
        var flashedLightsCount: Int = 0
        var honkedHornCount: Int = 0
        var fetchVehiclesCount: Int = 0
        var deferCloudStateUpdateOnCommand: Boolean = false
        var fetchGate: CompletableDeferred<Unit>? = null
        private var currentSelectedVehicleId: String? = "veh-gravity-01"
        var snapshot = GravityVehicleSnapshot(
            vehicleId = "veh-gravity-01",
            vin = "50EA1GBA7RA000101",
            nickname = "Lucid Gravity GT",
            model = VehicleModel.GRAVITY,
            variantName = "Grand Touring",
            paintColorName = "Aurora Green",
            powerState = PowerState.SLEEP,
            lockState = LockState.LOCKED,
            walkawayState = WalkawayState.ACTIVE,
            frunkState = DoorState.CLOSED,
            rearTrunkState = DoorState.CLOSED,
            chargePortState = DoorState.CLOSED,
            frontLeftDoor = DoorState.CLOSED,
            frontRightDoor = DoorState.CLOSED,
            rearLeftDoor = DoorState.CLOSED,
            rearRightDoor = DoorState.CLOSED,
            batteryPercent = 78.0,
            remainingRangeKm = 540.0,
            batteryKwh = 95.0,
            batteryCapacityKwh = 120.0,
            batteryPreconStatus = BatteryPreconStatus.OFF,
            chargeState = ChargeState.CABLE_CONNECTED,
            chargeLimitPercent = 80,
            chargeRateKw = 0.0,
            chargeRateMph = 0.0,
            sessionMinutesRemaining = 0,
            interiorTempCelsius = 20.0,
            exteriorTempCelsius = 15.0,
            targetTempCelsius = 21.0,
            hvacPower = HvacPower.OFF,
            defrostState = DefrostState.OFF,
            maxAcEnabled = false,
            driverSeatHeatMode = SeatClimateMode.OFF,
            driverSeatVentMode = SeatClimateMode.OFF,
            passengerSeatHeatMode = SeatClimateMode.OFF,
            steeringHeaterLevel = SteeringWheelHeaterLevel.OFF,
            odometerKm = 1850.0,
            softwareVersion = "2.5.10",
            lastUpdatedMs = 1792113000000L
        )

        override fun getActiveSession(): LucidSession? = currentSession
        override fun isFahrenheitPreferred(): Boolean = useFahrenheit
        override fun setFahrenheitPreferred(fahrenheit: Boolean) {
            useFahrenheit = fahrenheit
        }

        override fun getSelectedVehicleId(): String? = currentSelectedVehicleId
        override fun setSelectedVehicleId(vehicleId: String) {
            currentSelectedVehicleId = vehicleId
        }

        override suspend fun login(
            email: String,
            password: String,
            region: LucidRegion
        ): List<GravityVehicleSnapshot> {
            if (shouldFailRequests) throw IllegalStateException("Login failed")
            currentSession = LucidSession(
                uid = "u-1",
                idToken = "token-1",
                refreshToken = "ref-1",
                gigyaJwt = "gig-1",
                expiryTimeSec = ((System.currentTimeMillis() / 1000L) + 3600L).toInt(),
                userEmail = email,
                firstName = "Taylor",
                lastName = "Chen",
                region = region
            )
            return listOf(snapshot)
        }

        override suspend fun fetchVehicles(): List<GravityVehicleSnapshot> {
            fetchVehiclesCount++
            fetchGate?.await()
            if (shouldFailRequests) throw IllegalStateException("Fetch failed")
            return listOf(snapshot)
        }

        override suspend fun wakeVehicle(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
            if (shouldFailRequests) throw IllegalStateException("Wake failed")
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
            return snapshot
        }

        override suspend fun setDoorLocks(
            vehicle: GravityVehicleSnapshot,
            lockState: LockState
        ): GravityVehicleSnapshot {
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(lockState = lockState)
            }
            return vehicle.copy(lockState = lockState)
        }

        override suspend fun setFrunkState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(frunkState = doorState)
            }
            return vehicle.copy(frunkState = doorState)
        }

        override suspend fun setRearTrunkState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(rearTrunkState = doorState)
            }
            return vehicle.copy(rearTrunkState = doorState)
        }

        override suspend fun setChargePortState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(chargePortState = doorState)
            return snapshot
        }

        override suspend fun setCabinTemperature(
            vehicle: GravityVehicleSnapshot,
            temperatureCelsius: Double?
        ): GravityVehicleSnapshot {
            snapshot = if (temperatureCelsius == null) {
                snapshot.copy(hvacPower = HvacPower.OFF)
            } else {
                snapshot.copy(
                    targetTempCelsius = temperatureCelsius,
                    hvacPower = HvacPower.PRECONDITION
                )
            }
            return snapshot
        }

        override suspend fun setDefrost(
            vehicle: GravityVehicleSnapshot,
            defrostState: DefrostState
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(defrostState = defrostState)
            return snapshot
        }

        override suspend fun setSeatClimate(
            vehicle: GravityVehicleSnapshot,
            driverHeat: SeatClimateMode,
            driverVent: SeatClimateMode,
            passengerHeat: SeatClimateMode
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(
                driverSeatHeatMode = driverHeat,
                driverSeatVentMode = driverVent,
                passengerSeatHeatMode = passengerHeat
            )
            return snapshot
        }

        override suspend fun setSteeringWheelHeater(
            vehicle: GravityVehicleSnapshot,
            level: SteeringWheelHeaterLevel
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(steeringHeaterLevel = level)
            return snapshot
        }

        override suspend fun setBatteryPreconditioning(
            vehicle: GravityVehicleSnapshot,
            status: BatteryPreconStatus
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(batteryPreconStatus = status)
            return snapshot
        }

        override suspend fun setChargeLimit(
            vehicle: GravityVehicleSnapshot,
            limitPercent: Int
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(chargeLimitPercent = limitPercent)
            return snapshot
        }

        override suspend fun setChargingActive(
            vehicle: GravityVehicleSnapshot,
            active: Boolean
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(
                chargeState = if (active) ChargeState.CHARGING else ChargeState.CHARGING_STOPPED
            )
            return snapshot
        }

        override suspend fun flashLights(vehicle: GravityVehicleSnapshot) {
            if (shouldFailRequests) throw IllegalStateException("Flash failed")
            flashedLightsCount++
        }

        override suspend fun honkHorn(vehicle: GravityVehicleSnapshot) {
            if (shouldFailRequests) throw IllegalStateException("Horn failed")
            honkedHornCount++
        }

        override fun logout() {
            currentSession = null
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginAndControls_updateUiStateProperly() = runTest {
        val repo = FakeLucidVehicleRepository()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)

        // Blank credentials validation
        viewModel.login("", "", LucidRegion.US)
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.selectRegion(LucidRegion.EU)
        assertEquals(LucidRegion.EU, viewModel.uiState.value.selectedRegion)

        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertNotNull(viewModel.uiState.value.activeVehicle)
        assertEquals(LockState.LOCKED, viewModel.uiState.value.activeVehicle?.lockState)

        // UI preferences and dial switching
        viewModel.selectDialPage(WatchDialPage.CLOSURES)
        assertEquals(WatchDialPage.CLOSURES, viewModel.uiState.value.currentDialPage)
        viewModel.toggleTemperatureUnit()
        assertFalse(viewModel.uiState.value.useFahrenheit)
        viewModel.toggleWatchBezelFrame()
        assertFalse(viewModel.uiState.value.watchBezelFrameEnabled)
        viewModel.selectVehicle("veh-gravity-01")

        // Wake vehicle
        viewModel.wakeVehicle()
        advanceUntilIdle()
        assertEquals(PowerState.CLOUD_1, viewModel.uiState.value.activeVehicle?.powerState)

        // Toggle lock -> UNLOCKED and back -> LOCKED
        viewModel.toggleLock()
        advanceUntilIdle()
        assertEquals(LockState.UNLOCKED, viewModel.uiState.value.activeVehicle?.lockState)
        viewModel.toggleLock()
        advanceUntilIdle()
        assertEquals(LockState.LOCKED, viewModel.uiState.value.activeVehicle?.lockState)

        // Toggle Frunk -> OPEN and back -> CLOSED
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.frunkState)
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.frunkState)

        // Toggle Rear Trunk -> OPEN and back -> CLOSED
        viewModel.toggleRearTrunk()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.rearTrunkState)
        viewModel.toggleRearTrunk()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.rearTrunkState)

        // Toggle Charge Port -> OPEN and back -> CLOSED
        viewModel.toggleChargePort()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.chargePortState)
        viewModel.toggleChargePort()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.chargePortState)

        // Toggle Climate Precondition -> PRECONDITION and back -> OFF
        viewModel.toggleClimatePrecondition()
        advanceUntilIdle()
        assertEquals(HvacPower.PRECONDITION, viewModel.uiState.value.activeVehicle?.hvacPower)
        viewModel.toggleClimatePrecondition()
        advanceUntilIdle()
        assertEquals(HvacPower.OFF, viewModel.uiState.value.activeVehicle?.hvacPower)

        // Adjust cabin temperature +1C
        viewModel.adjustTargetTemperature(deltaCelsius = 1.0)
        advanceUntilIdle()
        assertEquals(22.0, viewModel.uiState.value.activeVehicle?.targetTempCelsius ?: 0.0, 0.01)

        // Toggle Defrost -> ON and back -> OFF
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.ON, viewModel.uiState.value.activeVehicle?.defrostState)
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.OFF, viewModel.uiState.value.activeVehicle?.defrostState)

        // Cycle Driver Seat Heat -> LOW
        viewModel.cycleDriverSeatHeat()
        advanceUntilIdle()
        assertEquals(SeatClimateMode.LOW, viewModel.uiState.value.activeVehicle?.driverSeatHeatMode)

        // Cycle Steering Heater -> LEVEL_1
        viewModel.cycleSteeringWheelHeater()
        advanceUntilIdle()
        assertEquals(
            SteeringWheelHeaterLevel.LEVEL_1,
            viewModel.uiState.value.activeVehicle?.steeringHeaterLevel
        )

        // Adjust Charge Limit to 90%
        viewModel.setChargeLimit(90)
        advanceUntilIdle()
        assertEquals(90, viewModel.uiState.value.activeVehicle?.chargeLimitPercent)

        // Toggle Charging -> CHARGING and back -> STOPPED
        viewModel.toggleCharging()
        advanceUntilIdle()
        assertEquals(ChargeState.CHARGING, viewModel.uiState.value.activeVehicle?.chargeState)
        viewModel.toggleCharging()
        advanceUntilIdle()
        assertEquals(ChargeState.CHARGING_STOPPED, viewModel.uiState.value.activeVehicle?.chargeState)

        // Toggle Battery Preconditioning -> ON and back -> OFF
        viewModel.toggleBatteryPreconditioning()
        advanceUntilIdle()
        assertEquals(
            BatteryPreconStatus.ON,
            viewModel.uiState.value.activeVehicle?.batteryPreconStatus
        )
        viewModel.toggleBatteryPreconditioning()
        advanceUntilIdle()
        assertEquals(
            BatteryPreconStatus.OFF,
            viewModel.uiState.value.activeVehicle?.batteryPreconStatus
        )

        // Flash lights & honk horn
        viewModel.flashLights()
        advanceUntilIdle()
        assertEquals(1, repo.flashedLightsCount)

        viewModel.honkHorn()
        advanceUntilIdle()
        assertEquals(1, repo.honkedHornCount)

        // Error handling branch
        repo.shouldFailRequests = true
        viewModel.wakeVehicle()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Logout
        viewModel.logout()
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun interactivePowerState_pollsImmediatelyEverySevenSeconds_switchesToThreeSecondsOnButtonPress_andSuspendsWhenNonInteractive() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        runCurrent()

        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        assertEquals(0, repo.fetchVehiclesCount)

        // 1. Enter Interactive power state -> polls immediately and shows yellow polling dot while in-flight
        val gate = CompletableDeferred<Unit>()
        repo.fetchGate = gate
        viewModel.setInteractivePowerState(true)
        runCurrent()

        assertTrue(viewModel.uiState.value.isInteractive)
        assertTrue(viewModel.uiState.value.isPolling)
        assertEquals(1, repo.fetchVehiclesCount)

        // Complete the in-flight poll -> dot hides
        repo.fetchGate = null
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isPolling)
        assertEquals(7_000L, viewModel.uiState.value.currentPollIntervalMs)

        // 2. Polls every 7 seconds while remaining in Interactive power state
        advanceTimeBy(6_999L)
        runCurrent()
        assertEquals(1, repo.fetchVehiclesCount)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, repo.fetchVehiclesCount)

        // 3. Press Frunk button -> polling interval changes to every 3 seconds until status change is reflected in API data
        viewModel.toggleFrunk()
        runCurrent()
        assertEquals(3_000L, viewModel.uiState.value.currentPollIntervalMs)

        // First 3-second poll arrives, but cloud snapshot still has frunk CLOSED -> stays on 3-second interval
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3, repo.fetchVehiclesCount)
        assertEquals(3_000L, viewModel.uiState.value.currentPollIntervalMs)

        // Cloud now reflects frunk OPEN (and updated battery level) -> next 3-second poll receives it and reverts interval to 7 seconds
        repo.snapshot = repo.snapshot.copy(frunkState = DoorState.OPEN, batteryPercent = 82.0)
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(4, repo.fetchVehiclesCount)
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.frunkState)
        assertEquals(82.0, viewModel.uiState.value.activeVehicle?.batteryPercent ?: 0.0, 0.01)
        assertEquals(7_000L, viewModel.uiState.value.currentPollIntervalMs)

        // Confirm next poll is back on 7-second schedule (does not fire at 3s)
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(4, repo.fetchVehiclesCount)
        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(5, repo.fetchVehiclesCount)

        // 4. Leaving Interactive power state enters Ambient power state -> polling remains active while yellow dot is hidden
        viewModel.setInteractivePowerState(false)
        runCurrent()
        assertFalse(viewModel.uiState.value.isInteractive)
        assertTrue(viewModel.uiState.value.isAmbient)
        assertFalse(viewModel.uiState.value.isPolling)

        val ambientGate = CompletableDeferred<Unit>()
        repo.fetchGate = ambientGate
        advanceTimeBy(7_000L)
        runCurrent()
        assertEquals(6, repo.fetchVehiclesCount)
        // Yellow dot remains hidden during Ambient poll
        assertFalse(viewModel.uiState.value.isPolling)

        repo.fetchGate = null
        ambientGate.complete(Unit)
        runCurrent()

        // 5. Explicitly suspending polling (on app close / sign out) stops the loop
        viewModel.suspendPolling()
        runCurrent()
        assertFalse(viewModel.uiState.value.isInteractive)
        assertFalse(viewModel.uiState.value.isAmbient)
        advanceTimeBy(21_000L)
        runCurrent()
        assertEquals(6, repo.fetchVehiclesCount)
    }
}

