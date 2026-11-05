package com.craftcam.app.ui.components

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.ui.theme.CameraBlack
import com.craftcam.app.ui.theme.SurfaceGraphite
import com.craftcam.app.ui.theme.TextPrimary
import com.craftcam.app.ui.theme.VintageAmber
import java.util.Locale

@Composable
fun TopControlBar(
    modifier: Modifier = Modifier,
    flashMode: Int,
    aspectRatio: AspectRatioMode,
    timerSeconds: Int,
    showGrid: Boolean,
    exposureIndex: Int = 0,
    exposureStep: Float = 0.5f,
    isExposureSliderVisible: Boolean = false,
    isTuned: Boolean = false,
    onFlashToggle: () -> Unit,
    onAspectRatioCycle: () -> Unit,
    onTimerCycle: () -> Unit,
    onGridToggle: () -> Unit,
    onExposureToggle: () -> Unit = {},
    onTuneClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBlack.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Flash Toggle
        IconButton(onClick = onFlashToggle) {
            when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Icon(Icons.Default.FlashOn, "Flash On", tint = VintageAmber)
                ImageCapture.FLASH_MODE_AUTO -> Icon(Icons.Default.FlashAuto, "Flash Auto", tint = VintageAmber)
                else -> Icon(Icons.Default.FlashOff, "Flash Off", tint = TextPrimary.copy(alpha = 0.7f))
            }
        }

        // 2. Aspect Ratio Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceGraphite)
                .clickable(onClick = onAspectRatioCycle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = aspectRatio.displayName,
                color = VintageAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // 3. Timer Indicator
        IconButton(onClick = onTimerCycle) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Timer,
                    "Timer",
                    tint = if (timerSeconds > 0) VintageAmber else TextPrimary.copy(alpha = 0.7f)
                )
                if (timerSeconds > 0) {
                    Text(
                        text = "${timerSeconds}s",
                        color = VintageAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }

        // 4. Grid Toggle
        IconButton(onClick = onGridToggle) {
            Icon(
                Icons.Default.GridOn,
                "Grid",
                tint = if (showGrid) VintageAmber else TextPrimary.copy(alpha = 0.7f)
            )
        }

        // 5. Quick Brightness / Exposure (Sun icon ☀️)
        IconButton(onClick = onExposureToggle) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.WbSunny,
                    "Exposure Brightness",
                    tint = if (exposureIndex != 0 || isExposureSliderVisible) VintageAmber else TextPrimary.copy(alpha = 0.7f)
                )
                if (exposureIndex != 0) {
                    val evVal = exposureIndex * exposureStep
                    val sign = if (evVal > 0) "+" else ""
                    Text(
                        text = "$sign${String.format(Locale.US, "%.1f", evVal)}",
                        color = VintageAmber,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }

        // 6. Analog Lab / Color Adjustments Tuning Toggle
        IconButton(onClick = onTuneClick) {
            Icon(
                Icons.Default.Tune,
                "Color Adjustments",
                tint = if (isTuned) VintageAmber else TextPrimary.copy(alpha = 0.7f)
            )
        }

        // 7. Settings Navigation
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Default.Settings,
                "Settings",
                tint = TextPrimary.copy(alpha = 0.7f)
            )
        }
    }
}
