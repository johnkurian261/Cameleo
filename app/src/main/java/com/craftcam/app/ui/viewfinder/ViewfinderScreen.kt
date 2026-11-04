package com.craftcam.app.ui.viewfinder

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.CameraManager
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.gl.GLViewfinderRenderer
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
import com.craftcam.app.util.SoundManager
import com.craftcam.app.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.craftcam.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ViewfinderScreen(
    cameraManager: CameraManager,
    selectedPreset: CameraPreset,
    onPresetChange: (CameraPreset) -> Unit,
    onNavigateToCollection: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val uiState by cameraManager.uiState.collectAsState()

    LaunchedEffect(selectedPreset) {
        cameraManager.setPreset(selectedPreset)
    }

    LaunchedEffect(Unit) {
        SoundManager.initialize(context)
    }

    var showTuningSheet by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var switchRotation by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(targetValue = switchRotation, label = "switch_rot")
    val isTuned = uiState.tuning.temperatureOffset != 0f || 
                  uiState.tuning.tintOffset != 0f || 
                  uiState.tuning.contrastOffset != 0f || 
                  uiState.tuning.saturationOffset != 0f || 
                  uiState.tuning.exposureCompensation != 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        // 1. Clean, 100% native 4:3 optical viewfinder (zero distortion, full sensor FOV)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    cameraManager.initialize(lifecycleOwner, this)
                }
            }
        )

        // 2. Viewfinder Overlay (Vintage Simulation, Matte, Grid, Tap-To-Focus)
        ViewfinderOverlay(
            modifier = Modifier.fillMaxSize(),
            preset = selectedPreset,
            aspectRatio = uiState.aspectRatio,
            showGrid = uiState.showGrid,
            onTapFocus = { tapX, tapY ->
                cameraManager.tapToFocus(tapX, tapY)
            }
        )

        // 3. Top Control Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            TopControlBar(
                flashMode = uiState.flashMode,
                aspectRatio = uiState.aspectRatio,
                timerSeconds = uiState.timerSeconds,
                showGrid = uiState.showGrid,
                onFlashToggle = {
                    val next = when (uiState.flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    cameraManager.setFlashMode(next)
                },
                onAspectRatioCycle = {
                    val next = when (uiState.aspectRatio) {
                        AspectRatioMode.RATIO_4_3 -> AspectRatioMode.RATIO_1_1
                        AspectRatioMode.RATIO_1_1 -> AspectRatioMode.RATIO_3_2
                        AspectRatioMode.RATIO_3_2 -> AspectRatioMode.RATIO_16_9
                        AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
                    }
                    cameraManager.setAspectRatio(next)
                },
                onTimerCycle = {
                    val next = when (uiState.timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                    cameraManager.setTimerSeconds(next)
                },
                onGridToggle = { cameraManager.toggleGrid() },
                isTuned = isTuned,
                onTuneClick = {
                    showTuningSheet = !showTuningSheet
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                },
                onSettingsClick = onNavigateToSettings
            )

            // Vintage camera model badge or Recording Indicator
            if (uiState.isRecording) {
                val mins = uiState.recordingSeconds / 60
                val secs = uiState.recordingSeconds % 60
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VintageRed.copy(alpha = 0.90f))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "● REC  ${String.format("%02d:%02d", mins, secs)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "${selectedPreset.name.uppercase()} • ${selectedPreset.year}",
                        color = VintageAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Timer Countdown Overlay
        countdown?.let { count ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    color = VintageAmber,
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Bottom Controls Section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CameraBlack.copy(alpha = 0.90f))
                .padding(bottom = 24.dp)
        ) {
            // Mode Switcher (PHOTO | VIDEO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceElevated)
                        .padding(3.dp)
                ) {
                    Row {
                        CaptureModeTab(
                            title = "PHOTO",
                            isSelected = uiState.captureMode == CaptureMode.PHOTO,
                            onClick = {
                                if (!uiState.isRecording) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    cameraManager.setCaptureMode(CaptureMode.PHOTO)
                                }
                            }
                        )
                        CaptureModeTab(
                            title = "VIDEO",
                            isSelected = uiState.captureMode == CaptureMode.VIDEO,
                            onClick = {
                                if (!uiState.isRecording) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    cameraManager.setCaptureMode(CaptureMode.VIDEO)
                                }
                            }
                        )
                    }
                }
            }

            // Camera Presets Carousel Wheel
            CameraWheelSelector(
                presets = PresetCatalog.allPresets,
                selectedPreset = selectedPreset,
                onPresetSelected = onPresetChange,
                onOpenCollection = onNavigateToCollection
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Actions Bar (Gallery, Shutter, Switch Camera)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Shortcut
                IconButton(
                    onClick = onNavigateToGallery,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, DividerSubtle, CircleShape)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = TextPrimary
                    )
                }

                // Shutter / Record Button
                TactileShutterButton(
                    captureMode = uiState.captureMode,
                    isCapturing = uiState.isCapturing || countdown != null,
                    isRecording = uiState.isRecording,
                    onClick = {
                        if (uiState.captureMode == CaptureMode.VIDEO) {
                            // VIDEO RECORDING TOGGLE
                            if (uiState.isRecording) {
                                cameraManager.stopRecording()
                            } else {
                                cameraManager.startRecording(
                                    onVideoSaved = { uri ->
                                        Toast.makeText(context, "Video saved to Cameleo album", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Video error: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        } else {
                            // STILL PHOTO CAPTURE
                            scope.launch {
                                if (uiState.timerSeconds > 0) {
                                    for (i in uiState.timerSeconds downTo 1) {
                                        countdown = i
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        delay(1000)
                                    }
                                    countdown = null
                                }

                                cameraManager.captureAndProcessPhoto(
                                    preset = selectedPreset,
                                    aspectRatio = uiState.aspectRatio,
                                    mirrorFrontCamera = uiState.lensFacing == CameraSelector.LENS_FACING_FRONT,
                                    onPhotoSaved = { uri ->
                                        Toast.makeText(context, "Saved to Cameleo album", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { exc ->
                                        Toast.makeText(context, "Capture failed: $exc", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                )

                // Lens Switcher (Rear / Front)
                IconButton(
                    onClick = {
                        if (!uiState.isRecording) {
                            switchRotation += 180f
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            cameraManager.switchCamera()
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, DividerSubtle, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = TextPrimary,
                        modifier = Modifier.rotate(animatedRotation)
                    )
                }
            }
        }

        // 6. Color Tuning & Analog Lab Bottom Sheet
        ColorTuningSheet(
            visible = showTuningSheet,
            tuning = uiState.tuning,
            onTuningChange = { cameraManager.updateTuning(it) },
            onReset = { cameraManager.resetTuning() },
            onDismiss = { showTuningSheet = false }
        )
    }
}

@Composable
private fun CaptureModeTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) VintageAmber else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) CameraBlack else TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
