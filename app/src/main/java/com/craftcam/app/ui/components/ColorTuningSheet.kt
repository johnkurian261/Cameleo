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
import androidx.compose.foundation.lazy.LazyRow
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
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.ui.theme.*

@Composable
fun ColorTuningSheet(
    visible: Boolean,
    tuning: UserTuningConfig,
    onTuningChange: ((UserTuningConfig) -> UserTuningConfig) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
                    .heightIn(max = 440.dp),
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
                                text = "ANALOG LAB • TUNING",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = VintageAmber
                            )
                            Text(
                                text = "Fine-tune color science, optics and emulsion",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onReset()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp), tint = TextSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RESET", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(top = 8.dp))
                }

                // 1. Exposure Compensation
                item {
                    TuningSlider(
                        label = "EXPOSURE",
                        value = tuning.exposureCompensation,
                        valueDisplay = String.format("%+.1f EV", tuning.exposureCompensation),
                        range = -2.0f..2.0f,
                        onValueChange = { v ->
                            onTuningChange { it.copy(exposureCompensation = v) }
                        }
                    )
                }

                // 2. Warmth / Temperature
                item {
                    TuningSlider(
                        label = "WARMTH (TEMPERATURE)",
                        value = tuning.temperatureOffset,
                        valueDisplay = if (tuning.temperatureOffset > 0.05f) "Golden Amber" else if (tuning.temperatureOffset < -0.05f) "Cool Blue" else "Neutral",
                        range = -0.5f..0.5f,
                        onValueChange = { v ->
                            onTuningChange { it.copy(temperatureOffset = v) }
                        }
                    )
                }

                // 3. Tint
                item {
                    TuningSlider(
                        label = "TINT (GREEN / MAGENTA)",
                        value = tuning.tintOffset,
                        valueDisplay = if (tuning.tintOffset > 0.05f) "Magenta" else if (tuning.tintOffset < -0.05f) "Fuji Green" else "Neutral",
                        range = -0.4f..0.4f,
                        onValueChange = { v ->
                            onTuningChange { it.copy(tintOffset = v) }
                        }
                    )
                }

                // 4. Contrast / Tone Curve
                item {
                    TuningSlider(
                        label = "CONTRAST & CURVE",
                        value = tuning.contrastOffset,
                        valueDisplay = if (tuning.contrastOffset > 0.05f) "Punchy" else if (tuning.contrastOffset < -0.05f) "Milky Fade" else "Preset",
                        range = -0.3f..0.3f,
                        onValueChange = { v ->
                            onTuningChange { it.copy(contrastOffset = v) }
                        }
                    )
                }

                // 5. Saturation / Color Richness
                item {
                    TuningSlider(
                        label = "SATURATION",
                        value = tuning.saturationOffset,
                        valueDisplay = if (tuning.saturationOffset > 0.05f) "Vibrant" else if (tuning.saturationOffset < -0.05f) "Muted" else "Preset",
                        range = -0.5f..0.5f,
                        onValueChange = { v ->
                            onTuningChange { it.copy(saturationOffset = v) }
                        }
                    )
                }

                // 6. Film Grain Intensity
                item {
                    Text("FILM GRAIN INTENSITY", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            GrainOptionPill("OFF (0%)", tuning.grainBoost == 0f) {
                                onTuningChange { it.copy(grainBoost = 0f) }
                            }
                        }
                        item {
                            GrainOptionPill("25%", tuning.grainBoost == 0.25f) {
                                onTuningChange { it.copy(grainBoost = 0.25f) }
                            }
                        }
                        item {
                            GrainOptionPill("50%", tuning.grainBoost == 0.50f) {
                                onTuningChange { it.copy(grainBoost = 0.50f) }
                            }
                        }
                        item {
                            GrainOptionPill("75%", tuning.grainBoost == 0.75f) {
                                onTuningChange { it.copy(grainBoost = 0.75f) }
                            }
                        }
                        item {
                            GrainOptionPill("100% (STD)", tuning.grainBoost == 1.0f) {
                                onTuningChange { it.copy(grainBoost = 1.0f) }
                            }
                        }
                        item {
                            GrainOptionPill("150%", tuning.grainBoost == 1.5f) {
                                onTuningChange { it.copy(grainBoost = 1.5f) }
                            }
                        }
                        item {
                            GrainOptionPill("200% (MAX)", tuning.grainBoost == 2.0f) {
                                onTuningChange { it.copy(grainBoost = 2.0f) }
                            }
                        }
                    }
                }

                // 6b. Developer Debug Controls (Debug builds only)
                if (isDebug) {
                    item {
                        Text("DEBUG • GRAIN ASSET SELECTION", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = VintageAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Switch physical scanned emulsion texture in real-time", fontSize = 10.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val assets = listOf(
                                "PRESET DEFAULT" to null,
                                "Kodak 35mm (1125x1500)" to "grains/grain_dclassic.jpg",
                                "Warm 35mm (2043x3070)" to "grains/grain_ou.jpg",
                                "Push LowLight (2043x3070)" to "grains/grain_ou_lowlight.jpg",
                                "Hasselblad KV88 (3024x3024)" to "grains/grain_kv88.jpg",
                                "CPM35 Negative (2075x3130)" to "grains/grain_cpm35.jpg",
                                "6x7 Portrait (1080x1360)" to "grains/grain_s67.jpg",
                                "Instant Packfilm (1000x1500)" to "grains/grain_instc.jpg",
                                "Disposable 540 (960x540)" to "grains/grain540_01.jpg",
                                "Super 8mm (960x540)" to "grains/grain8mm_01.jpg",
                                "Digicam CCD (1125x1500)" to "grains/grain_classicu.jpg",
                                "Toy Cam Hoga (1080x1080)" to "grains/grain_hoga.jpg"
                            )
                            items(assets.size) { idx ->
                                val (title, path) = assets[idx]
                                val selected = (tuning.debugGrainAsset == path)
                                GrainOptionPill(title, selected) {
                                    onTuningChange { it.copy(debugGrainAsset = path) }
                                }
                            }
                        }
                    }

                    item {
                        Text("DEBUG • GRAIN BLEND MODE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = VintageAmber)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GrainOptionPill("PRESET", tuning.debugGrainBlendMode == null) {
                                onTuningChange { it.copy(debugGrainBlendMode = null) }
                            }
                            GrainOptionPill("OVERLAY", tuning.debugGrainBlendMode == GrainBlendMode.OVERLAY) {
                                onTuningChange { it.copy(debugGrainBlendMode = GrainBlendMode.OVERLAY) }
                            }
                            GrainOptionPill("SCREEN", tuning.debugGrainBlendMode == GrainBlendMode.SCREEN) {
                                onTuningChange { it.copy(debugGrainBlendMode = GrainBlendMode.SCREEN) }
                            }
                            GrainOptionPill("MULTIPLY", tuning.debugGrainBlendMode == GrainBlendMode.MULTIPLY) {
                                onTuningChange { it.copy(debugGrainBlendMode = GrainBlendMode.MULTIPLY) }
                            }
                            GrainOptionPill("NORMAL", tuning.debugGrainBlendMode == GrainBlendMode.NORMAL) {
                                onTuningChange { it.copy(debugGrainBlendMode = GrainBlendMode.NORMAL) }
                            }
                        }
                    }

                    item {
                        Text("DEBUG • PROCEDURAL MICRO-HALIDE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = VintageAmber)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GrainOptionPill("OFF (0%)", tuning.debugProceduralGrainBoost == 0f) {
                                onTuningChange { it.copy(debugProceduralGrainBoost = 0f) }
                            }
                            GrainOptionPill("25%", tuning.debugProceduralGrainBoost == 0.25f) {
                                onTuningChange { it.copy(debugProceduralGrainBoost = 0.25f) }
                            }
                            GrainOptionPill("50%", tuning.debugProceduralGrainBoost == 0.50f) {
                                onTuningChange { it.copy(debugProceduralGrainBoost = 0.50f) }
                            }
                            GrainOptionPill("100%", tuning.debugProceduralGrainBoost == 1.0f) {
                                onTuningChange { it.copy(debugProceduralGrainBoost = 1.0f) }
                            }
                        }
                    }
                }

                // 7. Vignette
                item {
                    Text("LENS VIGNETTE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrainOptionPill("OFF", tuning.vignetteBoost == 0f) {
                            onTuningChange { it.copy(vignetteBoost = 0f) }
                        }
                        GrainOptionPill("SUBTLE (1.0x)", tuning.vignetteBoost == 1.0f) {
                            onTuningChange { it.copy(vignetteBoost = 1.0f) }
                        }
                        GrainOptionPill("DEEP (1.8x)", tuning.vignetteBoost == 1.8f) {
                            onTuningChange { it.copy(vignetteBoost = 1.8f) }
                        }
                    }
                }

                // 8. Optical Toggles (Halation, Light Leaks, Soft Lens, Date Stamp, Dust, Frame Border)
                item {
                    Text("OPTICAL FEATURES", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureToggleRow("Vintage Frame Cutout", tuning.frameBorderEnabled) {
                            onTuningChange { it.copy(frameBorderEnabled = !it.frameBorderEnabled) }
                        }
                        FeatureToggleRow("Film Dust & Patina", tuning.dustEnabled) {
                            onTuningChange { it.copy(dustEnabled = !it.dustEnabled) }
                        }
                        FeatureToggleRow("Red Halation Bloom", tuning.halationEnabled) {
                            onTuningChange { it.copy(halationEnabled = !it.halationEnabled) }
                        }
                        FeatureToggleRow("Canister Light Leaks", tuning.lightLeakEnabled) {
                            onTuningChange { it.copy(lightLeakEnabled = !it.lightLeakEnabled) }
                        }
                        FeatureToggleRow("Dreamy Soft Focus", tuning.softLensDiffusion) {
                            onTuningChange { it.copy(softLensDiffusion = !it.softLensDiffusion) }
                        }
                        FeatureToggleRow("Date Stamp", tuning.dateStampEnabled) {
                            onTuningChange { it.copy(dateStampEnabled = !it.dateStampEnabled) }
                        }
                    }
                }

                // 9. Quick Year Selection
                if (tuning.dateStampEnabled) {
                    item {
                        Text("DATE STAMP YEAR", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GrainOptionPill("CURRENT ('25)", tuning.customYear == null || tuning.customYear == "25") {
                                onTuningChange { it.copy(customYear = "25") }
                            }
                            GrainOptionPill("'98", tuning.customYear == "98") {
                                onTuningChange { it.copy(customYear = "98") }
                            }
                            GrainOptionPill("'94", tuning.customYear == "94") {
                                onTuningChange { it.copy(customYear = "94") }
                            }
                            GrainOptionPill("'84", tuning.customYear == "84") {
                                onTuningChange { it.copy(customYear = "84") }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    valueDisplay: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(valueDisplay, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = VintageAmber)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VintageAmber,
                activeTrackColor = VintageAmber,
                inactiveTrackColor = SurfaceElevated
            )
        )
    }
}

@Composable
private fun GrainOptionPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) VintageAmber else SurfaceElevated)
            .border(1.dp, if (isSelected) VintageAmber else DividerSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) CameraBlack else TextSecondary
        )
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = CameraBlack,
                checkedTrackColor = VintageAmber,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}
