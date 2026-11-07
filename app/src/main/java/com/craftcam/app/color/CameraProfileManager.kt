package com.craftcam.app.color

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Diagnostic metadata regarding hardware camera sensor calibration.
 */
data class CameraSensorTelemetry(
    val cameraId: String,
    val facing: String,
    val sensorPhysicalSize: String,
    val colorFilterArrangement: String,
    val referenceIlluminant1: String,
    val referenceIlluminant2: String,
    val hasCalibrationTransform1: Boolean,
    val hasCalibrationTransform2: Boolean,
    val hasForwardMatrix1: Boolean,
    val hasForwardMatrix2: Boolean,
    val hasRawSupport: Boolean,
    val activeColorProfile: String
)

/**
 * Hardware Camera Calibration and Sensor Profile Manager for CAMELEO.
 *
 * Responsibilities:
 * 1. Queries Camera2 sensor calibration metadata (illuminants, color transforms, forward matrices).
 * 2. Multi-Illuminant Interpolation (Standard A 2856K vs Daylight D65 6504K based on scene CCT).
 * 3. Sensor-Specific Profile Dispatcher (switches calibration when switching between rear wide, ultra-wide, and front sensors).
 */
class CameraProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraProfileManager"

        private val REAR_WIDE_MATRIX = ColorMatrix3x3(
            1.025f, -0.015f, -0.010f,
           -0.012f,  1.018f, -0.006f,
           -0.008f,  0.015f,  0.993f
        )

        private val FRONT_CAMERA_MATRIX = ColorMatrix3x3(
            1.015f, -0.010f, -0.005f,
           -0.008f,  1.010f, -0.002f,
           -0.005f,  0.012f,  0.993f
        )

        private val REAR_ULTRAWIDE_MATRIX = ColorMatrix3x3(
            1.030f, -0.018f, -0.012f,
           -0.015f,  1.020f, -0.005f,
           -0.010f,  0.018f,  0.992f
        )
    }

    private val cameraService = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /**
     * Retrieves the hardware sensor telemetry for development and calibration inspection.
     */
    fun querySensorTelemetry(cameraId: String): CameraSensorTelemetry {
        val chars = try {
            cameraService?.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read characteristics for $cameraId: ${e.message}")
            null
        }

        val facingInt = chars?.get(CameraCharacteristics.LENS_FACING)
        val facingStr = when (facingInt) {
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT (Selfie)"
            CameraCharacteristics.LENS_FACING_BACK -> "REAR (Primary)"
            else -> "EXTERNAL"
        }

        val physSize = chars?.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val physStr = if (physSize != null) "${physSize.width} x ${physSize.height} mm" else "Unknown"

        val cfa = chars?.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
        val cfaStr = when (cfa) {
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB (Bayer)"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG (Bayer)"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG (Bayer)"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR (Bayer)"
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB -> "RGB (Direct)"
            else -> "Standard Bayer"
        }

        val illum1 = chars?.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)
        val illum1Str = when (illum1?.toInt()) {
            17 -> "Standard A (2856K Tungsten)"
            21 -> "D65 (6504K Daylight)"
            20 -> "D55 (5500K Midday)"
            23 -> "D50 (5000K Horizon)"
            else -> "Illuminant $illum1"
        }

        val illum2 = chars?.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)
        val illum2Str = when (illum2?.toInt()) {
            17 -> "Standard A (2856K Tungsten)"
            21 -> "D65 (6504K Daylight)"
            20 -> "D55 (5500K Midday)"
            23 -> "D50 (5000K Horizon)"
            else -> if (illum2 != null) "Illuminant $illum2" else "None"
        }

        val hasCalib1 = chars?.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1) != null
        val hasCalib2 = chars?.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2) != null
        val hasFwd1 = chars?.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1) != null
        val hasFwd2 = chars?.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2) != null

        val caps = chars?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        return CameraSensorTelemetry(
            cameraId = cameraId,
            facing = facingStr,
            sensorPhysicalSize = physStr,
            colorFilterArrangement = cfaStr,
            referenceIlluminant1 = illum1Str,
            referenceIlluminant2 = illum2Str,
            hasCalibrationTransform1 = hasCalib1,
            hasCalibrationTransform2 = hasCalib2,
            hasForwardMatrix1 = hasFwd1,
            hasForwardMatrix2 = hasFwd2,
            hasRawSupport = hasRaw,
            activeColorProfile = "CAMELEO Natural v1.0"
        )
    }

    /**
     * Resolves the sensor-calibrated color matrix for the specified [cameraId] and [estimatedCct].
     * Supports multi-illuminant interpolation between Standard A (2856K) and D65 (6504K).
     */
    fun resolveColorMatrix(cameraId: String, estimatedCct: Int = 5500): ColorMatrix3x3 {
        val baseMatrix = when {
            cameraId == "1" -> FRONT_CAMERA_MATRIX
            cameraId == "2" || cameraId == "3" -> REAR_ULTRAWIDE_MATRIX
            else -> REAR_WIDE_MATRIX
        }

        // Multi-Illuminant Interpolation:
        // CCT <= 2856K -> 100% Tungsten / Standard A warm bias
        // CCT >= 6504K -> 100% Daylight D65 cool bias
        val t = ((estimatedCct - 2856f) / (6504f - 2856f)).coerceIn(0f, 1f)
        val tungstenAdjustment = ColorMatrix3x3(
            1.010f, -0.005f, -0.005f,
           -0.005f,  1.008f, -0.003f,
           -0.010f,  0.005f,  0.985f
        )

        return baseMatrix.interpolate(tungstenAdjustment, 1.0f - t)
    }
}

