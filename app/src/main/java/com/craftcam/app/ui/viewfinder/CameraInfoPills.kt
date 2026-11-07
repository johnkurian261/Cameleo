package com.craftcam.app.ui.viewfinder

import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.profile.*
import com.craftcam.app.model.CameraEngineMode
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.VintageAmber
import java.util.Locale

/**
 * Tactile quick-controls icon bar placed directly above the camera capture button.
 * Provides immediate single-tap access to Flash, Brightness (Exposure), Temperature (Tuning),
 * Timer, and Grid.
 */
@Composable
fun CameraInfoPills(
    modifier: Modifier = Modifier,
    shootingState: CameraShootingState,
    exposureIndex: Int,
    exposureStep: Float,
    isExposureSliderOpen: Boolean,
    flashMode: Int,
    timerSeconds: Int,
    showGrid: Boolean = false,
    isTuned: Boolean = false,
    engineMode: CameraEngineMode = CameraEngineMode.FILM,
    onFlashCycle: () -> Unit = {},
    onToggleExposure: () -> Unit = {},
    onOpenTuning: () -> Unit = {},
    onTimerCycle: () -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onFocalLengthCycle: () -> Unit = {}
) {
    val evValue = exposureIndex * exposureStep
    val hasEvOffset = exposureIndex != 0
    val evBadgeText = if (hasEvOffset) {
        val sign = if (evValue > 0f) "+" else ""
        String.format(Locale.US, "%s%.1f", sign, evValue)
    } else null

    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
        else -> Icons.Default.FlashOff
    }
    val isFlashActive = flashMode != ImageCapture.FLASH_MODE_OFF

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Flash Toggle Button (⚡)
        QuickIconButton(
            icon = flashIcon,
            contentDescription = "Flash",
            isActive = isFlashActive,
            onClick = onFlashCycle
        )

        // 2. Brightness / Exposure Slider Button (☀️)
        QuickIconButton(
            icon = Icons.Default.WbSunny,
            contentDescription = "Brightness / Exposure",
            isActive = hasEvOffset || isExposureSliderOpen,
            onClick = onToggleExposure
        )

        // 3. Temperature / Warmth Tuning Button (🌡️)
        QuickIconButton(
            icon = Icons.Default.DeviceThermostat,
            contentDescription = "Temperature / Tuning",
            isActive = isTuned,
            onClick = onOpenTuning
        )

        // 4. Timer Button (⏱️)
        QuickIconButton(
            icon = Icons.Default.Timer,
            contentDescription = "Timer",
            isActive = timerSeconds > 0,
            subscript = if (timerSeconds > 0) "${timerSeconds}s" else null,
            onClick = onTimerCycle
        )

        // 5. Grid Toggle Button (▦)
        QuickIconButton(
            icon = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
            contentDescription = "Grid",
            isActive = showGrid,
            onClick = onToggleGrid
        )
    }
}

@Composable
private fun QuickIconButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    subscript: String? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val activeRed = Color(0xFFFF3B30)
    val tint = if (isActive) activeRed else Color.White

    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        if (subscript != null) {
            Text(
                text = subscript,
                color = tint,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}
