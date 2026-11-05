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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmber
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    preset: CameraPreset,
    aspectRatio: AspectRatioMode,
    showGrid: Boolean,
    exposureIndex: Int = 0,
    minExposureIndex: Int = -4,
    maxExposureIndex: Int = 4,
    exposureStep: Float = 0.5f,
    onTapFocus: (Float, Float) -> Unit,
    onExposureChange: (Int) -> Unit = {}
) {
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

                    var isDrag = false
                    var totalDragY = 0f

                    if (focusPoint == null) {
                        focusPoint = startPos
                        onTapFocus(startPos.x, startPos.y)
                        scope.launch {
                            focusScale.snapTo(1.35f)
                            focusAlpha.snapTo(1.0f)
                            focusScale.animateTo(1.0f, tween(180))
                        }
                    } else {
                        val dist = (startPos - focusPoint!!).getDistance()
                        if (dist > moveThresholdPx) {
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
                    }
                    scheduleDismiss()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            scheduleDismiss()
                            break
                        }

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

            // 2. 8mm / 16mm Projector Rounded Film Gate
            val is8mm = preset.id.contains("8mm") || preset.id.contains("16mm")
            val isCamcorder = preset.category == CameraCategory.CAMCORDER
            if (is8mm) {
                val cornerRadius = frameW * 0.08f
                val gatePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            radiusX = cornerRadius,
                            radiusY = cornerRadius
                        )
                    )
                }
                val outerPath = Path().apply {
                    addRect(Rect(left, top, right, bottom))
                    op(this, gatePath, PathOperation.Difference)
                }
                drawPath(outerPath, Color(0xFF0A0A0C))
            }

            // 3. Authentic Live Date Stamp in Viewfinder
            if (preset.dateStamp?.enabled == true) {
                val cal = Calendar.getInstance()
                val offset = preset.dateStamp.customYearOffset
                val yy = (cal.get(Calendar.YEAR) + offset) % 100
                val mm = cal.get(Calendar.MONTH) + 1
                val dd = cal.get(Calendar.DAY_OF_MONTH)
                val dateStr = String.format(Locale.US, "'%02d  %2d  %2d", yy, mm, dd)

                val dateColor = when {
                    isCamcorder                                   -> android.graphics.Color.rgb(80, 255, 120)
                    is8mm                                         -> android.graphics.Color.rgb(255, 140, 20)
                    preset.category == CameraCategory.CCD_DIGITAL -> android.graphics.Color.rgb(255, 180, 30)
                    else                                          -> android.graphics.Color.rgb(255, 130, 20)
                }

                drawContext.canvas.nativeCanvas.drawText(
                    dateStr,
                    right - 20.dp.toPx(),
                    bottom - 18.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = dateColor
                        textSize = 14.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                        setShadowLayer(5f, 0f, 0f, android.graphics.Color.argb(180, 200, 40, 0))
                    }
                )
            }

            // 4. Camcorder OSD & Scanlines
            if (isCamcorder) {
                val cal = Calendar.getInstance()
                val dateStr = java.text.SimpleDateFormat("MMM. dd yyyy", Locale.US).format(cal.time).uppercase(Locale.US)
                drawContext.canvas.nativeCanvas.drawText(
                    dateStr,
                    right - 20.dp.toPx(),
                    bottom - 18.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(80, 255, 120)
                        textSize = 14.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "SP  [■■■]",
                    right - 20.dp.toPx(),
                    top + 28.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(80, 255, 120)
                        textSize = 13.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                )
            }

            // 5. Draw Matte / Letterboxing Bars
            if (top > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
                drawRect(color = CameraBlack, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            }
            if (left > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(left, canvasH))
                drawRect(color = CameraBlack, topLeft = Offset(right, 0f), size = Size(canvasW - right, canvasH))
            }

            // 6. Draw Rule of Thirds Grid if enabled
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

            // 7. Draw Viewfinder Frame Border
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                style = Stroke(width = 1.dp.toPx())
            )

            // 8. Draw Tap-to-Focus Reticle & Vertical Sun Brightness Slider (Dazz Cam Style)
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
