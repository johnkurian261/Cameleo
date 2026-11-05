package com.craftcam.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture

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
    val dateStampEnabled: Boolean = true,
    val customYear: String? = null
)

data class CameraUIState(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 5.0f,
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
    val showLevel: Boolean = false
) {
    val availableLenses: List<CameraLens>
        get() = if (lensFacing == CameraSelector.LENS_FACING_FRONT) frontLenses else rearLenses
}
