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
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.presets.PresetCatalog
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

    private val _uiState = MutableStateFlow(CameraUIState())
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private var currentPreviewView: PreviewView? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreset: CameraPreset = PresetCatalog.allPresets[0]

    var onCameraOrientationChanged: ((rotationDegrees: Int, isFront: Boolean, width: Int, height: Int) -> Unit)? = null

    fun setPreset(preset: CameraPreset) {
        if (currentPreset.id != preset.id) {
            currentPreset = preset
            if (_uiState.value.captureMode == CaptureMode.VIDEO) {
                bindCameraUseCases()
            }
        }
    }

    fun initialize(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        currentLifecycleOwner = lifecycleOwner
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
        val previewView = currentPreviewView ?: return

        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(_uiState.value.lensFacing)
            .build()

        // 1. Clean, 100% native 4:3 optical viewfinder (zero distortion, full FOV)
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        try {
            if (_uiState.value.captureMode == CaptureMode.PHOTO) {
                // 2A. Zero-lag instant shutter ImageCapture Use Case (4:3 full sensor)
                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(_uiState.value.flashMode)
                    .build()

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } else {
                // 2B. High-definition VideoCapture Use Case with vintage film overlay effects
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val prev = preview
                val vc = videoCapture
                if (prev != null && vc != null) {
                    val effect = VideoEffectManager.createVideoOverlayEffect(context, currentPreset)
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
                }
            }

            // Update Zoom and Exposure capabilities
            camera?.cameraInfo?.let { info ->
                info.zoomState.value?.let { zs ->
                    _uiState.update { it.copy(minZoom = zs.minZoomRatio, maxZoom = zs.maxZoomRatio.coerceAtMost(5.0f)) }
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
        val newFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.update { it.copy(lensFacing = newFacing) }
        bindCameraUseCases()
    }

    fun setFlashMode(flashMode: Int) {
        _uiState.update { it.copy(flashMode = flashMode) }
        imageCapture?.flashMode = flashMode
    }

    fun setExposureIndex(index: Int) {
        val clamped = index.coerceIn(_uiState.value.minExposureIndex, _uiState.value.maxExposureIndex)
        _uiState.update { it.copy(exposureIndex = clamped) }
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(_uiState.value.minZoom, _uiState.value.maxZoom)
        _uiState.update { it.copy(zoomRatio = clamped) }
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
        _uiState.update { it.copy(tuning = update(it.tuning)) }
    }

    fun resetTuning() {
        _uiState.update { it.copy(tuning = UserTuningConfig()) }
    }

    fun tapToFocus(xNorm: Float, yNorm: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(xNorm, yNorm)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
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
        stopRecording()
        cameraExecutor.shutdown()
    }
}
