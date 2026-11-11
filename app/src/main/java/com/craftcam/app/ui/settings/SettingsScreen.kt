package com.craftcam.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftcam.app.R
import com.craftcam.app.config.AppSettingsStore
import com.craftcam.app.config.performCameleoHaptic

/**
 * Authentic Dazz Cam-style Settings screen.
 * Features dark card grouping, coral accent switches, storage advisory banner,
 * and social media hashtag footer.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsStore = remember(context) { AppSettingsStore.get(context) }
    val settings by settingsStore.state.collectAsState()
    val haptics = settings.tactileHaptics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top App Bar ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Circular Back Button on Left
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1E))
                    .clickable {
                        view.performCameleoHaptic(haptics)
                        onNavigateBack()
                    }
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Centered Title
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ── Settings Scrollable Content ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Language
            item {
                SettingsGroupCard {
                    SettingsNavRow(
                        title = "Language",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "English (System Default)", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Card 2: Main Camera Toggles & Options (11 items)
            item {
                SettingsGroupCard {
                    SettingsToggleRow(
                        title = "Export to Album",
                        checked = settings.exportToAlbum,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setExportToAlbum(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Keep Original Photos",
                        checked = settings.keepOriginalPhotos,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setKeepOriginalPhotos(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Save Location",
                        checked = settings.saveLocation,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setSaveLocation(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Mirror Front Camera",
                        checked = settings.mirrorFrontCamera,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setMirrorFrontCamera(it)
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Live Photo Cover",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Key Frame Default", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsValueNavRow(
                        title = "ISO & Shutter Slider",
                        value = "Segmented",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Segmented Haptic Steps Active", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Assistive grid",
                        checked = settings.assistiveGrid,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setAssistiveGrid(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Level",
                        checked = settings.level,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setLevel(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Shutter Vibration",
                        checked = settings.shutterVibration,
                        onCheckedChange = {
                            view.performCameleoHaptic(it)
                            settingsStore.setShutterVibration(it)
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Timestamp Format",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Orange Digital 7-Segment (Classic)", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Preserve Setting",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Camera & Lens Preserved Across Sessions", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Card 3: Storage
            item {
                SettingsGroupCard {
                    SettingsNavRow(
                        title = "Storage",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "DCIM/Cameleo (Local Storage)", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Card 4: Local Storage Notice Advisory Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF381A18))
                        .border(1.dp, Color(0xFF4E2421), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👉",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = "Photos and videos are stored within the Cameleo App locally. Please backup when necessary.",
                            color = Color(0xFFF5D6D6),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Card 5: Share & Feedback
            item {
                SettingsGroupCard {
                    SettingsNavRow(
                        title = "Share with friends",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Experience authentic vintage 35mm film and CCD cameras with Cameleo: https://github.com/craftcam/cameleo")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Cameleo"))
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Send Feedback",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:support@cameleo.camera?subject=Cameleo%20Feedback")
                            }
                            try {
                                context.startActivity(emailIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Email support@cameleo.camera", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Recommended apps",
                        checked = settings.recommendedApps,
                        onCheckedChange = {
                            view.performCameleoHaptic(haptics)
                            settingsStore.setRecommendedApps(it)
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Write Review",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Thank you for supporting Cameleo!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Card 6: Privacy & Terms
            item {
                SettingsGroupCard {
                    SettingsNavRow(
                        title = "Privacy",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Cameleo processes all photos 100% on-device. No images are uploaded to the cloud.", Toast.LENGTH_LONG).show()
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        title = "Terms of Use",
                        onClick = {
                            view.performCameleoHaptic(haptics)
                            Toast.makeText(context, "Cameleo Vintage Camera Terms of Use", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Footer Area
            item {
                Spacer(modifier = Modifier.height(12.dp))

                val hashtagText = buildAnnotatedString {
                    append("Please use the hashtag ")
                    withStyle(style = SpanStyle(color = Color(0xFFE25241), fontWeight = FontWeight.SemiBold)) {
                        append("#dazzcam")
                    }
                    append(" when you post content on social media.")
                }

                Text(
                    text = hashtagText,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Image(
                    painter = painterResource(id = R.drawable.dazz_logo_footer),
                    contentDescription = "Dazz",
                    colorFilter = ColorFilter.tint(Color(0xFF2C2C2E)),
                    modifier = Modifier
                        .height(34.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "VERSION 2.0.0",
                    color = Color(0xFF48484A),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Clean rounded card container for grouped iOS/Dazz-style settings items.
 */
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(content = content)
    }
}

/**
 * Standard toggle row with authentic Dazz Cam coral switch coloring.
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE25241), // Authentic Dazz Cam Coral Switch
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * Navigation item row with a trailing chevron (>).
 */
@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Navigation item row with an explicit current value label on the right (e.g. "Segmented >").
 */
@Composable
private fun SettingsValueNavRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 15.sp,
                color = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Subtle horizontal divider between card rows.
 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = Color(0xFF2C2C2E),
        thickness = 0.5.dp
    )
}
