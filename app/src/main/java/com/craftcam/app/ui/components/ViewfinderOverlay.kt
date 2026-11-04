package com.craftcam.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmber
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    preset: CameraPreset,
    aspectRatio: AspectRatioMode,
    showGrid: Boolean,
    onTapFocus: (Float, Float) -> Unit
) {
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.5f) }
    val focusAlpha = remember { Animatable(1.0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    focusPoint = offset
                    onTapFocus(offset.x, offset.y)

                    scope.launch {
                        focusScale.snapTo(1.5f)
                        focusAlpha.snapTo(1.0f)
                        focusScale.animateTo(1.0f, tween(250))
                        focusAlpha.animateTo(0.0f, tween(800, delayMillis = 600))
                        focusPoint = null
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

            // 5. Authentic Live Date Stamp in Viewfinder
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

            // 6. Camcorder OSD & Scanlines
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

            // 7. Draw Matte / Letterboxing Bars
            if (top > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
                drawRect(color = CameraBlack, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            }
            if (left > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(left, canvasH))
                drawRect(color = CameraBlack, topLeft = Offset(right, 0f), size = Size(canvasW - right, canvasH))
            }

            // 8. Draw Rule of Thirds Grid if enabled
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

            // 9. Draw Viewfinder Frame Border
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                style = Stroke(width = 1.dp.toPx())
            )

            // 10. Draw Tap-to-Focus Reticle
            focusPoint?.let { pt ->
                val ringRadius = 32.dp.toPx() * focusScale.value
                drawCircle(
                    color = VintageAmber.copy(alpha = focusAlpha.value),
                    radius = ringRadius,
                    center = pt,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = VintageAmber.copy(alpha = focusAlpha.value * 0.8f),
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

