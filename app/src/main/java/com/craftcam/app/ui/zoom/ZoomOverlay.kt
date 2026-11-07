package com.craftcam.app.ui.zoom

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.ZoomState
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmberGlow

/**
 * Dazz-Style Interactive Zoom Overlay.
 *
 * Renders a rounded rectangular viewfinder frame with a thin semi-transparent stroke
 * and displays the current sensor-aware equivalent focal length (e.g. 26mm, 35mm, 37mm, 50mm)
 * centered horizontally directly above the upper edge of the frame.
 */
@Composable
fun DazzZoomOverlay(
    modifier: Modifier = Modifier,
    zoomState: ZoomState,
    aspectRatio: AspectRatioMode
) {
    val alpha by animateFloatAsState(
        targetValue = if (zoomState.isOverlayVisible) 1.0f else 0.0f,
        animationSpec = tween(
            durationMillis = if (zoomState.isOverlayVisible) 150 else 400,
            easing = LinearOutSlowInEasing
        ),
        label = "zoom_overlay_alpha"
    )

    if (alpha <= 0.005f) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val totalW = maxWidth
        val totalH = maxHeight

        // Aspect ratio framing in portrait orientation: ratio = width / height
        val targetRatio = 1f / aspectRatio.ratio
        val frameW: Dp
        val frameH: Dp

        if (totalW / totalH > targetRatio) {
            frameH = totalH
            frameW = totalH * targetRatio
        } else {
            frameW = totalW
            frameH = totalW / targetRatio
        }

        // Inset within active aspect ratio frame
        val margin = 26.dp
        val rectW = (frameW - margin * 2).coerceAtLeast(140.dp)
        val rectH = (frameH - margin * 2).coerceAtLeast(140.dp)

        val rectLeft = (totalW - rectW) / 2
        val rectTop = (totalH - rectH) / 2

        // 1. Rounded Rectangular Zoom Frame
        Box(
            modifier = Modifier
                .offset(x = rectLeft, y = rectTop)
                .size(rectW, rectH)
                .border(
                    width = 1.2.dp,
                    color = Color.White.copy(alpha = alpha * 0.60f),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        // 2. Focal Length Indicator Centered Directly Above Top Edge
        Box(
            modifier = Modifier
                .offset(x = rectLeft, y = rectTop - 34.dp)
                .width(rectW),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(
                            color = CameraBlack.copy(alpha = alpha * 0.55f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = zoomState.formattedFocalLength,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Optional debug summary (Camera ID, Ratio, Range)
                if (zoomState.isDebugVisible) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = zoomState.debugSummary,
                        color = VintageAmberGlow.copy(alpha = alpha),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

