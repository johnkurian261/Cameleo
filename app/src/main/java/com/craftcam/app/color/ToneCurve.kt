package com.craftcam.app.color

import kotlin.math.*

/**
 * Continuous Parametric Tone Curve for CAMELEO Advanced Color Science.
 *
 * Implements smooth, non-linear tonal response with:
 * - True black point anchor (0.0 -> 0.0)
 * - Rational toe shadow lifting (peaks around Y=0.15, zero at true black and midtones)
 * - Calibrated linear midtone slope
 * - Precomputed 1024-entry LUT for high-performance zero-allocation evaluation.
 */
data class ToneCurve(
    val name: String,
    val blackPoint: Float = 0.0f,
    val shadowLift: Float = 0.15f,
    val midtoneContrast: Float = 1.0f,
    val highlightKnee: Float = 0.65f,
    val highlightCompression: Float = 0.35f,
    val whitePoint: Float = 1.0f
) {
    // 1024-entry precomputed lookup table
    private val lut = FloatArray(1024) { i ->
        val x = (i / 1023.0f).coerceIn(0.0f, 1.0f)
        evaluateFunction(x)
    }

    /**
     * Evaluates the tone curve for input luminance/channel value [x] in [0.0, 1.0].
     */
    fun evaluate(x: Float): Float {
        val idx = (x.coerceIn(0f, 1f) * 1023f).toInt()
        return lut[idx]
    }

    /**
     * Mathematical continuous formulation of the tone curve.
     */
    private fun evaluateFunction(x: Float): Float {
        if (x <= blackPoint) return blackPoint
        if (x >= whitePoint) return whitePoint

        // 1. Rational Toe Shadow Lifting: peaks at x = 0.15, fades to 0 at x = 0.0 and x = 0.60
        val toeFactor = if (x < 0.60f) {
            val normX = x / 0.60f
            shadowLift * 1.5f * (1.0f - normX).pow(2.0f) * normX
        } else {
            0.0f
        }

        // 2. Midtone Contrast Scaling
        val centered = x - 0.5f
        val midtoneScaled = (0.5f + centered * midtoneContrast).coerceIn(0f, 1f)

        // 3. Highlight Shoulder Roll-Off
        val postShadow = midtoneScaled + toeFactor
        val result = if (postShadow > highlightKnee) {
            val d = postShadow - highlightKnee
            val factor = highlightCompression.coerceAtLeast(0.05f)
            highlightKnee + (whitePoint - highlightKnee) * (d / (d + factor))
        } else {
            postShadow
        }

        return result.coerceIn(blackPoint, whitePoint)
    }

    fun getLut(): FloatArray = lut

    companion object {
        val Natural = ToneCurve(
            name = "CAMELEO Natural v1.0",
            blackPoint = 0.0f,
            shadowLift = 0.12f,
            midtoneContrast = 1.02f,
            highlightKnee = 0.65f,
            highlightCompression = 0.35f,
            whitePoint = 1.0f
        )

        val Neutral = ToneCurve(
            name = "CAMELEO Neutral v1.0 (Linear Baseline)",
            blackPoint = 0.0f,
            shadowLift = 0.0f,
            midtoneContrast = 1.0f,
            highlightKnee = 0.95f,
            highlightCompression = 0.10f,
            whitePoint = 1.0f
        )

        val Portrait = ToneCurve(
            name = "CAMELEO Portrait v1.0",
            blackPoint = 0.0f,
            shadowLift = 0.18f, // Gentle shadow lift for facial skin clarity
            midtoneContrast = 0.98f, // Slightly softer midtone contrast for flattering skin
            highlightKnee = 0.62f,
            highlightCompression = 0.40f,
            whitePoint = 1.0f
        )
    }
}

