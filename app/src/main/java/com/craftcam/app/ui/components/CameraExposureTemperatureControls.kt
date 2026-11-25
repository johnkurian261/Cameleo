package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.craftcam.app.R
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
 * State representing color temperature (Kelvin).
 * In Dazz Cam, color temperature is a dedicated manual control with NO automatic temperature
 * adjustment mode ("Auto Color Temperature is not recommended for this camera").
 */
data class TemperatureState(
    val isAuto: Boolean = false,
    val kelvin: Int = 5500,
    val minKelvin: Int = 2500,
    val maxKelvin: Int = 9500,
    val tint: Float = 0.0f
) {
    val isDefault: Boolean get() = kelvin == 5500
}

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

@Composable
fun CameraExposureTemperatureControls(
    modifier: Modifier = Modifier,
    temperatureState: TemperatureState,
    exposureState: ExposureState,
    activeMode: QuickControlMode,
    onModeChange: (QuickControlMode) -> Unit,
    onTemperatureChange: (Int) -> Unit,
    onTemperatureReset: () -> Unit = {},
    onTemperatureAuto: () -> Unit = onTemperatureReset,
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
                onReset = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onTemperatureReset()
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
                onClose = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onModeChange(QuickControlMode.COMPACT)
                }
            )
        }

    }
}

/**
 * Expanded Temperature Control:
 * Authentic Dazz Cam color temperature slider with bold centered Kelvin readout,
 * rounded gradient pill track, dotted center line, and black/white ring thumb.
 */
@Composable
private fun ExpandedTemperaturePanel(
    state: TemperatureState,
    onKelvinChange: (Int) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Authentic Dazz Cam Centered Kelvin Readout (e.g. "5373K" or "5500K") ──
        // Tapping the readout resets directly to default 5500K daylight baseline (no auto AWB toggle)
        Text(
            text = "${state.kelvin}K",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onReset()
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // ── Authentic Dazz Cam Gradient Slider with Dotted Line & Black/White Thumb ──
        TemperatureGradientSlider(
            kelvin = state.kelvin,
            minKelvin = state.minKelvin,
            maxKelvin = state.maxKelvin,
            onKelvinChange = onKelvinChange,
            onResetToDaylight = onReset
        )
    }
}

/**
 * Authentic Dazz Cam color temperature gradient slider:
 * - Wide rounded pill track (aspect ratio ~3.2:1, corner radius 22dp).
 * - Exact 11-stop color gradient: cool periwinkle blue (#5F75E3) → mauve/lavender (#AB82A6) → warm apricot orange (#FE9E5A).
 * - Horizontal dotted center line through the track.
 * - Solid black thumb with crisp white outline ring centered vertically on the dotted line.
 * - Continuous 1K fine adjustment matching reference readout (e.g. 5373K).
 * - Double tap to reset to 5500K daylight.
 */
@Composable
private fun TemperatureGradientSlider(
    kelvin: Int,
    minKelvin: Int,
    maxKelvin: Int,
    onKelvinChange: (Int) -> Unit,
    onResetToDaylight: () -> Unit = {}
) {
    val view = LocalView.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val fraction = ((kelvin - minKelvin).toFloat() / (maxKelvin - minKelvin).toFloat()).coerceIn(0f, 1f)

    val gradientBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF5F75E3), // Cool periwinkle blue (2500K)
                Color(0xFF6674EA),
                Color(0xFF7979D9),
                Color(0xFF8C7DC8), // Soft violet
                Color(0xFF9D83BB),
                Color(0xFFAB82A6), // Neutral mauve / lavender
                Color(0xFFBF8996), // Dusty rose
                Color(0xFFD08B84),
                Color(0xFFE39076), // Peach
                Color(0xFFF29563), // Apricot
                Color(0xFFFE9E5A)  // Warm golden orange (9500K)
            )
        )
    }

    fun updateValueFromOffset(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= 0f) return
        val minXPx = with(density) { 14.dp.toPx() }
        val maxXPx = (width - minXPx).coerceAtLeast(minXPx)
        val newFraction = ((x - minXPx) / (maxXPx - minXPx)).coerceIn(0f, 1f)
        val rawK = minKelvin + newFraction * (maxKelvin - minKelvin)
        val newKelvin = rawK.roundToInt().coerceIn(minKelvin, maxKelvin)
        if (newKelvin != kelvin) {
            if (newKelvin / 50 != kelvin / 50) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            onKelvinChange(newKelvin)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .widthIn(max = 260.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(gradientBrush)
            .onSizeChanged { trackSize = it }
            .pointerInput(minKelvin, maxKelvin, kelvin) {
                detectTapGestures(
                    onDoubleTap = {
                        onResetToDaylight()
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    },
                    onTap = { offset ->
                        updateValueFromOffset(offset.x)
                    }
                )
            }
            .pointerInput(minKelvin, maxKelvin, kelvin) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateValueFromOffset(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Horizontal dotted center line running through the track
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val dotRadius = 1.1.dp.toPx()
            val spacing = 4.6.dp.toPx()
            val startX = 12.dp.toPx()
            val endX = size.width - 12.dp.toPx()
            var curX = startX
            while (curX <= endX) {
                drawCircle(
                    color = Color(0xDD0C0E14),
                    radius = dotRadius,
                    center = Offset(curX, cy)
                )
                curX += spacing
            }
        }

        // Draggable circular thumb: solid black fill with crisp white border ring
        val thumbRadiusDp = 12.dp // diameter = 24dp
        val minX = with(density) { 14.dp.toPx() }
        val maxX = (trackSize.width - with(density) { 14.dp.toPx() }).coerceAtLeast(minX)
        val thumbCenterX = minX + fraction * (maxX - minX)
        val thumbOffsetX = with(density) { (thumbCenterX - with(density) { thumbRadiusDp.toPx() }).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffsetX)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(2.2.dp, Color.White, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedExposurePanel(
    state: ExposureState,
    onExposureIndexChange: (Int) -> Unit,
    onAutoToggle: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    val low = state.minExposureIndex
    val high = state.maxExposureIndex
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xEE202124), RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onAutoToggle) {
            Icon(
                Icons.AutoMirrored.Filled.Undo, "Reset exposure",
                tint = Color.White, modifier = Modifier.size(18.dp)
            )
        }
        Slider(
            modifier = Modifier.weight(1f).height(48.dp)
                .semantics { contentDescription = "Exposure compensation" },
            value = state.exposureIndex.toFloat(),
            valueRange = low.toFloat()..maxOf(low + 1, high).toFloat(),
            enabled = high > low,
            steps = (high - low - 1).coerceAtLeast(0),
            onValueChange = {
                val index = it.roundToInt()
                if (index != state.exposureIndex) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onExposureIndexChange(index)
                }
            },
            thumb = { Box(Modifier.size(14.dp).background(Color.White, CircleShape)) },
            track = {
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val mid = size.height / 2
                    drawLine(Color.LightGray, Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx())
                    if (low < 0 && high > 0) {
                        val zero = -low.toFloat() / (high - low)
                        val x = size.width * if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 1f - zero else zero
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                }
            }
        )
        Text(
            text = "${state.formattedEv} EV",
            modifier = Modifier.widthIn(min = 60.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.White
        )
        androidx.compose.material3.IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "Close exposure", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
