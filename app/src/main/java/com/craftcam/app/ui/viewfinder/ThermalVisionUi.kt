package com.craftcam.app.ui.viewfinder

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.R
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.thermal.ThermalPalette
import com.craftcam.app.thermal.ThermalUiState
import com.craftcam.app.thermal.ThermalVisionConfig
import com.craftcam.app.ui.components.TactileShutterButton
import com.craftcam.app.ui.theme.SurfaceElevated
import com.craftcam.app.ui.theme.TextMuted
import com.craftcam.app.ui.theme.TextPrimary
import com.craftcam.app.ui.theme.TextSecondary
import com.craftcam.app.ui.theme.VintageRed
import java.util.Locale

private val ThermalOrange = Color(0xFFFF6B1A)
private val ThermalSurface = Color(0xFF151820)
private val ThermalMonoAccent = Color(0xFFE5EAF2)

@Composable
internal fun ThermalTopToolbar(
    thermalState: ThermalUiState,
    isRecording: Boolean,
    switchRotationDeg: Float,
    onSwitchCamera: () -> Unit,
    onToggleInvert: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettingsMenu: () -> Unit
) {
    val view = LocalView.current
    val cfg = thermalState.config
    val accent = if (cfg.monochromeHud) ThermalMonoAccent else ThermalOrange

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isRecording) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSwitchCamera()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dazz_switchcamera),
                    contentDescription = "Switch Camera",
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(switchRotationDeg)
                        .alpha(if (isRecording) 0.45f else 1f),
                    contentScale = ContentScale.Fit
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ThermalSurface,
                border = BorderStroke(1.2.dp, accent.copy(alpha = 0.72f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenDrawer()
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(
                        text = "THERMAL",
                        color = accent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        text = "SIMULATED IR",
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (cfg.inverted) accent.copy(alpha = 0.18f) else ThermalSurface,
                border = BorderStroke(
                    1.dp,
                    if (cfg.inverted) accent else Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleInvert()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Invert Hot Cold",
                        tint = if (cfg.inverted) accent else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (cfg.inverted) "INV ON" else "INVERT",
                        color = if (cfg.inverted) accent else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenDrawer()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Thermal Effects & HUD",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenSettingsMenu()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dazz_more),
                    contentDescription = "Menu",
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
internal fun ThermalViewfinderOverlay(
    thermalState: ThermalUiState,
    isRecording: Boolean,
    recordingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val cfg = thermalState.config
    val hudTint = if (cfg.monochromeHud) ThermalMonoAccent else ThermalOrange

    val pulse = rememberInfiniteTransition(label = "thermal_rec")
    val recAlpha by pulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "rec_alpha"
    )

    Box(modifier = modifier) {
        // 1. Optional corner tactical framing ticks + center open reticle
        if (cfg.hudEnabled || cfg.crosshairEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val stroke = 1.4.dp.toPx()

                if (cfg.hudEnabled) {
                    val m = 14.dp.toPx()
                    val arm = 16.dp.toPx()
                    val bracketCol = hudTint.copy(alpha = 0.65f)
                    // Top-left
                    drawLine(bracketCol, Offset(m, m), Offset(m + arm, m), stroke)
                    drawLine(bracketCol, Offset(m, m), Offset(m, m + arm), stroke)
                    // Top-right
                    drawLine(bracketCol, Offset(w - m, m), Offset(w - m - arm, m), stroke)
                    drawLine(bracketCol, Offset(w - m, m), Offset(w - m, m + arm), stroke)
                    // Bottom-left
                    drawLine(bracketCol, Offset(m, h - m), Offset(m + arm, h - m), stroke)
                    drawLine(bracketCol, Offset(m, h - m), Offset(m, h - m - arm), stroke)
                    // Bottom-right
                    drawLine(bracketCol, Offset(w - m, h - m), Offset(w - m - arm, h - m), stroke)
                    drawLine(bracketCol, Offset(w - m, h - m), Offset(w - m, h - m - arm), stroke)
                }

                if (cfg.crosshairEnabled) {
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val gap = 7.dp.toPx()
                    val tick = 15.dp.toPx()
                    val retCol = hudTint.copy(alpha = 0.85f)
                    drawLine(retCol, Offset(cx - tick, cy), Offset(cx - gap, cy), stroke)
                    drawLine(retCol, Offset(cx + gap, cy), Offset(cx + tick, cy), stroke)
                    drawLine(retCol, Offset(cx, cy - tick), Offset(cx, cy - gap), stroke)
                    drawLine(retCol, Offset(cx, cy + gap), Offset(cx, cy + tick), stroke)
                    drawCircle(retCol, radius = 1.6.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }

        // 2. Top-left honest simulated HUD readout (zero fabricated temperature values)
        if (cfg.hudEnabled) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(
                        text = "SIMULATED THERMAL • ${cfg.palette.shortLabel}${if (cfg.inverted) " [INV]" else ""}",
                        color = hudTint,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    val subjLabel = when {
                        !cfg.segmentationEnabled -> "LUMA MAP ONLY"
                        thermalState.isHumanLockActive -> "SUBJ HEAT PRIOR (${thermalState.detectedSubjectCount})"
                        thermalState.isMaskActive -> "SCENE RADIANT MAP"
                        else -> "GPU FALLBACK"
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "SENS %.2fx  CON %.2fx  •  %s",
                            cfg.sensitivity,
                            cfg.contrast,
                            subjLabel
                        ),
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 3. Vertical Heat Color Scale Bar (right edge; top=HOT, bottom=COLD, no numbers)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HOT",
                color = hudTint,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            val baseColors = cfg.palette.swatchColorsArgb.map { Color(it) }
            val orderedTopToBottom = if (cfg.inverted) baseColors else baseColors.reversed()
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(148.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(orderedTopToBottom))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "COLD",
                color = TextSecondary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // 4. Video recording indicator badge
        if (isRecording) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, VintageRed.copy(alpha = 0.85f)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(recAlpha)
                            .clip(CircleShape)
                            .background(VintageRed)
                    )
                    val mins = recordingSeconds / 60
                    val secs = recordingSeconds % 60
                    Text(
                        text = String.format(Locale.US, "REC %02d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThermalControlsDeck(
    thermalState: ThermalUiState,
    captureMode: CaptureMode,
    isWheelExpanded: Boolean,
    isRecording: Boolean,
    onSelectPalette: (ThermalPalette) -> Unit,
    onUpdateSensitivity: (Float) -> Unit,
    onUpdateContrast: (Float) -> Unit,
    onSelectMode: (CaptureMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onImportMedia: () -> Unit,
    onToggleWheel: () -> Unit
) {
    val view = LocalView.current
    val cfg = thermalState.config
    val accent = if (cfg.monochromeHud) ThermalMonoAccent else ThermalOrange

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. 5-Palette Quick Selector Strip with visual color gradient bars
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ThermalPalette.entries, key = { it.id }) { palette ->
                val selected = cfg.palette == palette
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) accent.copy(alpha = 0.16f) else ThermalSurface,
                    border = BorderStroke(
                        if (selected) 1.4.dp else 1.dp,
                        if (selected) accent else Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectPalette(palette)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val cols = palette.swatchColorsArgb.map { Color(it) }
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(if (cfg.inverted) cols.reversed() else cols))
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = palette.shortLabel,
                            color = if (selected) accent else TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Compact Photo / Video / Import / Wheel row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ThermalSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val photoSelected = captureMode != CaptureMode.VIDEO
                    ThermalModePill(
                        label = "PHOTO",
                        selected = photoSelected,
                        accent = accent,
                        enabled = !isRecording
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectMode(CaptureMode.PHOTO)
                    }
                    ThermalModePill(
                        label = "VIDEO",
                        selected = !photoSelected,
                        accent = VintageRed,
                        enabled = !isRecording
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectMode(CaptureMode.VIDEO)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ThermalSurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isRecording) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onImportMedia()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import into Thermal",
                            tint = TextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "IMPORT",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ThermalSurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenDrawer()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Effects",
                        tint = accent,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp).size(15.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ThermalSurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onToggleWheel()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAM",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (isWheelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Toggle Wheel",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 3. Simulated Thermal Sensitivity + Contrast Dual Slider Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThermalMiniSlider(
                label = "SENSITIVITY",
                valueText = String.format(Locale.US, "%.2fx", cfg.sensitivity),
                value = cfg.sensitivity,
                range = 0.50f..2.00f,
                accent = accent,
                onValueChange = onUpdateSensitivity,
                modifier = Modifier.weight(1f)
            )
            ThermalMiniSlider(
                label = "CONTRAST",
                valueText = String.format(Locale.US, "%.2fx", cfg.contrast),
                value = cfg.contrast,
                range = 0.60f..1.80f,
                accent = accent,
                onValueChange = onUpdateContrast,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThermalModePill(
    label: String,
    selected: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accent else TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ThermalMiniSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = accent,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
internal fun ThermalBottomControlRow(
    selectedPreset: CameraPreset,
    latestThumbnail: ImageBitmap?,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecording: Boolean,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onOpenCollection: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val iconResId = remember(selectedPreset.iconResName) {
        try {
            context.resources.getIdentifier(selectedPreset.iconResName, "drawable", context.packageName)
        } catch (_: Exception) {
            0
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF181A20))
                .border(1.dp, Color(0x38FFFFFF), CircleShape)
                .clickable(enabled = !isRecording) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onGalleryClick()
                },
            contentAlignment = Alignment.Center
        ) {
            if (latestThumbnail != null) {
                Image(
                    bitmap = latestThumbnail,
                    contentDescription = "Gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        TactileShutterButton(
            captureMode = captureMode,
            isCapturing = isCapturing,
            isRecording = isRecording,
            onClick = onShutterClick
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .clickable(
                    enabled = !isRecording,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenCollection()
                },
            contentAlignment = Alignment.Center
        ) {
            if (iconResId != 0) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = selectedPreset.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(60.dp)
                )
            } else {
                Icon(
                    imageVector = if (captureMode == CaptureMode.VIDEO) Icons.Default.Videocam else Icons.Default.PhotoCamera,
                    contentDescription = selectedPreset.name,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
internal fun ThermalEffectsDrawer(
    visible: Boolean,
    config: ThermalVisionConfig,
    onConfigChange: ((ThermalVisionConfig) -> ThermalVisionConfig) -> Unit,
    onImportMedia: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val accent = if (config.monochromeHud) ThermalMonoAccent else ThermalOrange

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "THERMAL VISION CONTROLS",
                                color = accent,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Simulated optical IR false-color system (no physical temperature sensing)",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                        }
                    }

                    ThermalMiniSlider(
                        label = "SENSOR SOFTNESS (OPTICAL BOLOMETER BLUR)",
                        valueText = String.format(Locale.US, "%d%%", (config.sensorSoftness * 100).toInt()),
                        value = config.sensorSoftness,
                        range = 0.0f..1.0f,
                        accent = accent,
                        onValueChange = { v -> onConfigChange { it.copy(sensorSoftness = v) } }
                    )

                    ThermalMiniSlider(
                        label = "FINE ELECTRONIC NOISE",
                        valueText = String.format(Locale.US, "%.3f", config.noiseIntensity),
                        value = config.noiseIntensity,
                        range = 0.0f..0.12f,
                        accent = accent,
                        onValueChange = { v -> onConfigChange { it.copy(noiseIntensity = v) } }
                    )

                    ThermalToggleRow("Hot / Cold Inversion", config.inverted, accent) {
                        onConfigChange { it.copy(inverted = !it.inverted) }
                    }
                    ThermalToggleRow("Human Subject Heat Guidance", config.segmentationEnabled, accent) {
                        onConfigChange { it.copy(segmentationEnabled = !it.segmentationEnabled) }
                    }
                    ThermalToggleRow("Adaptive Thermal Contrast", config.adaptiveContrastEnabled, accent) {
                        onConfigChange { it.copy(adaptiveContrastEnabled = !it.adaptiveContrastEnabled) }
                    }
                    ThermalToggleRow("Thermal HUD Overlay", config.hudEnabled, accent) {
                        onConfigChange { it.copy(hudEnabled = !it.hudEnabled) }
                    }
                    ThermalToggleRow("Monochrome HUD Style", config.monochromeHud, accent) {
                        onConfigChange { it.copy(monochromeHud = !it.monochromeHud) }
                    }
                    ThermalToggleRow("Center Crosshair Reticle", config.crosshairEnabled, accent) {
                        onConfigChange { it.copy(crosshairEnabled = !it.crosshairEnabled) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onDismiss()
                                onImportMedia()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "IMPORT MEDIA",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onReset()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252A36), contentColor = TextPrimary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "RESET",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalToggleRow(
    title: String,
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = accent
            )
        )
    }
}
