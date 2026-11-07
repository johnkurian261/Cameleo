package com.craftcam.app.captureframe

import android.graphics.Rect
import android.util.Log
import kotlin.math.roundToInt

/**
 * Pure Kotlin data representation of an integer pixel crop rectangle.
 * Completely immune to Android framework stub mocking limitations in JVM unit tests.
 */
data class FrameCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * Maps normalized capture framing coordinates from the UI/viewfinder coordinate system
 * into full-resolution pixel crop rectangles in upright oriented image space.
 */
object CaptureFrameMapper {

    private const val TAG = "CaptureFrameMapper"

    /**
     * Maps the immutable [CaptureFrameSnapshot] taken at shutter trigger to an integer pixel
     * [FrameCropRect] in the oriented image coordinate system.
     *
     * @param snapshot The capture framing snapshot frozen at shutter press.
     * @param orientedWidth Upright width of the captured photo in pixels.
     * @param orientedHeight Upright height of the captured photo in pixels.
     * @return Guaranteed valid [FrameCropRect] within [0, 0, orientedWidth, orientedHeight].
     */
    fun mapToCropRect(
        snapshot: CaptureFrameSnapshot?,
        orientedWidth: Int,
        orientedHeight: Int
    ): FrameCropRect {
        if (orientedWidth <= 0 || orientedHeight <= 0) {
            try {
                Log.e(TAG, "Invalid image dimensions for crop mapping: ${orientedWidth}x$orientedHeight")
            } catch (t: Throwable) {}
            return FrameCropRect(0, 0, 1, 1)
        }

        if (snapshot == null) {
            // Full-frame fallback
            return FrameCropRect(0, 0, orientedWidth, orientedHeight)
        }

        val norm = snapshot.normalizedRect

        // Map normalized coordinates directly to oriented pixel bounds
        var pixelLeft = (norm.left * orientedWidth).roundToInt()
        var pixelTop = (norm.top * orientedHeight).roundToInt()
        var pixelRight = (norm.right * orientedWidth).roundToInt()
        var pixelBottom = (norm.bottom * orientedHeight).roundToInt()

        // Clamp to physical pixel bounds with strict safety checks
        pixelLeft = pixelLeft.coerceIn(0, orientedWidth - 2)
        pixelTop = pixelTop.coerceIn(0, orientedHeight - 2)
        pixelRight = pixelRight.coerceIn(pixelLeft + 1, orientedWidth)
        pixelBottom = pixelBottom.coerceIn(pixelTop + 1, orientedHeight)

        val cropWidth = pixelRight - pixelLeft
        val cropHeight = pixelBottom - pixelTop

        if (cropWidth <= 0 || cropHeight <= 0) {
            try {
                Log.w(TAG, "Invalid mapped crop size (${cropWidth}x$cropHeight), falling back to full frame")
            } catch (t: Throwable) {}
            return FrameCropRect(0, 0, orientedWidth, orientedHeight)
        }

        try {
            Log.d(
                TAG,
                "Mapped capture crop: [${pixelLeft}, ${pixelTop}, ${pixelRight}, ${pixelBottom}] " +
                        "(${cropWidth}x$cropHeight px) from oriented ${orientedWidth}x$orientedHeight px"
            )
        } catch (t: Throwable) {}

        return FrameCropRect(pixelLeft, pixelTop, pixelRight, pixelBottom)
    }

    /**
     * Backward-compatible alias returning Android [Rect].
     */
    fun mapToImageCropRect(
        snapshot: CaptureFrameSnapshot?,
        orientedWidth: Int,
        orientedHeight: Int
    ): Rect {
        return mapToCropRect(snapshot, orientedWidth, orientedHeight).toAndroidRect()
    }
}
