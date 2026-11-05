package com.craftcam.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun QuickExposureSlider(
    modifier: Modifier = Modifier,
    exposureIndex: Int,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    exposureStep: Float,
    onExposureChange: (Int) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val evValue = exposureIndex * exposureStep
    val evFormatted = if (evValue > 0f) {
        String.format(Locale.US, "+%.1f EV", evValue)
    } else {
        String.format(Locale.US, "%.1f EV", evValue)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = CameraBlack.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, DividerSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WbSunny,
                contentDescription = "Exposure Down",
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )

            Slider(
                value = exposureIndex.toFloat(),
                onValueChange = { onExposureChange(it.roundToInt()) },
                valueRange = minExposureIndex.toFloat()..maxExposureIndex.toFloat(),
                steps = maxOf(0, (maxExposureIndex - minExposureIndex) - 1),
                colors = SliderDefaults.colors(
                    thumbColor = VintageAmber,
                    activeTrackColor = VintageAmber,
                    inactiveTrackColor = SurfaceElevated
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )

            Icon(
                Icons.Default.WbSunny,
                contentDescription = "Exposure Up",
                tint = VintageAmber,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = evFormatted,
                color = if (exposureIndex != 0) VintageAmber else TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 52.dp)
            )

            if (exposureIndex != 0) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset EV",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
