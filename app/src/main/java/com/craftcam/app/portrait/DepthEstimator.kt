package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import kotlin.math.*

/**
 * Interface for depth estimation providers.
 */
interface DepthEstimator {
    /**
     * Estimates relative inverse depth for the given frame and detected face regions.
     *
     * @param source Working bitmap (typically downsampled to depth resolution).
     * @param faces Detected face regions in the frame.
     * @param config Portrait configuration parameters.
     * @return Normalized relative DepthMap (0.0 near, 1.0 far).
     */
    fun estimateDepth(
        source: Bitmap,
        faces: List<FaceRegion>,
        config: PortraitConfig
    ): DepthMap
}

/**
 * Independent monocular depth estimation engine for CAMELEO.
 *
 * Combines:
 * 1. Optical Defocus Sharpness (monocular DfD energy cue via Sobel gradient magnitude).
 * 2. Spatial Face Anchor Constraints (pins detected subjects to the in-focus plane).
 * 3. Perspective Gradient Prior (vertical horizon/depth propagation).
 * 4. Edge-Preserving Cross-Bilateral Smoothing (locks depth boundaries to luminance transitions).
 *
 * Runs locally, 100% offline, with zero external cloud dependencies or network calls.
 */
class MonocularDepthEstimator : DepthEstimator {

    companion object {
        /**
         * Checks whether a camera hardware device supports Camera2 hardware depth output.
         */
        fun isHardwareDepthSupported(characteristics: CameraCharacteristics?): Boolean {
            if (characteristics == null) return false
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: return false
            val depthOutputCapability = 8 // CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT
            return capabilities.contains(depthOutputCapability)
        }
    }

    override fun estimateDepth(
        source: Bitmap,
        faces: List<FaceRegion>,
        config: PortraitConfig
    ): DepthMap {
        val targetW = config.depthWidth
        val targetH = config.depthHeight

        // Create downsampled analysis bitmap if needed
        val scaled = if (source.width == targetW && source.height == targetH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }

        val total = targetW * targetH
        val pixels = IntArray(total)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val luma = FloatArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        // 1. Compute Defocus Sharpness Map (Sobel gradient magnitude)
        val sharpness = FloatArray(total)
        var maxSharpness = 0.0001f

        for (y in 1 until targetH - 1) {
            val row = y * targetW
            val upRow = (y - 1) * targetW
            val downRow = (y + 1) * targetW

            for (x in 1 until targetW - 1) {
                val idx = row + x
                // Horizontal Sobel
                val gx = (luma[downRow + x + 1] + 2f * luma[row + x + 1] + luma[upRow + x + 1]) -
                         (luma[downRow + x - 1] + 2f * luma[row + x - 1] + luma[upRow + x - 1])
                // Vertical Sobel
                val gy = (luma[downRow + x - 1] + 2f * luma[downRow + x] + luma[downRow + x + 1]) -
                         (luma[upRow + x - 1] + 2f * luma[upRow + x] + luma[upRow + x + 1])

                val mag = sqrt(gx * gx + gy * gy)
                sharpness[idx] = mag
                if (mag > maxSharpness) maxSharpness = mag
            }
        }

        // Normalize sharpness
        for (i in 0 until total) {
            sharpness[i] = (sharpness[i] / maxSharpness).coerceIn(0f, 1f)
        }

        // Convert normalized face bounds to target depth coordinates
        val targetFaces = faces.map { it.toPixelRect(targetW, targetH) }

        // 2. Synthesize Initial Depth via Defocus + Spatial Geometry
        val rawDepth = FloatArray(total)
        val subjectAnchorDepth = 0.15f // In-focus portrait subject plane

        for (y in 0 until targetH) {
            val row = y * targetW
            val yNorm = y.toFloat() / (targetH - 1).coerceAtLeast(1)

            // Perspective prior: lower region (foreground floor/waist) is nearer; upper region recedes to background
            val perspectivePrior = 0.35f + (1f - yNorm) * 0.45f // 0.35 at bottom, 0.80 at top

            for (x in 0 until targetW) {
                val idx = row + x
                val xNorm = x.toFloat() / (targetW - 1).coerceAtLeast(1)

                // Check distance to nearest face center
                var minFaceDist = 1.0f
                var insideFace = false

                for (face in targetFaces) {
                    val cx = face.centerX() / targetW
                    val cy = face.centerY() / targetH
                    val dx = (xNorm - cx) * 1.2f // Horizontal weighting
                    val dy = (yNorm - cy)
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < minFaceDist) minFaceDist = dist

                    if (face.contains(x.toFloat(), y.toFloat())) {
                        insideFace = true
                    }
                }

                val depthVal = when {
                    insideFace -> {
                        // Inside confirmed face: lock depth to in-focus anchor
                        subjectAnchorDepth
                    }
                    targetFaces.isNotEmpty() -> {
                        // Interpolate outward from face anchor based on distance and local sharpness
                        val proximityWeight = (1f - minFaceDist * 1.8f).coerceIn(0f, 1f)
                        val sharpCues = 1f - sharpness[idx] // Sharp features are nearer
                        val blended = proximityWeight * subjectAnchorDepth +
                                      (1f - proximityWeight) * (perspectivePrior * 0.7f + sharpCues * 0.3f)
                        blended.coerceIn(0.12f, 1.0f)
                    }
                    else -> {
                        // Center-weighted fallback when no faces are detected
                        val dx = (xNorm - 0.5f) * 1.4f
                        val dy = (yNorm - 0.5f)
                        val centerDist = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                        val sharpCues = 1f - sharpness[idx]
                        (0.25f + centerDist * 0.55f + sharpCues * 0.2f).coerceIn(0.15f, 1.0f)
                    }
                }

                rawDepth[idx] = depthVal
            }
        }

        // 3. Edge-Preserving Cross-Bilateral Smoothing Pass
        // Smooths depth within homogeneous color areas while preserving object edges
        val smoothedDepth = FloatArray(total)
        val radius = 2
        val sigmaSpace = 2.0f
        val sigmaColor = 0.15f

        for (y in 0 until targetH) {
            val row = y * targetW
            for (x in 0 until targetW) {
                val idx = row + x
                val centerLuma = luma[idx]
                var weightSum = 0f
                var depthSum = 0f

                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, targetH - 1)
                    val nRow = ny * targetW
                    val distY2 = (dy * dy).toFloat()

                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, targetW - 1)
                        val nIdx = nRow + nx
                        val distX2 = (dx * dx).toFloat()

                        val spatialWeight = exp(-(distX2 + distY2) / (2f * sigmaSpace * sigmaSpace))
                        val lumaDiff = abs(luma[nIdx] - centerLuma)
                        val rangeWeight = exp(-(lumaDiff * lumaDiff) / (2f * sigmaColor * sigmaColor))

                        val weight = spatialWeight * rangeWeight
                        weightSum += weight
                        depthSum += weight * rawDepth[nIdx]
                    }
                }

                smoothedDepth[idx] = (depthSum / weightSum.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
            }
        }

        if (scaled != source) {
            scaled.recycle()
        }

        return DepthMap(targetW, targetH, smoothedDepth)
    }
}

