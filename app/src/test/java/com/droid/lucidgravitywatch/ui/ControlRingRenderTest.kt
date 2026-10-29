package com.droid.lucidgravitywatch.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import com.droid.lucidgravitywatch.theme.LucidGravityWatchTheme
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders real pixels to check the open-state ring follows the closure state on every change. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlRingRenderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val air = GravityVehicleSnapshot(
        vehicleId = "veh-air",
        vin = "50EA1GBA5NA005210",
        nickname = "PE GT",
        model = VehicleModel.AIR,
        variantName = "Grand Touring",
        paintColorName = "Stellar White",
        powerState = PowerState.MONITOR,
        lockState = LockState.LOCKED,
        walkawayState = WalkawayState.ACTIVE,
        frunkState = DoorState.CLOSED,
        rearTrunkState = DoorState.CLOSED,
        chargePortState = DoorState.CLOSED,
        frontLeftDoor = DoorState.CLOSED,
        frontRightDoor = DoorState.CLOSED,
        rearLeftDoor = DoorState.CLOSED,
        rearRightDoor = DoorState.CLOSED,
        batteryPercent = 66.0,
        remainingRangeKm = 486.0,
        batteryKwh = 68.3,
        batteryCapacityKwh = 102.8,
        batteryPreconStatus = BatteryPreconStatus.OFF,
        chargeState = ChargeState.NOT_CONNECTED,
        chargeLimitPercent = 80,
        chargeRateKw = 0.0,
        chargeRateMph = 0.0,
        sessionMinutesRemaining = 0,
        interiorTempCelsius = 20.0,
        exteriorTempCelsius = 19.0,
        targetTempCelsius = 21.0,
        hvacPower = HvacPower.OFF,
        defrostState = DefrostState.OFF,
        maxAcEnabled = false,
        driverSeatHeatMode = SeatClimateMode.OFF,
        driverSeatVentMode = SeatClimateMode.OFF,
        passengerSeatHeatMode = SeatClimateMode.OFF,
        steeringHeaterLevel = SteeringWheelHeaterLevel.OFF,
        odometerKm = 13360.0,
        softwareVersion = "2.9.0",
        lastUpdatedMs = 1792113000000L
    )

    @Test
    fun trunkRing_isYellowOnlyWhileTrunkIsOpen_acrossStateChanges() {
        val trunkState = mutableStateOf(DoorState.CLOSED)
        composeTestRule.setContent {
            LucidGravityWatchTheme {
                GravityWatchScreen(
                    uiState = GravityWatchUiState(
                        isAuthenticated = true,
                        hasFreshTelemetry = true,
                        vehicles = listOf(air.copy(rearTrunkState = trunkState.value)),
                        selectedVehicleId = air.vehicleId
                    ),
                    onSelectRegion = {},
                    onLogin = { _, _, _ -> }
                )
            }
        }

        // Same sequence as on the watch: closed -> ajar -> open -> ajar -> closed
        val expectedRing = listOf(
            DoorState.CLOSED to false,
            DoorState.AJAR to true,
            DoorState.OPEN to true,
            DoorState.AJAR to true,
            DoorState.CLOSED to false
        )
        for ((state, ringExpected) in expectedRing) {
            trunkState.value = state
            composeTestRule.waitForIdle()
            val ring = trunkRingIsYellow()
            if (ringExpected) {
                assertTrue("trunk $state should show the yellow ring", ring)
            } else {
                assertFalse("trunk $state should not show the yellow ring", ring)
            }
        }
    }

    /**
     * True if the top edge of the trunk button's border is the open-state yellow (0xFFFFEA00).
     * Draws the activity's view hierarchy into a bitmap (captureToImage() hangs under Robolectric).
     */
    private fun trunkRingIsYellow(): Boolean {
        val bounds = composeTestRule.onNodeWithTag("overview_trunk_button").fetchSemanticsNode().boundsInWindow
        val root = composeTestRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        composeTestRule.runOnUiThread { root.draw(Canvas(bitmap)) }
        val x = ((bounds.left + bounds.right) / 2).toInt()
        val top = bounds.top.toInt()
        return (top..top + 3).any { y ->
            val c = bitmap.getPixel(x, y)
            android.graphics.Color.red(c) > 240 && android.graphics.Color.green(c) in 215..245 &&
                android.graphics.Color.blue(c) < 30
        }
    }
}
