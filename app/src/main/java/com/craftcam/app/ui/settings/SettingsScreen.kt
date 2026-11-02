package com.craftcam.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    var mirrorFrontCamera by remember { mutableStateOf(true) }
    var saveFilmNegatives by remember { mutableStateOf(true) }
    var highResolutionExport by remember { mutableStateOf(true) }
    var tactileHaptics by remember { mutableStateOf(true) }
    var preserveExif by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Capture Engine
            item {
                SectionHeader("CAMERA & HARDWARE CAPTURE")
            }
            item {
                SettingsCard {
                    SettingsToggle(
                        title = "Mirror Front Camera",
                        subtitle = "Flip front camera preview and captured selfies horizontally",
                        checked = mirrorFrontCamera,
                        onCheckedChange = { mirrorFrontCamera = it }
                    )
                    HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 12.dp))
                    SettingsToggle(
                        title = "Save Film Negatives",
                        subtitle = "Preserve raw un-graded captures for darkroom redevelopment",
                        checked = saveFilmNegatives,
                        onCheckedChange = { saveFilmNegatives = it }
                    )
                    HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 12.dp))
                    SettingsToggle(
                        title = "High-Resolution Export",
                        subtitle = "Maximize hardware sensor resolution without downscaling",
                        checked = highResolutionExport,
                        onCheckedChange = { highResolutionExport = it }
                    )
                }
            }

            // Section 2: Vintage Simulation
            item {
                SectionHeader("ANALOG SIMULATION & HAPTICS")
            }
            item {
                SettingsCard {
                    SettingsToggle(
                        title = "Tactile Shutter Haptics",
                        subtitle = "Simulate mechanical camera shutter spring and vibration clicks",
                        checked = tactileHaptics,
                        onCheckedChange = { tactileHaptics = it }
                    )
                    HorizontalDivider(color = DividerSubtle, modifier = Modifier.padding(vertical = 12.dp))
                    SettingsToggle(
                        title = "Preserve Camera EXIF",
                        subtitle = "Embed simulated camera body and film stock name in photo metadata",
                        checked = preserveExif,
                        onCheckedChange = { preserveExif = it }
                    )
                }
            }

            // Section 3: About
            item {
                SectionHeader("ABOUT CAMELEO")
            }
            item {
                SettingsCard {
                    Text(
                        text = "Cameleo for Android",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0 (Build 2026.11)",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VintageAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A premium analog camera application recreating the organic feeling of 35mm film, early 2000s CCD digicams, party disposables, and vintage camcorders using real-time OpenGL ES 3.0 shaders and CameraX.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CameraBlack,
                checkedTrackColor = VintageAmber,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceElevated
            )
        )
    }
}

