package com.craftcam.app.scene

import kotlin.math.max

/**
 * Encapsulates dynamic range metrics derived from a 256-bin luminance histogram.
 */
data class DynamicRangeAnalysis(
    val blackClippingRatio: Float,
    val shadowDistribution: Float,
    val midtonesDistribution: Float,
    val highlightDistribution: Float,
    val whiteClippingRatio: Float,
    val dynamicRangeEv: Float,
    val isHighDynamicRange: Boolean,
    val isBacklit: Boolean
)

object DynamicRangeAnalyzer {

    /**
     * Analyzes dynamic range from the existing 256-bin luminance histogram (from CameraState/ProMode HUD).
     */
    fun analyzeHistogram(histogram: IntArray): DynamicRangeAnalysis {
        if (histogram.size != 256) {
            return DynamicRangeAnalysis(0f, 0.25f, 0.50f, 0.25f, 0f, 6.0f, false, false)
        }

        var totalPixels = 0L
        for (count in histogram) {
            totalPixels += count
        }

        if (totalPixels <= 0L) {
            return DynamicRangeAnalysis(0f, 0.25f, 0.50f, 0.25f, 0f, 6.0f, false, false)
        }

        val totalD = totalPixels.toDouble()

        // 1. Black clipping: bins 0..3
        var blackCount = 0L
        for (i in 0..3) blackCount += histogram[i]
        val blackClip = (blackCount / totalD).toFloat()

        // 2. Shadows: bins 4..63
        var shadowCount = 0L
        for (i in 4..63) shadowCount += histogram[i]
        val shadowDist = (shadowCount / totalD).toFloat()

        // 3. Midtones: bins 64..191
        var midCount = 0L
        for (i in 64..191) midCount += histogram[i]
        val midDist = (midCount / totalD).toFloat()

        // 4. Highlights: bins 192..251
        var highCount = 0L
        for (i in 192..251) highCount += histogram[i]
        val highDist = (highCount / totalD).toFloat()

        // 5. White clipping: bins 252..255
        var whiteCount = 0L
        for (i in 252..255) whiteCount += histogram[i]
        val whiteClip = (whiteCount / totalD).toFloat()

        // Estimate scene dynamic range span in photographic stops (EV)
        var minBin = 0
        while (minBin < 255 && histogram[minBin] < totalPixels * 0.002) minBin++

        var maxBin = 255
        while (maxBin > 0 && histogram[maxBin] < totalPixels * 0.002) maxBin--

        val binSpan = max(1, maxBin - minBin)
        // Map 0..255 span to estimated EV range (approx 4 to 12 EV)
        val estimatedEv = 4.0f + (binSpan / 255.0f) * 8.0f

        // High dynamic range trigger: both highlights and shadows have substantial content
        val isHdr = (whiteClip > 0.02f || highDist > 0.15f) && (blackClip > 0.02f || shadowDist > 0.25f)

        // Backlight trigger: strong highlights + deep shadows + depressed midtones (bimodal distribution)
        val isBacklit = (highDist + whiteClip > 0.20f) && (shadowDist + blackClip > 0.35f) && (midDist < 0.40f)

        return DynamicRangeAnalysis(
            blackClippingRatio = blackClip,
            shadowDistribution = shadowDist,
            midtonesDistribution = midDist,
            highlightDistribution = highDist,
            whiteClippingRatio = whiteClip,
            dynamicRangeEv = estimatedEv,
            isHighDynamicRange = isHdr || estimatedEv >= 9.5f,
            isBacklit = isBacklit
        )
    }
}
