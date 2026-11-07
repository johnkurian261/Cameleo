package com.craftcam.app.color

import kotlin.math.*

/**
 * CIE 1931 Chromaticity Coordinates (x, y) for photographic white points.
 */
data class WhitePoint(val x: Float, val y: Float) {
    companion object {
        /** CIE Standard Illuminant D65 (Average daylight ~6504 K). Standard sRGB / Display P3 white point. */
        val D65 = WhitePoint(0.31271f, 0.32902f)

        /** CIE Standard Illuminant D50 (Warm daylight ~5003 K). Standard ICC profile connection space. */
        val D50 = WhitePoint(0.34567f, 0.35850f)

        /** CIE Standard Illuminant A (Tungsten incandescent lamp ~2856 K). */
        val STANDARD_A = WhitePoint(0.44757f, 0.40745f)
    }
}

/**
 * White balance representation with Kelvin color temperature, tint, and channel gains.
 */
data class WhiteBalanceState(
    val kelvin: Int = 5500,
    val tint: Float = 0.0f,
    val redGain: Float = 1.0f,
    val greenGain: Float = 1.0f,
    val blueGain: Float = 1.0f
) {
    fun toRgbGainVector(): FloatArray = floatArrayOf(redGain, greenGain, blueGain)
}

/**
 * Temporal White Balance Processor & Sensor Illuminant Estimator for CAMELEO.
 *
 * Responsibilities:
 * 1. Converts between Correlated Color Temperature (CCT in Kelvin) and RGB sensor channel gains.
 * 2. Implements temporal low-pass IIR filtering to eliminate frame-to-frame color pumping.
 * 3. Ingests Camera2 hardware AWB gains and reference illuminants.
 */
class WhiteBalanceProcessor {

    private var smoothedR = 1.0f
    private var smoothedG = 1.0f
    private var smoothedB = 1.0f

    /**
     * Calculates linear RGB gains from Kelvin temperature (2000K..10000K) and Tint (-0.5..+0.5).
     * Uses Planckian blackbody locus approximation (Kang / Tanner formulation).
     */
    fun kelvinToRgbGains(kelvin: Int, tint: Float = 0f): WhiteBalanceState {
        val temp = (kelvin / 100.0f).coerceIn(20f, 100f)

        // Calculate Red
        val r: Float = if (temp <= 66f) {
            1.0f
        } else {
            val v = temp - 60f
            (1.2934187f * (v.pow(-0.13320476f))).coerceIn(0.6f, 1.4f)
        }

        // Calculate Green
        val g: Float = if (temp <= 66f) {
            val v = temp
            (0.39008157f * ln(v) - 0.63184144f).coerceIn(0.4f, 1.2f)
        } else {
            val v = temp - 60f
            (1.129891f * (v.pow(-0.075514846f))).coerceIn(0.6f, 1.2f)
        }

        // Calculate Blue
        val b: Float = if (temp >= 66f) {
            1.0f
        } else if (temp <= 19f) {
            0.1f
        } else {
            val v = temp - 10f
            (0.5432068f * ln(v) - 1.196254f).coerceIn(0.2f, 1.4f)
        }

        // Normalize relative to Green = 1.0 at 5500K
        val normR = (r / g).coerceIn(0.5f, 2.5f)
        val normG = (1.0f - tint * 0.15f).coerceIn(0.7f, 1.3f)
        val normB = (b / g).coerceIn(0.5f, 2.5f)

        return WhiteBalanceState(
            kelvin = kelvin,
            tint = tint,
            redGain = normR,
            greenGain = normG,
            blueGain = normB
        )
    }

    /**
     * Applies temporal IIR stabilization to live preview white balance gains:
     * newGain = alpha * current + (1 - alpha) * previous.
     * Prevents noticeable color pumping when scene lighting shifts subtly.
     */
    fun stabilizeGains(
        currentR: Float,
        currentG: Float,
        currentB: Float,
        alpha: Float = 0.20f
    ): FloatArray {
        smoothedR = alpha * currentR + (1.0f - alpha) * smoothedR
        smoothedG = alpha * currentG + (1.0f - alpha) * smoothedG
        smoothedB = alpha * currentB + (1.0f - alpha) * smoothedB

        return floatArrayOf(smoothedR, smoothedG, smoothedB)
    }

    /**
     * Resets temporal filter memory (e.g. on camera switch or mode change).
     */
    fun reset() {
        smoothedR = 1.0f
        smoothedG = 1.0f
        smoothedB = 1.0f
    }
}

