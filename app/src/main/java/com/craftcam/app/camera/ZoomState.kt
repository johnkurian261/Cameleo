package com.craftcam.app.camera

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Single source of truth for CAMELEO camera zoom and focal length state.
 *
 * @param zoomRatio Current CameraX zoom ratio (e.g. 1.0f, 1.42f, 3.0f).
 * @param displayFocalLengthMm 35mm equivalent focal length in millimeters (e.g. 26.0f, 37.0f).
 * @param minZoomRatio Minimum zoom ratio supported by active camera sensor.
 * @param maxZoomRatio Maximum zoom ratio supported by active camera sensor.
 * @param activeCameraId Active logical Camera2 ID ("0" for rear, "1" for front).
 * @param physicalCameraId Active physical Camera2 ID if logical multi-camera.
 * @param baseFocalLength35mm Base 35mm equivalent focal length at 1.0x optical zoom.
 * @param isOverlayVisible Whether the Dazz-style rounded rectangular zoom overlay is currently visible.
 * @param isDebugVisible Whether developer telemetry (Camera ID, Zoom Ratio, Range) is displayed.
 */
data class ZoomState(
    val zoomRatio: Float = 1.0f,
    val displayFocalLengthMm: Float = 26.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 10.0f,
    val activeCameraId: String = "0",
    val physicalCameraId: String? = null,
    val baseFocalLength35mm: Float = 26.0f,
    val isOverlayVisible: Boolean = false,
    val isDebugVisible: Boolean = false
) {
    /**
     * Formats focal length continuously:
     * - Whole millimeter (e.g. "35mm", "37mm", "50mm") when close to integer.
     * - Fractional millimeter (e.g. "35.4mm", "36.2mm") during smooth continuous zoom.
     */
    val formattedFocalLength: String
        get() {
            val rounded = displayFocalLengthMm.roundToInt()
            return if (abs(displayFocalLengthMm - rounded) < 0.12f) {
                "${rounded}mm"
            } else {
                String.format(Locale.US, "%.1fmm", displayFocalLengthMm)
            }
        }

    /**
     * Debug telemetry string for development inspection.
     */
    val debugSummary: String
        get() = "Cam: $activeCameraId • ${String.format(Locale.US, "%.2fx", zoomRatio)} • [${String.format(Locale.US, "%.1fx", minZoomRatio)}-${String.format(Locale.US, "%.1fx", maxZoomRatio)}]"
}

