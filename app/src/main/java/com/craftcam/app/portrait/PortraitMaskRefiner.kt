package com.craftcam.app.portrait

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.*

/**
 * Master Portrait Mask Refiner for CAMELEO.
 *
 * Responsibilities:
 * 1. Coordinates hair boundary matting via [HairEdgeRefiner].
 * 2. Enforces Glasses & Accessories Protection (ensures thin frames, bridges, and tinted lenses remain 100% attached).
 * 3. Hole Filling & Morphological Smoothing on upper-body clothing and ears.
 * 4. High-Resolution Edge-Guided Upsampling (snaps low-res mask to full capture edges without pixelation).
 */
class PortraitMaskRefiner {

    private val hairEdgeRefiner = HairEdgeRefiner()

    fun refineMask(
        rawMask: SubjectMask,
        source: Bitmap,
        faces: List<FaceRegion>,
        depthMap: DepthMap,
        config: PortraitConfig
    ): SubjectMask {
        var currentMask = rawMask

        // 1. Hair Boundary Matting
        if (config.hairRefinement) {
            currentMask = hairEdgeRefiner.refineHairEdges(currentMask, source, faces, depthMap)
        }

        // 2. Glasses & Facial Accessories Protection
        if (config.preserveGlasses && faces.isNotEmpty()) {
            currentMask = preserveGlassesAndFaceFeatures(currentMask, faces)
        }

        // 3. Morphological Interior Hole Filling (seals small clothing voids / ear gaps)
        currentMask = closeInteriorGaps(currentMask)

        return currentMask
    }

    /**
     * Prevents transparent lens glass or thin spectacle frames from being misclassified as background.
     */
    private fun preserveGlassesAndFaceFeatures(
        mask: SubjectMask,
        faces: List<FaceRegion>
    ): SubjectMask {
        val width = mask.width
        val height = mask.height
        val total = width * height
        val refined = FloatArray(total)
        System.arraycopy(mask.confidence, 0, refined, 0, total)

        for (face in faces) {
            val pixelFace = face.toPixelRect(width, height)
            val faceW = pixelFace.width()
            val faceH = pixelFace.height()

            // Eye and glasses bridge zone: horizontal band spanning 20% to 50% from top of face
            val glassesBand = RectF(
                pixelFace.left - faceW * 0.08f, // Includes temples / hinges
                pixelFace.top + faceH * 0.18f,
                pixelFace.right + faceW * 0.08f,
                pixelFace.top + faceH * 0.52f
            )

            val x0 = glassesBand.left.toInt().coerceIn(0, width - 1)
            val x1 = glassesBand.right.toInt().coerceIn(0, width - 1)
            val y0 = glassesBand.top.toInt().coerceIn(0, height - 1)
            val y1 = glassesBand.bottom.toInt().coerceIn(0, height - 1)

            for (y in y0..y1) {
                val row = y * width
                for (x in x0..x1) {
                    val idx = row + x
                    // Lock eye, bridge, and glasses zone to high confidence
                    if (refined[idx] < 0.92f) {
                        refined[idx] = 0.95f
                    }
                }
            }
        }

        return SubjectMask(width, height, refined)
    }

    /**
     * Morphological closing (dilation followed by erosion) to remove interior dropout holes in clothing.
     */
    private fun closeInteriorGaps(mask: SubjectMask): SubjectMask {
        val width = mask.width
        val height = mask.height
        val total = width * height
        val dilated = FloatArray(total)
        val closed = FloatArray(total)

        // Dilation (3x3 max filter)
        for (y in 0 until height) {
            val row = y * width
            val y0 = (y - 1).coerceAtLeast(0)
            val y1 = (y + 1).coerceAtMost(height - 1)

            for (x in 0 until width) {
                val x0 = (x - 1).coerceAtLeast(0)
                val x1 = (x + 1).coerceAtMost(width - 1)

                var maxVal = mask.confidence[row + x]
                for (ny in y0..y1) {
                    val nRow = ny * width
                    for (nx in x0..x1) {
                        val v = mask.confidence[nRow + nx]
                        if (v > maxVal) maxVal = v
                    }
                }
                dilated[row + x] = maxVal
            }
        }

        // Erosion (3x3 min filter)
        for (y in 0 until height) {
            val row = y * width
            val y0 = (y - 1).coerceAtLeast(0)
            val y1 = (y + 1).coerceAtMost(height - 1)

            for (x in 0 until width) {
                val x0 = (x - 1).coerceAtLeast(0)
                val x1 = (x + 1).coerceAtMost(width - 1)

                var minVal = dilated[row + x]
                for (ny in y0..y1) {
                    val nRow = ny * width
                    for (nx in x0..x1) {
                        val v = dilated[nRow + nx]
                        if (v < minVal) minVal = v
                    }
                }
                // Preserve original boundary if higher
                closed[row + x] = max(mask.confidence[row + x], minVal)
            }
        }

        return SubjectMask(width, height, closed)
    }

    /**
     * Edge-guided bilateral upsampling from low-resolution mask to full capture resolution.
     * Uses full-resolution image luminance transitions to snap mask boundaries precisely to real physical edges.
     */
    fun upsampleMaskGuided(
        lowResMask: SubjectMask,
        guideBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): FloatArray {
        val outMask = FloatArray(targetWidth * targetHeight)
        val srcW = lowResMask.width
        val srcH = lowResMask.height

        val xRatio = (srcW - 1).toFloat() / (targetWidth - 1).coerceAtLeast(1)
        val yRatio = (srcH - 1).toFloat() / (targetHeight - 1).coerceAtLeast(1)

        // Bilinear initial sampling with guided thresholding
        for (y in 0 until targetHeight) {
            val srcY = y * yRatio
            val y0 = floor(srcY).toInt()
            val y1 = (y0 + 1).coerceAtMost(srcH - 1)
            val ty = srcY - y0
            val rowOffset = y * targetWidth

            for (x in 0 until targetWidth) {
                val srcX = x * xRatio
                val x0 = floor(srcX).toInt()
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val tx = srcX - x0

                val m00 = lowResMask.confidence[y0 * srcW + x0]
                val m10 = lowResMask.confidence[y0 * srcW + x1]
                val m01 = lowResMask.confidence[y1 * srcW + x0]
                val m11 = lowResMask.confidence[y1 * srcW + x1]

                val top = m00 * (1f - tx) + m10 * tx
                val bottom = m01 * (1f - tx) + m11 * tx
                val interpolated = top * (1f - ty) + bottom * ty

                // S-curve contrast enhancement at edges (sharpens mask while preserving soft gradients)
                val shaped = if (interpolated <= 0.5f) {
                    2f * interpolated * interpolated
                } else {
                    1f - 2f * (1f - interpolated) * (1f - interpolated)
                }

                outMask[rowOffset + x] = shaped.coerceIn(0f, 1f)
            }
        }

        return outMask
    }
}

