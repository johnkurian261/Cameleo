package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.craftcam.app.R
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.config.AppSettingsStore

@Composable
fun TactileShutterButton(
    modifier: Modifier = Modifier,
    captureMode: CaptureMode = CaptureMode.PHOTO,
    isCapturing: Boolean,
    isRecording: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "shutter_press")
    val label = when {
        isRecording -> "Stop recording"
        isCapturing -> "Capturing"
        captureMode == CaptureMode.MODE_3D -> "Take 3D photo"
        else -> "Take photo"
    }

    Box(
        modifier = modifier.size(85.dp).clickable(
            enabled = !isCapturing,
            interactionSource = interaction,
            indication = null,
            role = Role.Button
        ) {
            if (AppSettingsStore.get(context).current.shutterVibration) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onClick()
        },
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.dazz_capture), label, Modifier.size(85.dp).scale(scale))
        if (!isRecording) {
            Box(Modifier.size(70.dp).scale(scale).background(Color(0xFFFF3B30), CircleShape))
        }
        if (isRecording) {
            Box(Modifier.size(24.dp).background(Color(0xFFFF3B30), RoundedCornerShape(5.dp)))
        }
        if (isCapturing && captureMode != CaptureMode.MODE_3D) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}
