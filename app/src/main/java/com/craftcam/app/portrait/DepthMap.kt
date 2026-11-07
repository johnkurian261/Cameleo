package com.craftcam.app.portrait

import kotlin.math.floor

/**
 * Normalized relative depth map for CAMELEO computational portrait processing.
 *
 * Values are normalized to [0.0f, 1.0f]:
 * - 0.0f represents the nearest foreground plane (in-focus subject).
 * - 1.0f represents infinite background distance.
 *
 * NOTE: Values represent relative inverse depth (disparity) rather than metric physical meters.
 */
data class DepthMap(
    val width: Int,
    val height: Int,
    val values: FloatArray
) {
    init {
        require(values.size == width * height) {
            "DepthMap values array size (${values.size}) must match width * height ($width * $height = ${width * height})"
        }
    }

    /**
     * Retrieves the relative depth value at integer coordinate (x, y).
     */
    fun getDepth(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return values[cy * width + cx]
    }

    /**
     * Samples normalized depth at continuous coordinate (u, v) in [0.0f, 1.0f] using bilinear interpolation.
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

    /**
     * Upsamples this depth map to the target resolution using bilinear interpolation.
     */
    fun upsampleTo(targetWidth: Int, targetHeight: Int): FloatArray {
        val out = FloatArray(targetWidth * targetHeight)
        val xRatio = (width - 1).toFloat() / (targetWidth - 1).coerceAtLeast(1)
        val yRatio = (height - 1).toFloat() / (targetHeight - 1).coerceAtLeast(1)

        for (y in 0 until targetHeight) {
            val srcY = y * yRatio
            val y0 = floor(srcY).toInt()
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            val ty = srcY - y0
            val rowOffset = y * targetWidth

            for (x in 0 until targetWidth) {
                val srcX = x * xRatio
                val x0 = floor(srcX).toInt()
                val x1 = (x0 + 1).coerceAtMost(width - 1)
                val tx = srcX - x0

                val d00 = values[y0 * width + x0]
                val d10 = values[y0 * width + x1]
                val d01 = values[y1 * width + x0]
                val d11 = values[y1 * width + x1]

                val top = d00 * (1f - tx) + d10 * tx
                val bottom = d01 * (1f - tx) + d11 * tx
                out[rowOffset + x] = top * (1f - ty) + bottom * ty
            }
        }
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DepthMap

        if (width != other.width) return false
        if (height != other.height) return false
        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + values.contentHashCode()
        return result
    }
}

