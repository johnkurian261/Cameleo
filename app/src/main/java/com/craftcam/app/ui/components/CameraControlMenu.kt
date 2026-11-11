package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.profile.CameraShootingState
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.config.AppSettingsStore
import com.craftcam.app.model.CameraEngineMode

/**
 * Authentic Dazz Cam-style floating capture popup menu.
 * Triggered by the top-right three-dot menu button.
 *
 * Layout:
 * Row 1: [⚡ Flash] [⊘ Frame Border] [⌗ Assistive Grid]
 * Row 2: [ [○] Zoom Mode / Frame ] [⚙ Settings]
 */
@Composable
fun CameraControlMenu(
    visible: Boolean,
    flashMode: Int,
    timerSeconds: Int = 0,
    aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_3_2,
    showGrid: Boolean,
    frameBorderEnabled: Boolean = true,
    engineMode: CameraEngineMode = CameraEngineMode.FILM,
    isTuned: Boolean = false,
    onFlashSelect: (Int) -> Unit,
    onTimerSelect: (Int) -> Unit = {},
    onAspectRatioSelect: (AspectRatioMode) -> Unit = {},
    onToggleGrid: () -> Unit,
    onToggleFrameBorder: () -> Unit = {},
    onResetFrame: () -> Unit = {},
    onEngineModeSelect: (CameraEngineMode) -> Unit = {},
    onOpenTuning: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToCollection: () -> Unit = {},
    shootingState: CameraShootingState? = null,
    onSelectLens: ((LensType) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val view = LocalView.current
    val context = LocalContext.current
    val settingsStore = remember(context) { AppSettingsStore.get(context) }
    val settings by settingsStore.state.collectAsState()

    // Fullscreen scrim to capture outside clicks and dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Floating rounded frosted menu anchored at top-right
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.90f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 52.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(236.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xD92D2D30))
                    .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(30.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks inside menu
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ── Row 1: Flash, Frame Border, Grid ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Flash Toggle Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E20))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val nextMode = when (flashMode) {
                                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                        else -> ImageCapture.FLASH_MODE_OFF
                                    }
                                    onFlashSelect(nextMode)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            FlashIcon(flashMode = flashMode)
                        }

                        // 2. Frame Border Toggle Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E20))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onToggleFrameBorder()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            FrameBorderIcon(frameBorderEnabled = frameBorderEnabled)
                        }

                        // 3. Assistive Grid Toggle Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E20))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onToggleGrid()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GridIcon(showGrid = showGrid)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Row 2: Zoom Mode Pill & Settings Button ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4. Zoom Mode Pill Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(RoundedCornerShape(30.dp))
                                .background(Color(0xFF1E1E20))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val nextMode = if (settings.zoomMode == "Frame") "Lens" else "Frame"
                                    settingsStore.setZoomMode(nextMode)
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ZoomModeIcon()
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Zoom Mode",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = settings.zoomMode,
                                        color = Color(0xFF8E8E93),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // 5. Settings Gear Button
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E20))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onDismiss()
                                    onNavigateToSettings()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws flash icon with dynamic state (Off with slash, Auto, or On).
 */
@Composable
private fun FlashIcon(flashMode: Int) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height

        // Lightning bolt path
        val path = Path().apply {
            moveTo(w * 0.58f, h * 0.12f)
            lineTo(w * 0.32f, h * 0.50f)
            lineTo(w * 0.52f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.88f)
            lineTo(w * 0.70f, h * 0.46f)
            lineTo(w * 0.50f, h * 0.46f)
            close()
        }

        val flashColor = if (flashMode == ImageCapture.FLASH_MODE_ON) Color(0xFFFFCC00) else Color.White
        drawPath(path, color = flashColor, style = Fill)

        if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            drawLine(
                color = Color.White,
                start = Offset(w * 0.18f, h * 0.82f),
                end = Offset(w * 0.82f, h * 0.18f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draws photographic framing rectangle with slash when disabled.
 */
@Composable
private fun FrameBorderIcon(frameBorderEnabled: Boolean) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.14f, h * 0.22f),
            size = Size(w * 0.72f, h * 0.56f),
            cornerRadius = CornerRadius(3.5.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        if (!frameBorderEnabled) {
            drawLine(
                color = Color.White,
                start = Offset(w * 0.14f, h * 0.80f),
                end = Offset(w * 0.86f, h * 0.20f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draws 3x3 rule of thirds grid with slash when disabled.
 */
@Composable
private fun GridIcon(showGrid: Boolean) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = 1.8.dp.toPx()

        // Two vertical lines
        drawLine(Color.White, Offset(w * 0.35f, h * 0.14f), Offset(w * 0.35f, h * 0.86f), strokeW)
        drawLine(Color.White, Offset(w * 0.65f, h * 0.14f), Offset(w * 0.65f, h * 0.86f), strokeW)

        // Two horizontal lines
        drawLine(Color.White, Offset(w * 0.14f, h * 0.35f), Offset(w * 0.86f, h * 0.35f), strokeW)
        drawLine(Color.White, Offset(w * 0.14f, h * 0.65f), Offset(w * 0.86f, h * 0.65f), strokeW)

        if (!showGrid) {
            drawLine(
                color = Color.White,
                start = Offset(w * 0.14f, h * 0.86f),
                end = Offset(w * 0.86f, h * 0.14f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Draws zoom mode framing symbol [○].
 */
@Composable
private fun ZoomModeIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.84f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx())
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.22f,
            center = center,
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
}
