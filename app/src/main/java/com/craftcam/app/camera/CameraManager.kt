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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.craftcam.app.gl.GLViewfinderRenderer
import com.craftcam.app.gl.GPUVideoProcessor
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.ImageProcessor
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

    var onCameraOrientationChanged: ((rotationDegrees: Int, isFront: Boolean, width: Int, height: Int) -> Unit)? = null

    private var lastSelectedRearLens: CameraLens? = null

    init {
        try {
            val discovered = CameraDiscovery.discover(context)
            val defaultRear = discovered.rearLenses.firstOrNull { kotlin.math.abs(it.equivalentZoom - 1.0f) < 0.1f }
                ?: discovered.rearLenses.firstOrNull()
            _uiState.update {
                it.copy(
                    rearLenses = discovered.rearLenses,
                    frontLenses = discovered.frontLenses,
                    selectedLens = defaultRear,
                    zoomRatio = defaultRear?.equivalentZoom ?: 1.0f
                )
            }
            lastSelectedRearLens = defaultRear
        } catch (e: Exception) {
            Log.e(TAG, "Lens initialization failed", e)
        }
    }

    fun setGLRenderer(renderer: GLViewfinderRenderer) {
        glRenderer = renderer
        glRenderer?.updatePreset(currentPreset)
        glRenderer?.updateTuning(_uiState.value.tuning)
    }

    fun setPreset(preset: CameraPreset) {
        if (currentPreset.id != preset.id) {
            currentPreset = preset
            glRenderer?.updatePreset(preset)
            // If in video mode, re-bind overlay effect for the active preset's video recording profile
            if (_uiState.value.captureMode == CaptureMode.VIDEO) {
                bindCameraUseCases()
            }
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

        if (_uiState.value.isCapturing) {
            Log.d(TAG, "Hardware photo capture in progress, skipping use-case unbind")
            return
        }

        try {
            provider.unbindAll()

            // 1. High-resolution 4:3 optical viewfinder
            val previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelector)
                .build()
                .also { prev ->
                    if (surfaceTex != null) {
                        prev.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
                            val res = request.resolution
                            surfaceTex.setDefaultBufferSize(res.width, res.height)
                            previewSurface?.release()
                            val currentSurface = Surface(surfaceTex)
                            previewSurface = currentSurface
                            request.provideSurface(currentSurface, ContextCompat.getMainExecutor(context)) {
                                currentSurface.release()
                                if (previewSurface == currentSurface) {
                                    previewSurface = null
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

            if (_uiState.value.captureMode == CaptureMode.PHOTO) {
                // 2A. Full native sensor resolution with hardware ISP multi-frame HDR & noise reduction
                val photoResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setResolutionSelector(photoResolutionSelector)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(95)
                    .setFlashMode(_uiState.value.flashMode)
                    .build()

                val prev = preview
                val ic = imageCapture
                if (prev != null && ic != null) {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        prev,
                        ic
                    )
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
                    _uiState.update { it.copy(minZoom = zs.minZoomRatio, maxZoom = zs.maxZoomRatio.coerceAtMost(10.0f)) }
                    val targetZoom = _uiState.value.selectedLens?.equivalentZoom ?: _uiState.value.zoomRatio
                    val clamped = targetZoom.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(clamped)
                }
                info.exposureState.let { es ->
                    _uiState.update {
                        it.copy(
                            minExposureIndex = es.exposureCompensationRange.lower,
                            maxExposureIndex = es.exposureCompensationRange.upper,
                            exposureStep = es.exposureCompensationStep.toFloat()
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    fun switchCamera() {
        val currentFacing = _uiState.value.lensFacing
        if (currentFacing == CameraSelector.LENS_FACING_BACK) {
            lastSelectedRearLens = _uiState.value.selectedLens
            val frontLens = _uiState.value.frontLenses.firstOrNull()
            _uiState.update {
                it.copy(
                    lensFacing = CameraSelector.LENS_FACING_FRONT,
                    selectedLens = frontLens,
                    zoomRatio = frontLens?.equivalentZoom ?: 1.0f
                )
            }
        } else {
            val restoreRear = lastSelectedRearLens
                ?: _uiState.value.rearLenses.firstOrNull { kotlin.math.abs(it.equivalentZoom - 1.0f) < 0.1f }
                ?: _uiState.value.rearLenses.firstOrNull()
            _uiState.update {
                it.copy(
                    lensFacing = CameraSelector.LENS_FACING_BACK,
                    selectedLens = restoreRear,
                    zoomRatio = restoreRear?.equivalentZoom ?: 1.0f
                )
            }
        }
        bindCameraUseCases()
    }

    fun selectLens(lens: CameraLens) {
        _uiState.update { it.copy(selectedLens = lens, zoomRatio = lens.equivalentZoom) }
        if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            lastSelectedRearLens = lens
        }
        val clamped = lens.equivalentZoom.coerceIn(_uiState.value.minZoom, _uiState.value.maxZoom)
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun setFlashMode(flashMode: Int) {
        _uiState.update { it.copy(flashMode = flashMode) }
        imageCapture?.flashMode = flashMode
    }

    fun setExposureIndex(index: Int) {
        val clamped = index.coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
        val evVal = clamped * _uiState.value.exposureStep
        _uiState.update {
            it.copy(
                exposureIndex = clamped,
                tuning = it.tuning.copy(exposureCompensation = evVal)
            )
        }
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(_uiState.value.minZoom, _uiState.value.maxZoom)
        val activeLenses = _uiState.value.availableLenses
        val closest = activeLenses.minByOrNull { kotlin.math.abs(it.equivalentZoom - clamped) }
        _uiState.update { it.copy(zoomRatio = clamped, selectedLens = closest) }
        if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK && closest != null) {
            lastSelectedRearLens = closest
        }
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _uiState.update { it.copy(aspectRatio = mode) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
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

    fun tapToFocus(tapX: Float, tapY: Float) {
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
            val factory = DisplayOrientedMeteringPointFactory(display, cam.cameraInfo, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
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

        if (_uiState.value.isCapturing) {
            Log.d(TAG, "Capture already in progress, ignoring duplicate tap")
            return
        }

        // Keep isCapturing active across both hardware capture and background photo development
        _uiState.update { it.copy(isCapturing = true) }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Instantly release the shutter button so the user can immediately take another photo
                    _uiState.update { it.copy(isCapturing = false) }

                    // Application-level coroutine scope: never aborted if user navigates to gallery
                    processingScope.launch {
                        try {
                            val uri = ImageProcessor.processAndSaveImage(
                                context = context,
                                imageProxy = image,
                                preset = preset,
                                tuning = _uiState.value.tuning,
                                aspectRatio = aspectRatio,
                                mirrorFrontCamera = mirrorFrontCamera
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
                    _uiState.update { it.copy(isCapturing = false) }
                    Log.e(TAG, "Hardware photo capture failed: ${exception.message}", exception)
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception.message ?: "Camera capture failed")
                    }
                }
            }
        )
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
        stopRecording()
        cameraExecutor.shutdown()
        processingScope.cancel()
    }
}
