package com.craftcam.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmber
import kotlinx.coroutines.launch

@Composable
fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    aspectRatio: AspectRatioMode,
    showGrid: Boolean,
    onTapFocus: (Offset, Float, Float) -> Unit
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
                    val xNorm = offset.x / size.width
                    val yNorm = offset.y / size.height
                    onTapFocus(offset, xNorm, yNorm)

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

            // 2. Draw Matte / Letterboxing Bars
            if (top > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
                drawRect(color = CameraBlack, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            }
            if (left > 0) {
                drawRect(color = CameraBlack, topLeft = Offset(0f, 0f), size = Size(left, canvasH))
                drawRect(color = CameraBlack, topLeft = Offset(right, 0f), size = Size(canvasW - right, canvasH))
            }

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

            // 4. Draw Viewfinder Frame Border
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                style = Stroke(width = 1.dp.toPx())
            )

            // 5. Draw Tap-to-Focus Reticle
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

