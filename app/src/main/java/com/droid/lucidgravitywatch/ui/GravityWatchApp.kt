package com.droid.lucidgravitywatch.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.lucidgravitywatch.R
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.theme.AuroraGreen
import com.droid.lucidgravitywatch.theme.CrimsonAlert
import com.droid.lucidgravitywatch.theme.CryoBlue
import com.droid.lucidgravitywatch.theme.EurekaGold
import com.droid.lucidgravitywatch.theme.LucidPlatinum
import com.droid.lucidgravitywatch.theme.LucidSilver
import com.droid.lucidgravitywatch.theme.ObsidianBlack
import com.droid.lucidgravitywatch.theme.StealthBorder
import com.droid.lucidgravitywatch.theme.StealthCard
import com.droid.lucidgravitywatch.theme.StealthSurface
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import com.droid.lucidgravitywatch.viewmodel.GravityWatchViewModel
import com.droid.lucidgravitywatch.viewmodel.WatchDialPage
import kotlin.math.roundToInt

@Composable
fun GravityWatchApp(viewModel: GravityWatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    GravityWatchScreen(
        uiState = uiState,
        onSelectRegion = viewModel::selectRegion,
        onLogin = viewModel::login,
        onSelectDialPage = viewModel::selectDialPage,
        onToggleTemperatureUnit = viewModel::toggleTemperatureUnit,
        onToggleWatchBezelFrame = viewModel::toggleWatchBezelFrame,
        onRefresh = viewModel::refreshVehicles,
        onWakeVehicle = viewModel::wakeVehicle,
        onToggleLock = viewModel::toggleLock,
        onToggleFrunk = viewModel::toggleFrunk,
        onToggleRearTrunk = viewModel::toggleRearTrunk,
        onToggleChargePort = viewModel::toggleChargePort,
        onToggleClimatePrecondition = viewModel::toggleClimatePrecondition,
        onAdjustTemperature = viewModel::adjustTargetTemperature,
        onToggleDefrost = viewModel::toggleDefrost,
        onCycleDriverSeatHeat = viewModel::cycleDriverSeatHeat,
        onCycleSteeringHeater = viewModel::cycleSteeringWheelHeater,
        onToggleBatteryPrecon = viewModel::toggleBatteryPreconditioning,
        onSetChargeLimit = viewModel::setChargeLimit,
        onToggleCharging = viewModel::toggleCharging,
        onFlashLights = viewModel::flashLights,
        onHonkHorn = viewModel::honkHorn,
        onSelectVehicle = viewModel::selectVehicle,
        onLogout = viewModel::logout
    )
}

@Composable
fun GravityWatchScreen(
    uiState: GravityWatchUiState,
    onSelectRegion: (LucidRegion) -> Unit,
    onLogin: (String, String, LucidRegion) -> Unit,
    onSelectDialPage: (WatchDialPage) -> Unit = {},
    onToggleTemperatureUnit: () -> Unit = {},
    onToggleWatchBezelFrame: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onWakeVehicle: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onToggleFrunk: () -> Unit = {},
    onToggleRearTrunk: () -> Unit = {},
    onToggleChargePort: () -> Unit = {},
    onToggleClimatePrecondition: () -> Unit = {},
    onAdjustTemperature: (Double) -> Unit = {},
    onToggleDefrost: () -> Unit = {},
    onCycleDriverSeatHeat: () -> Unit = {},
    onCycleSteeringHeater: () -> Unit = {},
    onToggleBatteryPrecon: () -> Unit = {},
    onSetChargeLimit: (Int) -> Unit = {},
    onToggleCharging: () -> Unit = {},
    onFlashLights: () -> Unit = {},
    onHonkHorn: () -> Unit = {},
    onSelectVehicle: (String) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        val isLargeDisplay = maxWidth > 320.dp && maxHeight > 320.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isLargeDisplay) 16.dp else 0.dp,
                    vertical = if (isLargeDisplay) 6.dp else 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLargeDisplay) {
                HostCompanionTopBar(
                    uiState = uiState,
                    onToggleWatchBezelFrame = onToggleWatchBezelFrame,
                    onLogout = onLogout
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val watchModifier = if (isLargeDisplay && uiState.watchBezelFrameEnabled) {
                Modifier
                    .size(340.dp)
                    .clip(CircleShape)
                    .background(StealthSurface)
                    .border(3.dp, StealthBorder, CircleShape)
            } else {
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxSize()
                    .background(ObsidianBlack)
            }

            Box(
                modifier = watchModifier,
                contentAlignment = Alignment.Center
            ) {
                val activeVehicle = uiState.activeVehicle

                if (!uiState.isAuthenticated) {
                    LucidWatchLoginView(
                        uiState = uiState,
                        onSelectRegion = onSelectRegion,
                        onLogin = onLogin
                    )
                } else {
                    SimplifiedWatchDashboard(
                        uiState = uiState,
                        vehicle = activeVehicle,
                        onRefresh = onRefresh,
                        onToggleFrunk = onToggleFrunk,
                        onToggleLock = onToggleLock,
                        onToggleRearTrunk = onToggleRearTrunk,
                        onHonkHorn = onHonkHorn,
                        onFlashLights = onFlashLights,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCompanionTopBar(
    uiState: GravityWatchUiState,
    onToggleWatchBezelFrame: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StealthCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = EurekaGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LUCID CARS",
                color = LucidPlatinum,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChipButton(
                label = if (uiState.watchBezelFrameEnabled) "Round Watch" else "Expanded",
                active = uiState.watchBezelFrameEnabled,
                onClick = onToggleWatchBezelFrame,
                testTag = "bezel_toggle_button"
            )
            if (uiState.isAuthenticated) {
                SmallChipButton(
                    label = "Sign Out",
                    active = false,
                    onClick = onLogout,
                    testTag = "vehicle_logout_button"
                )
            }
        }
    }
}

@Composable
private fun LucidWatchLoginView(
    uiState: GravityWatchUiState,
    onSelectRegion: (LucidRegion) -> Unit,
    onLogin: (String, String, LucidRegion) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LUCID CARS",
            color = EurekaGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.6.sp
        )
        Text(
            text = "Wear OS Cloud Control",
            color = LucidSilver,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LucidRegion.entries.forEach { region ->
                SmallChipButton(
                    label = region.code.uppercase(),
                    active = uiState.selectedRegion == region,
                    onClick = { onSelectRegion(region) },
                    testTag = "region_${region.code}"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Lucid Email", fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_email_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", fontSize = 11.sp) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input")
        )

        if (!uiState.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = uiState.errorMessage,
                color = CrimsonAlert,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("login_error_text")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { onLogin(email, password, uiState.selectedRegion) },
            enabled = !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = EurekaGold,
                contentColor = ObsidianBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("login_submit_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ObsidianBlack
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text("Sign In to Lucid Cloud", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Gateway: ${uiState.selectedRegion.apiHost}",
            color = LucidSilver.copy(alpha = 0.7f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

private enum class PendingConfirmationAction {
    FRUNK,
    LOCK,
    TRUNK
}

/**
 * Simplified Wear OS dashboard:
 * - Current charge level (%) and vehicle nickname at the top
 * - First row of three icon buttons:
 *   1. Frunk
 *   2. Door Lock/Unlock (displaying current Locked / Unlocked state)
 *   3. Trunk
 * - Second row of two icon buttons underneath:
 *   4. Horn (left)
 *   5. Lights (right)
 */
@Composable
private fun SimplifiedWatchDashboard(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot?,
    onRefresh: () -> Unit,
    onToggleFrunk: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleRearTrunk: () -> Unit,
    onHonkHorn: () -> Unit,
    onFlashLights: () -> Unit,
    onLogout: () -> Unit
) {
    if (vehicle == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No Lucid vehicle found (${uiState.userEmail}).",
                color = LucidSilver,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChipButton("Refresh", true, onRefresh, "empty_refresh_button")
                SmallChipButton("Sign Out", false, onLogout, "empty_logout_button")
            }
        }
        return
    }

    var pendingConfirmation by rememberSaveable { mutableStateOf<PendingConfirmationAction?>(null) }

    val isLocked = vehicle.lockState == LockState.LOCKED
    val chargePercent = vehicle.batteryPercent.roundToInt().coerceIn(0, 100)
    val isCharging = vehicle.chargeState.isActivelyCharging

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        val twentyPixelsInDp = with(LocalDensity.current) { 20f.toDp() }
        val topHeaderPadding = maxHeight * 0.05f

        // Top Section: Current Charge Level & Vehicle Name moved down by 5% of screen vertical height
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topHeaderPadding)
                .testTag("charge_level_header")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    contentDescription = "Battery Charge",
                    tint = if (isCharging) AuroraGreen else EurekaGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$chargePercent%",
                    color = LucidPlatinum,
                    fontSize = 28.8.sp,
                    lineHeight = 28.8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("charge_level_text")
                )
            }
            if (vehicle.chargeRateKw > 0.1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.offset(y = (-2).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Charging Power",
                        tint = AuroraGreen,
                        modifier = Modifier
                            .size(14.dp)
                            .testTag("charging_bolt_icon")
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = vehicle.formatChargingPowerKw(),
                        color = LucidSilver,
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("vehicle_nickname_text")
                    )
                }
            } else {
                val subtitleText = if (vehicle.chargeState.isCablePluggedIn) {
                    "PLUGGED IN"
                } else {
                    vehicle.nickname.uppercase()
                }
                Text(
                    text = subtitleText,
                    color = LucidSilver,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .testTag("vehicle_nickname_text")
                )
            }
        }

        // Middle Section: Three Icon Buttons (Frunk, Door Lock/Unlock, Trunk) - 10% smaller, centered vertically on screen
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Frunk Icon Button (user's Open Frunk.webp graphic)
            ControlIconButton(
                drawableRes = R.drawable.btn_open_frunk,
                contentDesc = "Frunk Open Button",
                isActive = vehicle.frunkState.isOpen,
                isPrimaryCenter = false,
                activeColor = EurekaGold,
                onClick = { pendingConfirmation = PendingConfirmationAction.FRUNK },
                testTag = "overview_frunk_button"
            )

            // 2. Door Lock / Unlock Icon Button (user's Doors Locked.webp graphic + unlocked state)
            ControlIconButton(
                drawableRes = if (isLocked) R.drawable.btn_doors_locked else R.drawable.ic_lock_open,
                contentDesc = if (isLocked) "Doors Locked - Tap to Unlock" else "Doors Unlocked - Tap to Lock",
                isActive = true,
                isPrimaryCenter = true,
                activeColor = if (isLocked) EurekaGold else CrimsonAlert,
                onClick = { pendingConfirmation = PendingConfirmationAction.LOCK },
                testTag = "primary_lock_toggle_button"
            )

            // 3. Trunk Icon Button (user's Open Trunk.webp graphic)
            ControlIconButton(
                drawableRes = R.drawable.btn_open_trunk,
                contentDesc = "Trunk Open Button",
                isActive = vehicle.rearTrunkState.isOpen,
                isPrimaryCenter = false,
                activeColor = EurekaGold,
                onClick = { pendingConfirmation = PendingConfirmationAction.TRUNK },
                testTag = "overview_trunk_button"
            )
        }

        // Bottom Row: Two Icon Buttons underneath (Horn on left, Lights on right)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 6.dp + twentyPixelsInDp),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4. Horn Icon Button (user's Horn.webp graphic)
            ControlIconButton(
                drawableRes = R.drawable.btn_horn,
                contentDesc = "Honk Horn Button",
                isActive = false,
                isPrimaryCenter = false,
                activeColor = EurekaGold,
                onClick = onHonkHorn,
                testTag = "overview_horn_button"
            )

            // 5. Lights Icon Button (user's Lights.webp graphic)
            ControlIconButton(
                drawableRes = R.drawable.btn_lights,
                contentDesc = "Flash Lights Button",
                isActive = false,
                isPrimaryCenter = false,
                activeColor = EurekaGold,
                onClick = onFlashLights,
                testTag = "overview_lights_button"
            )
        }

        // Small yellow dot at the very bottom center of the watch screen while polling in Interactive power state
        if (uiState.isInteractive && uiState.isPolling) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEA00))
                    .semantics { contentDescription = "Polling Lucid API" }
                    .testTag("polling_indicator_dot")
            )
        }

        // Confirmation Pop-up Overlay for Frunk, Door Lock/Unlock, and Trunk actions
        val activeConfirmation = pendingConfirmation
        if (activeConfirmation != null) {
            val promptText = when (activeConfirmation) {
                PendingConfirmationAction.FRUNK -> if (vehicle.frunkState.isOpen) "Close Frunk?" else "Open Frunk?"
                PendingConfirmationAction.LOCK -> if (isLocked) "Unlock Doors?" else "Lock Doors?"
                PendingConfirmationAction.TRUNK -> if (vehicle.rearTrunkState.isOpen) "Close Trunk?" else "Open Trunk?"
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack.copy(alpha = 0.88f))
                    .clickable(enabled = true, onClick = {})
                    .testTag("confirmation_popup_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(StealthCard)
                        .border(1.5.dp, EurekaGold, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = promptText,
                        color = LucidPlatinum,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("confirmation_prompt_text")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(StealthSurface)
                                .border(1.dp, StealthBorder, RoundedCornerShape(14.dp))
                                .clickable { pendingConfirmation = null }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("confirmation_cancel_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = LucidSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(EurekaGold)
                                .border(1.dp, EurekaGold, RoundedCornerShape(14.dp))
                                .clickable {
                                    pendingConfirmation = null
                                    when (activeConfirmation) {
                                        PendingConfirmationAction.FRUNK -> onToggleFrunk()
                                        PendingConfirmationAction.LOCK -> onToggleLock()
                                        PendingConfirmationAction.TRUNK -> onToggleRearTrunk()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("confirmation_confirm_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Confirm",
                                color = ObsidianBlack,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlIconButton(
    @DrawableRes drawableRes: Int,
    contentDesc: String,
    isActive: Boolean,
    isPrimaryCenter: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val buttonSize = if (isPrimaryCenter) 62.4.dp else 55.2.dp
    val borderColor = if (isActive) activeColor else StealthBorder

    Surface(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .border(
                width = if (isPrimaryCenter) 1.5.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        color = ObsidianBlack,
        shape = CircleShape
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SmallChipButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) EurekaGold else StealthCard)
            .border(
                width = 1.dp,
                color = if (active) EurekaGold else StealthBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) ObsidianBlack else LucidPlatinum,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
