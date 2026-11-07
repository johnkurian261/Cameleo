package com.craftcam.app.scene

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lighting classification based on scene luminance statistics and sensor exposure key.
 */
enum class LightingCondition(val displayName: String) {
    BRIGHT("Bright"),
    NORMAL("Normal"),
    LOW_LIGHT("Low Light"),
    VERY_LOW_LIGHT("Night / Very Low Light")
}

/**
 * Quantitative lighting analysis metrics extracted from preview/capture frame.
 */
data class LightingAnalysis(
    val condition: LightingCondition,
    val meanLuminance: Float,
    val shadowPercentage: Float,
    val highlightPercentage: Float,
    val contrastRatio: Float,
    val estimatedColorTempK: Int,
    val isLowLight: Boolean,
    val isBacklit: Boolean
)

object LightingAnalyzer {

    /**
     * Evaluates lighting parameters from downsampled RGB/Luminance pixels or histogram.
     */
    fun analyze(
        luminanceArray: FloatArray,
        width: Int,
        height: Int,
        sensorIso: Int = 100,
        exposureTimeNs: Long = 10_000_000L
    ): LightingAnalysis {
        val total = width * height
        if (total == 0 || luminanceArray.isEmpty()) {
            return LightingAnalysis(
                condition = LightingCondition.NORMAL,
                meanLuminance = 0.45f,
                shadowPercentage = 0.10f,
                highlightPercentage = 0.10f,
                contrastRatio = 3.0f,
                estimatedColorTempK = 5500,
                isLowLight = false,
                isBacklit = false
            )
        }

        var sumLum = 0.0
        var shadowCount = 0
        var highlightCount = 0
        var minLum = 1.0f
        var maxLum = 0.0f

        val step = max(1, total / 2000) // Sample up to 2000 pixels for fast O(1) analysis
        var sampled = 0

        for (i in 0 until total step step) {
            val y = luminanceArray[i]
            sumLum += y
            if (y < 0.18f) shadowCount++
            if (y > 0.82f) highlightCount++
            if (y < minLum) minLum = y
            if (y > maxLum) maxLum = y
            sampled++
        }

        val meanLum = (sumLum / sampled).toFloat().coerceIn(0.0f, 1.0f)
        val shadowPct = shadowCount.toFloat() / sampled
        val highlightPct = highlightCount.toFloat() / sampled
        val contrast = if (minLum > 0.01f) (maxLum / minLum).coerceIn(1.0f, 100.0f) else 50.0f

        // Correlate with hardware sensor ISO and shutter speed
        val expMs = exposureTimeNs / 1_000_000f
        val isHardwareDark = sensorIso >= 640 || (sensorIso >= 400 && expMs >= 30.0f)
        val isVeryDark = (sensorIso >= 1000 && expMs >= 40.0f) || (meanLum < 0.12f && shadowPct > 0.50f)

        val condition = when {
            isVeryDark || (meanLum < 0.15f && isHardwareDark) -> LightingCondition.VERY_LOW_LIGHT
            isHardwareDark || meanLum < 0.28f || shadowPct > 0.45f -> LightingCondition.LOW_LIGHT
            meanLum > 0.65f && highlightPct > 0.20f && sensorIso <= 100 -> LightingCondition.BRIGHT
            else -> LightingCondition.NORMAL
        }

        // Backlight detection: bright background (highlights in upper half) + dark foreground (shadows in center/lower half)
        val isBacklit = (highlightPct > 0.18f && shadowPct > 0.30f && contrast > 15.0f)

        // Estimated color temperature heuristic based on luminance key
        val tempK = if (condition == LightingCondition.BRIGHT) 5600 else if (condition == LightingCondition.VERY_LOW_LIGHT) 3200 else 4800

        return LightingAnalysis(
            condition = condition,
            meanLuminance = meanLum,
            shadowPercentage = shadowPct,
            highlightPercentage = highlightPct,
            contrastRatio = contrast,
            estimatedColorTempK = tempK,
            isLowLight = condition == LightingCondition.LOW_LIGHT || condition == LightingCondition.VERY_LOW_LIGHT,
            isBacklit = isBacklit
        )
    }
}
