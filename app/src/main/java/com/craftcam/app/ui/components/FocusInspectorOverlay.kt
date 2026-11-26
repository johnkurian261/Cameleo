package com.craftcam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.focus.AutofocusController
import com.craftcam.app.camera.focus.AutofocusStatus
import com.craftcam.app.camera.focus.FocusTelemetry

@Composable
fun FocusInspectorOverlay(
    modifier: Modifier = Modifier,
    telemetry: FocusTelemetry,
    autofocusController: AutofocusController,
    onClose: () -> Unit
) {
    val statusColor = when (telemetry.status) {
        AutofocusStatus.PASSIVE_FOCUSED, AutofocusStatus.FOCUSED_LOCKED -> Color(0xFF4CAF50) // Green
        AutofocusStatus.PASSIVE_SCAN, AutofocusStatus.ACTIVE_SCAN -> Color(0xFFFF9800) // Orange
        AutofocusStatus.FIXED_FOCUS -> Color(0xFF2196F3) // Blue
        AutofocusStatus.NOT_FOCUSED_LOCKED, AutofocusStatus.PASSIVE_UNFOCUSED -> Color(0xFFF44336) // Red
        AutofocusStatus.INACTIVE -> Color(0xFF9E9E9E) // Gray
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color(0xE6101214), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FOCUS INSPECTOR",
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = telemetry.status.displayName.uppercase(),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Metrics Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    InspectorMetric("Camera", "ID ${telemetry.cameraId} (${if (telemetry.lensFacing == 0) "Front" else "Rear"})")
                    InspectorMetric("AF Mode", if (telemetry.isFixedFocus) "FIXED" else "CONTINUOUS")
                    InspectorMetric("Lens State", if (telemetry.isLensMoving) "MOVING" else "STATIONARY", if (telemetry.isLensMoving) Color(0xFFFF9800) else Color(0xFF4CAF50))
                }
                Column(modifier = Modifier.weight(1f)) {
                    val distStr = if (telemetry.approxDistanceMeters.isInfinite() || telemetry.approxDistanceMeters > 50f) "Infinity" else "${"%.2f".format(telemetry.approxDistanceMeters)} m"
                    InspectorMetric("Distance", "${"%.2f".format(telemetry.lensDistanceDiopters)} D ($distStr)")
                    InspectorMetric("Min Focus", "${"%.1f".format(telemetry.minFocusDistanceDiopters)} D (${"%.2f".format(if (telemetry.minFocusDistanceDiopters > 0f) 1f/telemetry.minFocusDistanceDiopters else 0f)} m)")
                    InspectorMetric("Convergence", "${telemetry.lastConvergenceDurationMs} ms")
                }
            }

            if (telemetry.isSubjectTooClose) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FF5252), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFFF5252), RoundedCornerShape(4.dp))
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = "WARNING: SUBJECT TOO CLOSE (Below min focus distance)",
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (telemetry.isLocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AE/AF LOCKED (Tap screen to release)",
                    color = Color(0xFFFFD54F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recent Event Log Timeline
            Text(
                text = "EVENT TIMELINE:",
                color = Color(0xFFB0BEC5),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            val logs = autofocusController.getRecentLogEntries().take(6)
            Column(modifier = Modifier.fillMaxWidth()) {
                for (log in logs) {
                    Text(
                        text = "[${log.formattedTime}] ${log.message}",
                        color = Color(0xFFCFD8DC),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectorMetric(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            color = Color(0xFF90A4AE),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
