package com.craftcam.app.threed

import android.graphics.Bitmap
import android.util.Log
import com.craftcam.app.portrait.DepthMap
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.portrait.MonocularDepthEstimator
import com.craftcam.app.portrait.PortraitConfig
import kotlin.math.floor

/**
 * Normalized Disparity Map for 3D stereoscopic parallax synthesis.
 *
 * Disparity convention:
 * - 1.0f = Nearest foreground plane (receives maximum parallax shift).
 * - 0.0f = Infinite background distance (receives minimal or reverse parallax shift).
 */
data class DisparityMap(
    val width: Int,
    val height: Int,
    val values: FloatArray,
    val pivot: Float = 0.45f
) {
    init {
        require(values.size == width * height) {
            "DisparityMap size (${values.size}) must match $width * $height"
        }
    }

    /**
     * Retrieves normalized disparity at integer coordinate (x, y).
     */
    fun getDisparity(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return values[cy * width + cx]
    }

    /**
     * Samples disparity at normalized coordinates (u, v) in [0.0, 1.0] using bilinear interpolation.
     */
    fun sampleBilinear(u: Float, v: Float): Float {
        val x = (u * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v * (height - 1)).coerceIn(0f, (height - 1).toFloat())

        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)

        val tx = x - x0
        val ty = y - y0

        val d00 = values[y0 * width + x0]
        val d10 = values[y0 * width + x1]
        val d01 = values[y1 * width + x0]
        val d11 = values[y1 * width + x1]

        val top = d00 * (1f - tx) + d10 * tx
        val bottom = d01 * (1f - tx) + d11 * tx
        return top * (1f - ty) + bottom * ty
    }
}

/**
 * Depth estimation and normalization engine for CAMELEO 3D mode.
 *
 * Converts monocular depth energy (DfD sharpness + face anchors + perspective prior)
 * into a continuous normalized DisparityMap [0.0 = background, 1.0 = foreground].
 */
class ThreeDDepthEngine {

    private val TAG = "ThreeDDepthEngine"
    private val depthEstimator = MonocularDepthEstimator()

    fun computeDisparity(
        source: Bitmap,
        faces: List<FaceRegion> = emptyList()
    ): DisparityMap {
        try {
            // Compute at 320x240 for high fidelity depth edge boundaries
            val config = PortraitConfig(
                depthWidth = 320,
                depthHeight = 240
            )

            val rawDepthMap: DepthMap = depthEstimator.estimateDepth(source, faces, config)
            val w = rawDepthMap.width
            val h = rawDepthMap.height
            val total = w * h
            val disparityValues = FloatArray(total)

            // DepthMap convention: 0.0 = near subject, 1.0 = far background.
            // Disparity convention: 1.0 = near subject, 0.0 = far background.
            var minDisp = 1.0f
            var maxDisp = 0.0f

            for (i in 0 until total) {
                val d = 1.0f - rawDepthMap.values[i]
                disparityValues[i] = d
                if (d < minDisp) minDisp = d
                if (d > maxDisp) maxDisp = d
            }

            // Contrast stretch disparity so the full 0.0 -> 1.0 dynamic range is utilized
            val range = (maxDisp - minDisp).coerceAtLeast(0.001f)
            for (i in 0 until total) {
                disparityValues[i] = ((disparityValues[i] - minDisp) / range).coerceIn(0f, 1f)
            }

            val pivot = calculatePivot(w, h, disparityValues, faces)
            return DisparityMap(w, h, disparityValues, pivot)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed monocular depth estimation, falling back to perspective prior", t)
            return createPerspectiveFallback(320, 240)
        }
    }

    /**
     * Determines the convergence / optical pivot disparity.
     * The subject at this disparity plane remains perfectly stationary in physical space
     * while foreground and background exhibit stereoscopic parallax around them.
     */
    private fun calculatePivot(
        w: Int,
        h: Int,
        disparityValues: FloatArray,
        faces: List<FaceRegion>
    ): Float {
        // Priority 1: Detected face anchors
        if (faces.isNotEmpty()) {
            var faceSum = 0.0f
            var faceCount = 0
            for (face in faces) {
                val xMin = (face.bounds.left * w).toInt().coerceIn(0, w - 1)
                val xMax = (face.bounds.right * w).toInt().coerceIn(0, w - 1)
                val yMin = (face.bounds.top * h).toInt().coerceIn(0, h - 1)
                val yMax = (face.bounds.bottom * h).toInt().coerceIn(0, h - 1)
                for (y in yMin..yMax) {
                    val row = y * w
                    for (x in xMin..xMax) {
                        faceSum += disparityValues[row + x]
                        faceCount++
                    }
                }
            }
            if (faceCount > 0) {
                val facePivot = (faceSum / faceCount).coerceIn(0.15f, 0.85f)
                Log.d(TAG, "Calculated dynamic face convergence pivot: $facePivot ($faceCount samples)")
                return facePivot
            }
        }

        // Priority 2: Center-weighted subject area (central 40% region with radial weighting)
        var weightedSum = 0.0f
        var weightTotal = 0.0f
        val x0 = (w * 0.30f).toInt().coerceIn(0, w - 1)
        val x1 = (w * 0.70f).toInt().coerceIn(0, w - 1)
        val y0 = (h * 0.30f).toInt().coerceIn(0, h - 1)
        val y1 = (h * 0.70f).toInt().coerceIn(0, h - 1)

        val cx = (w - 1) * 0.5f
        val cy = (h - 1) * 0.5f
        val maxDist = kotlin.math.sqrt(cx * cx + cy * cy).coerceAtLeast(1f)

        for (y in y0..y1) {
            val row = y * w
            val dy = y - cy
            for (x in x0..x1) {
                val dx = x - cx
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val weight = (1.0f - dist / maxDist).coerceIn(0.1f, 1.0f)
                val d = disparityValues[row + x]
                weightedSum += d * weight
                weightTotal += weight
            }
        }

        val pivot = if (weightTotal > 0f) {
            (weightedSum / weightTotal).coerceIn(0.15f, 0.85f)
        } else {
            0.45f
        }
        Log.d(TAG, "Calculated dynamic center convergence pivot: $pivot")
        return pivot
    }

    /**
     * Fallback perspective gradient prior when depth estimation is unavailable.
     * Bottom = near (1.0), top = far (0.0), with subtle center subject weighting.
     */
    private fun createPerspectiveFallback(w: Int, h: Int): DisparityMap {
        val total = w * h
        val values = FloatArray(total)
        for (y in 0 until h) {
            val yNorm = y.toFloat() / (h - 1).coerceAtLeast(1)
            val row = y * w
            for (x in 0 until w) {
                val xNorm = x.toFloat() / (w - 1).coerceAtLeast(1)
                val centerDist = kotlin.math.sqrt((xNorm - 0.5f) * (xNorm - 0.5f) + (yNorm - 0.5f) * (yNorm - 0.5f))
                // Bottom is near (higher disparity), top is far (lower disparity)
                val base = yNorm * 0.7f + 0.15f
                val centerBonus = (1f - centerDist * 1.5f).coerceIn(0f, 0.25f)
                values[row + x] = (base + centerBonus).coerceIn(0f, 1f)
            }
        }
        val pivot = calculatePivot(w, h, values, emptyList())
        return DisparityMap(w, h, values, pivot)
    }
}

