package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.profile.CameraShootingState
import com.craftcam.app.camera.profile.LensType
import com.craftcam.app.model.CameraEngineMode
import com.craftcam.app.ui.theme.*

/**
 * Compact, floating minimal camera control menu panel.
 * Triggered by the top-right three-dot menu button.
 */
@Composable
fun CameraControlMenu(
    visible: Boolean,
    flashMode: Int,
    timerSeconds: Int,
    aspectRatio: AspectRatioMode,
    showGrid: Boolean,
    engineMode: CameraEngineMode,
    isTuned: Boolean,
    onFlashSelect: (Int) -> Unit,
    onTimerSelect: (Int) -> Unit,
    onAspectRatioSelect: (AspectRatioMode) -> Unit,
    onToggleGrid: () -> Unit,
    onResetFrame: () -> Unit,
    onEngineModeSelect: (CameraEngineMode) -> Unit,
    onOpenTuning: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollection: () -> Unit = {},
    shootingState: CameraShootingState? = null,
    onSelectLens: ((LensType) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val view = LocalView.current

    // Scrim overlay to capture outside taps
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Floating compact dark panel anchored at top-right
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.92f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xF216181E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume taps inside the card
                    )
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTROLS",
                            color = Color(0xB3FFFFFF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // 2. Flash Mode Selector
                    ControlSection(label = "FLASH") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MenuSegmentChip(
                                label = "⚡ OFF",
                                isSelected = flashMode == ImageCapture.FLASH_MODE_OFF,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFlashSelect(ImageCapture.FLASH_MODE_OFF)
                                }
                            )
                            MenuSegmentChip(
                                label = "⚡ AUTO",
                                isSelected = flashMode == ImageCapture.FLASH_MODE_AUTO,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFlashSelect(ImageCapture.FLASH_MODE_AUTO)
                                }
                            )
                            MenuSegmentChip(
                                label = "⚡ ON",
                                isSelected = flashMode == ImageCapture.FLASH_MODE_ON,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFlashSelect(ImageCapture.FLASH_MODE_ON)
                                }
                            )
                        }
                    }

                    // 3. Timer Selector
                    ControlSection(label = "TIMER") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MenuSegmentChip(
                                label = "OFF",
                                isSelected = timerSeconds == 0,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onTimerSelect(0)
                                }
                            )
                            MenuSegmentChip(
                                label = "3s",
                                isSelected = timerSeconds == 3,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onTimerSelect(3)
                                }
                            )
                            MenuSegmentChip(
                                label = "10s",
                                isSelected = timerSeconds == 10,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onTimerSelect(10)
                                }
                            )
                        }
                    }

                    // 4. Aspect Ratio Selector
                    ControlSection(label = "ASPECT RATIO") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val ratios = listOf(
                                AspectRatioMode.RATIO_4_3 to "4:3",
                                AspectRatioMode.RATIO_1_1 to "1:1",
                                AspectRatioMode.RATIO_3_2 to "3:2",
                                AspectRatioMode.RATIO_16_9 to "16:9"
                            )
                            ratios.forEach { (mode, name) ->
                                MenuSegmentChip(
                                    label = name,
                                    isSelected = aspectRatio == mode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onAspectRatioSelect(mode)
                                    }
                                )
                            }
                        }
                    }

                    // 4b. Optical Lens Accessories (if active camera profile supports multiple lenses)
                    if (shootingState != null && shootingState.cameraProfile.supportedLenses.size > 1) {
                        val supportedLenses = shootingState.cameraProfile.supportedLenses
                        val selectedLens = shootingState.selectedLens
                        ControlSection(label = "OPTICAL LENS") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                supportedLenses.forEach { lensType ->
                                    val isSelected = selectedLens.type == lensType
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) VintageAmber else Color(0x33FFFFFF))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) VintageAmber else Color(0x22FFFFFF),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onSelectLens?.invoke(lensType)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = lensType.iconRes),
                                                contentDescription = lensType.displayName,
                                                modifier = Modifier.size(20.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Text(
                                                text = lensType.displayName.uppercase(),
                                                color = if (isSelected) CameraBlack else Color.White,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Grid & Frame Reset
                    ControlSection(label = "FRAME & VIEW") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MenuButton(
                                text = if (showGrid) "⊞ GRID ON" else "⊞ GRID OFF",
                                isSelected = showGrid,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onToggleGrid()
                                }
                            )
                            MenuButton(
                                text = "⛶ RESET FRAME",
                                isSelected = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onResetFrame()
                                }
                            )
                        }
                    }

                    // 6. Camera Engine Mode Switcher
                    ControlSection(label = "ENGINE MODE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                CameraEngineMode.FILM to "FILM",
                                CameraEngineMode.NATURAL to "NATURAL",
                                CameraEngineMode.PRO to "PRO"
                            )
                            modes.forEach { (mode, name) ->
                                MenuSegmentChip(
                                    label = name,
                                    isSelected = engineMode == mode,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onEngineModeSelect(mode)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x24FFFFFF), thickness = 0.5.dp)

                    // 7. Cameras, Tuning & Settings Shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MenuButton(
                            text = "CAMERAS",
                            icon = Icons.Default.CameraAlt,
                            isSelected = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDismiss()
                                onNavigateToCollection()
                            }
                        )

                        MenuButton(
                            text = if (isTuned) "● TUNING" else "TUNING",
                            icon = Icons.Default.Tune,
                            isSelected = isTuned,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDismiss()
                                onOpenTuning()
                            }
                        )
                    }

                    MenuButton(
                        text = "SETTINGS",
                        icon = Icons.Default.Settings,
                        isSelected = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                            onNavigateToSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    label: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color(0x80FFFFFF),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp
        )
        content()
    }
}

@Composable
private fun MenuSegmentChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color.White else Color(0x1FFFFFFF))
            .border(
                width = 1.dp,
                color = if (isSelected) Color.White else Color(0x1FFFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) CameraBlack else Color(0xE6FFFFFF),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MenuButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
            .border(1.dp, if (isSelected) Color.White else Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xCCFFFFFF),
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

