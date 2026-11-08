package com.craftcam.app.ui.viewfinder

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.craftcam.app.camera.profile.*
import com.craftcam.app.ui.theme.*

/**
 * Dedicated Camera Shooting Bar for vintage physical camera simulation.
 * Provides camera-specific focal length switching and optical lens accessory mounting.
 */
@Composable
fun CameraShootingBar(
    modifier: Modifier = Modifier,
    shootingState: CameraShootingState,
    onFocalLengthSelected: (FocalLengthOption) -> Unit,
    onOpenLensSheet: () -> Unit
) {
    val view = LocalView.current
    val profile = shootingState.cameraProfile
    val focalLengths = profile.focalLengths
    val supportedLenses = profile.supportedLenses
    val selectedLens = shootingState.selectedLens
    val selectedFocal = shootingState.selectedFocalLength

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── 1. Focal Length Selector Chips ──────────────────────────────────────
        if (focalLengths.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                focalLengths.forEach { option ->
                    val isSelected = option.label == selectedFocal.label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) VintageAmber else SurfaceElevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) VintageAmber else DividerSubtle,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!isSelected) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onFocalLengthSelected(option)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            color = if (isSelected) CameraBlack else TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        } else if (focalLengths.size == 1) {
            // Single fixed focal length camera body (e.g. Disposable 32mm)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated.copy(alpha = 0.70f))
                    .border(1.dp, DividerSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${focalLengths[0].label} FIXED",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        // ── 2. Optical Lens Accessory Button ────────────────────────────────────
        if (supportedLenses.size > 1) {
            val hasAccessory = selectedLens.type != LensType.NORMAL
            val accessoryColor = if (selectedLens.type == LensType.COLOR_FLASH && selectedLens.colorFlashTint != ColorFlashTint.NONE) {
                selectedLens.colorFlashTint.uiColor
            } else if (hasAccessory) {
                VintageAmber
            } else {
                TextPrimary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hasAccessory) accessoryColor.copy(alpha = 0.20f) else SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (hasAccessory) accessoryColor else DividerSubtle,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenLensSheet()
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = selectedLens.type.iconRes),
                        contentDescription = selectedLens.name,
                        modifier = Modifier.size(20.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = selectedLens.name.uppercase(),
                        color = accessoryColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "▾",
                        color = if (hasAccessory) accessoryColor else TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Compact tactile rectangle bubble presenting the lens icons for the active camera.
 * Eliminates text walls and verbose sheets in favor of a sleek, floating lens bubble.
 */
@Composable
fun TactileLensBubble(
    modifier: Modifier = Modifier,
    shootingState: CameraShootingState,
    onLensSelected: (LensType) -> Unit,
    onColorFlashTintSelected: (ColorFlashTint) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val view = LocalView.current
    val profile = shootingState.cameraProfile
    val supportedLenses = profile.supportedLenses
    val selectedLens = shootingState.selectedLens

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF0181A20))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Horizontal row of lens icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                supportedLenses.forEach { lensType ->
                    val isSelected = lensType == selectedLens.type

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) VintageAmber.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) VintageAmber else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onLensSelected(lensType)
                                if (lensType != LensType.COLOR_FLASH) {
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = lensType.iconRes),
                            contentDescription = lensType.displayName,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // If Color Flash is selected, show 4 color flash gel tint choices
            if (selectedLens.type == LensType.COLOR_FLASH) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val flashTints = listOf(
                        ColorFlashTint.WARM,
                        ColorFlashTint.COOL,
                        ColorFlashTint.RED,
                        ColorFlashTint.GREEN
                    )
                    flashTints.forEach { tint ->
                        val isTintActive = selectedLens.colorFlashTint == tint
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(tint.uiColor)
                                .border(
                                    width = if (isTintActive) 2.dp else 1.dp,
                                    color = if (isTintActive) Color.White else Color(0x55FFFFFF),
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onColorFlashTintSelected(tint)
                                    onDismiss()
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible delegator for TactileLensBubble.
 */
@Composable
fun TactileLensSelectorSheet(
    shootingState: CameraShootingState,
    onLensSelected: (LensType) -> Unit,
    onColorFlashTintSelected: (ColorFlashTint) -> Unit = {},
    onDismiss: () -> Unit
) {
    TactileLensBubble(
        shootingState = shootingState,
        onLensSelected = onLensSelected,
        onColorFlashTintSelected = onColorFlashTintSelected,
        onDismiss = onDismiss
    )
}

