package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.R
import com.craftcam.app.color.CreativeTemperatureEngine
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.DividerSubtle
import com.craftcam.app.ui.theme.SurfaceElevated
import com.craftcam.app.ui.theme.TextMuted
import com.craftcam.app.ui.theme.TextPrimary
import com.craftcam.app.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * CAMELEO pixel-art Creative Temperature slider (`-100` Frozen Blue to `+100` Deep Red).
 *
 * Strictly independent from Camera2 hardware Kelvin white balance. Includes:
 * - Original pixel-art snowflake & flame icons (`ic_temp_snowflake`, `ic_temp_flame`)
 * - Smooth gradient bar: Deep blue -> Light blue -> Neutral gray -> Amber -> Orange -> Deep red
 * - Center neutral detent with magnetic snap & haptic click
 * - Numerical readout + semantic descriptor + Skin Guard toggle + one-tap reset
 * - Monochrome / 22M automatic lock to preserve achromatic rendering
 */
@Composable
fun CreativeTemperatureControl(
    value: Float,
    skinProtectionEnabled: Boolean,
    isMonochromeLocked: Boolean = false,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    onToggleSkinProtection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val view = LocalView.current
    val effectiveValue = if (isMonochromeLocked) 0.0f else CreativeTemperatureEngine.clampTemp(value)
    val rounded = effectiveValue.roundToInt()

    val readoutColor = when {
        isMonochromeLocked -> TextMuted
        rounded <= -45 -> Color(0xFF66B8FF)
        rounded < -5 -> Color(0xFFA0D4FF)
        rounded in -5..5 -> Color(0xFFE0E2EA)
        rounded <= 50 -> Color(0xFFFFB74D)
        else -> Color(0xFFFF5242)
    }

    val statusText = if (isMonochromeLocked) {
        "B&W / 22M LOCKED"
    } else {
        CreativeTemperatureEngine.describeTemperature(effectiveValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xD9101218))
                        .border(1.dp, DividerSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                }
            )
            .alpha(if (isMonochromeLocked) 0.62f else 1.0f),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        // Header row: Label, numerical value + descriptor, Skin Guard, Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "TEMPERATURE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1A1D26))
                        .border(1.dp, readoutColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .clickable(enabled = !isMonochromeLocked && rounded != 0) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReset()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = CreativeTemperatureEngine.formatValue(effectiveValue),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = readoutColor
                    )
                }
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = readoutColor.copy(alpha = 0.90f),
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Skin Tone Protection Toggle Pill
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (skinProtectionEnabled && !isMonochromeLocked) Color(0x33FFB74D) else Color(0xFF191B22),
                    border = BorderStroke(
                        1.dp,
                        if (skinProtectionEnabled && !isMonochromeLocked) Color(0xAAFFB74D) else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isMonochromeLocked) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleSkinProtection(!skinProtectionEnabled)
                        }
                ) {
                    Text(
                        text = if (skinProtectionEnabled) "SKIN ON" else "SKIN OFF",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (skinProtectionEnabled && !isMonochromeLocked) Color(0xFFFFCA72) else TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // One-tap Reset Button
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (!isMonochromeLocked && rounded != 0) Color(0x28FFFFFF) else Color(0xFF171920),
                    border = BorderStroke(
                        1.dp,
                        if (!isMonochromeLocked && rounded != 0) Color(0x66FFFFFF) else Color(0x22FFFFFF)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = !isMonochromeLocked && rounded != 0) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onReset()
                        }
                ) {
                    Text(
                        text = "RESET",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (!isMonochromeLocked && rounded != 0) TextPrimary else TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Slider Row: [Snowflake Pixel Icon] --- [Gradient Bar w/ Center Detent] --- [Flame Pixel Icon]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_temp_snowflake),
                contentDescription = "Cool / Frozen Blue (-100)",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isMonochromeLocked) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val stepped = (effectiveValue - 25f).coerceAtLeast(-100f)
                        onValueChange(stepped)
                    }
            )

            CreativeTemperatureGradientBar(
                value = effectiveValue,
                enabled = !isMonochromeLocked,
                onValueChange = onValueChange,
                onReset = onReset,
                modifier = Modifier.weight(1f)
            )

            Image(
                painter = painterResource(id = R.drawable.ic_temp_flame),
                contentDescription = "Warm / Deep Red (+100)",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isMonochromeLocked) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val stepped = (effectiveValue + 25f).coerceAtMost(100f)
                        onValueChange(stepped)
                    }
            )
        }
    }
}

@Composable
private fun CreativeTemperatureGradientBar(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHapticBucket by remember { mutableIntStateOf(0) }
    var wasNeutral by remember { mutableStateOf(abs(value) < 1.5f) }

    val clamped = CreativeTemperatureEngine.clampTemp(value)
    val fraction = ((clamped + 100f) / 200f).coerceIn(0f, 1f)

    // Requested spectrum: Deep blue -> Light blue -> Neutral gray -> Amber -> Orange -> Deep red
    val gradientBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0B3B8C), // -100 Frozen deep blue
                Color(0xFF1F6FEB), // -65 Cold blue
                Color(0xFF4DA6FF), // -25 Light cool blue
                Color(0xFF6C707A), // 0 Neutral gray
                Color(0xFFF5A623), // +25 Warm amber
                Color(0xFFFF6B1A), // +65 Golden / hot orange
                Color(0xFFC9141E)  // +100 Deep red photographic grade
            )
        )
    }

    fun applyPointerX(x: Float) {
        if (!enabled) return
        val w = trackSize.width.toFloat()
        if (w <= 0f) return
        val padPx = with(density) { 10.dp.toPx() }
        val usable = (w - 2f * padPx).coerceAtLeast(1f)
        val rawFrac = ((x - padPx) / usable).coerceIn(0f, 1f)
        val rawTemp = -100f + rawFrac * 200f

        // Magnetic center neutral detent within +-2.4 around 0
        val snapped = if (abs(rawTemp) <= 2.4f) 0.0f else rawTemp.roundToInt().toFloat()
        val isNowNeutral = snapped == 0.0f

        if (isNowNeutral && !wasNeutral) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val bucket = (snapped / 25f).roundToInt()
            if (bucket != lastHapticBucket) {
                lastHapticBucket = bucket
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        wasNeutral = isNowNeutral
        if (abs(snapped - clamped) >= 0.5f || (isNowNeutral && clamped != 0.0f)) {
            onValueChange(snapped)
        }
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(gradientBrush)
            .border(1.5.dp, Color(0x8814161D), RoundedCornerShape(6.dp))
            .semantics {
                contentDescription = "Creative Temperature Slider (${CreativeTemperatureEngine.formatValue(clamped)})"
            }
            .onSizeChanged { trackSize = it }
            .pointerInput(enabled, clamped) {
                detectTapGestures(
                    onDoubleTap = {
                        if (enabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onReset()
                        }
                    },
                    onTap = { offset ->
                        val w = trackSize.width.toFloat()
                        if (enabled && w > 0f && abs(offset.x - w * 0.5f) <= with(density) { 12.dp.toPx() }) {
                            // Tapping center neutral detent snaps straight to 0
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onReset()
                        } else {
                            applyPointerX(offset.x)
                        }
                    }
                )
            }
            .pointerInput(enabled) {
                detectDragGestures { change, _ ->
                    change.consume()
                    applyPointerX(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Pixel ruler ticks + center neutral detent notch
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val pad = 10.dp.toPx()
            val usable = (size.width - 2f * pad).coerceAtLeast(1f)
            val stops = floatArrayOf(0f, 0.175f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f)
            for (s in stops) {
                val tx = pad + s * usable
                val isCenter = abs(s - 0.5f) < 0.01f
                if (isCenter) {
                    drawLine(
                        color = Color(0xF0FFFFFF),
                        start = Offset(tx, 2.dp.toPx()),
                        end = Offset(tx, size.height - 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                } else {
                    drawCircle(
                        color = Color(0x990A0C12),
                        radius = 1.2.dp.toPx(),
                        center = Offset(tx, cy)
                    )
                }
            }
        }

        // Tactile CAMELEO pixel-framed thumb
        val thumbW = 14.dp
        val padPx = with(density) { 10.dp.toPx() }
        val maxPx = (trackSize.width - padPx).coerceAtLeast(padPx)
        val cx = padPx + fraction * (maxPx - padPx)
        val thumbOffsetX = with(density) { (cx - with(density) { (thumbW / 2).toPx() }).toDp() }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .width(thumbW)
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CameraBlack)
                .border(
                    width = 2.dp,
                    color = if (roundedZero(clamped)) Color(0xFFE0E4EC) else Color.White,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(if (roundedZero(clamped)) Color(0xFF9EA4B0) else Color.White)
            )
        }
    }
}

private fun roundedZero(v: Float): Boolean = abs(v) < 0.5f
