package com.craftcam.app.captureframe

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Normalized rectangle representing coordinates within [0.0..1.0].
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.001f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.001f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/**
 * Single source of truth for CAMELEO's movable, resizable camera composition window.
 *
 * The capture framing window defines what portion of the live full-resolution sensor image
 * is ultimately cropped and developed into the final photograph.
 *
 * @param normalizedCenterX Center X position in normalized preview coordinates [0.0..1.0].
 * @param normalizedCenterY Center Y position in normalized preview coordinates [0.0..1.0].
 * @param sizeFraction Relative scale of the framing window [minSizeFraction..maxSizeFraction].
 *                     1.0 corresponds to full preview boundary (base focal length).
 *                     0.70 corresponds to ~37mm equivalent framing on a 26mm lens.
 * @param minSizeFraction Minimum allowed scale (e.g. 0.25 = tight crop, high focal length).
 * @param maxSizeFraction Maximum allowed scale (1.0 = entire preview).
 * @param aspectRatio Target aspect ratio of the framing window (width / height in portrait orientation).
 * @param baseFocalLengthMm Optical base 35mm equivalent focal length of active sensor (e.g. 26mm).
 * @param isVisible Whether the framing overlay is rendered.
 * @param outsideDimAlpha Darkness alpha of the area outside the capture window [0.0..1.0].
 * @param isDebugVisible Whether developer coordinate telemetry is displayed.
 */
data class CaptureFrameState(
    val normalizedCenterX: Float = 0.5f,
    val normalizedCenterY: Float = 0.5f,
    val sizeFraction: Float = 0.70f,
    val minSizeFraction: Float = 0.25f,
    val maxSizeFraction: Float = 1.0f,
    val aspectRatio: Float = 3f / 4f, // Standard 3:4 portrait ratio
    val baseFocalLengthMm: Float = 26.0f,
    val isVisible: Boolean = true,
    val outsideDimAlpha: Float = 0.28f,
    val isDebugVisible: Boolean = false
) {
    /**
     * Effective 35mm equivalent focal length corresponding to the current composition window crop:
     * effectiveMm = baseMm / sizeFraction
     *
     * Example on a 26mm main sensor:
     * - sizeFraction = 1.00 -> 26mm (Full sensor)
     * - sizeFraction = 0.70 -> ~37mm (Dazz-style 37mm portrait composition)
     * - sizeFraction = 0.52 -> 50mm (Standard 50mm normal lens field of view)
     * - sizeFraction = 0.37 -> 70mm (Short telephoto framing)
     * - sizeFraction = 0.26 -> 100mm (100mm portrait telephoto)
     */
    val effectiveFocalLengthMm: Float
        get() = (baseFocalLengthMm / sizeFraction.coerceIn(minSizeFraction, maxSizeFraction))

    /**
     * Clean focal length readout displayed directly above the top edge of the framing window:
     * Whole numbers when resting (e.g. "37mm", "50mm"), smooth fractional tenths during motion (e.g. "35.4mm").
     */
    val formattedFocalLength: String
        get() {
            val rounded = effectiveFocalLengthMm.roundToInt()
            return if (abs(effectiveFocalLengthMm - rounded) < 0.12f) {
                "${rounded}mm"
            } else {
                String.format(Locale.US, "%.1fmm", effectiveFocalLengthMm)
            }
        }

    /**
     * Calculates the normalized rectangular boundary [0.0..1.0] within the active preview frame.
     * The framing window is strictly centered at (0.5, 0.5) and scales symmetrically.
     */
    fun getNormalizedRect(previewAspect: Float): NormalizedRect {
        val safeAspect = if (previewAspect > 0.001f) previewAspect else (3f / 4f)
        val targetAspect = if (aspectRatio > 0.001f) aspectRatio else (3f / 4f)

        val normW: Float
        val normH: Float

        if (targetAspect <= safeAspect) {
            normW = sizeFraction.coerceIn(minSizeFraction, maxSizeFraction)
            normH = (normW * safeAspect / targetAspect).coerceIn(0.01f, 1.0f)
        } else {
            normH = sizeFraction.coerceIn(minSizeFraction, maxSizeFraction)
            normW = (normH * targetAspect / safeAspect).coerceIn(0.01f, 1.0f)
        }

        val halfW = normW / 2f
        val halfH = normH / 2f

        val left = (0.5f - halfW).coerceIn(0f, 1f - normW)
        val top = (0.5f - halfH).coerceIn(0f, 1f - normH)
        val right = (left + normW).coerceAtMost(1f)
        val bottom = (top + normH).coerceAtMost(1f)

        return NormalizedRect(left, top, right, bottom)
    }

    /**
     * Preserves centered framing: the rectangle cannot move around.
     */
    fun moveBy(dx: Float, dy: Float, previewAspect: Float): CaptureFrameState {
        return copy(normalizedCenterX = 0.5f, normalizedCenterY = 0.5f)
    }

    /**
     * Resizes the framing window by a relative scale multiplier, maintaining center anchor (0.5, 0.5).
     */
    fun resizeBy(scaleMultiplier: Float, previewAspect: Float): CaptureFrameState {
        val newSize = (sizeFraction * scaleMultiplier).coerceIn(minSizeFraction, maxSizeFraction)
        return copy(
            sizeFraction = newSize,
            normalizedCenterX = 0.5f,
            normalizedCenterY = 0.5f
        )
    }

    /**
     * Incremental step for hardware volume buttons:
     * enlarge = true  -> Increase rectangle size by 5% (wider composition)
     * enlarge = false -> Decrease rectangle size by 5% (tighter composition)
     */
    fun stepSize(enlarge: Boolean, previewAspect: Float, stepPercent: Float = 0.05f): CaptureFrameState {
        val multiplier = if (enlarge) (1f + stepPercent) else (1f - stepPercent)
        return resizeBy(multiplier, previewAspect)
    }
}

