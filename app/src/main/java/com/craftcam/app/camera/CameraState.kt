package com.craftcam.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import com.craftcam.app.model.CameraEngineMode
import com.craftcam.app.model.GrainBlendMode
import com.craftcam.app.model.NaturalCameraProfile

enum class AspectRatioMode(val displayName: String, val ratio: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_1_1("1:1", 1f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_3_2("3:2", 3f / 2f)
}

enum class CaptureMode(val displayName: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO")
}

/**
 * Flag to pause video recording feature and focus all hardware pipeline,
 * latency, and UI controls purely on still photography and authentic film rendering.
 * Set to false to re-enable video recording.
 */
const val IS_VIDEO_FEATURE_PAUSED = true

data class UserTuningConfig(
    val filterIntensity: Float = 0.70f,     // 0.0 to 1.0 (default 70% for FXNR analog film grade)
    val exposureCompensation: Float = 0.0f, // -2.0 to +2.0 EV
    val temperatureOffset: Float = 0.0f,    // -0.5 (cool) to +0.5 (warm)
    val tintOffset: Float = 0.0f,           // -0.5 (green) to +0.5 (magenta)
    val contrastOffset: Float = 0.0f,       // -0.3 to +0.3
    val saturationOffset: Float = 0.0f,     // -0.5 to +0.5
    val grainBoost: Float = 1.0f,           // 0.0 (off) to 2.5 (heavy)
    val vignetteBoost: Float = 1.0f,        // 0.0 (none) to 2.0 (heavy)
    val halationEnabled: Boolean = true,
    val lightLeakEnabled: Boolean = true,
    val softLensDiffusion: Boolean = false,
    val dustEnabled: Boolean = true,
    val frameBorderEnabled: Boolean = true,
    val dateStampEnabled: Boolean = true,
    val customYear: String? = null,
    val preLinearizationEnabled: Boolean = true,
    val preLinearizationStrength: Float = 1.0f, // 0.0 = bypassed, 1.0 = full ISP contrast flattening
    val debugGrainAsset: String? = null, // Developer debug grain asset override (debug mode only)
    val debugGrainBlendMode: GrainBlendMode? = null, // Developer debug blend mode override (debug mode only)
    val debugProceduralGrainBoost: Float = 0.0f, // 0.0 (OFF), 0.25, 0.50, 1.0 (subtle procedural overlay)
    // Section 24 Visual A/B Testing developer stage bypass controls:
    val debugStagePreLinearization: Boolean = true,
    val debugStageLut: Boolean = true,
    val debugStageToneCurve: Boolean = true,
    val debugStageGrain: Boolean = true,
    val debugStageHalation: Boolean = true,
    val debugStageOptics: Boolean = true,
    val debugStageVignette: Boolean = true
)

data class CameraUIState(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 5.0f,
    val zoomState: ZoomState = ZoomState(),
    val captureFrameState: com.craftcam.app.captureframe.CaptureFrameState = com.craftcam.app.captureframe.CaptureFrameState(),
    val rearLenses: List<CameraLens> = emptyList(),
    val frontLenses: List<CameraLens> = emptyList(),
    val selectedLens: CameraLens? = null,
    val exposureIndex: Int = 0,
    val minExposureIndex: Int = -4,
    val maxExposureIndex: Int = 4,
    val exposureStep: Float = 0.5f,
    val aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
    val tuning: UserTuningConfig = UserTuningConfig(),
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val timerSeconds: Int = 0, // 0 = off, 3, 10
    val timerCountdown: Int? = null,
    val showGrid: Boolean = false,
    val showLevel: Boolean = false,
    val engineMode: CameraEngineMode = CameraEngineMode.FILM,
    val naturalProfile: NaturalCameraProfile = NaturalCameraProfile.S21_PROFILE,
    val showNaturalDebug: Boolean = false,
    val debugCompareOriginal: Boolean = false, // A/B Comparison: Original Camera Capture vs CAMELEO Natural
    val proConfig: com.craftcam.app.model.ProCameraConfig = com.craftcam.app.model.ProCameraConfig(),
    val hardwareCapabilities: com.craftcam.app.model.CameraHardwareCapabilities? = null,
    val histogramBins: IntArray = IntArray(256),
    val liveIso: Int = 100,
    val liveExposureNs: Long = 10_000_000L,
    val liveFocusDistance: Float = 0f,
    val computationalConfig: com.craftcam.app.computational.ComputationalPhotoConfig = com.craftcam.app.computational.ComputationalPhotoConfig(),
    val computationalStats: com.craftcam.app.computational.ComputationalStats? = null,
    val showComputationalDebug: Boolean = false,
    val isComputationalProcessing: Boolean = false,
    val sceneConfig: com.craftcam.app.config.SceneProcessingConfig = com.craftcam.app.config.SceneProcessingConfig(),
    val sceneAnalysis: com.craftcam.app.scene.SceneAnalysisResult? = null,
    val detectedFaces: List<com.craftcam.app.portrait.FaceRegion> = emptyList(),
    val portraitConfig: com.craftcam.app.portrait.PortraitConfig = com.craftcam.app.portrait.PortraitConfig(),
    val portraitBokehActive: Boolean = false,
    val colorScienceConfig: com.craftcam.app.color.ColorScienceConfig = com.craftcam.app.color.ColorScienceConfig(),
    val sensorTelemetry: com.craftcam.app.color.CameraSensorTelemetry? = null,
    val shootingState: com.craftcam.app.camera.profile.CameraShootingState = com.craftcam.app.camera.profile.CameraShootingState()
) {
    val availableLenses: List<CameraLens>
        get() = if (lensFacing == CameraSelector.LENS_FACING_FRONT) frontLenses else rearLenses
}
