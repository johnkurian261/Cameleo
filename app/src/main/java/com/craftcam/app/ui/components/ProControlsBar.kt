package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.model.*
import com.craftcam.app.model.ProCameraFormatters.STANDARD_ISO_STOPS
import com.craftcam.app.model.ProCameraFormatters.STANDARD_SHUTTER_FRACTIONS
import com.craftcam.app.model.ProCameraFormatters.formatFocusDistance
import com.craftcam.app.model.ProCameraFormatters.formatShutterSpeed
import com.craftcam.app.model.ProCameraFormatters.shutterFractionToNanos
import com.craftcam.app.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProControlsBar(
    modifier: Modifier = Modifier,
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    liveIso: Int,
    liveExposureNs: Long,
    liveFocusDistance: Float,
    onConfigChange: (ProCameraConfig) -> Unit,
    onTabSelected: (ProControlTab?) -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Popup Interactive Panel for active control tab
        AnimatedVisibility(
            visible = config.activeControl != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated.copy(alpha = 0.94f))
                    .border(1.dp, DividerSubtle, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when (config.activeControl) {
                    ProControlTab.ISO -> IsoControlPanel(
                        config = config,
                        caps = caps,
                        liveIso = liveIso,
                        onIsoChange = { mode, iso ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(config.copy(isoMode = mode, iso = iso))
                        }
                    )
                    ProControlTab.SHUTTER -> ShutterControlPanel(
                        config = config,
                        caps = caps,
                        liveExpNs = liveExposureNs,
                        onShutterChange = { mode, nanos ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(config.copy(shutterMode = mode, exposureTimeNs = nanos))
                        }
                    )
                    ProControlTab.WB -> WbControlPanel(
                        config = config,
                        caps = caps,
                        onWbChange = { mode, kelvin, tint ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(config.copy(wbMode = mode, kelvin = kelvin, tint = tint))
                        }
                    )
                    ProControlTab.EV -> EvControlPanel(
                        config = config,
                        caps = caps,
                        onEvChange = { ev ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(config.copy(evCompensation = ev))
                        }
                    )
                    ProControlTab.FOCUS -> FocusControlPanel(
                        config = config,
                        caps = caps,
                        liveFocusDistance = liveFocusDistance,
                        onFocusChange = { mode, dist ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(config.copy(focusMode = mode, focusDistance = dist))
                        }
                    )
                    ProControlTab.TOOLS -> ToolsControlPanel(
                        config = config,
                        onConfigChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfigChange(it)
                        }
                    )
                    null -> {}
                }
            }
        }

        // 2. Primary 6-Tile Parameter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceElevated.copy(alpha = 0.90f))
                .border(1.dp, DividerSubtle, RoundedCornerShape(18.dp))
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ISO Tile
            val isIsoLocked = config.isoMode == IsoMode.MANUAL
            ProParamTile(
                label = "ISO",
                value = if (isIsoLocked) "${config.iso}" else "A $liveIso",
                isLocked = isIsoLocked,
                isSelected = config.activeControl == ProControlTab.ISO,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.ISO)
                }
            )

            // SHUTTER Tile
            val isShutterLocked = config.shutterMode == ShutterMode.MANUAL
            ProParamTile(
                label = "SHUTTER",
                value = if (isShutterLocked) formatShutterSpeed(config.exposureTimeNs) else "A ${formatShutterSpeed(liveExposureNs)}",
                isLocked = isShutterLocked,
                isSelected = config.activeControl == ProControlTab.SHUTTER,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.SHUTTER)
                }
            )

            // WB Tile
            val isWbLocked = config.wbMode == WhiteBalanceMode.MANUAL
            ProParamTile(
                label = "WB",
                value = if (isWbLocked) "${config.kelvin}K" else "AWB",
                isLocked = isWbLocked,
                isSelected = config.activeControl == ProControlTab.WB,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.WB)
                }
            )

            // EV Tile
            val evVal = config.evCompensation
            val evSign = if (evVal > 0f) "+" else ""
            val evText = if (config.exposureMode == ExposureMode.MANUAL) "M" else "$evSign${String.format(Locale.US, "%.1f", evVal)}"
            ProParamTile(
                label = "EV",
                value = evText,
                isLocked = config.exposureMode == ExposureMode.MANUAL,
                isSelected = config.activeControl == ProControlTab.EV,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.EV)
                }
            )

            // FOCUS Tile
            val isFocusLocked = config.focusMode == FocusMode.MANUAL
            val focusText = if (isFocusLocked) {
                formatFocusDistance(config.focusDistance, caps?.minFocusDistance ?: 10f)
            } else {
                "AF"
            }
            ProParamTile(
                label = "FOCUS",
                value = focusText,
                isLocked = isFocusLocked,
                isSelected = config.activeControl == ProControlTab.FOCUS,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.FOCUS)
                }
            )

            // TOOLS Tile (Peaking, Zebra, Histogram)
            val toolsCount = (if (config.focusPeakingEnabled) 1 else 0) +
                             (if (config.zebraEnabled) 1 else 0) +
                             (if (config.histogramEnabled) 1 else 0)
            ProParamTile(
                label = "TOOLS",
                value = if (toolsCount > 0) "ON ($toolsCount)" else "OFF",
                isLocked = toolsCount > 0,
                isSelected = config.activeControl == ProControlTab.TOOLS,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTabSelected(ProControlTab.TOOLS)
                }
            )
        }
    }
}

@Composable
private fun ProParamTile(
    label: String,
    value: String,
    isLocked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VintageAmber.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) VintageAmber else TextMuted
            )
            if (isLocked) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = VintageAmber,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isSelected || isLocked) VintageAmber else TextPrimary
        )
    }
}

// ─── ISO Control Panel ───────────────────────────────────────────────────────
@Composable
private fun IsoControlPanel(
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    liveIso: Int,
    onIsoChange: (IsoMode, Int) -> Unit
) {
    val minIso = caps?.minIso ?: 50
    val maxIso = caps?.maxIso ?: 3200
    val availableStops = remember(minIso, maxIso) {
        STANDARD_ISO_STOPS.filter { it in minIso..maxIso }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ISO SENSITIVITY",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
            Text(
                text = if (config.isoMode == IsoMode.AUTO) "AUTO (Current: $liveIso)" else "MANUAL (${config.iso})",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProOptionPill(
                title = "AUTO",
                isSelected = config.isoMode == IsoMode.AUTO,
                onClick = { onIsoChange(IsoMode.AUTO, liveIso) }
            )

            availableStops.forEach { isoStop ->
                ProOptionPill(
                    title = "$isoStop",
                    isSelected = config.isoMode == IsoMode.MANUAL && config.iso == isoStop,
                    onClick = { onIsoChange(IsoMode.MANUAL, isoStop) }
                )
            }
        }
    }
}

// ─── Shutter Control Panel ───────────────────────────────────────────────────
@Composable
private fun ShutterControlPanel(
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    liveExpNs: Long,
    onShutterChange: (ShutterMode, Long) -> Unit
) {
    val minNs = caps?.minShutterNs ?: 100_000L
    val maxNs = caps?.maxShutterNs ?: 15_000_000_000L

    val availableFractions = remember(minNs, maxNs) {
        STANDARD_SHUTTER_FRACTIONS.filter { (num, den) ->
            val nanos = shutterFractionToNanos(num, den)
            nanos in minNs..maxNs
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SHUTTER SPEED",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
            Text(
                text = if (config.shutterMode == ShutterMode.AUTO) "AUTO (Current: ${formatShutterSpeed(liveExpNs)})" else "MANUAL (${formatShutterSpeed(config.exposureTimeNs)})",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProOptionPill(
                title = "AUTO",
                isSelected = config.shutterMode == ShutterMode.AUTO,
                onClick = { onShutterChange(ShutterMode.AUTO, liveExpNs) }
            )

            availableFractions.forEach { (num, den) ->
                val nanos = shutterFractionToNanos(num, den)
                val label = formatShutterSpeed(nanos)
                ProOptionPill(
                    title = label,
                    isSelected = config.shutterMode == ShutterMode.MANUAL && config.exposureTimeNs == nanos,
                    onClick = { onShutterChange(ShutterMode.MANUAL, nanos) }
                )
            }
        }
    }
}

// ─── White Balance Control Panel ─────────────────────────────────────────────
@Composable
private fun WbControlPanel(
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    onWbChange: (WhiteBalanceMode, Int, Float) -> Unit
) {
    val supportsWb = caps?.supportsManualWb ?: true

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WHITE BALANCE",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
            Text(
                text = if (config.wbMode == WhiteBalanceMode.AUTO) "AUTO" else "${config.kelvin}K",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProOptionPill(
                title = "AUTO AWB",
                isSelected = config.wbMode == WhiteBalanceMode.AUTO,
                onClick = { onWbChange(WhiteBalanceMode.AUTO, config.kelvin, config.tint) }
            )

            ProOptionPill(
                title = "MANUAL",
                isSelected = config.wbMode == WhiteBalanceMode.MANUAL,
                onClick = { onWbChange(WhiteBalanceMode.MANUAL, config.kelvin, config.tint) }
            )
        }

        if (config.wbMode == WhiteBalanceMode.MANUAL && supportsWb) {
            Spacer(modifier = Modifier.height(6.dp))

            // Kelvin Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Temperature", fontSize = 10.sp, color = TextMuted)
                Text(text = "${config.kelvin}K", fontSize = 10.sp, color = TextPrimary)
            }
            Slider(
                value = config.kelvin.toFloat(),
                onValueChange = { onWbChange(WhiteBalanceMode.MANUAL, it.roundToInt(), config.tint) },
                valueRange = 2500f..9000f,
                steps = 65,
                colors = SliderDefaults.colors(thumbColor = VintageAmber, activeTrackColor = VintageAmber)
            )

            // Tint Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Tint (Green / Magenta)", fontSize = 10.sp, color = TextMuted)
                Text(text = String.format(Locale.US, "%.2f", config.tint), fontSize = 10.sp, color = TextPrimary)
            }
            Slider(
                value = config.tint,
                onValueChange = { onWbChange(WhiteBalanceMode.MANUAL, config.kelvin, it) },
                valueRange = -0.5f..0.5f,
                steps = 20,
                colors = SliderDefaults.colors(thumbColor = VintageAmber, activeTrackColor = VintageAmber)
            )
        } else if (!supportsWb) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Manual WB is not supported on this camera sensor (AWB locked).",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

// ─── Exposure Value (EV) Control Panel ───────────────────────────────────────
@Composable
private fun EvControlPanel(
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    onEvChange: (Float) -> Unit
) {
    val minEvVal = (caps?.minEv ?: -4) * (caps?.evStep ?: 0.5f)
    val maxEvVal = (caps?.maxEv ?: 4) * (caps?.evStep ?: 0.5f)
    val step = caps?.evStep ?: 0.5f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXPOSURE COMPENSATION",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
            val sign = if (config.evCompensation > 0f) "+" else ""
            Text(
                text = "$sign${String.format(Locale.US, "%.1f EV", config.evCompensation)}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = config.evCompensation,
            onValueChange = {
                val clamped = ((it / step).roundToInt() * step).coerceIn(minEvVal, maxEvVal)
                onEvChange(clamped)
            },
            valueRange = minEvVal..maxEvVal,
            steps = ((maxEvVal - minEvVal) / step).roundToInt() - 1,
            colors = SliderDefaults.colors(thumbColor = VintageAmber, activeTrackColor = VintageAmber)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ProOptionPill(
                title = "RESET EV (0.0)",
                isSelected = config.evCompensation == 0f,
                onClick = { onEvChange(0f) }
            )
        }
    }
}

// ─── Manual Focus Control Panel ──────────────────────────────────────────────
@Composable
private fun FocusControlPanel(
    config: ProCameraConfig,
    caps: CameraHardwareCapabilities?,
    liveFocusDistance: Float,
    onFocusChange: (FocusMode, Float) -> Unit
) {
    val supportsFocus = caps?.supportsManualFocus ?: true
    val maxDiopters = caps?.minFocusDistance ?: 10.0f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOCUS DISTANCE",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
            Text(
                text = if (config.focusMode == FocusMode.AUTO) "AUTO AF" else "MANUAL (${formatFocusDistance(config.focusDistance, maxDiopters)})",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProOptionPill(
                title = "AUTO AF",
                isSelected = config.focusMode == FocusMode.AUTO,
                onClick = { onFocusChange(FocusMode.AUTO, 0f) }
            )

            if (supportsFocus) {
                ProOptionPill(
                    title = "MANUAL MF",
                    isSelected = config.focusMode == FocusMode.MANUAL,
                    onClick = { onFocusChange(FocusMode.MANUAL, liveFocusDistance.coerceIn(0f, maxDiopters)) }
                )
            }
        }

        if (config.focusMode == FocusMode.MANUAL && supportsFocus) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "∞ Far", fontSize = 10.sp, color = TextMuted)
                Text(
                    text = formatFocusDistance(config.focusDistance, maxDiopters),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = VintageAmber
                )
                Text(text = "Near (Macro)", fontSize = 10.sp, color = TextMuted)
            }

            Slider(
                value = config.focusDistance,
                onValueChange = { onFocusChange(FocusMode.MANUAL, it) },
                valueRange = 0.0f..maxDiopters,
                colors = SliderDefaults.colors(thumbColor = VintageAmber, activeTrackColor = VintageAmber)
            )
        } else if (!supportsFocus) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Manual focus is unsupported by this camera lens (Fixed focus AF).",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

// ─── Exposure Tools Panel (Peaking, Zebra, Histogram) ────────────────────────
@Composable
private fun ToolsControlPanel(
    config: ProCameraConfig,
    onConfigChange: (ProCameraConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "PRO EXPOSURE & FOCUS TOOLS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = VintageAmber
        )

        // 1. Focus Peaking
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Focus Peaking", fontSize = 11.sp, color = TextPrimary)
                Text(text = "Real-time edge highlights", fontSize = 9.sp, color = TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProOptionPill(
                    title = if (config.focusPeakingEnabled) "ON" else "OFF",
                    isSelected = config.focusPeakingEnabled,
                    onClick = { onConfigChange(config.copy(focusPeakingEnabled = !config.focusPeakingEnabled)) }
                )
            }
        }

        if (config.focusPeakingEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Color:", fontSize = 10.sp, color = TextMuted)
                PeakingColor.values().forEach { col ->
                    ProOptionPill(
                        title = col.displayName,
                        isSelected = config.peakingColor == col,
                        onClick = { onConfigChange(config.copy(peakingColor = col)) }
                    )
                }
            }
        }

        HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)

        // 2. Zebra Overexposure Warning
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Zebra Warning", fontSize = 11.sp, color = TextPrimary)
                Text(text = "Highlight clipping stripes", fontSize = 9.sp, color = TextMuted)
            }
            ProOptionPill(
                title = if (config.zebraEnabled) "ON" else "OFF",
                isSelected = config.zebraEnabled,
                onClick = { onConfigChange(config.copy(zebraEnabled = !config.zebraEnabled)) }
            )
        }

        if (config.zebraEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Threshold:", fontSize = 10.sp, color = TextMuted)
                listOf(0.70f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f, 1.00f).forEach { thresh ->
                    val pct = (thresh * 100f).roundToInt()
                    ProOptionPill(
                        title = "$pct%",
                        isSelected = config.zebraThreshold == thresh,
                        onClick = { onConfigChange(config.copy(zebraThreshold = thresh)) }
                    )
                }
            }
        }

        HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)

        // 3. Live Histogram
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Live Histogram", fontSize = 11.sp, color = TextPrimary)
                Text(text = "256-bin luminance HUD", fontSize = 9.sp, color = TextMuted)
            }
            ProOptionPill(
                title = if (config.histogramEnabled) "ON" else "OFF",
                isSelected = config.histogramEnabled,
                onClick = { onConfigChange(config.copy(histogramEnabled = !config.histogramEnabled)) }
            )
        }
    }
}

// ─── Live Histogram HUD Composable ───────────────────────────────────────────
@Composable
fun HistogramView(
    modifier: Modifier = Modifier,
    bins: IntArray
) {
    Box(
        modifier = modifier
            .size(width = 130.dp, height = 70.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraBlack.copy(alpha = 0.65f))
            .border(1.dp, DividerSubtle.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Find peak frequency for normalization
            var maxVal = 1
            for (i in 0 until 256) {
                if (bins[i] > maxVal) maxVal = bins[i]
            }

            // Draw grid guides (0%, 25%, 50%, 75%, 100%)
            val guideColor = Color.White.copy(alpha = 0.12f)
            drawLine(guideColor, Offset(w * 0.25f, 0f), Offset(w * 0.25f, h), strokeWidth = 1f)
            drawLine(guideColor, Offset(w * 0.50f, 0f), Offset(w * 0.50f, h), strokeWidth = 1f)
            drawLine(guideColor, Offset(w * 0.75f, 0f), Offset(w * 0.75f, h), strokeWidth = 1f)

            // Draw histogram area
            val path = Path()
            path.moveTo(0f, h)

            for (i in 0 until 256) {
                val x = (i / 255.0f) * w
                val norm = (bins[i].toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val y = h - (norm * (h - 2f))
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()

            drawPath(path, color = VintageAmber.copy(alpha = 0.55f))
            drawPath(path, color = VintageAmber, style = Stroke(width = 1.2f))
        }

        // Overlay text
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "0", fontSize = 8.sp, color = TextMuted)
            Text(text = "LUMA", fontSize = 8.sp, color = VintageAmber)
            Text(text = "255", fontSize = 8.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ProOptionPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VintageAmber else SurfaceElevated)
            .border(1.dp, if (isSelected) VintageAmber else DividerSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) CameraBlack else TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
