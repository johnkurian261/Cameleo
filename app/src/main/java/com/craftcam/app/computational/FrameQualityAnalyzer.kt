package com.craftcam.app.computational

import kotlin.math.abs
import kotlin.math.max

/**
 * Evaluates photographic quality of each frame in a multi-frame burst sequence.
 * Computes exposure balance, highlight/shadow clipping, Laplacian variance sharpness,
 * and overall utility score to select the optimal reference anchor frame.
 */
data class FrameQuality(
    val exposureScore: Float,
    val sharpnessScore: Float,
    val motionScore: Float,
    val clippingScore: Float,
    val meanLuminance: Float,
    val highlightClipRatio: Float,
    val shadowClipRatio: Float,
    val totalScore: Float
)

object FrameQualityAnalyzer {

    /**
     * Analyzes quality metrics for an individual [CapturedFrame] using its downsampled luminance.
     */
    fun analyzeFrame(frame: CapturedFrame): FrameQuality {
        val y = frame.getDownsampledY(sampleSize = 4)
        val w = frame.downsampledWidth
        val h = frame.downsampledHeight
        val total = w * h

        if (total == 0) {
            return FrameQuality(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        // 1. Exposure & Clipping Metrics
        var sumY = 0.0
        var highlightClipped = 0
        var shadowClipped = 0

        for (i in 0 until total) {
            val lum = y[i]
            sumY += lum
            if (lum > 0.96f) highlightClipped++
            else if (lum < 0.03f) shadowClipped++
        }

        val meanLum = (sumY / total).toFloat()
        val hiClipRatio = highlightClipped.toFloat() / total
        val shClipRatio = shadowClipped.toFloat() / total

        // Exposure score: optimal around 0.42 - 0.52 (balanced photographic key)
        val exposureDistance = abs(meanLum - 0.46f)
        val exposureScore = (1.0f - exposureDistance * 2.0f).coerceIn(0.1f, 1.0f)

        // Clipping penalty (heavier penalty for blown highlights than deep shadows)
        val clippingScore = (1.0f - (hiClipRatio * 2.5f + shClipRatio * 1.0f)).coerceIn(0.0f, 1.0f)

        // 2. Sharpness via Laplacian Variance:
        // Discrete 2D 4-neighbor Laplacian kernel: [0, 1, 0; 1, -4, 1; 0, 1, 0]
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapCount = 0

        val step = 2 // Subsample by 2 for ultra-fast cache-friendly convolution
        for (row in 1 until h - 1 step step) {
            val rOffset = row * w
            for (col in 1 until w - 1 step step) {
                val idx = rOffset + col
                val center = y[idx]
                val lap = (y[idx - 1] + y[idx + 1] + y[idx - w] + y[idx + w]) - (4.0f * center)
                val lapD = lap.toDouble()
                lapSum += lapD
                lapSqSum += lapD * lapD
                lapCount++
            }
        }

        val lapVariance = if (lapCount > 0) {
            val mean = lapSum / lapCount
            max(0.0, (lapSqSum / lapCount) - (mean * mean))
        } else {
            0.0
        }

        // Normalize Laplacian variance (typical range on downscaled luma: 0.0001 to 0.005)
        // Scaled to a human-readable 0.0 - 50.0 range
        val sharpnessScore = (lapVariance * 10_000.0).toFloat().coerceIn(0.0f, 50.0f)

        // Motion score placeholder (1.0 = sharp static, decreases if blurred)
        val motionScore = (sharpnessScore / 15.0f).coerceIn(0.2f, 1.0f)

        // Total weighted score for reference selection:
        // Prioritize sharpness and non-clipped exposure
        val totalScore = (sharpnessScore * 1.5f) + (exposureScore * 15.0f) + (clippingScore * 15.0f)

        return FrameQuality(
            exposureScore = exposureScore,
            sharpnessScore = sharpnessScore,
            motionScore = motionScore,
            clippingScore = clippingScore,
            meanLuminance = meanLum,
            highlightClipRatio = hiClipRatio,
            shadowClipRatio = shClipRatio,
            totalScore = totalScore
        )
    }

    /**
     * Selects the index of the highest quality reference frame to serve as geometric anchor and deghosting base.
     */
    fun selectReferenceFrame(sequence: FrameSequence): Int {
        if (sequence.size <= 1) return 0

        var bestIdx = 0
        var bestScore = -1.0f

        for (i in sequence.frames.indices) {
            val frame = sequence.frames[i]
            val quality = analyzeFrame(frame)
            // Prefer balanced EV 0.0 frame if scores are close
            val evBonus = if (abs(frame.evOffset) < 0.1f) 5.0f else 0.0f
            val composite = quality.totalScore + evBonus

            if (composite > bestScore) {
                bestScore = composite
                bestIdx = i
            }
        }

        return bestIdx
    }

    /**
     * Evaluates dynamic range across the sequence to decide if multi-frame HDR is required.
     */
    fun estimateSceneDynamicRange(sequence: FrameSequence): Float {
        if (sequence.size <= 1) return 0.0f

        var minLum = 1.0f
        var maxLum = 0.0f

        for (frame in sequence.frames) {
            val q = analyzeFrame(frame)
            if (q.meanLuminance < minLum) minLum = q.meanLuminance
            if (q.meanLuminance > maxLum) maxLum = q.meanLuminance
        }

        return (maxLum - minLum).coerceIn(0.0f, 1.0f)
    }
}

