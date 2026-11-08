package com.craftcam.app.ui.viewfinder

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.craftcam.app.R
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.craftcam.app.camera.profile.LensType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.camera.CameraManager
import com.craftcam.app.camera.CaptureMode
import com.craftcam.app.camera.IS_VIDEO_FEATURE_PAUSED
import com.craftcam.app.captureframe.CaptureFrameOverlay
import com.craftcam.app.gl.GLViewfinderRenderer
import com.craftcam.app.model.*
import com.craftcam.app.ui.components.*
import com.craftcam.app.ui.theme.*
import com.craftcam.app.util.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Redesigned Minimal Vintage Camera Capture Page for CAMELEO.
 * Provides a pure, photographic, dark, distraction-free shooting experience
 * inspired by classic mechanical cameras and minimalist vintage camera apps.
 */
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
        if (IS_VIDEO_FEATURE_PAUSED) {
            cameraManager.setCaptureMode(CaptureMode.PHOTO)
        }
        SoundManager.initialize(context)
    }

    DisposableEffect(Unit) {
        cameraManager.setCameraActive(true)
        onDispose {
            cameraManager.setCameraActive(false)
        }
    }

    var showControlMenu by remember { mutableStateOf(false) }
    var showTuningSheet by remember { mutableStateOf(false) }
    var showNaturalTuningSheet by remember { mutableStateOf(false) }
    var showLensSheet by remember { mutableStateOf(false) }
    var showExposureSlider by remember { mutableStateOf(false) }
    var latestThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
            glSurfaceViewRef = null
        }
    }

    LaunchedEffect(Unit) {
        latestThumbnail = loadLatestCameleoThumbnail(context)
    }

    var countdown by remember { mutableStateOf<Int?>(null) }
    var switchRotation by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(targetValue = switchRotation, label = "switch_rot")

    val isTuned = when (uiState.engineMode) {
        CameraEngineMode.NATURAL -> uiState.naturalProfile != NaturalCameraProfile.S21_PROFILE || uiState.debugCompareOriginal
        CameraEngineMode.PRO -> uiState.proConfig.activeControl != null || uiState.proConfig.focusPeakingEnabled || uiState.proConfig.zebraEnabled
        CameraEngineMode.FILM -> uiState.tuning.temperatureOffset != 0f ||
                uiState.tuning.tintOffset != 0f ||
                uiState.tuning.contrastOffset != 0f ||
                uiState.tuning.saturationOffset != 0f ||
                uiState.tuning.exposureCompensation != 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 1. Minimal Top Bar: Camera Switch (Left) & Menu Button (Right) ─────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-left: Camera Switch Button (Rear / Front) - Pure floating icon, no bubble
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!uiState.isRecording) {
                                switchRotation += 180f
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                cameraManager.switchCamera()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dazz_switchcamera),
                        contentDescription = "Switch Camera",
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(animatedRotation),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top-right: Subtle settings/more menu button - Pure floating icon, no bubble
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showControlMenu = !showControlMenu
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_dazz_more),
                        contentDescription = "Menu",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // ── 2. Framed Viewfinder Window (Does NOT touch screen borders) ──────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    // OpenGL Live Viewfinder
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                            val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
                            val supportsGLES3 = glEsVersion >= 0x00030000

                            GLSurfaceView(ctx).apply {
                                if (supportsGLES3) {
                                    setEGLContextClientVersion(3)
                                } else {
                                    setEGLContextClientVersion(2)
                                }
                                var rendererRef: GLViewfinderRenderer? = null
                                val renderer = GLViewfinderRenderer(ctx, this) { surfaceTexture ->
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        cameraManager.initializeWithGLSurface(
                                            lifecycleOwner = lifecycleOwner,
                                            surfaceTexture = surfaceTexture
                                        ) { rot, isFront, bufW, bufH ->
                                            rendererRef?.setCameraOrientation(rot, isFront, bufW, bufH)
                                        }
                                    }
                                }
                                rendererRef = renderer
                                setRenderer(renderer)
                                cameraManager.setGLRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                onResume()
                                glSurfaceViewRef = this
                            }
                        },
                        update = { _ ->
                            cameraManager.setPreset(selectedPreset)
                        },
                        onRelease = { glSurfaceView ->
                            glSurfaceView.onPause()
                            if (glSurfaceViewRef == glSurfaceView) {
                                glSurfaceViewRef = null
                            }
                        }
                    )

                    // Tap-To-Focus, Exposure Drag & Grid Overlay
                    ViewfinderOverlay(
                        modifier = Modifier.fillMaxSize(),
                        preset = selectedPreset,
                        tuning = uiState.tuning,
                        aspectRatio = uiState.aspectRatio,
                        showGrid = uiState.showGrid,
                        isNaturalMode = uiState.engineMode != CameraEngineMode.FILM,
                        exposureIndex = uiState.exposureIndex,
                        minExposureIndex = uiState.minExposureIndex,
                        maxExposureIndex = uiState.maxExposureIndex,
                        exposureStep = uiState.exposureStep,
                        onTapFocus = { tapX, tapY ->
                            cameraManager.tapToFocus(tapX, tapY)
                        },
                        onExposureChange = { newIdx ->
                            cameraManager.setExposureIndex(newIdx)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    )

                    // Centered, Resizable Camera Framing Composition Window (bypassed for circular Fisheye)
                    if (uiState.shootingState.selectedLens.type != com.craftcam.app.camera.profile.LensType.FISHEYE_F) {
                        CaptureFrameOverlay(
                            modifier = Modifier.fillMaxSize(),
                            state = uiState.captureFrameState,
                            aspectRatio = uiState.aspectRatio,
                            onResize = { scale ->
                                val previewAspect = 1f / uiState.aspectRatio.ratio
                                cameraManager.resizeCaptureFrame(scale, previewAspect)
                            }
                        )
                    }

                    // Developing 3D Wigglegram Overlay
                    if (uiState.threeDProgressMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xCC1A1C22))
                                    .border(1.dp, Color(0x5500E5FF), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = uiState.threeDProgressMessage ?: "DEVELOPING 3D...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Multi-Lens Parallax Fusion",
                                    color = Color(0xAAFFFFFF),
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. Pro Mode Manual Controls (only when PRO engine mode is active) ─────
            if (uiState.engineMode == CameraEngineMode.PRO) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    ProControlsBar(
                        modifier = Modifier.fillMaxWidth(),
                        config = uiState.proConfig,
                        caps = uiState.hardwareCapabilities,
                        liveIso = uiState.liveIso,
                        liveExposureNs = uiState.liveExposureNs,
                        liveFocusDistance = uiState.liveFocusDistance,
                        onConfigChange = { cameraManager.updateProConfig { _ -> it } },
                        onTabSelected = { cameraManager.setProActiveTab(it) }
                    )
                }
            }

            // ── 4. Bottom Controls: Information Pills + Shutter Control Bar ───────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Brightness / Exposure Slider (Dazz-style ☀️ slider)
                AnimatedVisibility(
                    visible = showExposureSlider,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    QuickExposureSlider(
                        exposureIndex = uiState.exposureIndex,
                        minExposureIndex = uiState.minExposureIndex,
                        maxExposureIndex = uiState.maxExposureIndex,
                        exposureStep = uiState.exposureStep,
                        onExposureChange = { newIdx ->
                            cameraManager.setExposureIndex(newIdx)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        onReset = {
                            cameraManager.setExposureIndex(0)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        onClose = { showExposureSlider = false }
                    )
                }

                // Tactile Quick Controls Bar directly above the capture button
                CameraInfoPills(
                    shootingState = uiState.shootingState,
                    exposureIndex = uiState.exposureIndex,
                    exposureStep = uiState.exposureStep,
                    isExposureSliderOpen = showExposureSlider,
                    flashMode = uiState.flashMode,
                    timerSeconds = uiState.timerSeconds,
                    showGrid = uiState.showGrid,
                    isTuned = isTuned,
                    engineMode = uiState.engineMode,
                    onFlashCycle = {
                        val nextMode = when (uiState.flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                        cameraManager.setFlashMode(nextMode)
                    },
                    onToggleExposure = {
                        showExposureSlider = !showExposureSlider
                        if (showExposureSlider) {
                            showLensSheet = false
                        }
                    },
                    onOpenTuning = {
                        when (uiState.engineMode) {
                            CameraEngineMode.PRO -> cameraManager.setProActiveTab(ProControlTab.TOOLS)
                            CameraEngineMode.NATURAL -> showNaturalTuningSheet = true
                            CameraEngineMode.FILM -> showTuningSheet = true
                        }
                    },
                    onTimerCycle = {
                        val nextTimer = when (uiState.timerSeconds) {
                            0 -> 3
                            3 -> 10
                            else -> 0
                        }
                        cameraManager.setTimerSeconds(nextTimer)
                    },
                    onToggleGrid = {
                        cameraManager.toggleGrid()
                    },
                    onOpenLensSheet = {
                        showLensSheet = !showLensSheet
                        if (showLensSheet) {
                            showExposureSlider = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Bottom Actions Bar (Gallery Thumbnail, White Shutter Button, Current Camera Icon)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Gallery Thumbnail
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF181A20))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onNavigateToGallery()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (latestThumbnail != null) {
                            Image(
                                bitmap = latestThumbnail!!,
                                contentDescription = "Gallery",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    val isThreeD = selectedPreset.id == "analog_cinema_film" || 
                                   selectedPreset.id == "d3d" || 
                                   selectedPreset.id == "d_3d" || 
                                   selectedPreset.name.contains("3D", ignoreCase = true) ||
                                   uiState.captureMode == CaptureMode.MODE_3D

                    // 2. Large Dominant Circular Shutter Button
                    TactileShutterButton(
                        captureMode = if (isThreeD) CaptureMode.MODE_3D else CaptureMode.PHOTO,
                        isCapturing = uiState.isCapturing || countdown != null,
                        isRecording = false,
                        onClick = {
                            scope.launch {
                                if (uiState.timerSeconds > 0) {
                                    for (i in uiState.timerSeconds downTo 1) {
                                        countdown = i
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        delay(1000)
                                    }
                                    countdown = null
                                }

                                if (isThreeD) {
                                    cameraManager.captureAndProcessThreeDPhoto(
                                        preset = selectedPreset,
                                        aspectRatio = uiState.aspectRatio,
                                        mirrorFrontCamera = uiState.lensFacing == CameraSelector.LENS_FACING_FRONT,
                                        onVideoSaved = { uri ->
                                            Toast.makeText(context, "3D Wigglegram saved to Cameleo album", Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                latestThumbnail = loadThumbnailForUri(context, uri) ?: loadLatestCameleoThumbnail(context)
                                            }
                                        },
                                        onError = { exc ->
                                            Toast.makeText(context, "3D capture failed: $exc", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    cameraManager.captureAndProcessPhoto(
                                        preset = selectedPreset,
                                        aspectRatio = uiState.aspectRatio,
                                        mirrorFrontCamera = uiState.lensFacing == CameraSelector.LENS_FACING_FRONT,
                                        onPhotoSaved = { uri ->
                                            Toast.makeText(context, "Saved to Cameleo album", Toast.LENGTH_SHORT).show()
                                            scope.launch {
                                                latestThumbnail = loadThumbnailForUri(context, uri) ?: loadLatestCameleoThumbnail(context)
                                            }
                                        },
                                        onError = { exc ->
                                            Toast.makeText(context, "Capture failed: $exc", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    )

                    // 3. Current Camera Icon (Tapping opens camera collection)
                    val iconResId = remember(selectedPreset.iconResName) {
                        try {
                            context.resources.getIdentifier(selectedPreset.iconResName, "drawable", context.packageName)
                        } catch (e: Exception) {
                            0
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onNavigateToCollection()
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
                                Icons.Default.PhotoCamera,
                                contentDescription = selectedPreset.name,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 7. Timer Countdown Overlay ────────────────────────────────────────────
        countdown?.let { count ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    color = Color.White,
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── 8. Floating Three-Dot Control Menu Panel ──────────────────────────────
        CameraControlMenu(
            visible = showControlMenu,
            flashMode = uiState.flashMode,
            timerSeconds = uiState.timerSeconds,
            aspectRatio = uiState.aspectRatio,
            showGrid = uiState.showGrid,
            engineMode = uiState.engineMode,
            isTuned = isTuned,
            onFlashSelect = { mode -> cameraManager.setFlashMode(mode) },
            onTimerSelect = { seconds -> cameraManager.setTimerSeconds(seconds) },
            onAspectRatioSelect = { ratio -> cameraManager.setAspectRatio(ratio) },
            onToggleGrid = { cameraManager.toggleGrid() },
            onResetFrame = { cameraManager.resetCaptureFrame() },
            onEngineModeSelect = { mode -> cameraManager.setEngineMode(mode) },
            onOpenTuning = {
                when (uiState.engineMode) {
                    CameraEngineMode.PRO -> cameraManager.setProActiveTab(ProControlTab.TOOLS)
                    CameraEngineMode.NATURAL -> showNaturalTuningSheet = true
                    CameraEngineMode.FILM -> showTuningSheet = true
                }
            },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToCollection = onNavigateToCollection,
            shootingState = uiState.shootingState,
            onSelectLens = { lensType -> cameraManager.selectLensAccessory(lensType) },
            onDismiss = { showControlMenu = false }
        )

        // ── 9. Color Tuning Bottom Sheet ─────────────────────────────────────────
        ColorTuningSheet(
            visible = showTuningSheet,
            tuning = uiState.tuning,
            onTuningChange = { cameraManager.updateTuning(it) },
            onReset = { cameraManager.resetTuning() },
            onDismiss = { showTuningSheet = false }
        )

        // ── 11. Natural Lab Tuning Bottom Sheet ───────────────────────────────────
        NaturalTuningSheet(
            visible = showNaturalTuningSheet,
            profile = uiState.naturalProfile,
            compareWithOriginal = uiState.debugCompareOriginal,
            computationalConfig = uiState.computationalConfig,
            computationalStats = uiState.computationalStats,
            sceneConfig = uiState.sceneConfig,
            sceneAnalysis = uiState.sceneAnalysis,
            portraitConfig = uiState.portraitConfig,
            portraitActive = uiState.portraitBokehActive,
            colorScienceConfig = uiState.colorScienceConfig,
            sensorTelemetry = uiState.sensorTelemetry,
            onProfileChange = { cameraManager.updateNaturalProfile(it) },
            onCompareChange = { cameraManager.setDebugCompareOriginal(it) },
            onComputationalConfigChange = { cameraManager.updateComputationalConfig(it) },
            onSceneConfigChange = { cameraManager.updateSceneConfig(it) },
            onPortraitConfigChange = { cameraManager.updatePortraitConfig(it) },
            onColorScienceConfigChange = { cameraManager.updateColorScienceConfig(it) },
            onReset = { cameraManager.resetNaturalProfile() },
            onDismiss = { showNaturalTuningSheet = false }
        )

        // ── 12. Floating Tactile Lens Rectangle Bubble Overlay ───────────────────
        AnimatedVisibility(
            visible = showLensSheet,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) + scaleIn(initialScale = 0.90f, animationSpec = androidx.compose.animation.core.tween(150)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) + scaleOut(targetScale = 0.90f, animationSpec = androidx.compose.animation.core.tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showLensSheet = false
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 150.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Consume taps so clicking inside the bubble doesn't dismiss
                        }
                ) {
                    TactileLensBubble(
                        shootingState = uiState.shootingState,
                        onLensSelected = { lensType ->
                            cameraManager.selectLensAccessory(lensType)
                            if (lensType != LensType.COLOR_FLASH) {
                                showLensSheet = false
                            }
                        },
                        onColorFlashTintSelected = { tint ->
                            cameraManager.selectColorFlashTint(tint)
                            showLensSheet = false
                        },
                        onDismiss = { showLensSheet = false }
                    )
                }
            }
        }
    }
}

private suspend fun loadLatestCameleoThumbnail(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("CAMELEO_%", "CRAFTCAM_%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        var latestUri: Uri? = null
        var latestDate = 0L

        // Check images
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val id = cursor.getLong(idCol)
                latestDate = cursor.getLong(dateCol)
                latestUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }

        // Check videos
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val vidDate = cursor.getLong(dateCol)
                if (vidDate > latestDate) {
                    val id = cursor.getLong(idCol)
                    latestUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }

        latestUri?.let { uri ->
            loadThumbnailForUri(context, uri)
        }
    } catch (e: Exception) {
        null
    }
}

private suspend fun loadThumbnailForUri(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(192, 192), null)
            bmp.asImageBitmap()
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 8
                }
                val bmp = BitmapFactory.decodeStream(stream, null, options)
                bmp?.asImageBitmap()
            }
        }
    } catch (e: Exception) {
        null
    }
}
