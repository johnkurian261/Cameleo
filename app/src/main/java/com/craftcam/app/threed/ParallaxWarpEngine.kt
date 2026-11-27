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
        internal fun <T> bounce(viewpoints: List<T>): List<T> {
            require(viewpoints.size == LENS_OFFSETS.size)
            return listOf(viewpoints[0], viewpoints[1], viewpoints[2], viewpoints[3], viewpoints[2], viewpoints[1])
        }
    }

    /**
     * Warps [sourceBitmap] into the four physical lens viewpoints
     * using the estimated [disparityMap].
     *
     * @return Four distinct viewpoints in lens order.
     */
    fun generateViewpoints(
        sourceBitmap: Bitmap,
        disparityMap: DisparityMap,
        face: com.craftcam.app.portrait.FaceRegion? = null
    ): List<Bitmap> {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        sourceBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val dispGrid = resampleDisparity(srcPixels, width, height, disparityMap)
        val maxDisplacementPx = baselinePixels(width, disparityMap, face)
        val pivot = disparityMap.pivot

        // Generate 4 distinct viewpoint Bitmaps
        val views = ArrayList<Bitmap>(LENS_OFFSETS.size)
        try {
            for (offset in LENS_OFFSETS) {
                views.add(warpViewpoint(srcPixels, dispGrid, width, height, maxDisplacementPx * offset, pivot))
            }
            return views
        } catch (failure: Throwable) {
            views.forEach { it.recycle() }
            throw failure
        }
    }

    internal fun baselinePixels(width: Int, map: DisparityMap, face: com.craftcam.app.portrait.FaceRegion?): Float {
        val bounds = face?.bounds
        val faceArea = if (bounds == null) 0f else (bounds.right-bounds.left)*(bounds.bottom-bounds.top)
        val span = (map.foregroundDisparity - map.backgroundDisparity).coerceIn(0f, 1f)
        val portraitScale = 1f - 0.4f * (faceArea / 0.35f).coerceIn(0f, 1f)
        val confidenceScale = if (map.backendUsed == DepthBackend.CURRENT) 0.65f else 1f
        // Bound the width between extreme views without boosting a nearly flat depth map.
        val requested = width * config.maxParallaxDisplacement
        val budget = minOf(requested, requested * 0.45f / span.coerceAtLeast(0.01f)) * portraitScale * confidenceScale
        if (bounds == null) return budget
        // Reduce the entire camera baseline if the model stretches the central facial features.
        // Sampling inside the face avoids mistaking the hair/background boundary for facial relief.
        val samples = FloatArray(81) { i ->
            val u = bounds.left + (bounds.right - bounds.left) * (0.2f + 0.6f * (i % 9) / 8)
            val v = bounds.top + (bounds.bottom - bounds.top) * (0.28f + 0.44f * (i / 9) / 8)
            map.sampleBilinear(u,v)
        }
        samples.sort()
        val relief = samples[72] - samples[8]
        return if (relief > 0f) minOf(budget, width * 0.0025f / relief) else budget
    }

    private fun colorWeight(a: Int, b: Int): Float {
        val dr = (a shr 16 and 255) - (b shr 16 and 255)
        val dg = (a shr 8 and 255) - (b shr 8 and 255)
        val db = (a and 255) - (b and 255)
        return 1f / (1f + (dr * dr + dg * dg + db * db) / (3f * 24f * 24f))
    }

    internal fun resampleDisparity(srcPixels: IntArray, width: Int, height: Int, disparityMap: DisparityMap): FloatArray {
        // Resample disparity map to match sourceBitmap dimensions
        val dispGrid = FloatArray(width * height)
        val dispW = disparityMap.width
        val dispH = disparityMap.height
        val xRatio = (dispW - 1).toFloat() / (width - 1).coerceAtLeast(1)
        val yRatio = (dispH - 1).toFloat() / (height - 1).coerceAtLeast(1)

        // Map each depth texel back to the corresponding full-resolution color.
        val guideX = IntArray(dispW) { (it * (width - 1f) / (dispW - 1).coerceAtLeast(1)).roundToInt() }
        val guideRow = IntArray(dispH) { (it * (height - 1f) / (dispH - 1).coerceAtLeast(1)).roundToInt() * width }
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

                // At a depth step, plain bilinear interpolation makes a narrow
                // foreground object bend toward the wall behind it.
                if (maxOf(d00, d10, d01, d11) - minOf(d00, d10, d01, d11) > 0.08f) {
                    val center = srcPixels[rowOffset + x]
                    val w00 = (1f - tx) * (1f - ty) * colorWeight(center, srcPixels[guideRow[y0] + guideX[x0]])
                    val w10 = tx * (1f - ty) * colorWeight(center, srcPixels[guideRow[y0] + guideX[x1]])
                    val w01 = (1f - tx) * ty * colorWeight(center, srcPixels[guideRow[y1] + guideX[x0]])
                    val w11 = tx * ty * colorWeight(center, srcPixels[guideRow[y1] + guideX[x1]])
                    dispGrid[rowOffset + x] = (w00 * d00 + w10 * d10 + w01 * d01 + w11 * d11) / (w00 + w10 + w01 + w11)
                    continue
                }

                val top = d00 * (1f - tx) + d10 * tx
                val bottom = d01 * (1f - tx) + d11 * tx
                dispGrid[rowOffset + x] = top * (1f - ty) + bottom * ty
            }
        }

        return dispGrid
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
        val pixels = warpPixels(srcPixels, dispGrid, width, height, deltaX, pivot)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    internal fun warpPixels(
        srcPixels: IntArray, dispGrid: FloatArray, width: Int, height: Int,
        deltaX: Float, pivot: Float
    ): IntArray {
        val outPixels = IntArray(width * height)
        val tgtDisparity = FloatArray(width)
        val sourceColumn = IntArray(width)
        val shiftByColumn = FloatArray(width)

        for (y in 0 until height) {
            val rowOffset = y * width

            // 1. Project source disparity to target screen space with Z-buffering
            tgtDisparity.fill(-1.0f)

            for (x in 0 until width) {
                val d = dispGrid[rowOffset + x]
                // Perspective-projective disparity: d(Z) = deltaX * (d - pivot) / (1 + 0.35 * (1 - d))
                // Accelerates foreground pop while smoothly dampening background shifts
                val dx = deltaX * (d - pivot) / (1.0f + 0.35f * (1.0f - d))
                shiftByColumn[x] = dx
                val xt = x + dx
                val xt0 = floor(xt).toInt()
                val xt1 = xt0 + 1

                if (xt0 in 0 until width) {
                    if (d > tgtDisparity[xt0]) {
                        tgtDisparity[xt0] = d
                        sourceColumn[xt0] = x
                    }
                }
                if (xt > xt0 && xt1 in 0 until width) {
                    if (d > tgtDisparity[xt1]) {
                        tgtDisparity[xt1] = d
                        sourceColumn[xt1] = x
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

                    val neighbor = when {
                        leftD < 0f -> holeEnd + 1
                        rightD < 0f || leftD <= rightD -> holeStart - 1
                        else -> holeEnd + 1
                    }.coerceIn(0, width - 1)
                    // Newly exposed pixels can only borrow the visible far edge.
                    // Inverse sampling across the gap would paint the subject twice.
                    val anchor = sourceColumn[neighbor]
                    val step = if (neighbor < holeStart) -1 else 1
                    val farDepth = dispGrid[rowOffset + anchor]
                    var span = 0
                    while (span < holeEnd - holeStart) {
                        val next = anchor + step * (span + 1)
                        if (next !in 0 until width || abs(dispGrid[rowOffset + next] - farDepth) > 0.05f) break
                        span++
                    }
                    for (hx in holeStart..holeEnd) {
                        val distance = if (step < 0) hx - holeStart else holeEnd - hx
                        val sample = anchor + step * minOf(distance, span)
                        outPixels[rowOffset + hx] = srcPixels[rowOffset + sample]
                    }
                } else {
                    x++
                }
            }

            // 3. Sub-Pixel Backward Warping with Bilinear Interpolation
            for (xt in 0 until width) {
                val dt = tgtDisparity[xt]
                if (dt < 0f) continue
                val dx = shiftByColumn[sourceColumn[xt]]
                val xs = (xt - dx).coerceIn(0f, (width - 1).toFloat())

                val xs0 = floor(xs).toInt()
                val xs1 = (xs0 + 1).coerceAtMost(width - 1)
                val tx = xs - xs0

                // At a silhouette, the two source pixels can belong to different
                // surfaces. Only the surface that won the depth test is visible.
                val depth0 = dispGrid[rowOffset + xs0]
                val depth1 = dispGrid[rowOffset + xs1]
                if (abs(depth0 - depth1) > 0.08f) {
                    val visibleX = if (abs(depth0 - dt) <= abs(depth1 - dt)) xs0 else xs1
                    outPixels[rowOffset + xt] = srcPixels[rowOffset + visibleX]
                    continue
                }

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

        return outPixels
    }
}
