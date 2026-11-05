package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.CameraLens
import com.craftcam.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CameraLensWheel(
    modifier: Modifier = Modifier,
    lenses: List<CameraLens>,
    selectedLens: CameraLens?,
    onLensSelected: (CameraLens) -> Unit
) {
    if (lenses.isEmpty()) return

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val currentIndex = remember(lenses, selectedLens) {
        val idx = lenses.indexOfFirst { it.id == selectedLens?.id }
        if (idx >= 0) idx else lenses.indexOfFirst { abs(it.equivalentZoom - (selectedLens?.equivalentZoom ?: 1.0f)) < 0.15f }.coerceAtLeast(0)
    }

    // Continuous wheel position for dragging and spring snapping
    val wheelPosition = remember { Animatable(currentIndex.toFloat()) }
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }

    // Sync wheel animation when selectedLens changes from outside (e.g. camera flip or external zoom)
    LaunchedEffect(currentIndex) {
        if (abs(wheelPosition.value - currentIndex) > 0.05f) {
            wheelPosition.animateTo(
                targetValue = currentIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val itemSpacingDp = 50.dp
    val itemSpacingPx = with(density) { itemSpacingDp.toPx() }
    val maxArcOffsetPx = with(density) { 3.5.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalLenses = lenses.size

        // Single lens display (e.g. Front Camera with single lens)
        if (totalLenses == 1) {
            val single = lenses[0]
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CameraBlack.copy(alpha = 0.60f))
                    .border(1.5.dp, VintageAmber.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics {
                        role = Role.RadioButton
                        selected = true
                        contentDescription = single.lensDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = single.displayName,
                    color = VintageAmber,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            return@BoxWithConstraints
        }

        // Multi-lens interactive rotating wheel
        val velocityTracker = remember { VelocityTracker() }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(CameraBlack.copy(alpha = 0.50f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .pointerInput(lenses) {
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().x
                            val flingBonus = (velocity / 1400f).coerceIn(-1.5f, 1.5f)
                            val targetIndex = (wheelPosition.value - flingBonus).roundToInt().coerceIn(0, totalLenses - 1)

                            scope.launch {
                                wheelPosition.animateTo(
                                    targetValue = targetIndex.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = 0.80f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onLensSelected(lenses[targetIndex])
                            }
                        },
                        onDragCancel = {
                            val targetIndex = wheelPosition.value.roundToInt().coerceIn(0, totalLenses - 1)
                            scope.launch {
                                wheelPosition.animateTo(targetIndex.toFloat(), spring())
                                onLensSelected(lenses[targetIndex])
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val delta = -dragAmount.x / itemSpacingPx
                            val newPos = (wheelPosition.value + delta).coerceIn(-0.35f, (totalLenses - 1) + 0.35f)

                            scope.launch {
                                wheelPosition.snapTo(newPos)
                            }

                            // Trigger subtle tick on crossing index boundary
                            val nearestIdx = newPos.roundToInt().coerceIn(0, totalLenses - 1)
                            if (nearestIdx != lastHapticIndex) {
                                lastHapticIndex = nearestIdx
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            // Container height for wheel items
            Box(
                modifier = Modifier
                    .width(itemSpacingDp * minOf(3.4f, totalLenses.toFloat() + 0.5f))
                    .height(42.dp),
                contentAlignment = Alignment.Center
            ) {
                lenses.forEachIndexed { index, lens ->
                    val offsetFromCurrent = (index - wheelPosition.value)
                    val distance = abs(offsetFromCurrent)
                    val normDist = (distance / 1.6f).coerceIn(0f, 1f)

                    // Mathematical arc & scale calculation
                    val scale = lerp(1.0f, 0.74f, normDist)
                    val alpha = lerp(1.0f, 0.35f, normDist)
                    val arcOffsetY = normDist * normDist * maxArcOffsetPx // Subtle downward curvature towards edges
                    val xPosPx = offsetFromCurrent * itemSpacingPx

                    val isSelected = distance < 0.45f

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
                            .clip(CircleShape)
                            .background(if (isSelected) CameraBlack.copy(alpha = 0.85f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) VintageAmber.copy(alpha = 0.90f) else Color.Transparent,
                                shape = CircleShape
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
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    onLensSelected(lens)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                                contentDescription = lens.lensDescription
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lens.displayName,
                            color = if (isSelected) VintageAmber else TextPrimary,
                            fontSize = if (isSelected) 13.sp else 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
