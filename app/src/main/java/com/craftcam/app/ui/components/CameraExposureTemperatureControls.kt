package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modes of the quick camera exposure and temperature control state machine.
 */
enum class QuickControlMode {
    COMPACT,
    TEMPERATURE,
    EXPOSURE
}

/**
 * State representing color temperature (Kelvin) and Auto White Balance status.
 */
data class TemperatureState(
    val isAuto: Boolean = true,
    val kelvin: Int = 5500,
    val minKelvin: Int = 2500,
    val maxKelvin: Int = 9500,
    val tint: Float = 0.0f
)

/**
 * State representing exposure compensation (EV) and Auto Exposure baseline status.
 */
data class ExposureState(
    val isAuto: Boolean = true,
    val exposureIndex: Int = 0,
    val minExposureIndex: Int = -4,
    val maxExposureIndex: Int = 4,
    val exposureStep: Float = 0.5f
) {
    val evValue: Float get() = exposureIndex * exposureStep
    val formattedEv: String
        get() = if (exposureIndex == 0) "0"
        else if (evValue > 0f) String.format(Locale.US, "+%.1f", evValue)
        else String.format(Locale.US, "%.1f", evValue)
}

/**
 * Authentic Dazz Cam-style expanded Temperature and Brightness/Exposure control system.
 *
 * Implements:
 * 1. Compact pill bar directly beneath the viewfinder preview (`[ 🌡️ ] 5500K [ ☀️A ] 0`).
 * 2. Smooth 180ms animated expansion to custom colorful temperature gradient slider.
 * 3. Smooth 180ms animated expansion to dark translucent horizontal exposure slider.
 * 4. Single-active control state machine: tapping active control collapses back to compact.
 */
@Composable
fun CameraExposureTemperatureControls(
    modifier: Modifier = Modifier,
    temperatureState: TemperatureState,
    exposureState: ExposureState,
    activeMode: QuickControlMode,
    onModeChange: (QuickControlMode) -> Unit,
    onTemperatureChange: (Int) -> Unit,
    onTemperatureAuto: () -> Unit,
    onExposureChange: (Int) -> Unit,
    onExposureAuto: () -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 1. Expanded Temperature Panel ──────────────────────────────────
        AnimatedVisibility(
            visible = activeMode == QuickControlMode.TEMPERATURE,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            ExpandedTemperaturePanel(
                state = temperatureState,
                onKelvinChange = { newKelvin ->
                    onTemperatureChange(newKelvin)
                },
                onAutoToggle = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTemperatureAuto()
                },
                onReset = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTemperatureChange(5500)
                },
                onClose = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onModeChange(QuickControlMode.COMPACT)
                }
            )
        }

        // ── 2. Expanded Brightness / Exposure Panel ─────────────────────────
        AnimatedVisibility(
            visible = activeMode == QuickControlMode.EXPOSURE,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            ExpandedExposurePanel(
                state = exposureState,
                onExposureIndexChange = { newIdx ->
                    onExposureChange(newIdx)
                },
                onAutoToggle = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onExposureAuto()
                },
                onReset = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onExposureAuto()
                },
                onClose = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onModeChange(QuickControlMode.COMPACT)
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── 3. Compact Control Capsule Pill ─────────────────────────────────
        CompactControlsPill(
            temperatureState = temperatureState,
            exposureState = exposureState,
            activeMode = activeMode,
            onToggleTemperature = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (activeMode == QuickControlMode.TEMPERATURE) {
                    onModeChange(QuickControlMode.COMPACT)
                } else {
                    onModeChange(QuickControlMode.TEMPERATURE)
                }
            },
            onToggleExposure = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (activeMode == QuickControlMode.EXPOSURE) {
                    onModeChange(QuickControlMode.COMPACT)
                } else {
                    onModeChange(QuickControlMode.EXPOSURE)
                }
            }
        )
    }
}

/**
 * Compact control capsule pill matching authentic Dazz Cam UI:
 * `[ 🌡️ ] 5500K   [ ☀️A ] 0`
 */
@Composable
private fun CompactControlsPill(
    temperatureState: TemperatureState,
    exposureState: ExposureState,
    activeMode: QuickControlMode,
    onToggleTemperature: () -> Unit,
    onToggleExposure: () -> Unit
) {
    val isTempActive = activeMode == QuickControlMode.TEMPERATURE
    val isExpActive = activeMode == QuickControlMode.EXPOSURE

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xD91E1E22))
            .border(1.dp, Color(0x28FFFFFF), CircleShape)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Temperature Segment ──
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleTemperature
                    )
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isTempActive) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeviceThermostat,
                        contentDescription = "Temperature",
                        tint = if (isTempActive) Color.Black else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Text(
                    text = "${temperatureState.kelvin}K",
                    color = if (isTempActive) Color(0xFFFF9E3D) else Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Subtle vertical separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color(0x38FFFFFF))
            )

            // ── Brightness / Exposure Segment ──
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExposure
                    )
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isExpActive) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Brightness",
                            tint = if (isExpActive) Color.Black else Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        if (exposureState.exposureIndex == 0) {
                            Text(
                                text = "A",
                                color = if (isExpActive) Color.Black else Color(0xFFFFD166),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = exposureState.formattedEv,
                    color = if (isExpActive) Color(0xFFFFD166) else Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Expanded Temperature Control:
 * Cool Cyan → Neutral Daylight (5500K) → Warm Orange Gradient Slider.
 */
@Composable
private fun ExpandedTemperaturePanel(
    state: TemperatureState,
    onKelvinChange: (Int) -> Unit,
    onAutoToggle: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF0181A20))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Header Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto White Balance circular [ A ] button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (state.isAuto) Color.White else Color(0x3D3A3D46))
                        .clickable(onClick = onAutoToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        color = if (state.isAuto) Color.Black else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Centered Kelvin readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.kelvin}K",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (state.isAuto) "AUTO WHITE BALANCE" else "MANUAL KELVIN",
                        color = if (state.isAuto) Color(0xFF5AC8FA) else Color(0xFFFF9E3D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                // Reset / Undo button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x3D3A3D46))
                        .clickable {
                            if (!state.isAuto || state.kelvin != 5500) {
                                onReset()
                            } else {
                                onClose()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!state.isAuto || state.kelvin != 5500) {
                            Icons.AutoMirrored.Filled.Undo
                        } else {
                            Icons.Default.Close
                        },
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Temperature Gradient Slider ──
            TemperatureGradientSlider(
                kelvin = state.kelvin,
                minKelvin = state.minKelvin,
                maxKelvin = state.maxKelvin,
                onKelvinChange = onKelvinChange
            )
        }
    }
}

/**
 * Custom color temperature gradient slider:
 * cool blue/cyan (2500K) → neutral daylight (5500K) → warm yellow/orange (9500K).
 */
@Composable
private fun TemperatureGradientSlider(
    kelvin: Int,
    minKelvin: Int,
    maxKelvin: Int,
    onKelvinChange: (Int) -> Unit
) {
    val view = LocalView.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val fraction = ((kelvin - minKelvin).toFloat() / (maxKelvin - minKelvin).toFloat()).coerceIn(0f, 1f)

    val gradientBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2670E8), // Cool deep blue (2500K)
                Color(0xFF5AC8FA), // Cool cyan (3500K)
                Color(0xFFAFE0FC), // Light sky blue (4500K)
                Color(0xFFF7F8FA), // Neutral daylight (5500K)
                Color(0xFFFFDF7D), // Pale warm yellow (6500K)
                Color(0xFFFF9E3D), // Warm golden amber (8000K)
                Color(0xFFFF5722)  // Deep warm orange (9500K)
            )
        )
    }

    fun updateValueFromOffset(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= 0f) return
        val newFraction = (x / width).coerceIn(0f, 1f)
        val rawK = minKelvin + newFraction * (maxKelvin - minKelvin)
        val steppedK = ((rawK / 100f).roundToInt() * 100).coerceIn(minKelvin, maxKelvin)
        if (steppedK != kelvin) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onKelvinChange(steppedK)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { trackSize = it }
            .pointerInput(minKelvin, maxKelvin, kelvin) {
                detectTapGestures { offset ->
                    updateValueFromOffset(offset.x)
                }
            }
            .pointerInput(minKelvin, maxKelvin, kelvin) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateValueFromOffset(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Continuous color temperature track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(gradientBrush)
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(17.dp))
        ) {
            // Neutral daylight (5500K) tick mark
            val neutralFrac = ((5500 - minKelvin).toFloat() / (maxKelvin - minKelvin).toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { (trackSize.width * neutralFrac).toDp() - 1.dp })
                    .background(Color(0x77000000))
            )
        }

        // Draggable Circular Thumb
        val thumbRadiusDp = 15.dp
        val thumbOffsetDp = with(density) {
            val trackW = trackSize.width.toFloat()
            val thumbPx = (fraction * trackW).coerceIn(0f, trackW)
            thumbPx.toDp() - thumbRadiusDp
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0x33000000), CircleShape)
        )
    }
}

/**
 * Expanded Brightness / Exposure Compensation Control:
 * Dark translucent horizontal slider matching authentic Dazz Cam UI.
 */
@Composable
private fun ExpandedExposurePanel(
    state: ExposureState,
    onExposureIndexChange: (Int) -> Unit,
    onAutoToggle: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF0181A20))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Header Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Undo / Reset icon button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x3D3A3D46))
                            .clickable(onClick = onReset),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Reset EV",
                            tint = if (state.exposureIndex != 0) Color.White else Color(0x66FFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Auto Exposure baseline [ A ] button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (state.exposureIndex == 0) Color.White else Color(0x3D3A3D46))
                            .clickable(onClick = onAutoToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = if (state.exposureIndex == 0) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Centered Prominent EV readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val evFormatted = if (state.exposureIndex == 0) "0.0"
                    else if (state.evValue > 0f) String.format(Locale.US, "+%.1f", state.evValue)
                    else String.format(Locale.US, "%.1f", state.evValue)

                    Text(
                        text = "$evFormatted EV",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (state.exposureIndex == 0) "AUTO EXPOSURE" else "MANUAL EV BIAS",
                        color = if (state.exposureIndex == 0) Color(0xFF5AC8FA) else Color(0xFFFFD166),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                // Close button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x3D3A3D46))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Horizontal Exposure Slider ──
            ExposureSlider(
                exposureIndex = state.exposureIndex,
                minExposureIndex = state.minExposureIndex,
                maxExposureIndex = state.maxExposureIndex,
                onExposureIndexChange = onExposureIndexChange
            )
        }
    }
}

/**
 * Custom dark translucent exposure slider:
 * large rounded track, centered baseline 0.0 tick, and white thumb.
 */
@Composable
private fun ExposureSlider(
    exposureIndex: Int,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    onExposureIndexChange: (Int) -> Unit
) {
    val view = LocalView.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val totalRange = (maxExposureIndex - minExposureIndex).toFloat()
    val fraction = if (totalRange > 0f) {
        ((exposureIndex - minExposureIndex).toFloat() / totalRange).coerceIn(0f, 1f)
    } else 0.5f

    fun updateValueFromOffset(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= 0f) return
        val newFraction = (x / width).coerceIn(0f, 1f)
        val rawIdx = minExposureIndex + (newFraction * totalRange).roundToInt()
        val clampedIdx = rawIdx.coerceIn(minExposureIndex, maxExposureIndex)
        if (clampedIdx != exposureIndex) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onExposureIndexChange(clampedIdx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { trackSize = it }
            .pointerInput(minExposureIndex, maxExposureIndex, exposureIndex) {
                detectTapGestures { offset ->
                    updateValueFromOffset(offset.x)
                }
            }
            .pointerInput(minExposureIndex, maxExposureIndex, exposureIndex) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateValueFromOffset(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Dark Translucent Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF2C2D32))
                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Center notch tick at 0.0 EV
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(Color(0x80FFFFFF))
            )
        }

        // Draggable Circular Thumb
        val thumbRadiusDp = 15.dp
        val thumbOffsetDp = with(density) {
            val trackW = trackSize.width.toFloat()
            val thumbPx = (fraction * trackW).coerceIn(0f, trackW)
            thumbPx.toDp() - thumbRadiusDp
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp.coerceAtLeast(0.dp))
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0x33000000), CircleShape)
        )
    }
}

