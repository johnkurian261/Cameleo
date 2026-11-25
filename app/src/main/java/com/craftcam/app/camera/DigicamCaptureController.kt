package com.craftcam.app.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop

/**
 * Controller for Genuine Minimally Processed Digicam Capture.
 *
 * Enforces single-frame capture discipline on Camera2 / CameraX:
 * 1. Checks CameraCharacteristics before requesting options.
 * 2. Requests NOISE_REDUCTION_MODE_OFF (or MINIMAL / FAST) to preserve sensor noise texture.
 * 3. Requests EDGE_MODE_OFF (or FAST) to eliminate aggressive modern haloing.
 * 4. Disables scene modes (CONTROL_SCENE_MODE_DISABLED) to prevent vendor Night/HDR extensions.
 * 5. Requests TONEMAP_MODE_CONTRAST_CURVE or FAST to avoid aggressive multi-frame shadow lifting.
 * 6. Maintains separate capture policies for normal photography vs hardware flash photography.
 */
class DigicamCaptureController {

    companion object {
        private const val TAG = "DigicamCapture"
    }

    data class AppliedCaptureSettings(
        val noiseReductionMode: Int?,
        val edgeMode: Int?,
        val aberrationMode: Int?,
        val tonemapMode: Int?,
        val distortionCorrectionMode: Int?,
        val sceneModeDisabled: Boolean,
        val isFlashActive: Boolean
    )

    /**
     * Configures Camera2 capture request options on [captureExtender] for vintage digicam capture.
     */
    fun configureCapture(
        captureExtender: Camera2Interop.Extender<*>,
        characteristics: CameraCharacteristics,
        isFlashActive: Boolean
    ): AppliedCaptureSettings {
        fun supports(key: CameraCharacteristics.Key<IntArray>, mode: Int): Boolean {
            return characteristics.get(key)?.contains(mode) == true
        }

        // 1. Noise Reduction: Prefer OFF to capture raw sensor texture; fallback to MINIMAL (API 31+) or FAST
        val nrMode = when {
            supports(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES, CaptureRequest.NOISE_REDUCTION_MODE_OFF) ->
                CaptureRequest.NOISE_REDUCTION_MODE_OFF
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    supports(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES, 3 /* MINIMAL */) -> 3
            supports(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES, CaptureRequest.NOISE_REDUCTION_MODE_FAST) ->
                CaptureRequest.NOISE_REDUCTION_MODE_FAST
            else -> null
        }
        nrMode?.let {
            captureExtender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, it)
        }

        // 2. Edge Enhancement: Prefer OFF or FAST to eliminate heavy unsharp mask halos
        val edgeMode = when {
            supports(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES, CaptureRequest.EDGE_MODE_OFF) ->
                CaptureRequest.EDGE_MODE_OFF
            supports(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES, CaptureRequest.EDGE_MODE_FAST) ->
                CaptureRequest.EDGE_MODE_FAST
            else -> null
        }
        edgeMode?.let {
            captureExtender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, it)
        }

        // 3. Aberration Correction: Prefer OFF or FAST
        val abMode = when {
            supports(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF) ->
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF
            supports(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST) ->
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST
            else -> null
        }
        abMode?.let {
            captureExtender.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, it)
        }

        // 4. Tone Mapping: Prefer FAST or CONTRAST_CURVE over HIGH_QUALITY multi-frame HDR
        val tonemapMode = when {
            supports(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES, CaptureRequest.TONEMAP_MODE_FAST) ->
                CaptureRequest.TONEMAP_MODE_FAST
            supports(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE) ->
                CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
            else -> null
        }
        tonemapMode?.let {
            captureExtender.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, it)
        }

        // 5. Distortion Correction: OFF to retain natural compact lens curvature where supported
        var distMode: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (supports(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES, CaptureRequest.DISTORTION_CORRECTION_MODE_OFF)) {
                distMode = CaptureRequest.DISTORTION_CORRECTION_MODE_OFF
                captureExtender.setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE, distMode)
            }
        }

        // 6. Scene Mode: Disable vendor Night/HDR extensions
        var sceneDisabled = false
        if (supports(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)) {
            captureExtender.setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            sceneDisabled = true
        }

        // 7. Flash Policy: In flash photography, ensure AE and flash strobe sync operate normally
        if (isFlashActive) {
            captureExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
        }

        return AppliedCaptureSettings(
            noiseReductionMode = nrMode,
            edgeMode = edgeMode,
            aberrationMode = abMode,
            tonemapMode = tonemapMode,
            distortionCorrectionMode = distMode,
            sceneModeDisabled = sceneDisabled,
            isFlashActive = isFlashActive
        )
    }
}
