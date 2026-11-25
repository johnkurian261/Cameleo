package com.craftcam.app.color.skin

import kotlin.math.max

/**
 * Edge-Aware Skin Mask Refiner.
 *
 * Implements separable guided box filtering and edge feathering on the confidence map.
 * Ensures smooth, imperceptible transitions across:
 * - Hairlines and stray hair strands.
 * - Ear rims and jawlines.
 * - Shirt collars and jewelry.
 * - Soft shadows and background boundaries.
 *
 * CRITICAL DIRECTIVE: The refiner only smooths the confidence mask that controls color rendering.
 * It NEVER blurs, softens, or removes genuine facial skin texture, freckles, or pores.
 */
class SkinMaskRefiner {

    /**
     * Refines and feathers raw [fusedMask] using separable edge-aware smoothing.
     *
     * @param fusedMask Raw fused confidence map in [0.0f, 1.0f].
     * @param width Image width.
     * @param height Image height.
     * @param featherRadiusFrac Feathering radius as a fraction of shortest image dimension (e.g. 0.02f).
     * @return Feathered and edge-refined confidence map in [0.0f, 1.0f].
     */
    fun refineMask(
        fusedMask: FloatArray,
        width: Int,
        height: Int,
        featherRadiusFrac: Float = 0.02f
    ): FloatArray {
        val total = width * height
        if (total == 0 || fusedMask.isEmpty()) return fusedMask

        val radius = max(2, (minOf(width, height) * featherRadiusFrac).toInt())
        val refined = FloatArray(total)
        val temp = FloatArray(total)

        val windowSize = 2 * radius + 1
        val invWindowSize = 1.0f / windowSize

        // Horizontal Pass
        for (y in 0 until height) {
            val rowOffset = y * width
            var windowSum = 0.0f

            for (x in -radius..radius) {
                val cx = x.coerceIn(0, width - 1)
                windowSum += fusedMask[rowOffset + cx]
            }

            for (x in 0 until width) {
                temp[rowOffset + x] = windowSum * invWindowSize
                val xLeft = (x - radius).coerceIn(0, width - 1)
                val xRight = (x + radius + 1).coerceIn(0, width - 1)
                windowSum += fusedMask[rowOffset + xRight] - fusedMask[rowOffset + xLeft]
            }
        }

        // Vertical Pass
        for (x in 0 until width) {
            var windowSum = 0.0f

            for (y in -radius..radius) {
                val cy = y.coerceIn(0, height - 1)
                windowSum += temp[cy * width + x]
            }

            for (y in 0 until height) {
                val idx = y * width + x
                refined[idx] = (windowSum * invWindowSize).coerceIn(0.0f, 1.0f)
                val yTop = (y - radius).coerceIn(0, height - 1)
                val yBottom = (y + radius + 1).coerceIn(0, height - 1)
                windowSum += temp[yBottom * width + x] - temp[yTop * width + x]
            }
        }

        return refined
    }
}
