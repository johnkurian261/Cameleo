package com.craftcam.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.computational.ComputationalPhotoConfig
import com.craftcam.app.computational.ComputationalStats
import com.craftcam.app.model.NaturalCameraProfile
import com.craftcam.app.ui.theme.*
import java.util.Locale

@Composable
fun NaturalTuningSheet(
    visible: Boolean,
    profile: NaturalCameraProfile,
    compareWithOriginal: Boolean,
    computationalConfig: ComputationalPhotoConfig = ComputationalPhotoConfig(),
    computationalStats: ComputationalStats? = null,
    sceneConfig: com.craftcam.app.config.SceneProcessingConfig = com.craftcam.app.config.SceneProcessingConfig(),
    sceneAnalysis: com.craftcam.app.scene.SceneAnalysisResult? = null,
    portraitConfig: com.craftcam.app.portrait.PortraitConfig = com.craftcam.app.portrait.PortraitConfig(),
    portraitActive: Boolean = false,
    colorScienceConfig: com.craftcam.app.color.ColorScienceConfig = com.craftcam.app.color.ColorScienceConfig(),
    sensorTelemetry: com.craftcam.app.color.CameraSensorTelemetry? = null,
    onProfileChange: ((NaturalCameraProfile) -> NaturalCameraProfile) -> Unit,
    onCompareChange: (Boolean) -> Unit,
    onComputationalConfigChange: ((ComputationalPhotoConfig) -> ComputationalPhotoConfig) -> Unit = {},
    onSceneConfigChange: ((com.craftcam.app.config.SceneProcessingConfig) -> com.craftcam.app.config.SceneProcessingConfig) -> Unit = {},
    onPortraitConfigChange: ((com.craftcam.app.portrait.PortraitConfig) -> com.craftcam.app.portrait.PortraitConfig) -> Unit = {},
    onColorScienceConfigChange: ((com.craftcam.app.color.ColorScienceConfig) -> com.craftcam.app.color.ColorScienceConfig) -> Unit = {},
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(CameraBlack.copy(alpha = 0.96f))
                .border(1.dp, DividerSubtle, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "NATURAL ENGINE • LAB",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmber
                            )
                            Text(
                                text = "Computational Photography & Sensor Tuning",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onReset()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset S21 Profile",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ─── Computational Telemetry Panel (Section 37) ──────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "COMPUTATIONAL TELEMETRY (DIAGNOSTICS)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VintageAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val stats = computationalStats
                        if (stats != null) {
                            TelemetryRow("Burst Captured", "${stats.frameCount} frames (EV bracketing)")
                            TelemetryRow("ISO / Exposure", "ISO ${stats.sensorIso} • ${(stats.exposureTimeNs / 1_000_000f).toInt()} ms")
                            TelemetryRow("Alignment Shift", String.format(Locale.US, "dx: %+.1f px, dy: %+.1f px", stats.alignDx, stats.alignDy))
                            TelemetryRow("Motion Ratio", String.format(Locale.US, "%.1f%% of scene", stats.motionPercentage))
                            TelemetryRow("HDR Processing", if (stats.isHdrFused) "Mertens Radiometric Fusion" else "Single Frame Anchor")
                            TelemetryRow("Capture Latency", "${stats.captureLatencyMs} ms (shutter tap to last frame)")
                            TelemetryRow("Processing Time", "${stats.processingLatencyMs} ms (alignment + fusion)")
                            TelemetryRow("Working Memory", String.format(Locale.US, "%.1f MB (pooled buffers)", stats.peakMemoryMb))
                            if (stats.fallbackReason != null) {
                                TelemetryRow("Fallback Event", stats.fallbackReason)
                            }
                        } else {
                            Text(
                                text = "Take a photo in Natural mode to view multi-frame telemetry (alignment dx/dy, motion %, latency, peak memory).",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // ─── Multi-Frame HDR Controls (Section 30, 31) ────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceGraphite)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "COMPUTATIONAL CAPTURE CONFIG",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Force Multi-Frame HDR Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Force Multi-Frame HDR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (computationalConfig.forceMultiFrameHdr) "Always captures 3-frame HDR bracket" else "Automatic scene dynamic range trigger",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = computationalConfig.forceMultiFrameHdr,
                                onCheckedChange = { checked ->
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onComputationalConfigChange { it.copy(forceMultiFrameHdr = checked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VintageAmber,
                                    checkedTrackColor = VintageAmber.copy(alpha = 0.4f)
                                )
                            )
                        }

                        HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 8.dp))

                        // Low-Light Computational Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Low-Light Multi-Frame Stack",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Temporal denoising with short exposures",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = computationalConfig.enableLowLightMode,
                                onCheckedChange = { checked ->
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onComputationalConfigChange { it.copy(enableLowLightMode = checked) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VintageAmber,
                                    checkedTrackColor = VintageAmber.copy(alpha = 0.4f)
                                )
                            )
                        }

                        HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 8.dp))

                        // Burst Frame Count Selector (2, 3, 5 frames)
                        Text(
                            text = "Burst Frame Count: ${computationalConfig.burstFrameCount} frames",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(2, 3, 5).forEach { count ->
                                val selected = computationalConfig.burstFrameCount == count
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onComputationalConfigChange { it.copy(burstFrameCount = count) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count Frames",
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Portrait Depth & Computational Bokeh (Phase 6) ─────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PORTRAIT DEPTH & COMPUTATIONAL BOKEH",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmber
                            )
                            Text(
                                text = if (portraitActive) "ENGAGED" else "READY",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (portraitActive) VintageAmber else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Monocular depth map, subject segmentation, hair/glasses matting & anti-bleed bokeh",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Portrait Mode Selector (OFF / AUTO / ON)
                        Text(
                            text = "Portrait Processing Mode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.craftcam.app.portrait.PortraitMode.values().forEach { mode ->
                                val selected = portraitConfig.portraitMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onPortraitConfigChange { it.copy(portraitMode = mode) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bokeh Strength Selector (NATURAL / SUBTLE / STRONG)
                        Text(
                            text = "Bokeh Strength (Aperture Simulation)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                com.craftcam.app.portrait.BokehStrength.NATURAL to "Natural f/4",
                                com.craftcam.app.portrait.BokehStrength.SUBTLE to "Subtle f/2.8",
                                com.craftcam.app.portrait.BokehStrength.STRONG to "Strong f/1.8"
                            ).forEach { (strength, label) ->
                                val selected = portraitConfig.bokehStrength == strength
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onPortraitConfigChange { it.copy(bokehStrength = strength) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Diagnostic Mask Visualization (Section 49)
                        Text(
                            text = "Diagnostic Visualization (Debug View)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                com.craftcam.app.portrait.DebugMaskMode.NONE to "Photo",
                                com.craftcam.app.portrait.DebugMaskMode.DEPTH to "Depth",
                                com.craftcam.app.portrait.DebugMaskMode.SEGMENTATION to "Seg",
                                com.craftcam.app.portrait.DebugMaskMode.FINAL_MASK to "Mask",
                                com.craftcam.app.portrait.DebugMaskMode.BLUR_MAP to "Blur"
                            ).forEach { (mode, label) ->
                                val selected = portraitConfig.debugMaskMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onPortraitConfigChange { it.copy(debugMaskMode = mode) }
                                        }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Advanced Color Science & Sensor Profiles (Phase 7) ─────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COLOR SCIENCE & SENSOR PROFILES",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmber
                            )
                            Text(
                                text = colorScienceConfig.targetColorSpace.name,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calibrated sensor matrices, illuminants, tone curve & selective gamut protection",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Color Profile Selection Chips
                        Text(
                            text = "Color Profile",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.craftcam.app.color.ColorProfileType.values().forEach { profileType ->
                                val selected = colorScienceConfig.activeProfileType == profileType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onColorScienceConfigChange { it.copy(activeProfileType = profileType) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profileType.name,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Color Space Chips (sRGB vs Display P3)
                        Text(
                            text = "Output Color Space Gamut",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.craftcam.app.color.ColorSpaceType.values().forEach { cs ->
                                val selected = colorScienceConfig.targetColorSpace == cs
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) VintageAmber else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onColorScienceConfigChange { it.copy(targetColorSpace = cs) }
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cs.displayName,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) VintageAmber else TextMuted
                                    )
                                }
                            }
                        }

                        // Sensor Calibration Telemetry Summary
                        if (sensorTelemetry != null) {
                            HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "CAMERA SENSOR CALIBRATION (DIAGNOSTICS)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TelemetryRow("Camera / Facing", "Cam ${sensorTelemetry.cameraId} • ${sensorTelemetry.facing}")
                            TelemetryRow("Sensor Array", sensorTelemetry.colorFilterArrangement)
                            TelemetryRow("Sensor Physical Size", sensorTelemetry.sensorPhysicalSize)
                            TelemetryRow("Ref Illuminants", "${sensorTelemetry.referenceIlluminant1} / ${sensorTelemetry.referenceIlluminant2}")
                            TelemetryRow("RAW DNG Support", if (sensorTelemetry.hasRawSupport) "Available (RAW_SENSOR)" else "YUV/JPEG Capture")
                        }
                    }
                }

                // ─── A/B Comparison Toggle ──────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "A/B COMPARISON MODE (DEBUG)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VintageAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Split output: Left = Original Camera Capture, Right = CAMELEO Natural",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!compareWithOriginal) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                    .border(
                                        width = 1.dp,
                                        color = if (!compareWithOriginal) VintageAmber else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onCompareChange(false)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Full Natural",
                                    fontSize = 11.sp,
                                    fontWeight = if (!compareWithOriginal) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!compareWithOriginal) VintageAmber else TextMuted
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (compareWithOriginal) VintageAmber.copy(alpha = 0.25f) else SurfaceCard)
                                    .border(
                                        width = 1.dp,
                                        color = if (compareWithOriginal) VintageAmber else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onCompareChange(true)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Split A/B Compare",
                                    fontSize = 11.sp,
                                    fontWeight = if (compareWithOriginal) FontWeight.Bold else FontWeight.Normal,
                                    color = if (compareWithOriginal) VintageAmber else TextMuted
                                )
                            }
                        }
                    }
                }

                // ─── Exposure Bias ──────────────────────────────────────────
                item {
                    NaturalSliderItem(
                        title = "Exposure Bias",
                        subtitle = "Hardware highlight-protection preference",
                        value = profile.exposureBias,
                        valueDisplay = String.format(Locale.US, "%+.1f EV", profile.exposureBias),
                        valueRange = -1.5f..0.5f,
                        onValueChange = { v -> onProfileChange { it.copy(exposureBias = v) } }
                    )
                }

                // ─── Highlight Rolloff ──────────────────────────────────────
                item {
                    NaturalSliderItem(
                        title = "Highlight Shoulder Compression",
                        subtitle = "Prevents blown skies/windows with asymptotic roll-off",
                        value = profile.highlightRollOff,
                        valueDisplay = String.format(Locale.US, "%.0f%%", profile.highlightRollOff * 100f),
                        valueRange = 0.0f..1.0f,
                        onValueChange = { v -> onProfileChange { it.copy(highlightRollOff = v) } }
                    )
                }

                // ─── Shadow Lift ────────────────────────────────────────────
                item {
                    NaturalSliderItem(
                        title = "Rational Shadow Toe Lift",
                        subtitle = "Gentle shadow opening with preserved true blacks",
                        value = profile.shadowLift,
                        valueDisplay = String.format(Locale.US, "%.0f%%", profile.shadowLift * 100f),
                        valueRange = 0.0f..0.50f,
                        onValueChange = { v -> onProfileChange { it.copy(shadowLift = v) } }
                    )
                }

                // ─── Restrained Sharpening ──────────────────────────────────
                item {
                    NaturalSliderItem(
                        title = "Edge-Aware Sharpening",
                        subtitle = "Subtle high-frequency detail (dampened on faces)",
                        value = profile.sharpening,
                        valueDisplay = String.format(Locale.US, "%.0f%%", profile.sharpening * 100f),
                        valueRange = 0.0f..0.80f,
                        onValueChange = { v -> onProfileChange { it.copy(sharpening = v) } }
                    )
                }

                // ─── Microcontrast / Detail ─────────────────────────────────
                item {
                    NaturalSliderItem(
                        title = "Local Contrast (Microcontrast)",
                        subtitle = "Restrained depth enhancement without HDR halos",
                        value = profile.localContrast,
                        valueDisplay = String.format(Locale.US, "%.0f%%", profile.localContrast * 100f),
                        valueRange = 0.0f..0.40f,
                        onValueChange = { v -> onProfileChange { it.copy(localContrast = v) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = TextMuted)
        Text(text = value, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun NaturalSliderItem(
    title: String,
    subtitle: String,
    value: Float,
    valueDisplay: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceGraphite)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Text(
                text = valueDisplay,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VintageAmber
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = VintageAmber,
                activeTrackColor = VintageAmber,
                inactiveTrackColor = DividerSubtle
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
