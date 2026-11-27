package com.craftcam.app.ui.viewfinder

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.core.content.ContextCompat
import com.craftcam.app.R
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.motion.GolfMotionCapturePhase
import com.craftcam.app.motion.GolfMotionDuration
import com.craftcam.app.motion.GolfMotionUiState
import com.craftcam.app.motion.GolfPlaybackCadence
import com.craftcam.app.ui.components.TactileShutterButton
import com.craftcam.app.ui.theme.SurfaceElevated
import com.craftcam.app.ui.theme.TextMuted
import com.craftcam.app.ui.theme.TextPrimary
import com.craftcam.app.ui.theme.TextSecondary
import com.craftcam.app.ui.theme.VintageAmber
import com.craftcam.app.ui.theme.VintageRed
import java.util.Locale

/**
 * Dedicated retro-tactile Golf (`disp_classic`) top toolbar.
 * Uses original CAMELEO styling and controls without modifying any other preset's top bar.
 */
@Composable
internal fun GolfTopToolbar(
    golfState: GolfMotionUiState,
    flashMode: Int,
    onToggleMotion: () -> Unit,
    onCycleFlash: () -> Unit,
    onOpenSettingsMenu: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Pixel-bordered GOLF identity badge (taps open motion settings drawer)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF17181E),
            border = BorderStroke(1.5.dp, VintageAmber.copy(alpha = 0.72f)),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenDrawer()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (golfState.enabled) VintageAmber else TextMuted)
                )
                Text(
                    text = "GOLF",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }

        // Center: Motion Photo ON/OFF toggle pill
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (golfState.enabled) VintageAmber else Color(0xFF1A1C22),
            border = BorderStroke(1.dp, if (golfState.enabled) VintageAmber else Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleMotion()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MotionPhotosOn,
                    contentDescription = "Toggle Motion Photo",
                    tint = if (golfState.enabled) Color.Black else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (golfState.enabled) "MOTION ON" else "MOTION OFF",
                    color = if (golfState.enabled) Color.Black else TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Right: Flash Control + Existing Settings Menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (flashMode != ImageCapture.FLASH_MODE_OFF) Color(0xFF282218) else Color(0xFF17181E),
                border = BorderStroke(
                    1.dp,
                    if (flashMode != ImageCapture.FLASH_MODE_OFF) VintageAmber.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.16f)
                ),
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCycleFlash()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val flashIcon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash mode",
                        tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) VintageAmber else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenSettingsMenu()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dazz_more),
                    contentDescription = "Camera Settings",
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Unobtrusive corner indicator + subtle hairline progress bar rendered inside Golf viewfinder.
 */
@Composable
internal fun GolfViewfinderOverlay(
    golfState: GolfMotionUiState,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "golf_rec_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    Box(modifier = modifier) {
        // Minimal top-left corner status badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (golfState.isRecordingMotion) Color(0xD61A1012) else Color(0xA6121318),
            border = BorderStroke(
                1.dp,
                when {
                    golfState.phase == GolfMotionCapturePhase.CAPTURING_POST_SHUTTER -> VintageRed.copy(alpha = 0.85f)
                    golfState.phase == GolfMotionCapturePhase.DEVELOPING_MOTION -> VintageAmber.copy(alpha = 0.85f)
                    golfState.enabled -> VintageAmber.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.14f)
                }
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                when (golfState.phase) {
                    GolfMotionCapturePhase.CAPTURING_POST_SHUTTER -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(VintageRed)
                        )
                        val secStr = String.format(Locale.US, "%.1fs", (golfState.remainingMs.coerceAtLeast(0L) / 1000f))
                        Text(
                            text = "MOTION REC • $secStr",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    GolfMotionCapturePhase.DEVELOPING_MOTION -> {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(VintageAmber)
                        )
                        Text(
                            text = "DEVELOPING ${golfState.duration.label}",
                            color = VintageAmber,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    GolfMotionCapturePhase.ARMED -> {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(VintageAmber)
                        )
                        Text(
                            text = "MOTION • ${golfState.duration.label} • ${golfState.playback.targetFps}FPS",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    GolfMotionCapturePhase.IDLE -> {
                        Text(
                            text = if (golfState.enabled) "MOTION • ${golfState.duration.label}" else "STILL",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Subtle recording / development hairline animation along bottom viewfinder frame
        if (golfState.isRecordingMotion) {
            LinearProgressIndicator(
                progress = { golfState.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = if (golfState.phase == GolfMotionCapturePhase.CAPTURING_POST_SHUTTER) VintageRed else VintageAmber,
                trackColor = Color.White.copy(alpha = 0.16f)
            )
        }
    }
}

/**
 * Compact Still/Motion selector + Motion Settings drawer trigger + Wheel visibility toggle.
 */
@Composable
internal fun GolfModeAndSettingsStrip(
    golfState: GolfMotionUiState,
    isWheelExpanded: Boolean,
    onSelectStill: () -> Unit,
    onSelectMotion: () -> Unit,
    onOpenMotionDrawer: () -> Unit,
    onToggleWheel: () -> Unit
) {
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Still / Motion mode selector
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF16181E),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                GolfModeTab(
                    label = "STILL",
                    selected = !golfState.enabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectStill()
                    }
                )
                GolfModeTab(
                    label = "MOTION",
                    selected = golfState.enabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSelectMotion()
                    }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Motion Settings Drawer button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF191B22),
                border = BorderStroke(
                    1.dp,
                    if (golfState.enabled) VintageAmber.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenMotionDrawer()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Motion Settings",
                        tint = if (golfState.enabled) VintageAmber else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${golfState.duration.label} · ${golfState.playback.label.uppercase()}",
                        color = if (golfState.enabled) VintageAmber else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Camera selection wheel toggle
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isWheelExpanded) Color(0xFF232018) else Color(0xFF16181E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleWheel()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CAM",
                        color = if (isWheelExpanded) VintageAmber else TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isWheelExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Toggle camera dial",
                        tint = if (isWheelExpanded) VintageAmber else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GolfModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (selected) VintageAmber else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * Golf Primary Bottom Control Bar: Gallery Thumbnail, Central Shutter Button,
 * Front/Rear Camera Switch, and Golf Camera Preset Icon.
 */
@Composable
internal fun GolfBottomControlRow(
    selectedPreset: CameraPreset,
    latestThumbnail: ImageBitmap?,
    isCapturing: Boolean,
    switchRotationDeg: Float,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
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
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Gallery Thumbnail + compact placeholder spacing to balance right side
        Row(
            modifier = Modifier.width(102.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181A20))
                    .border(1.dp, Color(0x38FFFFFF), CircleShape)
                    .clickable {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. Central Tactile Shutter Button
        TactileShutterButton(
            captureMode = CaptureMode.PHOTO,
            isCapturing = isCapturing,
            isRecording = false,
            onClick = onShutterClick
        )

        // 3. Front/Rear Camera Switch + Current Golf Camera Artwork Icon
        Row(
            modifier = Modifier.width(102.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF17181E))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSwitchCamera()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dazz_switchcamera),
                    contentDescription = "Switch Front/Rear Camera",
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(switchRotationDeg),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clickable(
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
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = selectedPreset.name,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tactile retro-styled Motion Settings Drawer for Golf camera:
 * Duration (1s/2s/3s), Playback (Vintage/Smooth), Flash effect, Loop playback, and Audio.
 */
@Composable
internal fun GolfMotionSettingsDrawer(
    visible: Boolean,
    golfState: GolfMotionUiState,
    onDurationChange: (GolfMotionDuration) -> Unit,
    onPlaybackChange: (GolfPlaybackCadence) -> Unit,
    onFlashEffectChange: (Boolean) -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onAudioChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onAudioChange(true)
        } else {
            onAudioChange(false)
            Toast.makeText(context, "Microphone permission declined; motion will record silent", Toast.LENGTH_SHORT).show()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.5.dp, VintageAmber.copy(alpha = 0.42f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GOLF MOTION SETTINGS",
                                color = VintageAmber,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Original Golf LUT, grain & tone curve remain locked",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    // 1. Duration: 1s / 2s / 3s
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CLIP DURATION",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GolfMotionDuration.entries.forEach { d ->
                                val isDefault = d == GolfMotionDuration.TWO_SEC
                                DrawerChoiceButton(
                                    label = if (isDefault) "${d.label} (DEFAULT)" else d.label,
                                    selected = golfState.duration == d,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onDurationChange(d) }
                                )
                            }
                        }
                    }

                    // 2. Playback: Vintage / Smooth
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PLAYBACK CADENCE",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GolfPlaybackCadence.entries.forEach { p ->
                                DrawerChoiceButton(
                                    label = "${p.label.uppercase()} (${p.targetFps} FPS)",
                                    selected = golfState.playback == p,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPlaybackChange(p) }
                                )
                            }
                        }
                    }

                    // 3. Flash effect / Loop / Audio switches
                    DrawerSwitchRow(
                        title = "Shutter Flash Effect",
                        subtitle = "Brief controlled optical exposure bloom at shutter moment",
                        checked = golfState.flashEffectEnabled,
                        onCheckedChange = onFlashEffectChange
                    )

                    DrawerSwitchRow(
                        title = "Loop Playback",
                        subtitle = "Continuous seamless replay in Darkroom viewer",
                        checked = golfState.loopPlayback,
                        onCheckedChange = onLoopChange
                    )

                    DrawerSwitchRow(
                        title = "Motion Audio",
                        subtitle = "Capture ambient sound before & after shutter press",
                        checked = golfState.audioEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                onAudioChange(false)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onAudioChange(true)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) VintageAmber else Color(0xFF14151B),
        border = BorderStroke(1.dp, if (selected) VintageAmber else Color.White.copy(alpha = 0.16f)),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Color.Black else TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DrawerSwitchRow(
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
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = VintageAmber,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color(0xFF14151B)
            )
        )
    }
}
