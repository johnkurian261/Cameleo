package com.craftcam.app.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlin.math.roundToInt
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.craftcam.app.gl.GLViewfinderRenderer
import com.craftcam.app.gl.GPUVideoProcessor
import com.craftcam.app.model.*
import com.craftcam.app.model.ProCameraFormatters.kelvinToRggbGains
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
import com.craftcam.app.camera.profile.*
import com.craftcam.app.captureframe.CaptureFrameSnapshot
import com.craftcam.app.captureframe.CaptureFrameState
import com.craftcam.app.computational.CapturedFrame
import com.craftcam.app.computational.FrameSequence
import com.craftcam.app.computational.ComputationalPhotoConfig
import com.craftcam.app.computational.ComputationalStats
import com.craftcam.app.config.AppSettingsStore
import com.craftcam.app.config.ProcessingProfile
import com.craftcam.app.config.SceneProcessingConfig
import com.craftcam.app.scene.SceneAnalysisEngine
import com.craftcam.app.scene.SceneType
import com.craftcam.app.portrait.HardwareFaceDetector
import com.craftcam.app.portrait.FaceRegion
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private val TAG = "CameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val processingScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _uiState = MutableStateFlow(CameraUIState())
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private var currentPreviewView: PreviewView? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreset: CameraPreset = PresetCatalog.allPresets[0]
    private var glRenderer: GLViewfinderRenderer? = null
    private var glSurfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null

    private val isCapturingInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var captureWatchdogJob: Job? = null
    private var pendingRebindAfterCapture = false
    private val naturalExposureController = com.craftcam.app.camera.exposure.NaturalExposureController()

    private fun startCaptureWatchdog(timeoutMs: Long = 8000L) {
        captureWatchdogJob?.cancel()
        captureWatchdogJob = processingScope.launch {
            delay(timeoutMs)
            if (isCapturingInProgress.get() || _uiState.value.isCapturing || _uiState.value.isComputationalProcessing) {
                Log.w(TAG, "Capture watchdog triggered: capture operation timed out after ${timeoutMs}ms, unlocking capture state")
                isCapturingInProgress.set(false)
                _uiState.update { it.copy(isCapturing = false, isComputationalProcessing = false) }
                checkPendingRebind()
            }
        }
    }

    private fun cancelCaptureWatchdog() {
        captureWatchdogJob?.cancel()
        captureWatchdogJob = null
    }

    private var lastTelemetryUpdateTime = 0L
    private var lastReportedIso = 0
    private var lastReportedExpNs = 0L
    private var lastReportedFaceCount = 0
    private val EMPTY_INT_ARRAY = IntArray(0)
    private val EMPTY_FLOAT_ARRAY = FloatArray(0)
    @Volatile
    var lastPreviewFrameTimestamp = SystemClock.elapsedRealtime()
    private var previewWatchdogJob: Job? = null

    private fun startPreviewWatchdog() {
        previewWatchdogJob?.cancel()
        previewWatchdogJob = processingScope.launch {
            while (isActive) {
                delay(1000L)
                val now = SystemClock.elapsedRealtime()
                val isCapturing = _uiState.value.isCapturing || isCapturingInProgress.get()
                if (isCameraActive && !isCapturing) {
                    val timeSinceLastFrame = now - lastPreviewFrameTimestamp
                    if (timeSinceLastFrame > 2000L) {
                        Log.w(TAG, "Preview watchdog alert: No camera frame for ${timeSinceLastFrame}ms. Triggering non-destructive recovery.")
                        lastPreviewFrameTimestamp = now
                        withContext(Dispatchers.Main) {
                            try {
                                glRenderer?.requestRenderSafe()
                                if (timeSinceLastFrame > 3500L) {
                                    bindCameraUseCases()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Preview watchdog recovery encountered error", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPreviewWatchdog() {
        previewWatchdogJob?.cancel()
        previewWatchdogJob = null
    }

    private fun checkPendingRebind() {
        if (pendingRebindAfterCapture) {
            pendingRebindAfterCapture = false
            ContextCompat.getMainExecutor(context).execute {
                bindCameraUseCases()
            }
        }
    }

    var onCameraOrientationChanged: ((rotationDegrees: Int, isFront: Boolean, width: Int, height: Int) -> Unit)? = null

    private var lastSelectedRearLens: CameraLens? = null
    private val sceneAnalysisEngine = SceneAnalysisEngine()
    private val cameraProfileManager = com.craftcam.app.color.CameraProfileManager(context)
    private var lastSceneAnalysisTime = 0L

    val zoomModel = ZoomModel(context)
    var isCameraActive: Boolean = false
        private set
    private var zoomOverlayJob: Job? = null

    init {
        try {
            val discovered = CameraDiscovery.discover(context)
            val defaultRear = discovered.rearLenses.firstOrNull { kotlin.math.abs(it.equivalentZoom - 1.0f) < 0.1f }
                ?: discovered.rearLenses.firstOrNull()
            val telemetry = cameraProfileManager.querySensorTelemetry(defaultRear?.cameraId ?: "0")

            val zoomProfile = zoomModel.queryProfile(defaultRear?.cameraId ?: "0")
            val baseEq = defaultRear?.equivalentFocalLength35mm ?: zoomProfile.baseEquivalentFocalLength35mm
            val initialZoom = defaultRear?.equivalentZoom ?: 1.0f
            val initialMm = zoomModel.calculateDisplayFocalLengthMm(baseEq, initialZoom)
            val initialZoomState = ZoomState(
                zoomRatio = initialZoom,
                displayFocalLengthMm = initialMm,
                minZoomRatio = zoomProfile.minZoomRatio,
                maxZoomRatio = zoomProfile.maxZoomRatio,
                activeCameraId = defaultRear?.cameraId ?: "0",
                baseFocalLength35mm = baseEq,
                isOverlayVisible = false,
                isDebugVisible = false
            )

            _uiState.update {
                it.copy(
                    rearLenses = discovered.rearLenses,
                    frontLenses = discovered.frontLenses,
                    selectedLens = defaultRear,
                    zoomRatio = initialZoom,
                    minZoom = zoomProfile.minZoomRatio,
                    maxZoom = zoomProfile.maxZoomRatio,
                    zoomState = initialZoomState,
                    captureFrameState = it.captureFrameState.copy(baseFocalLengthMm = baseEq),
                    sensorTelemetry = telemetry,
                    showGrid = AppSettingsStore.get(context).current.assistiveGrid
                )
            }
            lastSelectedRearLens = defaultRear

            processingScope.launch {
                AppSettingsStore.get(context).state.collect { settings ->
                    _uiState.update { current ->
                        if (current.showGrid != settings.assistiveGrid) {
                            current.copy(showGrid = settings.assistiveGrid)
                        } else current
                    }
                    glRenderer?.setMirrorFrontPreview(settings.mirrorFrontCamera)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lens initialization failed", e)
        }
    }

    fun setCameraActive(active: Boolean) {
        isCameraActive = active
        if (active) {
            lastPreviewFrameTimestamp = SystemClock.elapsedRealtime()
            startPreviewWatchdog()
        } else {
            stopPreviewWatchdog()
        }
    }

    fun triggerZoomOverlay(durationMs: Long = 1500L) {
        _uiState.update { state ->
            state.copy(zoomState = state.zoomState.copy(isOverlayVisible = true))
        }
        zoomOverlayJob?.cancel()
        zoomOverlayJob = processingScope.launch(Dispatchers.Main) {
            delay(durationMs)
            _uiState.update { state ->
                state.copy(zoomState = state.zoomState.copy(isOverlayVisible = false))
            }
        }
    }

    fun setMirrorFrontPreview(mirror: Boolean) {
        glRenderer?.setMirrorFrontPreview(mirror)
    }

    fun clearGLRenderer(renderer: GLViewfinderRenderer) {
        if (glRenderer === renderer) {
            glRenderer?.onHistogramUpdated = null
            glRenderer = null
        }
    }

    fun setGLRenderer(renderer: GLViewfinderRenderer) {
        glRenderer = renderer
        glRenderer?.setMirrorFrontPreview(AppSettingsStore.get(context).current.mirrorFrontCamera)
        glRenderer?.updatePreset(currentPreset)
        glRenderer?.updateTuning(_uiState.value.tuning)
        glRenderer?.setNaturalMode(
            _uiState.value.engineMode == CameraEngineMode.NATURAL,
            _uiState.value.naturalProfile
        )
        glRenderer?.updateColorScience(_uiState.value.colorScienceConfig)
        glRenderer?.setProMode(
            _uiState.value.engineMode == CameraEngineMode.PRO,
            _uiState.value.proConfig
        )
        glRenderer?.updateLensProfile(
            _uiState.value.shootingState.selectedLens,
            isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF
        )
        glRenderer?.onHistogramUpdated = { bins ->
            _uiState.update { it.copy(histogramBins = bins) }
        }
    }

    fun setEngineMode(mode: CameraEngineMode) {
        if (_uiState.value.engineMode != mode) {
            _uiState.update { it.copy(engineMode = mode) }
            val isNatural = mode == CameraEngineMode.NATURAL
            val isPro = mode == CameraEngineMode.PRO

            glRenderer?.setNaturalMode(isNatural, _uiState.value.naturalProfile)
            glRenderer?.setProMode(isPro, _uiState.value.proConfig)

            if (isPro) {
                applyProCameraControls()
            } else {
                clearProCameraControls()
                // Adjust hardware exposure bias for the active engine mode
                val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
                val targetIndex = if (isNatural) {
                    (_uiState.value.naturalProfile.exposureBias / step).roundToInt()
                        .coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
                } else {
                    _uiState.value.exposureIndex
                }
                camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
            }
        }
    }

    // ─── Pro Camera Controls & Hardware Interop ─────────────────────────────

    fun queryHardwareCapabilities(cameraId: String): CameraHardwareCapabilities {
        val cameraService = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            ?: return CameraHardwareCapabilities(cameraId = cameraId)
        return try {
            val chars = cameraService.getCameraCharacteristics(cameraId)
            val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val shutterRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f
            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val evStepVal = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.5f

            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val manualSensor = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ||
                    hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                    hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

            val supportsManualPostProcessing = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) ||
                    hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                    hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

            CameraHardwareCapabilities(
                cameraId = cameraId,
                hardwareLevel = hwLevel,
                minIso = isoRange?.lower ?: 50,
                maxIso = isoRange?.upper ?: 3200,
                minShutterNs = shutterRange?.lower ?: 100_000L,
                maxShutterNs = shutterRange?.upper ?: 15_000_000_000L,
                supportsManualExposure = manualSensor || (isoRange != null && shutterRange != null),
                minFocusDistance = minFocus,
                supportsManualFocus = minFocus > 0.0f,
                minEv = evRange?.lower ?: -4,
                maxEv = evRange?.upper ?: 4,
                evStep = evStepVal,
                supportsManualWb = supportsManualPostProcessing,
                supportsColorGains = supportsManualPostProcessing
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query hardware capabilities for camera $cameraId", e)
            CameraHardwareCapabilities(cameraId = cameraId)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun applyProCameraControls() {
        val cam = camera ?: return
        try {
            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            if (_uiState.value.engineMode != CameraEngineMode.PRO) {
                camera2Control.clearCaptureRequestOptions()
                return
            }

            val config = _uiState.value.proConfig
            val caps = _uiState.value.hardwareCapabilities
            val builder = CaptureRequestOptions.Builder()

            // 1. Exposure (ISO & Shutter)
            val isManualExposure = config.exposureMode == ExposureMode.MANUAL ||
                    (config.isoMode == IsoMode.MANUAL && config.shutterMode == ShutterMode.MANUAL)
            val isManualShutterAutoIso = config.shutterMode == ShutterMode.MANUAL && config.isoMode == IsoMode.AUTO

            if (isManualExposure && (caps?.supportsManualExposure != false)) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val minIso = caps?.minIso ?: 50
                val maxIso = caps?.maxIso ?: 3200
                val minShutter = caps?.minShutterNs ?: 100_000L
                val maxShutter = caps?.maxShutterNs ?: 15_000_000_000L

                val clampedIso = config.iso.coerceIn(minIso, maxIso)
                val clampedShutter = config.exposureTimeNs.coerceIn(minShutter, maxShutter)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedShutter)
            } else if (isManualShutterAutoIso && (caps?.supportsManualExposure != false)) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val minIso = caps?.minIso ?: 50
                val maxIso = caps?.maxIso ?: 3200
                val minShutter = caps?.minShutterNs ?: 100_000L
                val maxShutter = caps?.maxShutterNs ?: 15_000_000_000L

                val liveIso = _uiState.value.liveIso.coerceIn(minIso, maxIso)
                val clampedShutter = config.exposureTimeNs.coerceIn(minShutter, maxShutter)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, liveIso)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedShutter)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
                val targetIndex = (config.evCompensation / step).roundToInt()
                    .coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
                cam.cameraControl.setExposureCompensationIndex(targetIndex)
            }

            // 2. Focus
            if (config.focusMode == FocusMode.MANUAL && (caps?.supportsManualFocus == true)) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                val clampedDist = config.focusDistance.coerceIn(0f, caps.minFocusDistance)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDist)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            // 3. White Balance
            if (config.wbMode == WhiteBalanceMode.MANUAL && (caps?.supportsManualWb == true)) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                val gains = kelvinToRggbGains(config.kelvin, config.tint)
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }

            camera2Control.setCaptureRequestOptions(builder.build())
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply Pro Camera2 controls", t)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun clearProCameraControls() {
        val cam = camera ?: return
        try {
            val camera2Control = Camera2CameraControl.from(cam.cameraControl)
            camera2Control.clearCaptureRequestOptions()
        } catch (t: Throwable) {
            Log.w(TAG, "Error clearing Pro Camera2 controls", t)
        }
    }

    fun updateProConfig(update: (ProCameraConfig) -> ProCameraConfig) {
        _uiState.update { state ->
            val updated = update(state.proConfig)
            if (state.engineMode == CameraEngineMode.PRO) {
                glRenderer?.setProMode(true, updated)
            }
            state.copy(proConfig = updated)
        }
        if (_uiState.value.engineMode == CameraEngineMode.PRO) {
            applyProCameraControls()
        }
    }

    fun setProIso(mode: IsoMode, iso: Int) {
        updateProConfig { it.copy(isoMode = mode, iso = iso) }
    }

    fun setProShutter(mode: ShutterMode, exposureNs: Long) {
        updateProConfig { it.copy(shutterMode = mode, exposureTimeNs = exposureNs) }
    }

    fun setProFocus(mode: FocusMode, diopters: Float) {
        updateProConfig { it.copy(focusMode = mode, focusDistance = diopters) }
    }

    fun setProWb(mode: WhiteBalanceMode, kelvin: Int, tint: Float) {
        updateProConfig { it.copy(wbMode = mode, kelvin = kelvin, tint = tint) }
    }

    fun setProEv(ev: Float) {
        updateProConfig { it.copy(evCompensation = ev) }
    }

    fun setProActiveTab(tab: ProControlTab?) {
        updateProConfig { it.copy(activeControl = if (it.activeControl == tab) null else tab) }
    }

    fun toggleFocusPeaking() {
        updateProConfig { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) }
    }

    fun setPeakingColor(color: PeakingColor) {
        updateProConfig { it.copy(peakingColor = color) }
    }

    fun toggleZebra() {
        updateProConfig { it.copy(zebraEnabled = !it.zebraEnabled) }
    }

    fun setZebraThreshold(threshold: Float) {
        updateProConfig { it.copy(zebraThreshold = threshold) }
    }

    fun toggleHistogram() {
        updateProConfig { it.copy(histogramEnabled = !it.histogramEnabled) }
    }

    fun resetProConfig() {
        updateProConfig { ProCameraConfig() }
    }

    // ─── Natural Camera Profile Methods ─────────────────────────────────────

    fun updateNaturalProfile(update: (NaturalCameraProfile) -> NaturalCameraProfile) {
        _uiState.update { state ->
            val updatedProfile = update(state.naturalProfile)
            if (state.engineMode == CameraEngineMode.NATURAL) {
                glRenderer?.setNaturalMode(true, updatedProfile)
                val step = if (state.exposureStep > 0f) state.exposureStep else 0.5f
                val targetIndex = (updatedProfile.exposureBias / step).roundToInt()
                    .coerceIn(state.minExposureIndex, state.maxExposureIndex)
                camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
            }
            state.copy(naturalProfile = updatedProfile)
        }
    }

    fun resetNaturalProfile() {
        updateNaturalProfile { NaturalCameraProfile.S21_PROFILE }
    }

    fun setDebugCompareOriginal(enabled: Boolean) {
        _uiState.update { it.copy(debugCompareOriginal = enabled) }
    }

    fun toggleNaturalDebug() {
        _uiState.update { it.copy(showNaturalDebug = !it.showNaturalDebug) }
    }

    fun setPreset(preset: CameraPreset) {
        if (currentPreset.id != preset.id) {
            currentPreset = preset
            glRenderer?.updatePreset(preset)

            val isZero = preset.id == "zero" || preset.name.equals("Zero", ignoreCase = true)
            val newEngineMode = if (isZero) {
                naturalExposureController.reset()
                CameraEngineMode.NATURAL
            } else if (_uiState.value.engineMode == CameraEngineMode.NATURAL) {
                CameraEngineMode.FILM
            } else {
                _uiState.value.engineMode
            }

            val newCaptureMode = if (_uiState.value.captureMode == CaptureMode.MODE_3D) {
                CaptureMode.PHOTO
            } else {
                _uiState.value.captureMode
            }

            val profile = CameraProfileCatalog.getProfileForPreset(preset)
            val initialLens = LensProfile.forType(profile.defaultLens)
            val initialFocal = profile.getDefaultFocalOption()
            val initialRatio = profile.defaultRatio
            val targetFocal = if (profile.defaultFocalLengthMm > 0f) profile.defaultFocalLengthMm else 35f
            val defaultFraction = if (targetFocal <= 26f) {
                0.88f
            } else {
                (26.0f / targetFocal).coerceIn(0.25f, 0.92f)
            }

            _uiState.update { state ->
                state.copy(
                    engineMode = newEngineMode,
                    captureMode = newCaptureMode,
                    shootingState = state.shootingState.copy(
                        cameraProfile = profile,
                        selectedLens = initialLens,
                        selectedFocalLength = initialFocal,
                        selectedAspectRatio = initialRatio
                    ),
                    aspectRatio = initialRatio,
                    captureFrameState = state.captureFrameState.copy(
                        sizeFraction = defaultFraction,
                        normalizedCenterX = 0.5f,
                        normalizedCenterY = 0.5f
                    )
                )
            }
            glRenderer?.setNaturalMode(newEngineMode == CameraEngineMode.NATURAL, _uiState.value.naturalProfile)
            glRenderer?.updateLensProfile(initialLens, isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF)

            if (newEngineMode != CameraEngineMode.NATURAL) {
                val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
                val targetIndex = (_uiState.value.tuning.exposureCompensation / step).roundToInt()
                    .coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
                camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
            }

            // If in video mode, re-bind overlay effect for the active preset's video recording profile
            if (_uiState.value.captureMode == CaptureMode.VIDEO) {
                bindCameraUseCases()
            }
        }
    }

    fun setFrameBorderEnabled(enabled: Boolean) {
        updateTuning { it.copy(frameBorderEnabled = enabled) }
        _uiState.update { it.copy(captureFrameState = it.captureFrameState.copy(isVisible = enabled)) }
    }

    fun setGrainEnabled(enabled: Boolean) {
        updateTuning { it.copy(grainBoost = if (enabled) 1.0f else 0.0f, debugStageGrain = enabled) }
    }

    fun setFilmColorProfileIndex(index: Int) {
        updateTuning { it.copy(filmColorProfileIndex = index.coerceIn(0, 3)) }
    }

    fun selectLensAccessory(lensType: LensType) {
        val lensProfile = LensProfile.forType(lensType)
        if (lensType == LensType.COLOR_FLASH && _uiState.value.flashMode == ImageCapture.FLASH_MODE_OFF) {
            setFlashMode(ImageCapture.FLASH_MODE_ON)
        }
        _uiState.update { state ->
            state.copy(
                shootingState = state.shootingState.copy(
                    selectedLens = lensProfile
                )
            )
        }
        glRenderer?.updateLensProfile(lensProfile, isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF)

        // Physical sensor exposure adjustment for optical accessories
        val unifiedProfile = com.craftcam.app.camera.profile.CameraProfileCatalog.getUnifiedProfile(currentPreset)
        val ndParams = com.craftcam.app.gl.OpticalEffectEngine.resolveNd(unifiedProfile.exposureProfile, lensProfile)
        if (ndParams.evStopsReduction > 0.01f) {
            val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
            val targetReductionSteps = (ndParams.evStopsReduction / step).roundToInt()
            val ndEv = (_uiState.value.exposureIndex - targetReductionSteps).coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
            camera?.cameraControl?.setExposureCompensationIndex(ndEv)
        } else {
            camera?.cameraControl?.setExposureCompensationIndex(_uiState.value.exposureIndex)
        }
    }

    fun selectColorFlashTint(tint: ColorFlashTint) {
        val lensProfile = LensProfile.forColorFlash(tint)
        if (_uiState.value.flashMode == ImageCapture.FLASH_MODE_OFF) {
            setFlashMode(ImageCapture.FLASH_MODE_ON)
        }
        _uiState.update { state ->
            state.copy(
                shootingState = state.shootingState.copy(
                    selectedLens = lensProfile
                )
            )
        }
        glRenderer?.updateLensProfile(lensProfile, isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF)
    }

    fun selectFocalLength(focalOption: FocalLengthOption) {
        val targetFraction = if (focalOption.focalLengthMm <= 26f) 0.88f else (26.0f / focalOption.focalLengthMm).coerceIn(0.25f, 0.92f)
        _uiState.update { state ->
            state.copy(
                shootingState = state.shootingState.copy(
                    selectedFocalLength = focalOption
                ),
                captureFrameState = state.captureFrameState.copy(
                    sizeFraction = targetFraction,
                    normalizedCenterX = 0.5f,
                    normalizedCenterY = 0.5f
                )
            )
        }
    }

    fun initializeWithGLSurface(
        lifecycleOwner: LifecycleOwner,
        surfaceTexture: SurfaceTexture,
        onOrientationUpdate: (rot: Int, isFront: Boolean, bufW: Int, bufH: Int) -> Unit
    ) {
        val effectiveOwner = (context as? LifecycleOwner) ?: lifecycleOwner
        currentLifecycleOwner = effectiveOwner
        if (glSurfaceTexture != surfaceTexture) {
            previewSurface?.release()
            previewSurface = null
            glSurfaceTexture = surfaceTexture
        }
        onCameraOrientationChanged = onOrientationUpdate

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun initialize(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        // Bind to Activity lifecycle so screen navigation (e.g. opening Gallery) does not abort in-flight capture
        val effectiveOwner = (context as? LifecycleOwner) ?: lifecycleOwner
        if (currentPreviewView == previewView && currentLifecycleOwner == effectiveOwner && camera != null) {
            return
        }
        currentLifecycleOwner = effectiveOwner
        currentPreviewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setCaptureMode(mode: CaptureMode) {
        if (IS_VIDEO_FEATURE_PAUSED && mode == CaptureMode.VIDEO) {
            Log.d(TAG, "Video mode is paused; keeping photo mode")
            return
        }
        if (_uiState.value.captureMode != mode) {
            _uiState.update { it.copy(captureMode = mode) }
            bindCameraUseCases()
        }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val lifecycleOwner = currentLifecycleOwner ?: return
        val previewView = currentPreviewView
        val surfaceTex = glSurfaceTexture

        if (previewView == null && surfaceTex == null) return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_uiState.value.lensFacing)
            .build()

        if (!provider.hasCamera(cameraSelector)) {
            Log.w(TAG, "Requested lens facing ${_uiState.value.lensFacing} not available on this device")
            return
        }

        if (_uiState.value.isCapturing || isCapturingInProgress.get()) {
            Log.d(TAG, "Hardware photo capture in progress, queueing use-case rebind after capture")
            pendingRebindAfterCapture = true
            return
        }

        try {
            provider.unbindAll()

            // 1. High-resolution 4:3 optical viewfinder with optimal FHD native display strategy
            val previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1440, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val previewBuilder = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)

            // Attach Camera2Interop CaptureCallback to receive live sensor readouts & hardware face metadata
            val camera2Extender = Camera2Interop.Extender(previewBuilder)
            camera2Extender.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_FAST
            )
            camera2Extender.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_FAST
            )
            try {
                val cameraService = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                val targetFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }
                val isFaceDetectSupported = cameraService?.cameraIdList?.any { id ->
                    val chars = cameraService.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == targetFacing &&
                        chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                            ?.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) == true
                } ?: false

                if (isFaceDetectSupported) {
                    camera2Extender.setCaptureRequestOption(
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware face detect query failed; skipping face detect mode setting", e)
            }
            camera2Extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    lastPreviewFrameTimestamp = SystemClock.elapsedRealtime()

                    val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                    val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                    val focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE)

                    // Hardware Face Detection Ingestion
                    val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
                    val rot = if (isFront) 270 else 90
                    HardwareFaceDetector.updateFromCaptureResult(result, 4032, 3024, rot)
                    val liveFaces = HardwareFaceDetector.getDetectedFaces()

                    // Scene Intelligence Analysis at 5 Hz cadence
                    val now = SystemClock.elapsedRealtime()
                    val shouldRunSceneAnalysis = (now - lastSceneAnalysisTime >= 200) && (_uiState.value.engineMode == CameraEngineMode.NATURAL)
                    val analysisResult = if (shouldRunSceneAnalysis) {
                        lastSceneAnalysisTime = now

                        // Adaptive Natural Exposure Controller (scene-informed highlight/shadow preservation)
                        val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
                        val exposureDecision = naturalExposureController.evaluate(
                            histogram = _uiState.value.histogramBins,
                            sensorIso = iso ?: _uiState.value.liveIso,
                            exposureTimeNs = expNs ?: _uiState.value.liveExposureNs,
                            faces = liveFaces,
                            step = step,
                            minIndex = _uiState.value.minExposureIndex,
                            maxIndex = _uiState.value.maxExposureIndex
                        )
                        if (exposureDecision.targetIndex != _uiState.value.exposureIndex) {
                            ContextCompat.getMainExecutor(context).execute {
                                camera?.cameraControl?.setExposureCompensationIndex(exposureDecision.targetIndex)
                            }
                        }

                        sceneAnalysisEngine.analyze(
                            downsampledPixels = EMPTY_INT_ARRAY,
                            downsampledLuminance = EMPTY_FLOAT_ARRAY,
                            width = 64,
                            height = 48,
                            histogram = _uiState.value.histogramBins,
                            sensorIso = iso ?: _uiState.value.liveIso,
                            exposureTimeNs = expNs ?: _uiState.value.liveExposureNs,
                            overrideFaces = liveFaces
                        )
                    } else null

                    val currentIso = iso ?: _uiState.value.liveIso
                    val currentExp = expNs ?: _uiState.value.liveExposureNs
                    val faceCount = liveFaces.size

                    val isoDelta = kotlin.math.abs(currentIso - lastReportedIso)
                    val expRatio = if (lastReportedExpNs > 0) kotlin.math.abs(currentExp - lastReportedExpNs).toFloat() / lastReportedExpNs else 1.0f
                    val elapsed = now - lastTelemetryUpdateTime

                    // Throttle Compose UI recompositions to max 4 Hz (250ms) unless a significant exposure/face event occurs
                    val isSignificantChange = isoDelta >= 100 || expRatio >= 0.25f || faceCount != lastReportedFaceCount || analysisResult != null
                    if (elapsed >= 250 || (isSignificantChange && elapsed >= 100)) {
                        lastTelemetryUpdateTime = now
                        lastReportedIso = currentIso
                        lastReportedExpNs = currentExp
                        lastReportedFaceCount = faceCount

                        val isPortraitBokehActive = when (_uiState.value.portraitConfig.portraitMode) {
                            com.craftcam.app.portrait.PortraitMode.OFF -> false
                            com.craftcam.app.portrait.PortraitMode.AUTO -> liveFaces.isNotEmpty() && liveFaces.any { it.confidence >= 0.40f || it.areaFraction >= 0.03f }
                            com.craftcam.app.portrait.PortraitMode.ON -> true
                        }
                        _uiState.update { state ->
                            state.copy(
                                liveIso = currentIso,
                                liveExposureNs = currentExp,
                                liveFocusDistance = focusDist ?: state.liveFocusDistance,
                                detectedFaces = liveFaces,
                                sceneAnalysis = analysisResult ?: state.sceneAnalysis,
                                portraitBokehActive = isPortraitBokehActive && (_uiState.value.engineMode == CameraEngineMode.NATURAL)
                            )
                        }
                    }
                }
            })

            preview = previewBuilder.build()
                .also { prev ->
                    if (surfaceTex != null) {
                        prev.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
                            val res = request.resolution
                            surfaceTex.setDefaultBufferSize(res.width, res.height)
                            val currentSurface = previewSurface?.takeIf { it.isValid } ?: run {
                                previewSurface?.release()
                                val surf = Surface(surfaceTex)
                                previewSurface = surf
                                surf
                            }
                            request.provideSurface(currentSurface, ContextCompat.getMainExecutor(context)) { result ->
                                Log.d(TAG, "CameraX SurfaceRequest result: ${result.resultCode}")
                                if (result.resultCode != androidx.camera.core.SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY) {
                                    currentSurface.release()
                                    if (previewSurface == currentSurface) {
                                        previewSurface = null
                                    }
                                }
                            }
                            val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
                            val rot = camera?.cameraInfo?.sensorRotationDegrees ?: (if (isFront) 270 else 90)
                            onCameraOrientationChanged?.invoke(rot, isFront, res.width, res.height)
                        }
                    } else if (previewView != null) {
                        prev.setSurfaceProvider(previewView.surfaceProvider)
                    }
                }

            val effectiveMode = if (IS_VIDEO_FEATURE_PAUSED && _uiState.value.captureMode == CaptureMode.VIDEO) CaptureMode.PHOTO else _uiState.value.captureMode
            if (effectiveMode == CaptureMode.PHOTO || effectiveMode == CaptureMode.MODE_3D) {
                // 2A. Full native sensor resolution with hardware ISP multi-frame HDR & noise reduction
                val photoResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val imageCaptureBuilder = ImageCapture.Builder()
                    .setResolutionSelector(photoResolutionSelector)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(95)
                    .setFlashMode(_uiState.value.flashMode)

                val captureExtender = Camera2Interop.Extender(imageCaptureBuilder)
                captureExtender.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
                captureExtender.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY
                )
                captureExtender.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
                captureExtender.setCaptureRequestOption(
                    CaptureRequest.HOT_PIXEL_MODE,
                    CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY
                )
                captureExtender.setCaptureRequestOption(
                    CaptureRequest.SHADING_MODE,
                    CaptureRequest.SHADING_MODE_HIGH_QUALITY
                )

                imageCapture = imageCaptureBuilder.build()

                val prev = preview
                val ic = imageCapture
                if (prev != null && ic != null) {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        prev,
                        ic
                    )
                    val rot = camera?.cameraInfo?.sensorRotationDegrees
                    val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
                    val res = prev.resolutionInfo?.resolution
                    if (rot != null && res != null) {
                        onCameraOrientationChanged?.invoke(rot, isFront, res.width, res.height)
                    }
                }
            } else {
                // 2B. High-definition VideoCapture Use Case with vintage film overlay effects
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val prev = preview
                val vc = videoCapture
                if (prev != null && vc != null) {
                    try {
                        val effect = GPUVideoProcessor.createCameraEffect(
                            context = context,
                            presetProvider = { currentPreset },
                            tuningProvider = { _uiState.value.tuning },
                            onError = { t ->
                                Log.w(TAG, "GPU video processing issue reported", t)
                            }
                        )
                        val useCaseGroup = UseCaseGroup.Builder()
                            .addUseCase(prev)
                            .addUseCase(vc)
                            .addEffect(effect)
                            .build()

                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            useCaseGroup
                        )
                        Log.d(TAG, "Bound VideoCapture with GPU 3D LUT effect successfully")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to bind GPU video effect, falling back to clean video pipeline", t)
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            prev,
                            vc
                        )
                    }
                }
            }

            // Update Zoom and Exposure capabilities
            camera?.cameraInfo?.let { info ->
                info.zoomState.value?.let { zs ->
                    val minZ = zs.minZoomRatio
                    val maxZ = zs.maxZoomRatio.coerceAtMost(10.0f)
                    val targetZoom = _uiState.value.selectedLens?.equivalentZoom ?: _uiState.value.zoomRatio
                    val clamped = targetZoom.coerceIn(minZ, maxZ)
                    val baseEq = _uiState.value.selectedLens?.equivalentFocalLength35mm
                        ?: _uiState.value.zoomState.baseFocalLength35mm
                    val displayMm = zoomModel.calculateDisplayFocalLengthMm(baseEq, clamped)

                    _uiState.update { state ->
                        state.copy(
                            minZoom = minZ,
                            maxZoom = maxZ,
                            zoomRatio = clamped,
                            zoomState = state.zoomState.copy(
                                zoomRatio = clamped,
                                displayFocalLengthMm = displayMm,
                                minZoomRatio = minZ,
                                maxZoomRatio = maxZ
                            )
                        )
                    }
                    camera?.cameraControl?.setZoomRatio(clamped)
                }
                info.exposureState.let { es ->
                    val step = es.exposureCompensationStep.toFloat()
                    _uiState.update {
                        it.copy(
                            minExposureIndex = es.exposureCompensationRange.lower,
                            maxExposureIndex = es.exposureCompensationRange.upper,
                            exposureStep = step
                        )
                    }
                    val targetIndex = if (_uiState.value.engineMode == CameraEngineMode.NATURAL) {
                        (_uiState.value.naturalProfile.exposureBias / (if (step > 0f) step else 0.5f)).roundToInt()
                            .coerceIn(es.exposureCompensationRange.lower, es.exposureCompensationRange.upper)
                    } else if (_uiState.value.engineMode == CameraEngineMode.PRO) {
                        (_uiState.value.proConfig.evCompensation / (if (step > 0f) step else 0.5f)).roundToInt()
                            .coerceIn(es.exposureCompensationRange.lower, es.exposureCompensationRange.upper)
                    } else {
                        _uiState.value.exposureIndex
                    }
                    camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
                }
            }

            // Query dynamic hardware capabilities for active camera and apply active mode controls
            val activeCamId = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT) "1" else "0"
            val caps = queryHardwareCapabilities(activeCamId)
            _uiState.update { it.copy(hardwareCapabilities = caps) }
            if (_uiState.value.engineMode == CameraEngineMode.PRO) {
                applyProCameraControls()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    fun switchCamera() {
        val currentFacing = _uiState.value.lensFacing
        val newFacing = if (currentFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val activeCamId = if (newFacing == CameraSelector.LENS_FACING_FRONT) "1" else "0"
        val caps = queryHardwareCapabilities(activeCamId)
        val telemetry = cameraProfileManager.querySensorTelemetry(activeCamId)
        val zoomProfile = zoomModel.queryProfile(activeCamId)

        if (currentFacing == CameraSelector.LENS_FACING_BACK) {
            lastSelectedRearLens = _uiState.value.selectedLens
            val frontLens = _uiState.value.frontLenses.firstOrNull()
            val baseEq = frontLens?.equivalentFocalLength35mm ?: zoomProfile.baseEquivalentFocalLength35mm
            val zRatio = frontLens?.equivalentZoom ?: 1.0f
            val displayMm = zoomModel.calculateDisplayFocalLengthMm(baseEq, zRatio)
            val newZoomState = ZoomState(
                zoomRatio = zRatio,
                displayFocalLengthMm = displayMm,
                minZoomRatio = zoomProfile.minZoomRatio,
                maxZoomRatio = zoomProfile.maxZoomRatio,
                activeCameraId = activeCamId,
                baseFocalLength35mm = baseEq,
                isOverlayVisible = false,
                isDebugVisible = _uiState.value.showNaturalDebug
            )

            _uiState.update { state ->
                val adaptedPro = state.proConfig.copy(
                    focusMode = if (caps.supportsManualFocus) state.proConfig.focusMode else FocusMode.AUTO,
                    exposureMode = if (caps.supportsManualExposure) state.proConfig.exposureMode else ExposureMode.AUTO,
                    isoMode = if (caps.supportsManualExposure) state.proConfig.isoMode else IsoMode.AUTO,
                    shutterMode = if (caps.supportsManualExposure) state.proConfig.shutterMode else ShutterMode.AUTO,
                    wbMode = if (caps.supportsManualWb) state.proConfig.wbMode else WhiteBalanceMode.AUTO
                )
                state.copy(
                    lensFacing = CameraSelector.LENS_FACING_FRONT,
                    selectedLens = frontLens,
                    zoomRatio = zRatio,
                    minZoom = zoomProfile.minZoomRatio,
                    maxZoom = zoomProfile.maxZoomRatio,
                    zoomState = newZoomState,
                    captureFrameState = state.captureFrameState.copy(baseFocalLengthMm = baseEq),
                    hardwareCapabilities = caps,
                    proConfig = adaptedPro,
                    sensorTelemetry = telemetry
                )
            }
        } else {
            val restoreRear = lastSelectedRearLens
                ?: _uiState.value.rearLenses.firstOrNull { kotlin.math.abs(it.equivalentZoom - 1.0f) < 0.1f }
                ?: _uiState.value.rearLenses.firstOrNull()
            val baseEq = restoreRear?.equivalentFocalLength35mm ?: zoomProfile.baseEquivalentFocalLength35mm
            val zRatio = restoreRear?.equivalentZoom ?: 1.0f
            val displayMm = zoomModel.calculateDisplayFocalLengthMm(baseEq, zRatio)
            val newZoomState = ZoomState(
                zoomRatio = zRatio,
                displayFocalLengthMm = displayMm,
                minZoomRatio = zoomProfile.minZoomRatio,
                maxZoomRatio = zoomProfile.maxZoomRatio,
                activeCameraId = activeCamId,
                baseFocalLength35mm = baseEq,
                isOverlayVisible = false,
                isDebugVisible = _uiState.value.showNaturalDebug
            )

            _uiState.update { state ->
                val adaptedPro = state.proConfig.copy(
                    focusMode = if (caps.supportsManualFocus) state.proConfig.focusMode else FocusMode.AUTO,
                    exposureMode = if (caps.supportsManualExposure) state.proConfig.exposureMode else ExposureMode.AUTO,
                    isoMode = if (caps.supportsManualExposure) state.proConfig.isoMode else IsoMode.AUTO,
                    shutterMode = if (caps.supportsManualExposure) state.proConfig.shutterMode else ShutterMode.AUTO,
                    wbMode = if (caps.supportsManualWb) state.proConfig.wbMode else WhiteBalanceMode.AUTO
                )
                state.copy(
                    lensFacing = CameraSelector.LENS_FACING_BACK,
                    selectedLens = restoreRear,
                    zoomRatio = zRatio,
                    minZoom = zoomProfile.minZoomRatio,
                    maxZoom = zoomProfile.maxZoomRatio,
                    zoomState = newZoomState,
                    captureFrameState = state.captureFrameState.copy(baseFocalLengthMm = baseEq),
                    hardwareCapabilities = caps,
                    proConfig = adaptedPro,
                    sensorTelemetry = telemetry
                )
            }
        }
        bindCameraUseCases()
    }

    fun selectLens(lens: CameraLens) {
        if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            lastSelectedRearLens = lens
        }
        _uiState.update { state ->
            state.copy(
                selectedLens = lens,
                zoomState = state.zoomState.copy(
                    baseFocalLength35mm = lens.equivalentFocalLength35mm
                ),
                captureFrameState = state.captureFrameState.copy(
                    baseFocalLengthMm = lens.equivalentFocalLength35mm
                )
            )
        }
        setZoomRatio(lens.equivalentZoom, triggerOverlay = true)
    }

    fun setFlashMode(flashMode: Int) {
        val targetMode = if (flashMode != ImageCapture.FLASH_MODE_OFF && camera?.cameraInfo?.hasFlashUnit() == false) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            flashMode
        }
        _uiState.update { it.copy(flashMode = targetMode) }
        imageCapture?.flashMode = targetMode
        glRenderer?.updateLensProfile(
            _uiState.value.shootingState.selectedLens,
            isFlashActive = targetMode != ImageCapture.FLASH_MODE_OFF
        )
    }

    fun setExposureIndex(index: Int) {
        val clamped = index.coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
        val evVal = clamped * _uiState.value.exposureStep
        if (_uiState.value.engineMode == CameraEngineMode.NATURAL) {
            updateNaturalProfile { it.copy(exposureBias = evVal) }
        } else if (_uiState.value.engineMode == CameraEngineMode.PRO) {
            setProEv(evVal)
        } else {
            _uiState.update {
                it.copy(
                    exposureIndex = clamped,
                    tuning = it.tuning.copy(exposureCompensation = evVal)
                )
            }
            camera?.cameraControl?.setExposureCompensationIndex(clamped)
        }
    }

    fun setZoomRatio(ratio: Float, triggerOverlay: Boolean = false) {
        val minZ = _uiState.value.minZoom
        val maxZ = _uiState.value.maxZoom
        val clamped = ratio.coerceIn(minZ, maxZ)
        val activeLenses = _uiState.value.availableLenses
        val closest = activeLenses.minByOrNull { kotlin.math.abs(it.equivalentZoom - clamped) }

        val baseEq = _uiState.value.selectedLens?.equivalentFocalLength35mm
            ?: _uiState.value.zoomState.baseFocalLength35mm
        val displayMm = zoomModel.calculateDisplayFocalLengthMm(baseEq, clamped)

        _uiState.update { state ->
            state.copy(
                zoomRatio = clamped,
                selectedLens = closest,
                zoomState = state.zoomState.copy(
                    zoomRatio = clamped,
                    displayFocalLengthMm = displayMm,
                    minZoomRatio = minZ,
                    maxZoomRatio = maxZ
                )
            )
        }
        if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK && closest != null) {
            lastSelectedRearLens = closest
        }
        camera?.cameraControl?.setZoomRatio(clamped)

        if (triggerOverlay) {
            triggerZoomOverlay()
        }
    }

    fun onPinchZoom(scaleFactor: Float) {
        val currentZoom = _uiState.value.zoomRatio
        val target = currentZoom * scaleFactor
        setZoomRatio(target, triggerOverlay = true)
    }

    fun stepZoom(zoomIn: Boolean, stepPercent: Float = 0.08f) {
        val current = _uiState.value.zoomRatio
        val step = (current * stepPercent).coerceIn(0.08f, 0.40f)
        val target = if (zoomIn) current + step else current - step
        setZoomRatio(target, triggerOverlay = true)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        val targetRatio = 1f / mode.ratio
        _uiState.update {
            it.copy(
                aspectRatio = mode,
                captureFrameState = it.captureFrameState.copy(aspectRatio = targetRatio)
            )
        }
    }

    fun moveCaptureFrame(dxNorm: Float, dyNorm: Float, previewAspect: Float) {
        android.util.Log.d("CaptureFrame", "moveCaptureFrame: dxNorm=$dxNorm, dyNorm=$dyNorm, currentCenterX=${_uiState.value.captureFrameState.normalizedCenterX}, currentCenterY=${_uiState.value.captureFrameState.normalizedCenterY}")
        _uiState.update { state ->
            val newState = state.captureFrameState.moveBy(dxNorm, dyNorm, previewAspect)
            android.util.Log.d("CaptureFrame", "moveCaptureFrame result: newCenterX=${newState.normalizedCenterX}, newCenterY=${newState.normalizedCenterY}")
            state.copy(
                captureFrameState = newState
            )
        }
    }

    fun resizeCaptureFrame(scaleMultiplier: Float, previewAspect: Float) {
        _uiState.update { state ->
            state.copy(
                captureFrameState = state.captureFrameState.resizeBy(scaleMultiplier, previewAspect)
            )
        }
    }

    fun stepCaptureFrameSize(enlarge: Boolean, previewAspect: Float = 3f / 4f) {
        _uiState.update { state ->
            state.copy(
                captureFrameState = state.captureFrameState.stepSize(enlarge, previewAspect)
            )
        }
    }

    fun resetCaptureFrame() {
        val focalMm = _uiState.value.shootingState.selectedFocalLength.focalLengthMm
        val targetFraction = if (focalMm <= 26f) 0.88f else (26.0f / focalMm).coerceIn(0.25f, 0.92f)
        _uiState.update { state ->
            state.copy(
                captureFrameState = state.captureFrameState.copy(
                    sizeFraction = targetFraction,
                    normalizedCenterX = 0.5f,
                    normalizedCenterY = 0.5f
                )
            )
        }
    }

    private fun createCaptureFrameSnapshot(aspectRatio: AspectRatioMode): CaptureFrameSnapshot {
        val currentFrameState = _uiState.value.captureFrameState
        val previewAspect = 1f / aspectRatio.ratio
        val normRect = currentFrameState.getNormalizedRect(previewAspect)
        val isFront = _uiState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
        val rot = camera?.cameraInfo?.sensorRotationDegrees ?: (if (isFront) 270 else 90)

        return CaptureFrameSnapshot(
            normalizedRect = normRect,
            previewAspect = previewAspect,
            sensorOrientation = rot,
            isFrontCamera = isFront,
            baseFocalLengthMm = currentFrameState.baseFocalLengthMm,
            effectiveFocalLengthMm = currentFrameState.effectiveFocalLengthMm
        )
    }

    fun toggleGrid() {
        val next = !_uiState.value.showGrid
        _uiState.update { it.copy(showGrid = next) }
        AppSettingsStore.get(context).setAssistiveGrid(next)
    }

    fun setGridEnabled(enabled: Boolean) {
        _uiState.update { it.copy(showGrid = enabled) }
        AppSettingsStore.get(context).setAssistiveGrid(enabled)
    }

    fun toggleLevel() {
        _uiState.update { it.copy(showLevel = !it.showLevel) }
    }

    fun setTimerSeconds(seconds: Int) {
        _uiState.update { it.copy(timerSeconds = seconds) }
    }

    fun updateTuning(update: (UserTuningConfig) -> UserTuningConfig) {
        _uiState.update {
            val updatedTuning = update(it.tuning)
            if (updatedTuning.exposureCompensation != it.tuning.exposureCompensation) {
                val step = if (it.exposureStep > 0f) it.exposureStep else 0.5f
                val targetIndex = (updatedTuning.exposureCompensation / step).roundToInt()
                    .coerceIn(it.minExposureIndex, it.maxExposureIndex)
                camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
                val updated = it.copy(tuning = updatedTuning, exposureIndex = targetIndex)
                glRenderer?.updateTuning(updated.tuning)
                updated
            } else {
                val updated = it.copy(tuning = updatedTuning)
                glRenderer?.updateTuning(updated.tuning)
                updated
            }
        }
    }

    fun resetTuning() {
        val defaultTuning = UserTuningConfig()
        glRenderer?.updateTuning(defaultTuning)
        camera?.cameraControl?.setExposureCompensationIndex(0)
        _uiState.update { it.copy(tuning = defaultTuning, exposureIndex = 0) }
    }

    fun tapToFocus(tapX: Float, tapY: Float, viewWidth: Int = 0, viewHeight: Int = 0) {
        val previewView = currentPreviewView
        if (previewView != null) {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(tapX, tapY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)
        } else {
            val cam = camera ?: return
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display ?: windowManager?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            } ?: return
            val metrics = context.resources.displayMetrics
            val targetW = if (viewWidth > 0) viewWidth.toFloat() else metrics.widthPixels.toFloat()
            val targetH = if (viewHeight > 0) viewHeight.toFloat() else metrics.heightPixels.toFloat()
            val factory = DisplayOrientedMeteringPointFactory(display, cam.cameraInfo, targetW, targetH)
            val point = factory.createPoint(tapX, tapY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        }
    }

    fun captureAndProcessPhoto(
        preset: CameraPreset,
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        onPhotoSaved: (Uri) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val capture = imageCapture ?: run {
            onError("Camera capture not ready")
            return
        }

        if (!isCapturingInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Capture already in progress, ignoring duplicate tap")
            return
        }

        // Freeze immutable composition frame snapshot at shutter trigger instant (null if frame overlay disabled)
        val frameSnapshot = if (_uiState.value.tuning.frameBorderEnabled) createCaptureFrameSnapshot(aspectRatio) else null

        // Branch into multi-frame computational photography pipeline when in NATURAL mode and forceMultiFrameHdr is explicitly active
        if (_uiState.value.engineMode == CameraEngineMode.NATURAL) {
            val compConfig = _uiState.value.computationalConfig
            if (compConfig.burstFrameCount > 1 && compConfig.forceMultiFrameHdr) {
                captureNaturalBurst(preset, aspectRatio, mirrorFrontCamera, frameSnapshot, onPhotoSaved, onError)
                return
            }
        }

        _uiState.update { it.copy(isCapturing = true) }
        startCaptureWatchdog()

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    cancelCaptureWatchdog()
                    isCapturingInProgress.set(false)
                    _uiState.update { it.copy(isCapturing = false) }
                    checkPendingRebind()

                    // Application-level coroutine scope: never aborted if user navigates to gallery
                    processingScope.launch {
                        try {
                            val uri = ImageProcessor.processAndSaveImage(
                                context = context,
                                imageProxy = image,
                                preset = preset,
                                tuning = _uiState.value.tuning,
                                aspectRatio = aspectRatio,
                                mirrorFrontCamera = mirrorFrontCamera,
                                engineMode = _uiState.value.engineMode,
                                naturalProfile = _uiState.value.naturalProfile,
                                naturalColorProfile = _uiState.value.naturalColorProfile,
                                compareWithOriginal = _uiState.value.debugCompareOriginal,
                                captureFrameSnapshot = frameSnapshot,
                                lensProfile = _uiState.value.shootingState.selectedLens,
                                isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF,
                                sensorIso = _uiState.value.liveIso,
                                exposureTimeNs = _uiState.value.liveExposureNs,
                                faces = _uiState.value.detectedFaces
                            )
                            if (uri != null) {
                                withContext(Dispatchers.Main) {
                                    onPhotoSaved(uri)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Failed to develop photograph")
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error developing captured photo", t)
                            withContext(Dispatchers.Main) {
                                onError(t.message ?: "Image processing failed")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cancelCaptureWatchdog()
                    isCapturingInProgress.set(false)
                    _uiState.update { it.copy(isCapturing = false) }
                    checkPendingRebind()
                    Log.e(TAG, "Hardware photo capture failed: ${exception.message}", exception)
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception.message ?: "Camera capture failed")
                    }
                }
            }
        )
    }

    fun captureAndProcessThreeDPhoto(
        preset: CameraPreset,
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        onVideoSaved: (Uri) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val capture = imageCapture ?: run {
            onError("Camera capture not ready")
            return
        }

        if (!isCapturingInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Capture already in progress, ignoring duplicate tap")
            return
        }

        val frameSnapshot = if (_uiState.value.tuning.frameBorderEnabled) createCaptureFrameSnapshot(aspectRatio) else null

        _uiState.update { it.copy(isCapturing = true, threeDProgressMessage = "Capturing 3D...") }
        startCaptureWatchdog(timeoutMs = 15000L)

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    cancelCaptureWatchdog()
                    isCapturingInProgress.set(false)
                    _uiState.update { it.copy(threeDProgressMessage = "Developing 3D Wigglegram...") }
                    checkPendingRebind()

                    processingScope.launch {
                        try {
                            val uri = ImageProcessor.processAndSaveThreeDImage(
                                context = context,
                                imageProxy = image,
                                preset = preset,
                                tuning = _uiState.value.tuning,
                                aspectRatio = aspectRatio,
                                mirrorFrontCamera = mirrorFrontCamera,
                                captureFrameSnapshot = frameSnapshot,
                                lensProfile = _uiState.value.shootingState.selectedLens,
                                isFlashActive = _uiState.value.flashMode != ImageCapture.FLASH_MODE_OFF,
                                faces = _uiState.value.detectedFaces,
                                onProgress = { progressText ->
                                    _uiState.update { it.copy(threeDProgressMessage = progressText) }
                                }
                            )

                            _uiState.update { it.copy(isCapturing = false, threeDProgressMessage = null) }

                            if (uri != null) {
                                withContext(Dispatchers.Main) {
                                    onVideoSaved(uri)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Failed to develop 3D photograph")
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error developing 3D photo", t)
                            _uiState.update { it.copy(isCapturing = false, threeDProgressMessage = null) }
                            withContext(Dispatchers.Main) {
                                onError(t.message ?: "3D image processing failed")
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cancelCaptureWatchdog()
                    isCapturingInProgress.set(false)
                    _uiState.update { it.copy(isCapturing = false, threeDProgressMessage = null) }
                    checkPendingRebind()
                    Log.e(TAG, "Hardware photo capture failed for 3D: ${exception.message}", exception)
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception.message ?: "Camera capture failed")
                    }
                }
            }
        )
    }

    private fun captureNaturalBurst(
        preset: CameraPreset,
        aspectRatio: AspectRatioMode,
        mirrorFrontCamera: Boolean,
        frameSnapshot: CaptureFrameSnapshot?,
        onPhotoSaved: (Uri) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val capture = imageCapture ?: run {
            isCapturingInProgress.set(false)
            onError("Camera capture not ready")
            return
        }

        val compConfig = _uiState.value.computationalConfig
        val targetFrames = compConfig.burstFrameCount.coerceIn(1, 5)
        val evOffsets = compConfig.evOffsets
        val tapTime = SystemClock.elapsedRealtime()

        _uiState.update { it.copy(isCapturing = true, isComputationalProcessing = true) }
        startCaptureWatchdog(timeoutMs = 12000L)

        val capturedFrames = ArrayList<CapturedFrame>()
        val cam = camera
        val previousExposureIndex = _uiState.value.exposureIndex

        fun processCapturedSequence() {
            cancelCaptureWatchdog()
            isCapturingInProgress.set(false)
            _uiState.update { it.copy(isCapturing = false) }
            checkPendingRebind()
            val captureLatencyMs = SystemClock.elapsedRealtime() - tapTime

            if (capturedFrames.isEmpty()) {
                _uiState.update { it.copy(isComputationalProcessing = false) }
                onError("No frames captured in computational burst")
                return
            }

            val sequence = FrameSequence(capturedFrames)
            val currentSceneProfile = _uiState.value.sceneAnalysis?.profile ?: ProcessingProfile.NaturalDefault
            val currentDetectedFaces = _uiState.value.detectedFaces

            processingScope.launch {
                try {
                    val uri = ImageProcessor.processComputationalPhoto(
                        context = context,
                        sequence = sequence,
                        config = _uiState.value.computationalConfig,
                        aspectRatio = aspectRatio,
                        mirrorFrontCamera = mirrorFrontCamera,
                        naturalProfile = _uiState.value.naturalProfile,
                        compareWithOriginal = _uiState.value.debugCompareOriginal,
                        captureLatencyMs = captureLatencyMs,
                        processingProfile = currentSceneProfile,
                        faces = currentDetectedFaces,
                        portraitConfig = _uiState.value.portraitConfig,
                        colorConfig = _uiState.value.colorScienceConfig,
                        cameraId = _uiState.value.selectedLens?.cameraId ?: "0",
                        captureFrameSnapshot = frameSnapshot,
                        onStatsUpdated = { stats ->
                            _uiState.update { it.copy(computationalStats = stats, isComputationalProcessing = false) }
                        }
                    )
                    if (uri != null) {
                        withContext(Dispatchers.Main) {
                            onPhotoSaved(uri)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onError("Failed to develop computational photograph")
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error developing computational photo", t)
                    _uiState.update { it.copy(isComputationalProcessing = false) }
                    withContext(Dispatchers.Main) {
                        onError(t.message ?: "Computational photo processing failed")
                    }
                } finally {
                    sequence.release()
                }
            }
        }

        fun captureNextFrame(frameIndex: Int) {
            if (frameIndex >= targetFrames) {
                // Restore user exposure compensation after bracketing
                cam?.cameraControl?.setExposureCompensationIndex(previousExposureIndex)
                processCapturedSequence()
                return
            }

            // Apply exposure bracketing offset if configured
            val ev = if (frameIndex < evOffsets.size) evOffsets[frameIndex] else 0.0f
            val step = if (_uiState.value.exposureStep > 0f) _uiState.value.exposureStep else 0.5f
            val targetIndex = (ev / step).roundToInt()
                .coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
            cam?.cameraControl?.setExposureCompensationIndex(targetIndex)

            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val rotation = image.imageInfo.rotationDegrees
                            val timestamp = image.imageInfo.timestamp
                            val width = image.width
                            val height = image.height
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            val captured = CapturedFrame(
                                index = frameIndex,
                                evOffset = ev,
                                iso = _uiState.value.liveIso,
                                exposureTimeNs = _uiState.value.liveExposureNs,
                                jpegBytes = bytes,
                                width = width,
                                height = height,
                                rotation = rotation,
                                timestampNs = timestamp
                            )
                            capturedFrames.add(captured)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed to read buffer for burst frame #$frameIndex", e)
                        } finally {
                            image.close() // Close ImageProxy immediately to free hardware surface
                        }

                        captureNextFrame(frameIndex + 1)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "Burst frame #$frameIndex capture error: ${exception.message}", exception)
                        if (capturedFrames.isNotEmpty()) {
                            // Safe fallback: proceed with captured subset
                            cam?.cameraControl?.setExposureCompensationIndex(previousExposureIndex)
                            processCapturedSequence()
                        } else {
                            cancelCaptureWatchdog()
                            isCapturingInProgress.set(false)
                            cam?.cameraControl?.setExposureCompensationIndex(previousExposureIndex)
                            _uiState.update { it.copy(isCapturing = false, isComputationalProcessing = false) }
                            checkPendingRebind()
                            ContextCompat.getMainExecutor(context).execute {
                                onError(exception.message ?: "Burst capture failed")
                            }
                        }
                    }
                }
            )
        }

        captureNextFrame(0)
    }

    fun updateComputationalConfig(transform: (ComputationalPhotoConfig) -> ComputationalPhotoConfig) {
        _uiState.update { it.copy(computationalConfig = transform(it.computationalConfig)) }
    }

    fun setForceMultiFrameHdr(force: Boolean) {
        _uiState.update { it.copy(computationalConfig = it.computationalConfig.copy(forceMultiFrameHdr = force)) }
    }

    fun setComputationalDebug(show: Boolean) {
        _uiState.update { it.copy(showComputationalDebug = show) }
    }

    fun updateSceneConfig(transform: (SceneProcessingConfig) -> SceneProcessingConfig) {
        val updated = transform(_uiState.value.sceneConfig)
        sceneAnalysisEngine.updateConfig(updated)
        _uiState.update { it.copy(sceneConfig = updated) }
    }

    fun setForceSceneOverride(scene: SceneType?) {
        updateSceneConfig { it.copy(forceSceneOverride = scene) }
    }

    fun updatePortraitConfig(transform: (com.craftcam.app.portrait.PortraitConfig) -> com.craftcam.app.portrait.PortraitConfig) {
        val updated = transform(_uiState.value.portraitConfig)
        _uiState.update { it.copy(portraitConfig = updated) }
    }

    fun setPortraitMode(mode: com.craftcam.app.portrait.PortraitMode) {
        updatePortraitConfig { it.copy(portraitMode = mode) }
    }

    fun setBokehStrength(strength: com.craftcam.app.portrait.BokehStrength) {
        updatePortraitConfig { it.copy(bokehStrength = strength) }
    }

    fun setPortraitDebugMode(mode: com.craftcam.app.portrait.DebugMaskMode) {
        updatePortraitConfig { it.copy(debugMaskMode = mode) }
    }

    fun updateColorScienceConfig(transform: (com.craftcam.app.color.ColorScienceConfig) -> com.craftcam.app.color.ColorScienceConfig) {
        val updated = transform(_uiState.value.colorScienceConfig)
        _uiState.update { it.copy(colorScienceConfig = updated) }
        glRenderer?.updateColorScience(updated)
    }

    fun setColorProfileType(type: com.craftcam.app.color.ColorProfileType) {
        updateColorScienceConfig { it.copy(activeProfileType = type) }
    }

    fun setTargetColorSpace(colorSpace: com.craftcam.app.color.ColorSpaceType) {
        updateColorScienceConfig { it.copy(targetColorSpace = colorSpace) }
    }

    fun capturePhoto(
        onImageCaptured: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture ?: return
        _uiState.update { it.copy(isCapturing = true) }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    _uiState.update { it.copy(isCapturing = false) }
                    onImageCaptured(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.update { it.copy(isCapturing = false) }
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    fun startRecording(
        onVideoSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val vc = videoCapture ?: return
        val filename = "CAMELEO_VIDEO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Cameleo")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pending = vc.output.prepareRecording(context, mediaStoreOutput)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                pending = pending.withAudioEnabled()
            } catch (e: SecurityException) {
                Log.w(TAG, "Microphone permission missing", e)
            }
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _uiState.update { it.copy(isRecording = true, recordingSeconds = 0) }
                }
                is VideoRecordEvent.Status -> {
                    val sec = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                    _uiState.update { it.copy(recordingSeconds = sec) }
                }
                is VideoRecordEvent.Finalize -> {
                    _uiState.update { it.copy(isRecording = false, recordingSeconds = 0) }
                    activeRecording = null
                    if (!event.hasError()) {
                        onVideoSaved(event.outputResults.outputUri)
                    } else {
                        onError(event.cause?.message ?: "Recording error")
                    }
                }
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun shutdown() {
        stopPreviewWatchdog()
        cancelCaptureWatchdog()
        isCapturingInProgress.set(false)
        previewSurface?.release()
        previewSurface = null
        stopRecording()
        cameraExecutor.shutdown()
        processingScope.cancel()
    }
}
