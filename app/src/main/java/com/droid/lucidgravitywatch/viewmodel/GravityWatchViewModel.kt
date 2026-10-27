package com.droid.lucidgravitywatch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.repository.LucidVehicleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WatchDialPage(val title: String) {
    OVERVIEW_LOCK("Security"),
    CLOSURES("Closures"),
    CLIMATE("Climate"),
    BATTERY_CHARGING("Energy"),
    VEHICLE_ACCOUNT("Vehicle")
}

data class GravityWatchUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val isCommandPending: Boolean = false,
    val isInteractive: Boolean = false,
    val isAmbient: Boolean = false,
    val isPolling: Boolean = false,
    val currentPollIntervalMs: Long = GravityWatchViewModel.DEFAULT_POLL_INTERVAL_MS,
    val selectedRegion: LucidRegion = LucidRegion.US,
    val useFahrenheit: Boolean = true,
    val watchBezelFrameEnabled: Boolean = true,
    val currentDialPage: WatchDialPage = WatchDialPage.OVERVIEW_LOCK,
    val userEmail: String = "",
    val userDisplayName: String = "",
    val vehicles: List<GravityVehicleSnapshot> = emptyList(),
    val selectedVehicleId: String? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null
) {
    val activeVehicle: GravityVehicleSnapshot?
        get() = vehicles.firstOrNull { it.vehicleId == selectedVehicleId }
            ?: vehicles.firstOrNull { it.model == VehicleModel.GRAVITY }
            ?: vehicles.firstOrNull()
}

class GravityWatchViewModel(
    private val repository: LucidVehicleRepository
) : ViewModel() {

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 7_000L
        const val FAST_POLL_INTERVAL_MS = 3_000L
    }

    private val _uiState = MutableStateFlow(GravityWatchUiState())
    val uiState: StateFlow<GravityWatchUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var expectedLockState: LockState? = null
    private var expectedFrunkOpen: Boolean? = null
    private var expectedRearTrunkOpen: Boolean? = null

    init {
        restoreExistingSession()
    }

    private fun restoreExistingSession() {
        val session = repository.getActiveSession()
        val useF = repository.isFahrenheitPreferred()
        if (session == null) {
            _uiState.update { it.copy(useFahrenheit = useF) }
            return
        }
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                isLoading = true,
                selectedRegion = session.region,
                useFahrenheit = useF,
                userEmail = session.userEmail,
                userDisplayName = listOf(session.firstName, session.lastName)
                    .filter { part -> part.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { session.userEmail },
                selectedVehicleId = repository.getSelectedVehicleId()
            )
        }
        refreshVehicles()
    }

    private fun isPollingPowerStateActive(): Boolean =
        _uiState.value.isInteractive || _uiState.value.isAmbient

    /**
     * Updates whether the watch app is currently in the Interactive power state or Ambient power state.
     * - When entering Interactive state (`interactive = true`), immediately polls the Lucid API
     *   and continues polling every 7 seconds (or every 3 seconds while awaiting a frunk, trunk,
     *   or door lock/unlock state transition).
     * - When leaving Interactive state (`interactive = false`), transitions to the Ambient power state
     *   and keeps polling active on the current schedule while hiding the interactive-only yellow dot.
     */
    fun setInteractivePowerState(interactive: Boolean) {
        val wasInteractive = _uiState.value.isInteractive
        _uiState.update {
            it.copy(
                isInteractive = interactive,
                isAmbient = !interactive,
                isPolling = if (!interactive) false else it.isPolling
            )
        }

        if (!_uiState.value.isAuthenticated) return

        if (interactive && !wasInteractive) {
            startPollingLoop(pollImmediately = true)
        } else if (pollingJob?.isActive != true) {
            startPollingLoop(pollImmediately = interactive)
        }
    }

    /**
     * Explicitly suspends polling when the app is completely closed or signed out.
     */
    fun suspendPolling() {
        _uiState.update {
            it.copy(
                isInteractive = false,
                isAmbient = false,
                isPolling = false
            )
        }
        stopPollingLoop()
    }

    private fun hasPendingExpectedChanges(): Boolean =
        expectedLockState != null || expectedFrunkOpen != null || expectedRearTrunkOpen != null

    private fun resolvePollIntervalMs(): Long =
        if (hasPendingExpectedChanges()) FAST_POLL_INTERVAL_MS else DEFAULT_POLL_INTERVAL_MS

    private fun activateFastPolling() {
        _uiState.update { it.copy(currentPollIntervalMs = FAST_POLL_INTERVAL_MS) }
        if (isPollingPowerStateActive() && _uiState.value.isAuthenticated) {
            startPollingLoop(pollImmediately = false)
        }
    }

    private fun startPollingLoop(pollImmediately: Boolean) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var isFirstIteration = true
            while (isActive && isPollingPowerStateActive() && _uiState.value.isAuthenticated) {
                if (isFirstIteration && pollImmediately) {
                    isFirstIteration = false
                    performInteractivePoll()
                } else {
                    isFirstIteration = false
                    val delayMs = resolvePollIntervalMs()
                    _uiState.update { it.copy(currentPollIntervalMs = delayMs) }
                    delay(delayMs)
                    if (!isActive || !isPollingPowerStateActive() || !_uiState.value.isAuthenticated) {
                        break
                    }
                    performInteractivePoll()
                }
            }
        }
    }

    private fun stopPollingLoop() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    private suspend fun performInteractivePoll() {
        if (!isPollingPowerStateActive() || !_uiState.value.isAuthenticated) return

        val showYellowDot = _uiState.value.isInteractive
        _uiState.update { it.copy(isPolling = showYellowDot) }
        try {
            val vehicles = repository.fetchVehicles()
            val selectedId = _uiState.value.selectedVehicleId
                ?: repository.getSelectedVehicleId()
                ?: vehicles.firstOrNull { it.model == VehicleModel.GRAVITY }?.vehicleId
                ?: vehicles.firstOrNull()?.vehicleId

            val polledVehicle = vehicles.firstOrNull { it.vehicleId == selectedId }
                ?: vehicles.firstOrNull { it.model == VehicleModel.GRAVITY }
                ?: vehicles.firstOrNull()

            if (polledVehicle != null) {
                if (expectedLockState != null && polledVehicle.lockState == expectedLockState) {
                    expectedLockState = null
                }
                if (expectedFrunkOpen != null && polledVehicle.frunkState.isOpen == expectedFrunkOpen) {
                    expectedFrunkOpen = null
                }
                if (expectedRearTrunkOpen != null && polledVehicle.rearTrunkState.isOpen == expectedRearTrunkOpen) {
                    expectedRearTrunkOpen = null
                }
            }

            val nextIntervalMs = resolvePollIntervalMs()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPolling = false,
                    currentPollIntervalMs = nextIntervalMs,
                    vehicles = vehicles,
                    selectedVehicleId = selectedId,
                    statusBanner = "Telemetry synced",
                    errorMessage = null
                )
            }
        } catch (err: Throwable) {
            val nextIntervalMs = resolvePollIntervalMs()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPolling = false,
                    currentPollIntervalMs = nextIntervalMs,
                    errorMessage = err.message ?: "Failed to refresh vehicle telemetry."
                )
            }
        }
    }

    fun selectRegion(region: LucidRegion) {
        _uiState.update { it.copy(selectedRegion = region, errorMessage = null) }
    }

    fun selectDialPage(page: WatchDialPage) {
        _uiState.update { it.copy(currentDialPage = page) }
    }

    fun toggleTemperatureUnit() {
        val next = !_uiState.value.useFahrenheit
        repository.setFahrenheitPreferred(next)
        _uiState.update { it.copy(useFahrenheit = next) }
    }

    fun toggleWatchBezelFrame() {
        _uiState.update { it.copy(watchBezelFrameEnabled = !it.watchBezelFrameEnabled) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusBanner = null) }
    }

    fun login(email: String, password: String, region: LucidRegion = _uiState.value.selectedRegion) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Enter your Lucid account email and password.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedRegion = region,
                    errorMessage = null,
                    statusBanner = null
                )
            }
            runCatching {
                repository.login(email.trim(), password, region)
            }.onSuccess { vehicles ->
                val session = repository.getActiveSession()
                val preferredId = repository.getSelectedVehicleId()
                    ?: vehicles.firstOrNull { it.model == VehicleModel.GRAVITY }?.vehicleId
                    ?: vehicles.firstOrNull()?.vehicleId
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isLoading = false,
                        userEmail = session?.userEmail ?: email.trim(),
                        userDisplayName = listOfNotNull(session?.firstName, session?.lastName)
                            .filter { p -> p.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { email.trim() },
                        vehicles = vehicles,
                        selectedVehicleId = preferredId,
                        statusBanner = if (vehicles.isEmpty()) {
                            "Signed in, but no vehicles were found on this Lucid account."
                        } else {
                            "Connected to ${vehicles.firstOrNull { v -> v.vehicleId == preferredId }?.nickname ?: "Lucid Gravity"}"
                        },
                        errorMessage = null
                    )
                }
                if (isPollingPowerStateActive()) {
                    startPollingLoop(pollImmediately = false)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to authenticate with Lucid Cloud."
                    )
                }
            }
        }
    }

    fun refreshVehicles() {
        viewModelScope.launch {
            val showInteractiveDot = _uiState.value.isInteractive
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isPolling = if (showInteractiveDot) true else it.isPolling,
                    errorMessage = null
                )
            }
            runCatching {
                repository.fetchVehicles()
            }.onSuccess { vehicles ->
                val selectedId = _uiState.value.selectedVehicleId
                    ?: repository.getSelectedVehicleId()
                    ?: vehicles.firstOrNull { it.model == VehicleModel.GRAVITY }?.vehicleId
                    ?: vehicles.firstOrNull()?.vehicleId
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        vehicles = vehicles,
                        selectedVehicleId = selectedId,
                        statusBanner = "Telemetry synced",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        errorMessage = err.message ?: "Failed to refresh vehicle telemetry."
                    )
                }
            }
        }
    }

    fun selectVehicle(vehicleId: String) {
        repository.setSelectedVehicleId(vehicleId)
        _uiState.update { it.copy(selectedVehicleId = vehicleId) }
    }

    private fun executeVehicleCommand(
        actionLabel: String,
        block: suspend (GravityVehicleSnapshot) -> GravityVehicleSnapshot
    ) {
        val currentVehicle = _uiState.value.activeVehicle ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCommandPending = true,
                    statusBanner = "$actionLabel…",
                    errorMessage = null
                )
            }
            runCatching {
                block(currentVehicle)
            }.onSuccess { updatedVehicle ->
                _uiState.update { state ->
                    state.copy(
                        isCommandPending = false,
                        vehicles = state.vehicles.map {
                            if (it.vehicleId == updatedVehicle.vehicleId) updatedVehicle else it
                        },
                        statusBanner = "$actionLabel complete",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCommandPending = false,
                        statusBanner = null,
                        errorMessage = err.message ?: "$actionLabel failed"
                    )
                }
            }
        }
    }

    fun wakeVehicle() {
        executeVehicleCommand("Waking Gravity") { vehicle ->
            repository.wakeVehicle(vehicle)
        }
    }

    fun toggleLock() {
        val current = _uiState.value.activeVehicle ?: return
        val nextLock = if (current.lockState == LockState.LOCKED) {
            LockState.UNLOCKED
        } else {
            LockState.LOCKED
        }
        expectedLockState = nextLock
        activateFastPolling()
        val label = if (nextLock == LockState.LOCKED) "Locking doors" else "Unlocking doors"
        executeVehicleCommand(label) { vehicle ->
            repository.setDoorLocks(vehicle, nextLock)
        }
    }

    fun toggleFrunk() {
        val current = _uiState.value.activeVehicle ?: return
        val nextState = if (current.frunkState.isOpen) DoorState.CLOSED else DoorState.OPEN
        expectedFrunkOpen = nextState.isOpen
        activateFastPolling()
        val label = if (nextState == DoorState.OPEN) "Opening Frunk" else "Closing Frunk"
        executeVehicleCommand(label) { vehicle ->
            repository.setFrunkState(vehicle, nextState)
        }
    }

    fun toggleRearTrunk() {
        val current = _uiState.value.activeVehicle ?: return
        val nextState = if (current.rearTrunkState.isOpen) DoorState.CLOSED else DoorState.OPEN
        expectedRearTrunkOpen = nextState.isOpen
        activateFastPolling()
        val label = if (nextState == DoorState.OPEN) "Opening Rear Trunk" else "Closing Rear Trunk"
        executeVehicleCommand(label) { vehicle ->
            repository.setRearTrunkState(vehicle, nextState)
        }
    }

    fun toggleChargePort() {
        val current = _uiState.value.activeVehicle ?: return
        val nextState = if (current.chargePortState.isOpen) DoorState.CLOSED else DoorState.OPEN
        val label = if (nextState == DoorState.OPEN) "Opening Charge Port" else "Closing Charge Port"
        executeVehicleCommand(label) { vehicle ->
            repository.setChargePortState(vehicle, nextState)
        }
    }

    fun toggleClimatePrecondition() {
        val current = _uiState.value.activeVehicle ?: return
        val turnOn = !current.hvacPower.isActive
        val label = if (turnOn) "Starting Cabin Climate" else "Stopping Cabin Climate"
        executeVehicleCommand(label) { vehicle ->
            repository.setCabinTemperature(
                vehicle = vehicle,
                temperatureCelsius = if (turnOn) vehicle.targetTempCelsius else null
            )
        }
    }

    fun adjustTargetTemperature(deltaCelsius: Double) {
        val current = _uiState.value.activeVehicle ?: return
        val nextTemp = (current.targetTempCelsius + deltaCelsius).coerceIn(15.5, 29.5)
        executeVehicleCommand("Setting Cabin Temp") { vehicle ->
            repository.setCabinTemperature(vehicle, nextTemp)
        }
    }

    fun toggleDefrost() {
        val current = _uiState.value.activeVehicle ?: return
        val nextDefrost = if (current.defrostState.isEnabled) DefrostState.OFF else DefrostState.ON
        val label = if (nextDefrost == DefrostState.ON) "Enabling Max Defrost" else "Disabling Defrost"
        executeVehicleCommand(label) { vehicle ->
            repository.setDefrost(vehicle, nextDefrost)
        }
    }

    fun cycleDriverSeatHeat() {
        val current = _uiState.value.activeVehicle ?: return
        val nextMode = current.driverSeatHeatMode.nextCycle()
        executeVehicleCommand("Driver Seat Heat (${nextMode.displayName})") { vehicle ->
            repository.setSeatClimate(
                vehicle = vehicle,
                driverHeat = nextMode,
                driverVent = vehicle.driverSeatVentMode,
                passengerHeat = vehicle.passengerSeatHeatMode
            )
        }
    }

    fun cycleSteeringWheelHeater() {
        val current = _uiState.value.activeVehicle ?: return
        val nextLevel = current.steeringHeaterLevel.nextCycle()
        executeVehicleCommand("Steering Heater (${nextLevel.displayName})") { vehicle ->
            repository.setSteeringWheelHeater(vehicle, nextLevel)
        }
    }

    fun toggleBatteryPreconditioning() {
        val current = _uiState.value.activeVehicle ?: return
        val nextStatus = if (current.batteryPreconStatus.isEnabled) {
            BatteryPreconStatus.OFF
        } else {
            BatteryPreconStatus.ON
        }
        val label = if (nextStatus == BatteryPreconStatus.ON) {
            "Starting Battery Precon"
        } else {
            "Stopping Battery Precon"
        }
        executeVehicleCommand(label) { vehicle ->
            repository.setBatteryPreconditioning(vehicle, nextStatus)
        }
    }

    fun setChargeLimit(limitPercent: Int) {
        val clamped = limitPercent.coerceIn(50, 100)
        executeVehicleCommand("Setting Charge Limit to $clamped%") { vehicle ->
            repository.setChargeLimit(vehicle, clamped)
        }
    }

    fun toggleCharging() {
        val current = _uiState.value.activeVehicle ?: return
        val startCharging = !current.chargeState.isActivelyCharging
        val label = if (startCharging) "Starting Charge" else "Stopping Charge"
        executeVehicleCommand(label) { vehicle ->
            repository.setChargingActive(vehicle, startCharging)
        }
    }

    fun stopCharging() {
        activateFastPolling()
        executeVehicleCommand("Stopping Charge") { vehicle ->
            repository.setChargingActive(vehicle, false)
        }
    }

    fun flashLights() {
        val current = _uiState.value.activeVehicle ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCommandPending = true, statusBanner = "Flashing headlights…") }
            runCatching { repository.flashLights(current) }
                .onSuccess {
                    _uiState.update { it.copy(isCommandPending = false, statusBanner = "Headlights flashed") }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isCommandPending = false, errorMessage = err.message ?: "Flash lights failed")
                    }
                }
        }
    }

    fun honkHorn() {
        val current = _uiState.value.activeVehicle ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCommandPending = true, statusBanner = "Honking horn…") }
            runCatching { repository.honkHorn(current) }
                .onSuccess {
                    _uiState.update { it.copy(isCommandPending = false, statusBanner = "Horn honked") }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isCommandPending = false, errorMessage = err.message ?: "Honk horn failed")
                    }
                }
        }
    }

    fun logout() {
        stopPollingLoop()
        expectedLockState = null
        expectedFrunkOpen = null
        expectedRearTrunkOpen = null
        repository.logout()
        _uiState.value = GravityWatchUiState(
            isInteractive = _uiState.value.isInteractive,
            useFahrenheit = repository.isFahrenheitPreferred()
        )
    }

    class Factory(private val repository: LucidVehicleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GravityWatchViewModel(repository) as T
        }
    }
}
