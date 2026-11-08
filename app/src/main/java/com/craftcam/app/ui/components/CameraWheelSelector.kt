package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tactile Mechanical Rotary Camera Scroll Wheel.
 *
 * Implements a continuous 3D cylindrical rotary dial for switching camera presets.
 * Features:
 * - Smooth horizontal dragging with velocity fling physics.
 * - Auto-centering spring snap on release with automatic preset activation.
 * - Tactile CLOCK_TICK mechanical notches on index boundary crossings.
 * - Cylindrical perspective scale and subtle downward arc physics.
 * - Amber mechanical dial pointer aligned to the active camera.
 * - Tap to center and activate any visible camera.
 */
@Composable
fun CameraWheelSelector(
    modifier: Modifier = Modifier,
    presets: List<CameraPreset>,
    selectedPreset: CameraPreset,
    onPresetSelected: (CameraPreset) -> Unit,
    onOpenCollection: () -> Unit
) {
    if (presets.isEmpty()) return

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    val totalPresets = presets.size
    val currentIndex = remember(presets, selectedPreset) {
        val idx = presets.indexOfFirst { it.id == selectedPreset.id }
        if (idx >= 0) idx else 0
    }

    // Continuous wheel position (floating index: 0.0, 1.0, 2.5...)
    val wheelPosition = remember { Animatable(currentIndex.toFloat()) }
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }

    // Synchronize animation when selectedPreset changes externally (e.g. from Collection screen)
    LaunchedEffect(selectedPreset.id) {
        val targetIdx = presets.indexOfFirst { it.id == selectedPreset.id }
        if (targetIdx >= 0 && abs(wheelPosition.value - targetIdx) > 0.05f) {
            wheelPosition.animateTo(
                targetValue = targetIdx.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val itemSpacingDp = 80.dp
    val itemSpacingPx = with(density) { itemSpacingDp.toPx() }
    val maxArcOffsetPx = with(density) { 5.dp.toPx() }
    val velocityTracker = remember { VelocityTracker() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        // ─── 1. Tactile Rotary Wheel Drag Surface (Full width) ─────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(presets) {
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val delta = -dragAmount.x / itemSpacingPx
                            val newPos = (wheelPosition.value + delta).coerceIn(-0.35f, (totalPresets - 1) + 0.35f)

                            scope.launch {
                                wheelPosition.snapTo(newPos)
                            }

                            // Tactile mechanical notch tick when crossing preset boundary
                            val nearestIdx = newPos.roundToInt().coerceIn(0, totalPresets - 1)
                            if (nearestIdx != lastHapticIndex) {
                                lastHapticIndex = nearestIdx
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().x
                            // Velocity fling: flicking spins across multiple camera dials
                            val flingBonus = (velocity / 1200f).coerceIn(-3.0f, 3.0f)
                            val targetIndex = (wheelPosition.value - flingBonus).roundToInt().coerceIn(0, totalPresets - 1)

                            scope.launch {
                                wheelPosition.animateTo(
                                    targetValue = targetIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (targetIndex in presets.indices) {
                                    onPresetSelected(presets[targetIndex])
                                }
                            }
                        },
                        onDragCancel = {
                            val targetIndex = wheelPosition.value.roundToInt().coerceIn(0, totalPresets - 1)
                            scope.launch {
                                wheelPosition.animateTo(
                                    targetValue = targetIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                if (targetIndex in presets.indices) {
                                    onPresetSelected(presets[targetIndex])
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Render visible camera dials along cylindrical arc
            presets.forEachIndexed { index, preset ->
                val offsetFromCurrent = index - wheelPosition.value
                val distance = abs(offsetFromCurrent)

                // Only render items within visible viewport to optimize GPU draw calls
                if (distance > 2.8f) return@forEachIndexed

                val normDist = (distance / 2.2f).coerceIn(0f, 1f)
                val scale = lerpFloat(1.06f, 0.78f, normDist)
                val alpha = lerpFloat(1.0f, 0.32f, normDist)
                val arcOffsetY = normDist * normDist * maxArcOffsetPx
                val xPosPx = offsetFromCurrent * itemSpacingPx

                val isCenter = distance < 0.45f

                val iconResId = remember(preset.iconResName) {
                    context.resources.getIdentifier(preset.iconResName, "drawable", context.packageName)
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = xPosPx.roundToInt(),
                                y = arcOffsetY.roundToInt()
                            )
                        }
                        .scale(scale)
                        .alpha(alpha)
                        .width(78.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCenter) SurfaceCard else SurfaceGraphite.copy(alpha = 0.65f)
                        )
                        .border(
                            width = if (isCenter) 1.5.dp else 1.dp,
                            color = if (isCenter) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            scope.launch {
                                wheelPosition.animateTo(
                                    targetValue = index.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.80f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                onPresetSelected(preset)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (iconResId != 0) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = preset.name,
                                modifier = Modifier
                                    .size(42.dp)
                                    .padding(bottom = 2.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = preset.name,
                                tint = if (isCenter) VintageAmber else TextMuted,
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(bottom = 2.dp)
                            )
                        }

                        Text(
                            text = preset.name,
                            color = if (isCenter) TextPrimary else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = if (isCenter && preset.subtitle.isNotEmpty()) preset.subtitle else preset.year,
                            color = if (isCenter) VintageAmber else TextMuted,
                            fontSize = if (isCenter && preset.subtitle.isNotEmpty()) 8.5.sp else 9.5.sp,
                            fontFamily = if (isCenter && preset.subtitle.isNotEmpty()) FontFamily.Default else FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Amber reticle dial pointer at top center
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VintageAmber)
            )

            // Left Edge Soft Vignette Fade
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CameraBlack.copy(alpha = 0.95f), Color.Transparent)
                        )
                    )
            )

            // Right Edge Soft Vignette Fade (placed before collection icon)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 56.dp)
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, CameraBlack.copy(alpha = 0.95f))
                        )
                    )
            )
        }

        // ─── 2. Floating Collection Catalog Grid Button ────────────────────────
        IconButton(
            onClick = onOpenCollection,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "Collection",
                tint = VintageAmber,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
