package com.craftcam.app.computational

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Output of motion estimation between an aligned frame and reference frame.
 */
data class MotionMap(
    /** Downsampled motion confidence map [0.0f = static, 1.0f = high motion]. */
    val map: FloatArray,
    val width: Int,
    val height: Int,
    /** Fraction of evaluated pixels containing detected motion [0.0f to 1.0f]. */
    val globalMotionFraction: Float
)

object MotionEstimator {

    private const val SAMPLE_SIZE = 4

    /**
     * Estimates local subject motion by computing the exposure-normalized residual
     * between [target] (translated by [alignment]) and [reference].
     */
    fun estimateMotion(
        reference: CapturedFrame,
        target: CapturedFrame,
        alignment: AlignmentResult,
        ghostingSensitivity: Float = 0.12f
    ): MotionMap {
        val refY = reference.getDownsampledY(SAMPLE_SIZE)
        val tgtY = target.getDownsampledY(SAMPLE_SIZE)
        val w = reference.downsampledWidth
        val h = reference.downsampledHeight
        val total = w * h

        if (total == 0) {
            return MotionMap(FloatArray(0), 0, 0, 0f)
        }

        // Translation in downscaled pixel space
        val dxDown = (alignment.dx / SAMPLE_SIZE.toFloat()).roundToInt()
        val dyDown = (alignment.dy / SAMPLE_SIZE.toFloat()).roundToInt()

        // Exposure compensation factor to prevent EV differences being flagged as subject motion
        var sumRef = 0.0
        var sumTgt = 0.0
        for (i in 0 until total) {
            sumRef += refY[i]
            sumTgt += tgtY[i]
        }
        val meanRef = (sumRef / total).toFloat().coerceAtLeast(0.01f)
        val meanTgt = (sumTgt / total).toFloat().coerceAtLeast(0.01f)
        val evScale = (meanRef / meanTgt).coerceIn(0.2f, 5.0f)

        // Sensor noise floor derived from ISO (higher ISO has wider thermal/shot noise band)
        val isoFactor = (target.iso.coerceAtLeast(50) / 3200.0f).coerceIn(0.0f, 1.0f)
        val noiseFloor = (ghostingSensitivity * 0.5f) + (isoFactor * 0.08f)

        val rawResidual = FloatArray(total)
        var motionPixels = 0

        for (y in 0 until h) {
            val refRow = y * w
            val tgtYPos = (y + dyDown).coerceIn(0, h - 1)
            val tgtRow = tgtYPos * w
            for (x in 0 until w) {
                val tgtXPos = (x + dxDown).coerceIn(0, w - 1)
                val refLum = refY[refRow + x]
                val tgtLum = tgtY[tgtRow + tgtXPos] * evScale

                val diff = abs(refLum - tgtLum)
                // Normalize residual over noise floor
                val mVal = if (diff > noiseFloor) {
                    ((diff - noiseFloor) / (ghostingSensitivity * 1.5f)).coerceIn(0.0f, 1.0f)
                } else {
                    0.0f
                }

                val idx = refRow + x
                rawResidual[idx] = mVal
                if (mVal > 0.4f) motionPixels++
            }
        }

        // Spatial 3x3 max-filter (dilation) to expand motion boundaries,
        // eliminating sharp fringe artifacts at the silhouettes of moving subjects
        val dilated = FloatArray(total)
        for (y in 1 until h - 1) {
            val r0 = (y - 1) * w
            val r1 = y * w
            val r2 = (y + 1) * w
            for (x in 1 until w - 1) {
                var maxVal = rawResidual[r1 + x]
                for (ky in -1..1) {
                    val kr = (y + ky) * w
                    for (kx in -1..1) {
                        val v = rawResidual[kr + (x + kx)]
                        if (v > maxVal) maxVal = v
                    }
                }
                dilated[r1 + x] = maxVal
            }
        }

        val globalFraction = motionPixels.toFloat() / total

        return MotionMap(
            map = dilated,
            width = w,
            height = h,
            globalMotionFraction = globalFraction
        )
    }
}

