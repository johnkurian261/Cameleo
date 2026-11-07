package com.craftcam.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as HardwareCameraManager
import android.os.Build
import android.util.Log
import android.util.SizeF
import kotlin.math.sqrt

/**
 * Sensor-aware camera zoom model for CAMELEO.
 *
 * Inspects CameraCharacteristics to extract actual physical focal lengths,
 * sensor dimensions, and zoom ratio bounds rather than relying on hardcoded values.
 */
class ZoomModel(private val context: Context) {

    companion object {
        private const val TAG = "ZoomModel"
        // 35mm full frame sensor diagonal = sqrt(36^2 + 24^2) = 43.2667mm
        private const val FULL_FRAME_DIAGONAL_MM = 43.2667f
    }

    data class CameraZoomProfile(
        val cameraId: String,
        val physicalCameraId: String? = null,
        val physicalFocalLengthMm: Float,
        val baseEquivalentFocalLength35mm: Float,
        val minZoomRatio: Float,
        val maxZoomRatio: Float
    )

    /**
     * Queries the hardware characteristics for the specified camera and builds a zoom profile.
     */
    fun queryProfile(cameraId: String, physicalCameraId: String? = null): CameraZoomProfile {
        val hwManager = context.getSystemService(Context.CAMERA_SERVICE) as? HardwareCameraManager
            ?: return fallbackProfile(cameraId)

        return try {
            val targetId = physicalCameraId ?: cameraId
            val chars = hwManager.getCameraCharacteristics(targetId)

            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val physicalFocal = focalLengths?.firstOrNull() ?: if (cameraId == "1") 3.8f else 5.4f

            val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: SizeF(5.6f, 4.2f)
            val sensorDiag = sqrt(sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height)
            val cropFactor = if (sensorDiag > 0.5f) FULL_FRAME_DIAGONAL_MM / sensorDiag else 4.8f
            val baseEq35 = (physicalFocal * cropFactor).coerceIn(10.0f, 150.0f)

            var minZ = 1.0f
            var maxZ = 10.0f

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { range ->
                    minZ = range.lower
                    maxZ = range.upper
                }
            } else {
                chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { maxDigital ->
                    maxZ = maxDigital.coerceAtLeast(1.0f)
                }
            }

            CameraZoomProfile(
                cameraId = cameraId,
                physicalCameraId = physicalCameraId,
                physicalFocalLengthMm = physicalFocal,
                baseEquivalentFocalLength35mm = baseEq35,
                minZoomRatio = minZ,
                maxZoomRatio = maxZ.coerceAtMost(10.0f) // Keep zoom manageable for photographic quality
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error querying zoom characteristics for camera $cameraId", e)
            fallbackProfile(cameraId)
        }
    }

    private fun fallbackProfile(cameraId: String): CameraZoomProfile {
        val isFront = cameraId == "1"
        return CameraZoomProfile(
            cameraId = cameraId,
            physicalCameraId = null,
            physicalFocalLengthMm = if (isFront) 3.8f else 5.4f,
            baseEquivalentFocalLength35mm = 26.0f,
            minZoomRatio = 1.0f,
            maxZoomRatio = 10.0f
        )
    }

    /**
     * Calculates effective 35mm equivalent focal length from base equivalent focal length and zoom ratio.
     * effectiveFocalLength = baseFocalLength35mm * zoomRatio
     */
    fun calculateDisplayFocalLengthMm(baseEquivalent35mm: Float, zoomRatio: Float): Float {
        return (baseEquivalent35mm * zoomRatio).coerceAtLeast(1.0f)
    }
}

