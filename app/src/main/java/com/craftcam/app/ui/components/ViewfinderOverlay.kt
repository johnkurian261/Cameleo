package com.craftcam.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmber
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    preset: CameraPreset,
    tuning: UserTuningConfig = UserTuningConfig(),
    aspectRatio: AspectRatioMode,
    showGrid: Boolean,
    isNaturalMode: Boolean = false,
    exposureIndex: Int = 0,
    minExposureIndex: Int = -4,
    maxExposureIndex: Int = 4,
    exposureStep: Float = 0.5f,
    onTapFocus: (Float, Float) -> Unit,
    onExposureChange: (Int) -> Unit = {},
    onPinchZoom: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.35f) }
    val focusAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleDismiss() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(3500)
            focusAlpha.animateTo(0f, tween(500))
            focusPoint = null
        }
    }

    val density = LocalDensity.current
    val moveThresholdPx = with(density) { 60.dp.toPx() }
    val pixelsPerStep = with(density) { 16.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(minExposureIndex, maxExposureIndex, exposureIndex) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val downTime = System.currentTimeMillis()

                    var isDrag = false
                    var isPinch = false
                    var totalDragY = 0f
                    var lastPinchSpan = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            // Multi-touch pinch is smoothly and authoritatively handled by CaptureFrameOverlay
                            isPinch = true
                            isDrag = false
                            focusPoint = null
                            lastPinchSpan = 0f
                        } else if (pressedPointers.size == 1 && !isPinch) {
                            val change = pressedPointers[0]
                            val dy = change.position.y - startPos.y
                            val dx = change.position.x - startPos.x

                            if (!isDrag && (abs(dy) > touchSlop || abs(dx) > touchSlop)) {
                                isDrag = true
                            }

                            if (isDrag) {
                                change.consume()
                                scheduleDismiss()

                                val dragDeltaY = change.position.y - change.previousPosition.y
                                totalDragY -= dragDeltaY // Dragging up increases exposure

                                val stepsDelta = (totalDragY / pixelsPerStep).toInt()
                                if (stepsDelta != 0) {
                                    totalDragY -= stepsDelta * pixelsPerStep
                                    val targetIndex = (exposureIndex + stepsDelta).coerceIn(minExposureIndex, maxExposureIndex)
                                    if (targetIndex != exposureIndex) {
                                        onExposureChange(targetIndex)
                                    }
                                }
                            }
                        }

                        if (pressedPointers.isEmpty()) {
                            if (!isDrag && !isPinch) {
                                val duration = System.currentTimeMillis() - downTime
                                if (duration < 450) {
                                    if (focusPoint == null || (startPos - focusPoint!!).getDistance() > moveThresholdPx) {
                                        focusPoint = startPos
                                        onTapFocus(startPos.x, startPos.y)
                                        scope.launch {
                                            focusScale.snapTo(1.35f)
                                            focusAlpha.snapTo(1.0f)
                                            focusScale.animateTo(1.0f, tween(180))
                                        }
                                    } else {
                                        scope.launch {
                                            focusAlpha.snapTo(1.0f)
                                        }
                                    }
                                    scheduleDismiss()
                                }
                            }
                            break
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Calculate Active Frame Rect for selected Aspect Ratio
            val targetRatio = 1f / aspectRatio.ratio
            val frameW: Float
            val frameH: Float
            if (canvasW / canvasH > targetRatio) {
                frameH = canvasH
                frameW = canvasH * targetRatio
            } else {
                frameW = canvasW
                frameH = canvasW / targetRatio
            }

            val left = (canvasW - frameW) / 2f
            val top = (canvasH - frameH) / 2f
            val right = left + frameW
            val bottom = top + frameH

            // 2. Draw Matte / Letterboxing Bars outside active image frame
            if (top > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
                drawRect(color = CameraBlack, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            }
            if (left > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(left, canvasH))
                drawRect(color = CameraBlack, topLeft = Offset(right, 0f), size = Size(canvasW - right, canvasH))
            }

            // Note: Asset overlays (border cutouts, film dust, and date stamp) are strictly omitted
            // from the live viewfinder preview to keep framing clean and unobstructed, and are
            // applied directly to the final captured photo during development in ImageProcessor.

            // 3. Draw Rule of Thirds Grid if enabled
            if (showGrid) {
                val gridColor = Color.White.copy(alpha = 0.22f)
                val strokeWidth = 1.dp.toPx()

                // Vertical lines
                val col1 = left + frameW / 3f
                val col2 = left + (frameW * 2f) / 3f
                drawLine(gridColor, Offset(col1, top), Offset(col1, bottom), strokeWidth)
                drawLine(gridColor, Offset(col2, top), Offset(col2, bottom), strokeWidth)

                // Horizontal lines
                val row1 = top + frameH / 3f
                val row2 = top + (frameH * 2f) / 3f
                drawLine(gridColor, Offset(left, row1), Offset(right, row1), strokeWidth)
                drawLine(gridColor, Offset(left, row2), Offset(right, row2), strokeWidth)
            }

            // 8. Draw Subtle Viewfinder Guide Frame
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                style = Stroke(width = 1.dp.toPx())
            )

            // 9. Draw Tap-to-Focus Reticle & Vertical Sun Brightness Slider (Dazz Cam Style)
            focusPoint?.let { pt ->
                val alpha = focusAlpha.value
                if (alpha > 0.01f) {
                    val boxSize = 52.dp.toPx() * focusScale.value
                    val halfBox = boxSize / 2f

                    // Clamped focus center to keep reticle and slider fully visible
                    val clampedPtX = pt.x.coerceIn(left + halfBox + 10.dp.toPx(), right - halfBox - 60.dp.toPx())
                    val clampedPtY = pt.y.coerceIn(top + halfBox + 10.dp.toPx(), bottom - halfBox - 10.dp.toPx())

                    // Focus frame: clean rounded square with corner brackets in VintageAmber
                    drawRoundRect(
                        color = VintageAmber.copy(alpha = alpha * 0.90f),
                        topLeft = Offset(clampedPtX - halfBox, clampedPtY - halfBox),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Small center dot
                    drawCircle(
                        color = VintageAmber.copy(alpha = alpha * 0.70f),
                        radius = 2.dp.toPx(),
                        center = Offset(clampedPtX, clampedPtY)
                    )

                    // Vertical Exposure Track & Sun Handle
                    val trackHeight = 94.dp.toPx()
                    val placeOnRight = (clampedPtX + halfBox + 48.dp.toPx()) < right
                    val trackX = if (placeOnRight) clampedPtX + halfBox + 16.dp.toPx() else clampedPtX - halfBox - 16.dp.toPx()
                    val trackTop = (clampedPtY - trackHeight / 2f).coerceIn(top + 8.dp.toPx(), bottom - trackHeight - 8.dp.toPx())
                    val trackBottom = trackTop + trackHeight
                    val trackCenterY = (trackTop + trackBottom) / 2f

                    // Draw vertical track line
                    drawLine(
                        color = Color.White.copy(alpha = alpha * 0.35f),
                        start = Offset(trackX, trackTop),
                        end = Offset(trackX, trackBottom),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Neutral 0.0 EV tick mark
                    drawLine(
                        color = Color.White.copy(alpha = alpha * 0.65f),
                        start = Offset(trackX - 4.dp.toPx(), trackCenterY),
                        end = Offset(trackX + 4.dp.toPx(), trackCenterY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Compute Sun Y position based on exposureIndex
                    val range = maxOf(1, maxExposureIndex - minExposureIndex)
                    val progress = ((exposureIndex - minExposureIndex).toFloat() / range.toFloat()).coerceIn(0f, 1f)
                    val sunY = trackBottom - progress * trackHeight

                    // Draw Glowing Sun Icon at (trackX, sunY)
                    val sunRadius = 4.2.dp.toPx()
                    val rayInner = 6.2.dp.toPx()
                    val rayOuter = 9.2.dp.toPx()
                    val sunColor = VintageAmber.copy(alpha = alpha)

                    // Sun core
                    drawCircle(
                        color = sunColor,
                        radius = sunRadius,
                        center = Offset(trackX, sunY)
                    )

                    // 8 Sun Rays
                    for (i in 0 until 8) {
                        val angle = (i * 45.0) * (Math.PI / 180.0)
                        val cosA = Math.cos(angle).toFloat()
                        val sinA = Math.sin(angle).toFloat()
                        drawLine(
                            color = sunColor,
                            start = Offset(trackX + cosA * rayInner, sunY + sinA * rayInner),
                            end = Offset(trackX + cosA * rayOuter, sunY + sinA * rayOuter),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // EV value readout text (e.g. "+0.7" or "0.0")
                    val evVal = exposureIndex * exposureStep
                    val evText = if (evVal > 0f) String.format(Locale.US, "+%.1f", evVal) else String.format(Locale.US, "%.1f", evVal)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt(), 255, 179, 71)
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                        textAlign = if (placeOnRight) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val textX = if (placeOnRight) trackX + 13.dp.toPx() else trackX - 13.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(
                        "$evText EV",
                        textX,
                        sunY + 3.5.dp.toPx(),
                        textPaint
                    )
                }
            }
        }
    }
}
