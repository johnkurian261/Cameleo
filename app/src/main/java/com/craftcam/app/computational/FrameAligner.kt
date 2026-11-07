package com.craftcam.app.computational

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result of handheld image registration between two frames.
 */
data class AlignmentResult(
    /** Horizontal translation in full-resolution pixels (positive means target shifted right relative to reference). */
    val dx: Float,
    /** Vertical translation in full-resolution pixels (positive means target shifted down relative to reference). */
    val dy: Float,
    /** Registration confidence in [0.0f, 1.0f]. High confidence indicates reliable registration. */
    val confidence: Float,
    /** Minimum mean absolute difference per pixel at optimal alignment. */
    val meanDifference: Float
)

object FrameAligner {

    private const val SEARCH_RADIUS = 14 // Search +/- 14 downscaled pixels (+/- 56 full-res pixels)
    private const val SAMPLE_SIZE = 4   // 1/4 resolution downsampling

    /**
     * Estimates translation vector (dx, dy) aligning [target] frame onto [reference] frame.
     * Operates purely on downscaled luminance representations with zero full-res Bitmap allocations.
     */
    fun align(reference: CapturedFrame, target: CapturedFrame): AlignmentResult {
        val refY = reference.getDownsampledY(SAMPLE_SIZE)
        val tgtY = target.getDownsampledY(SAMPLE_SIZE)

        val w = reference.downsampledWidth
        val h = reference.downsampledHeight

        if (w <= 0 || h <= 0 || w != target.downsampledWidth || h != target.downsampledHeight) {
            return AlignmentResult(0f, 0f, 0f, 1.0f)
        }

        // Evaluate central 50% ROI where handheld camera shake is most coherent
        val roiX0 = w / 4
        val roiX1 = 3 * w / 4
        val roiY0 = h / 4
        val roiY1 = 3 * h / 4
        val roiWidth = roiX1 - roiX0
        val roiHeight = roiY1 - roiY0

        val step = 2 // Subsample evaluation grid inside ROI for speed
        var minSad = Double.MAX_VALUE
        var bestDx = 0
        var bestDy = 0

        // 1. Coarse search: step 2 across search window
        for (dy in -SEARCH_RADIUS..SEARCH_RADIUS step 2) {
            for (dx in -SEARCH_RADIUS..SEARCH_RADIUS step 2) {
                val sad = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, dx, dy, step)
                if (sad < minSad) {
                    minSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // 2. Fine search: step 1 in neighborhood around coarse minimum
        val fineRadius = 2
        for (dy in (bestDy - fineRadius)..(bestDy + fineRadius)) {
            for (dx in (bestDx - fineRadius)..(bestDx + fineRadius)) {
                if (abs(dx) > SEARCH_RADIUS || abs(dy) > SEARCH_RADIUS) continue
                val sad = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, dx, dy, step = 1)
                if (sad < minSad) {
                    minSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // 3. Subpixel refinement via 1D quadratic peak interpolation
        val sadL = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, bestDx - 1, bestDy, step = 1)
        val sadR = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, bestDx + 1, bestDy, step = 1)
        val sadT = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, bestDx, bestDy - 1, step = 1)
        val sadB = computeSad(refY, tgtY, w, h, roiX0, roiX1, roiY0, roiY1, bestDx, bestDy + 1, step = 1)

        val denomX = 2.0 * (sadL - 2.0 * minSad + sadR)
        val subDx = if (abs(denomX) > 1e-5) {
            val delta = ((sadL - sadR) / denomX).toFloat().coerceIn(-0.5f, 0.5f)
            bestDx + delta
        } else {
            bestDx.toFloat()
        }

        val denomY = 2.0 * (sadT - 2.0 * minSad + sadB)
        val subDy = if (abs(denomY) > 1e-5) {
            val delta = ((sadT - sadB) / denomY).toFloat().coerceIn(-0.5f, 0.5f)
            bestDy + delta
        } else {
            bestDy.toFloat()
        }

        // 4. Scale translation up to full-resolution space
        val fullDx = subDx * SAMPLE_SIZE.toFloat()
        val fullDy = subDy * SAMPLE_SIZE.toFloat()

        val evaluatedPixels = (roiWidth / 1) * (roiHeight / 1)
        val meanDiff = (minSad / max(1, evaluatedPixels)).toFloat()
        val confidence = (1.0f - meanDiff * 4.0f).coerceIn(0.0f, 1.0f)

        return AlignmentResult(
            dx = fullDx,
            dy = fullDy,
            confidence = confidence,
            meanDifference = meanDiff
        )
    }

    private fun computeSad(
        ref: FloatArray,
        tgt: FloatArray,
        w: Int,
        h: Int,
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
        dx: Int,
        dy: Int,
        step: Int
    ): Double {
        var sad = 0.0
        for (y in y0 until y1 step step) {
            val refRow = y * w
            val tgtYPos = (y + dy).coerceIn(0, h - 1)
            val tgtRow = tgtYPos * w
            for (x in x0 until x1 step step) {
                val tgtXPos = (x + dx).coerceIn(0, w - 1)
                val diff = ref[refRow + x] - tgt[tgtRow + tgtXPos]
                sad += abs(diff)
            }
        }
        return sad
    }
}

