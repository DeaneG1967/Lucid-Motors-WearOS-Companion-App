package com.droid.lucidgravitywatch.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import com.droid.lucidgravitywatch.theme.LucidGravityWatchTheme
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class GravityWatchAppUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleGravity = GravityVehicleSnapshot(
        vehicleId = "veh-gravity-gt",
        vin = "50EA1GBA7RA000888",
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
        chargeState = ChargeState.CABLE_CONNECTED,
        chargeLimitPercent = 85,
        chargeRateKw = 19.2,
        chargeRateMph = 58.0,
        sessionMinutesRemaining = 18,
        interiorTempCelsius = 21.0,
        exteriorTempCelsius = 16.0,
        targetTempCelsius = 21.0,
        hvacPower = HvacPower.PRECONDITION,
        defrostState = DefrostState.OFF,
        maxAcEnabled = false,
        driverSeatHeatMode = SeatClimateMode.LOW,
        driverSeatVentMode = SeatClimateMode.OFF,
        passengerSeatHeatMode = SeatClimateMode.OFF,
        steeringHeaterLevel = SteeringWheelHeaterLevel.LEVEL_1,
        odometerKm = 3200.0,
        softwareVersion = "2.5.12",
        lastUpdatedMs = 1792113000000L
    )

    @Test
    fun loginView_submitsEmailPasswordAndRegion() {
        var submittedEmail = ""
        var submittedPassword = ""
        var submittedRegion = LucidRegion.US

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = false,
                        selectedRegion = LucidRegion.EU
                    ),
                    onSelectRegion = {},
                    onLogin = { email, pass, region ->
                        submittedEmail = email
                        submittedPassword = pass
                        submittedRegion = region
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("LUCID ACCESS").assertIsDisplayed()
        // Step 1: "Lucid account email" entry box is displayed without password box underneath
        composeTestRule.onNodeWithTag("login_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").assertDoesNotExist()

        composeTestRule.onNodeWithTag("login_email_input").performTextInput("pilot@lucidmotors.com")
        composeTestRule.onNodeWithTag("login_email_input").performImeAction()

        // Step 2: "Lucid account email" entry box is replaced by "Lucid account password" entry box
        composeTestRule.onNodeWithTag("login_email_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("login_password_input").assertIsDisplayed()

        composeTestRule.onNodeWithTag("login_password_input").performTextInput("Gravity2026!")
        composeTestRule.onNodeWithText("Gravity2026!").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").performImeAction()

        assertEquals("pilot@lucidmotors.com", submittedEmail)
        assertEquals("Gravity2026!", submittedPassword)
        assertEquals(LucidRegion.EU, submittedRegion)
    }

    @Test
    fun simplifiedDashboard_showsChargeLevelAtTopAndFiveIconButtonsBelow() {
        var frunkClicked = false
        var lockClicked = false
        var trunkClicked = false
        var stopChargingClicked = false
        var hornClicked = false
        var lightsClicked = false
        val currentVehicle = mutableStateOf(sampleGravity)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = true,
                        hasFreshTelemetry = true,
                        vehicles = listOf(currentVehicle.value),
                        selectedVehicleId = currentVehicle.value.vehicleId
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleFrunk = { frunkClicked = true },
                    onToggleLock = {
                        lockClicked = true
                        currentVehicle.value = currentVehicle.value.copy(lockState = LockState.UNLOCKED)
                    },
                    onToggleRearTrunk = { trunkClicked = true },
                    onStopCharging = { stopChargingClicked = true },
                    onHonkHorn = { hornClicked = true },
                    onFlashLights = { lightsClicked = true }
                )
            }
        }

        // Verify charge level at top; sampleGravity has chargeRateKw = 19.2 (> 0.1kW) -> nearest kW "19 kW"
        composeTestRule.onNodeWithTag("charge_level_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("84%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("19 kW").assertIsDisplayed()

        // Tapping charging power text opens "Stop Charging?" confirmation dialog
        composeTestRule.onNodeWithText("19 kW").performClick()
        org.junit.Assert.assertFalse(stopChargingClicked)
        composeTestRule.onNodeWithText("Stop Charging?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()
        org.junit.Assert.assertFalse(stopChargingClicked)
        composeTestRule.onNodeWithText("19 kW").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(stopChargingClicked)

        // When plugged in with charging power between -0.1kW and 0.1kW -> shows lightning bolt + "0 kW"
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.CABLE_CONNECTED,
            chargeRateKw = 0.05
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 kW").assertIsDisplayed()

        // When API returns EVSE_NOT_READY (proto 25) -> treated same as CABLE_CONNECTED with red lightning bolt + "0 kW"
        assertEquals(ChargeState.EVSE_NOT_READY, ChargeState.fromProto(25))
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.fromProto(25),
            chargeRateKw = 0.0
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 kW").assertIsDisplayed()

        // When RangeXChange discharging (< -0.1kW) -> shows lightning bolt + "-9 kW"
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.DISCHARGING,
            chargeRateKw = -9.4
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertIsDisplayed()
        composeTestRule.onNodeWithText("-9 kW").assertIsDisplayed()

        // When unplugged and charging power within [-0.1, 0.1] -> shows vehicle nickname
        currentVehicle.value = currentVehicle.value.copy(
            chargeState = ChargeState.NOT_CONNECTED,
            chargeRateKw = 0.0
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charging_bolt_icon").assertDoesNotExist()
        composeTestRule.onNodeWithText("GRAVITY GT").assertIsDisplayed()

        composeTestRule.onNodeWithTag("swap_vehicle_button").assertDoesNotExist()

        // Verify Locked state initially shown on Door Lock/Unlock button
        composeTestRule.onNodeWithContentDescription("Doors Locked - Tap to Unlock").assertIsDisplayed()

        // 1. Frunk button requires confirmation ("Open Frunk?" when closed, "Close Frunk?" when open)
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        org.junit.Assert.assertFalse(frunkClicked)
        composeTestRule.onNodeWithText("Open Frunk?").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeTestRule.onNodeWithText("Confirm").assertDoesNotExist()
        // Cancel (X icon button) does not send command
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()
        org.junit.Assert.assertFalse(frunkClicked)
        // Confirm (Checkmark icon button) sends command
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(frunkClicked)

        // When Frunk is open, pressing Frunk shows "Close Frunk?"
        currentVehicle.value = currentVehicle.value.copy(frunkState = DoorState.OPEN)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithText("Close Frunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()

        // 2. Door Lock/Unlock button requires confirmation ("Unlock Doors?" when locked, "Lock Doors?" when unlocked)
        composeTestRule.onNodeWithTag("primary_lock_toggle_button").performClick()
        org.junit.Assert.assertFalse(lockClicked)
        composeTestRule.onNodeWithText("Unlock Doors?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(lockClicked)
        composeTestRule.onNodeWithContentDescription("Doors Unlocked - Tap to Lock").assertIsDisplayed()

        // Now unlocked -> pressing Lock button shows "Lock Doors?"
        composeTestRule.onNodeWithTag("primary_lock_toggle_button").performClick()
        composeTestRule.onNodeWithText("Lock Doors?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()

        // 3. Trunk button requires confirmation ("Open Trunk?" when closed, "Close Trunk?" when open)
        composeTestRule.onNodeWithTag("overview_trunk_button").performClick()
        org.junit.Assert.assertFalse(trunkClicked)
        composeTestRule.onNodeWithText("Open Trunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(trunkClicked)

        currentVehicle.value = currentVehicle.value.copy(rearTrunkState = DoorState.OPEN)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_trunk_button").performClick()
        composeTestRule.onNodeWithText("Close Trunk?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmation_cancel_button").performClick()

        // 4. Horn and Lights trigger immediately without confirmation pop-up
        composeTestRule.onNodeWithTag("overview_horn_button").performClick()
        assertTrue(hornClicked)

        composeTestRule.onNodeWithTag("overview_lights_button").performClick()
        assertTrue(lightsClicked)
    }

    @Test
    fun simplifiedDashboard_freezesControlsAndShowsReasonUntilCarAwakeAndStatusKnown() {
        var frunkClicked = false
        var hornClicked = false
        val uiState = mutableStateOf(
            GravityWatchUiState(
                isAuthenticated = true,
                hasFreshTelemetry = true,
                isWakingVehicle = true,
                vehicles = listOf(sampleGravity.copy(powerState = PowerState.SLEEP)),
                selectedVehicleId = sampleGravity.vehicleId
            )
        )

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = uiState.value,
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onToggleFrunk = { frunkClicked = true },
                    onHonkHorn = { hornClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Waking car…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_popup_overlay").assertDoesNotExist()
        composeTestRule.onNodeWithTag("overview_horn_button").performClick()
        org.junit.Assert.assertFalse(hornClicked)

        // Car awake and status known -> controls work and the status line is replaced
        uiState.value = uiState.value.copy(
            isWakingVehicle = false,
            vehicles = listOf(sampleGravity)
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_status_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("overview_frunk_button").performClick()
        composeTestRule.onNodeWithTag("confirmation_confirm_button").performClick()
        assertTrue(frunkClicked)
    }

    @Test
    fun vehicleSelector_shownWhenNoVehicleChosen_andSwapButtonOpensIt() {
        val secondCar = sampleGravity.copy(vehicleId = "veh-air-pe", nickname = "PE GT", vin = "50EA1GBA5NA005210")
        val uiState = mutableStateOf(
            GravityWatchUiState(
                isAuthenticated = true,
                hasFreshTelemetry = true,
                vehicles = listOf(sampleGravity, secondCar)
            )
        )
        var selectedId: String? = null
        var swapClicked = false

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = uiState.value,
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> },
                    onSelectVehicle = { id ->
                        selectedId = id
                        uiState.value = uiState.value.copy(selectedVehicleId = id, isSelectingVehicle = false)
                    },
                    onOpenVehicleSelector = {
                        swapClicked = true
                        uiState.value = uiState.value.copy(isSelectingVehicle = true)
                    }
                )
            }
        }

        // No vehicle chosen yet -> selector lists both cars, no Cancel
        composeTestRule.onNodeWithTag("vehicle_selector").assertIsDisplayed()
        composeTestRule.onNodeWithText("VIN …005210", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("vehicle_selector_cancel").assertDoesNotExist()

        composeTestRule.onNodeWithTag("vehicle_option_veh-air-pe").performClick()
        assertEquals("veh-air-pe", selectedId)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_selector").assertDoesNotExist()

        // Dashboard shows the swap button, which reopens the selector with a Cancel option
        composeTestRule.onNodeWithTag("swap_vehicle_button").performClick()
        assertTrue(swapClicked)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vehicle_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vehicle_selector_cancel").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun simplifiedDashboard_showsYellowPollingDotOnlyWhilePollingInInteractiveState() {
        val isPollingState = mutableStateOf(true)

        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = true,
                        isInteractive = true,
                        isPolling = isPollingState.value,
                        vehicles = listOf(sampleGravity),
                        selectedVehicleId = sampleGravity.vehicleId
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("polling_indicator_dot").assertIsDisplayed()

        isPollingState.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("polling_indicator_dot").assertDoesNotExist()
    }
}

