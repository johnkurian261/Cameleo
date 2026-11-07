package com.craftcam.app.ui.viewfinder

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalView
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

            val lensIcon = when (selectedLens.type) {
                LensType.NORMAL -> "⊙"
                LensType.WIDE -> "⊞"
                LensType.FISHEYE_F -> "◉"
                LensType.FISHEYE_W -> "⊚"
                LensType.PRISM -> "⟡"
                LensType.STARBURST -> "★"
                LensType.ND -> "◐"
                LensType.COLOR_FLASH -> "⚡"
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
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lensIcon,
                        color = accessoryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
 * Sleek bottom sheet presenting compatible optical accessories for the active camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TactileLensSelectorSheet(
    shootingState: CameraShootingState,
    onLensSelected: (LensType) -> Unit,
    onColorFlashTintSelected: (ColorFlashTint) -> Unit = {},
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val profile = shootingState.cameraProfile
    val supportedLenses = profile.supportedLenses
    val selectedLens = shootingState.selectedLens

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGraphite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OPTICAL ACCESSORIES",
                        color = VintageAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "${profile.name} Lens System",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Lenses List
            supportedLenses.forEach { lensType ->
                val isSelected = lensType == selectedLens.type
                val icon = when (lensType) {
                    LensType.NORMAL -> "⊙"
                    LensType.WIDE -> "⊞"
                    LensType.FISHEYE_F -> "◉"
                    LensType.FISHEYE_W -> "⊚"
                    LensType.PRISM -> "⟡"
                    LensType.STARBURST -> "★"
                    LensType.ND -> "◐"
                    LensType.COLOR_FLASH -> "⚡"
                }

                val description = when (lensType) {
                    LensType.NORMAL -> "Standard optical prime glass"
                    LensType.WIDE -> "Wide-angle optical conversion element"
                    LensType.FISHEYE_F -> "Hemispherical circular fisheye with dark circular boundary"
                    LensType.FISHEYE_W -> "Full-frame ultra-wide curvilinear distortion"
                    LensType.PRISM -> "Multi-faceted optical refraction and chromatic splitting"
                    LensType.STARBURST -> "4-point diffraction star rays around specular highlights"
                    LensType.ND -> "Neutral density light attenuation (-2 EV)"
                    LensType.COLOR_FLASH -> "Tinted xenon flash gelatin filter"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) VintageAmber.copy(alpha = 0.12f) else SurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) VintageAmber else DividerSubtle,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onLensSelected(lensType)
                                if (lensType != LensType.COLOR_FLASH) {
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) VintageAmber else SurfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = icon,
                                    color = if (isSelected) CameraBlack else VintageAmber,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = lensType.displayName,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = description,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(VintageAmber),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CameraBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (lensType == LensType.COLOR_FLASH && isSelected) {
                        HorizontalDivider(
                            color = DividerSubtle,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isTintActive) tint.uiColor.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = if (isTintActive) 1.5.dp else 1.dp,
                                            color = if (isTintActive) tint.uiColor else DividerSubtle,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onColorFlashTintSelected(tint)
                                            onDismiss()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(tint.uiColor)
                                        )
                                        Text(
                                            text = tint.displayName.split(" ").last(),
                                            color = if (isTintActive) tint.uiColor else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isTintActive) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

