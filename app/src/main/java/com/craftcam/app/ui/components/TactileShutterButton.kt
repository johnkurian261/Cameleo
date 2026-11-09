package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.ui.theme.ShutterCenter
import com.craftcam.app.ui.theme.ShutterRing
import com.craftcam.app.ui.theme.VintageAmber
import com.craftcam.app.ui.theme.VintageRed

@Composable
fun TactileShutterButton(
    modifier: Modifier = Modifier,
    captureMode: CaptureMode = CaptureMode.PHOTO,
    isCapturing: Boolean,
    isRecording: Boolean = false,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "shutter_scale"
    )

    // Pulsing recording glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    val activeRed = Color(0xFFFF3B30)
    val pressedRed = Color(0xFFD32F2F)

    val buttonColor by animateColorAsState(
        targetValue = when {
            isPressed -> pressedRed
            else -> activeRed
        },
        label = "btn_color"
    )

    Box(
        modifier = modifier
            .size(86.dp)
            .pointerInput(isCapturing, isRecording) {
                if (!isCapturing) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            tryAwaitRelease()
                            isPressed = false
                            onClick()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(82.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.minDimension / 2f) - 2.dp.toPx()

            // 1. Outer ring (thin white outer ring / pulses red if recording)
            val ringColor = when {
                isRecording -> activeRed.copy(alpha = pulseAlpha)
                else -> Color.White.copy(alpha = 0.85f)
            }
            drawCircle(
                color = ringColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = if (isRecording) 5.dp.toPx() else 3.5.dp.toPx())
            )

            // 2. Inner dark mechanical recess
            drawCircle(
                color = Color(0xFF121418),
                radius = outerRadius - 3.5.dp.toPx(),
                center = center
            )

            // 3. Central dominant white circular button
            if (isRecording) {
                // Rounded square stop icon
                val squareSize = (outerRadius * 0.7f) * scale
                drawRoundRect(
                    color = activeRed,
                    topLeft = Offset(center.x - squareSize / 2f, center.y - squareSize / 2f),
                    size = Size(squareSize, squareSize),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            } else {
                val coreRadius = (outerRadius - 7.5.dp.toPx()) * scale
                drawCircle(
                    color = buttonColor,
                    radius = coreRadius,
                    center = center
                )
            }
        }

        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
    }
}
