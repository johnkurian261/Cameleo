package com.craftcam.app.captureframe

/**
 * Immutable snapshot of the camera composition framing window taken at the exact instant
 * of shutter button tap.
 *
 * This ensures that in-flight asynchronous photo processing (CameraX JPEG capture, orientation,
 * crop, LUT, grain, date stamp) is completely decoupled and protected from any subsequent
 * UI touch or drag events.
 */
data class CaptureFrameSnapshot(
    val normalizedRect: NormalizedRect,
    val previewAspect: Float,
    val sensorOrientation: Int,
    val isFrontCamera: Boolean,
    val baseFocalLengthMm: Float,
    val effectiveFocalLengthMm: Float,
    val timestamp: Long = System.currentTimeMillis()
)

