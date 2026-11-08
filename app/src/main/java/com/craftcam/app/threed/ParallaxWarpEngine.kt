package com.craftcam.app.threed

import android.graphics.Bitmap
import kotlin.math.*

/**
 * Depth-aware horizontal parallax warping engine with authentic 4-lens stereoscopy
 * and sub-pixel backward warping (inverse mapping with occlusion hole inpainting).
 *
 * Simulates vintage 4-lens 35mm stereoscopic cameras (Nishika N8000 / Nimslo 3D):
 * 1. 4 physical lens viewpoints: [-1.0f, -0.333f, +0.333f, +1.0f]
 * 2. 6-frame bounce oscillation cycle: [Lens 1, Lens 2, Lens 3, Lens 4, Lens 3, Lens 2]
 * 3. Dynamic subject convergence pinning (pivot disparity) ensuring primary subject stays anchored
 * 4. Sub-pixel backward warping with target disparity projection and bilinear interpolation,
 *    guaranteeing 0 holes, 0 comb artifacts, and tack-sharp edges.
 */
class ParallaxWarpEngine(private val config: ThreeDConfig = ThreeDConfig()) {

    companion object {
        // Quad-lens normalized horizontal baseline positions
        private val LENS_OFFSETS = floatArrayOf(-1.0f, -0.333f, 0.333f, 1.0f)
        // Authentic 6-frame bounce sequence: Lens 1 -> 2 -> 3 -> 4 -> 3 -> 2
        private val BOUNCE_SEQUENCE = intArrayOf(0, 1, 2, 3, 2, 1)
    }

    /**
     * Warps [sourceBitmap] into the 6-frame stereoscopic bounce sequence
     * using the estimated [disparityMap].
     *
     * @return List of 6 generated viewpoint Bitmaps.
     */
    fun generateParallaxFrames(
        sourceBitmap: Bitmap,
        disparityMap: DisparityMap
    ): List<Bitmap> {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        sourceBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Resample disparity map to match sourceBitmap dimensions
        val dispGrid = FloatArray(totalPixels)
        val dispW = disparityMap.width
        val dispH = disparityMap.height
        val xRatio = (dispW - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val yRatio = (dispH - 1).toFloat() / (height - 1).coerceAtLeast(1)

        for (y in 0 until height) {
            val srcY = y * yRatio
            val y0 = floor(srcY).toInt()
            val y1 = (y0 + 1).coerceAtMost(dispH - 1)
            val ty = srcY - y0
            val rowOffset = y * width

            for (x in 0 until width) {
                val srcX = x * xRatio
                val x0 = floor(srcX).toInt()
                val x1 = (x0 + 1).coerceAtMost(dispW - 1)
                val tx = srcX - x0

                val d00 = disparityMap.values[y0 * dispW + x0]
                val d10 = disparityMap.values[y0 * dispW + x1]
                val d01 = disparityMap.values[y1 * dispW + x0]
                val d11 = disparityMap.values[y1 * dispW + x1]

                val top = d00 * (1f - tx) + d10 * tx
                val bottom = d01 * (1f - tx) + d11 * tx
                dispGrid[rowOffset + x] = top * (1f - ty) + bottom * ty
            }
        }

        val maxDisplacementPx = width * config.maxParallaxDisplacement
        val pivot = disparityMap.pivot

        // Generate 4 distinct viewpoint Bitmaps
        val distinctViewpoints = Array(4) { i ->
            warpViewpoint(
                srcPixels = srcPixels,
                dispGrid = dispGrid,
                width = width,
                height = height,
                deltaX = maxDisplacementPx * LENS_OFFSETS[i],
                pivot = pivot
            )
        }

        // Construct 6-frame bounce sequence: [Lens 1, Lens 2, Lens 3, Lens 4, Lens 3, Lens 2]
        val frames = ArrayList<Bitmap>(BOUNCE_SEQUENCE.size)
        for (seqIndex in BOUNCE_SEQUENCE.indices) {
            val lensIdx = BOUNCE_SEQUENCE[seqIndex]
            val baseBmp = distinctViewpoints[lensIdx]
            // Frame instances must be distinct so per-frame grain and shutter variations can be applied
            if (seqIndex < 4) {
                frames.add(baseBmp)
            } else {
                frames.add(baseBmp.copy(Bitmap.Config.ARGB_8888, true))
            }
        }

        return frames
    }

    /**
     * Synthesizes a single viewpoint at horizontal displacement [deltaX]
     * using Sub-Pixel Backward Warping with target disparity Z-buffering and background hole inpainting.
     */
    private fun warpViewpoint(
        srcPixels: IntArray,
        dispGrid: FloatArray,
        width: Int,
        height: Int,
        deltaX: Float,
        pivot: Float
    ): Bitmap {
        val outPixels = IntArray(width * height)
        val tgtDisparity = FloatArray(width)

        for (y in 0 until height) {
            val rowOffset = y * width

            // 1. Project source disparity to target screen space with Z-buffering
            tgtDisparity.fill(-1.0f)

            for (x in 0 until width) {
                val d = dispGrid[rowOffset + x]
                val dx = (d - pivot) * deltaX
                val xt = x + dx
                val xt0 = floor(xt).toInt()
                val xt1 = xt0 + 1

                if (xt0 in 0 until width) {
                    if (d > tgtDisparity[xt0]) {
                        tgtDisparity[xt0] = d
                    }
                }
                if (xt1 in 0 until width) {
                    if (d > tgtDisparity[xt1]) {
                        tgtDisparity[xt1] = d
                    }
                }
            }

            // 2. Fill disoccluded occlusion gaps with background disparity
            // In physical optics, when a nearer foreground surface moves aside, it reveals the background behind it.
            var x = 0
            while (x < width) {
                if (tgtDisparity[x] < 0f) {
                    val holeStart = x
                    while (x < width && tgtDisparity[x] < 0f) {
                        x++
                    }
                    val holeEnd = x - 1

                    val leftD = if (holeStart > 0) tgtDisparity[holeStart - 1] else -1f
                    val rightD = if (holeEnd < width - 1) tgtDisparity[holeEnd + 1] else -1f

                    val bgD = when {
                        leftD >= 0f && rightD >= 0f -> minOf(leftD, rightD)
                        leftD >= 0f -> leftD
                        rightD >= 0f -> rightD
                        else -> pivot
                    }

                    for (hx in holeStart..holeEnd) {
                        tgtDisparity[hx] = bgD
                    }
                } else {
                    x++
                }
            }

            // 3. Sub-Pixel Backward Warping with Bilinear Interpolation
            for (xt in 0 until width) {
                val dt = tgtDisparity[xt]
                val xs = (xt - (dt - pivot) * deltaX).coerceIn(0f, (width - 1).toFloat())

                val xs0 = floor(xs).toInt()
                val xs1 = (xs0 + 1).coerceAtMost(width - 1)
                val tx = xs - xs0

                val c0 = srcPixels[rowOffset + xs0]
                val c1 = srcPixels[rowOffset + xs1]

                val r0 = (c0 shr 16) and 0xFF
                val g0 = (c0 shr 8) and 0xFF
                val b0 = c0 and 0xFF

                val r1 = (c1 shr 16) and 0xFF
                val g1 = (c1 shr 8) and 0xFF
                val b1 = c1 and 0xFF

                val r = (r0 * (1f - tx) + r1 * tx).roundToInt().coerceIn(0, 255)
                val g = (g0 * (1f - tx) + g1 * tx).roundToInt().coerceIn(0, 255)
                val b = (b0 * (1f - tx) + b1 * tx).roundToInt().coerceIn(0, 255)

                outPixels[rowOffset + xt] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        frameBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return frameBitmap
    }
}
