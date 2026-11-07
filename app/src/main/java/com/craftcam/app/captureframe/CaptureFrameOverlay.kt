package com.craftcam.app.captureframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmberGlow
import kotlin.math.abs

/**
 * Movable, resizable composition window overlay for CAMELEO.
 *
 * Displays a live composition framing window over the full camera preview:
 * - The entire camera preview remains visible.
 * - The area outside the framing window is subtly dimmed.
 * - The inside area has a clean, thin white rounded border.
 * - Displays the effective equivalent focal length (e.g. 26mm, 35mm, 37mm, 50mm) above the top edge.
 * - User can drag inside the window to reposition it anywhere across the preview.
 * - User can pinch to resize the window (pinch in = smaller window/tighter crop, pinch out = larger window).
 */
@Composable
fun CaptureFrameOverlay(
    modifier: Modifier = Modifier,
    state: CaptureFrameState,
    aspectRatio: AspectRatioMode,
    onMove: ((dxNorm: Float, dyNorm: Float) -> Unit)? = null,
    onResize: (scaleMultiplier: Float) -> Unit
) {
    if (!state.isVisible) return

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val totalW = maxWidth
        val totalH = maxHeight

        val totalWPx = with(density) { totalW.toPx() }
        val totalHPx = with(density) { totalH.toPx() }

        // Active preview bounds in portrait orientation
        val targetRatio = 1f / aspectRatio.ratio
        val frameWPx: Float
        val frameHPx: Float

        if (totalWPx / totalHPx > targetRatio) {
            frameHPx = totalHPx
            frameWPx = totalHPx * targetRatio
        } else {
            frameWPx = totalWPx
            frameHPx = totalWPx / targetRatio
        }

        val frameLeftPx = (totalWPx - frameWPx) / 2f
        val frameTopPx = (totalHPx - frameHPx) / 2f

        val previewAspect = frameWPx / frameHPx
        val normRect = state.getNormalizedRect(previewAspect)

        val windowLeftPx = frameLeftPx + normRect.left * frameWPx
        val windowTopPx = frameTopPx + normRect.top * frameHPx
        val windowWPx = normRect.width * frameWPx
        val windowHPx = normRect.height * frameHPx

        val cornerRadiusPx = with(density) { 16.dp.toPx() }

        val currentOnResize by rememberUpdatedState(onResize)

        // 1. Interactive Pointer Input Layer (Multi-Touch Pinch to Zoom In/Out)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var lastPinchDistance = 0f
                        var accumulatedDelta = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.filter { it.pressed }

                            if (pressedPointers.size >= 2) {
                                // MULTI-TOUCH PINCH TO RESIZE
                                val p1 = pressedPointers[0].position
                                val p2 = pressedPointers[1].position
                                val currentDistance = (p1 - p2).getDistance()

                                if (lastPinchDistance > 0f && currentDistance > 0f) {
                                    val ratio = currentDistance / lastPinchDistance
                                    if (abs(ratio - 1.0f) > 0.001f) {
                                        // Silky-smooth exponential low-pass filter (avoids touch digitizer sub-pixel jitter)
                                        val delta = (ratio - 1.0f) * 0.70f
                                        accumulatedDelta = accumulatedDelta * 0.35f + delta * 0.65f
                                        val smoothedMultiplier = 1.0f + accumulatedDelta.coerceIn(-0.08f, 0.08f)
                                        currentOnResize(smoothedMultiplier)
                                    }
                                }
                                lastPinchDistance = currentDistance
                                pressedPointers.forEach { it.consume() }
                            }

                            if (pressedPointers.isEmpty()) {
                                break
                            }
                        }
                    }
                }
        ) {
            // 2. Canvas with Subtle Outside Dimming & Cutout Window
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    // Outer active preview boundary
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            frameLeftPx,
                            frameTopPx,
                            frameLeftPx + frameWPx,
                            frameTopPx + frameHPx
                        )
                    )
                    // Inner rounded composition window cutout
                    addRoundRect(
                        RoundRect(
                            left = windowLeftPx,
                            top = windowTopPx,
                            right = windowLeftPx + windowWPx,
                            bottom = windowTopPx + windowHPx,
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        )
                    )
                }

                // Draw subtle outside dimming scrim (28% black)
                drawPath(
                    path = dimPath,
                    color = Color.Black.copy(alpha = state.outsideDimAlpha)
                )

                // 3. Thin White Rounded Rectangular Framing Stroke
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(windowLeftPx, windowTopPx),
                    size = Size(windowWPx, windowHPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() })
                )
            }

            // 4. Debug Focal Length & Rect Readout (only when debug is explicitly enabled)
            if (state.isDebugVisible) {
                val windowLeftDp: Dp = with(density) { windowLeftPx.toDp() }
                val windowTopDp: Dp = with(density) { windowTopPx.toDp() }
                val windowWDp: Dp = with(density) { windowWPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = windowLeftDp, y = windowTopDp - 32.dp)
                        .width(windowWDp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = CameraBlack.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = state.formattedFocalLength,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Rect: [${String.format("%.2f", normRect.left)}, ${String.format("%.2f", normRect.top)}]",
                            color = VintageAmberGlow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

