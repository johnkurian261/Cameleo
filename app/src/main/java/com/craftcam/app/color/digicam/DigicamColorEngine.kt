package com.craftcam.app.color.digicam

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.*

/**
 * Illuminant-Aware Golden Y2K Digicam Color Engine.
 *
 * Implements:
 * 1. Dynamic illuminant estimation across the four reference lighting regimes:
 *    - [DigicamIlluminant.GOLDEN_DAYLIGHT] (Refs 4, 5)
 *    - [DigicamIlluminant.AMBER_TUNGSTEN] (Refs 1, 3, 6)
 *    - [DigicamIlluminant.GOLDEN_DIRECT_FLASH] (Refs 2, 5)
 *    - [DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL] (Refs 7, 8)
 * 2. Partial white balance retention preventing modern smartphone over-correction.
 * 3. Hue-dependent red channel protection maintaining vivid scarlet/crimson (Ref 2).
 * 4. Compact digital camera tonal contrast with deep blacks, rich lower midtones, and soft highlight rolloff.
 * 5. Distinct parameter profiles for CPM35 (warm nostalgic compact) and CCD (pocket digicam).
 */
class DigicamColorEngine {

    companion object {
        private const val TAG = "DigicamColorEngine"

        val DAYLIGHT_PARAMS = IlluminantParameters(
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            gains = floatArrayOf(1.03f, 0.98f, 0.86f),
            shadowTint = floatArrayOf(0.015f, 0.010f, 0.005f),
            retentionRatio = 0.45f,
            redSaturationBoost = 1.10f,
            contrast = 1.12f,
            saturation = 1.08f,
            highlightRollOff = 0.88f,
            toeLift = 0.018f,
            opticalScatterIntensity = 0.15f,
            noiseMultiplier = 0.85f
        )

        val TUNGSTEN_PARAMS = IlluminantParameters(
            illuminant = DigicamIlluminant.AMBER_TUNGSTEN,
            gains = floatArrayOf(1.18f, 0.88f, 0.32f),
            shadowTint = floatArrayOf(0.045f, 0.022f, 0.008f),
            retentionRatio = 0.75f,
            redSaturationBoost = 1.15f,
            contrast = 1.15f,
            saturation = 1.12f,
            highlightRollOff = 0.82f,
            toeLift = 0.025f,
            opticalScatterIntensity = 0.34f,
            noiseMultiplier = 1.45f
        )

        val FLASH_PARAMS = IlluminantParameters(
            illuminant = DigicamIlluminant.GOLDEN_DIRECT_FLASH,
            gains = floatArrayOf(1.12f, 0.95f, 0.65f),
            shadowTint = floatArrayOf(0.030f, 0.012f, 0.006f),
            retentionRatio = 0.60f,
            redSaturationBoost = 1.35f,
            contrast = 1.25f,
            saturation = 1.18f,
            highlightRollOff = 0.78f,
            toeLift = 0.015f,
            opticalScatterIntensity = 0.22f,
            noiseMultiplier = 1.05f
        )

        val ARTIFICIAL_PARAMS = IlluminantParameters(
            illuminant = DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL,
            gains = floatArrayOf(1.02f, 1.05f, 0.52f),
            shadowTint = floatArrayOf(0.012f, 0.024f, 0.008f),
            retentionRatio = 0.65f,
            redSaturationBoost = 1.12f,
            contrast = 1.18f,
            saturation = 1.06f,
            highlightRollOff = 0.84f,
            toeLift = 0.020f,
            opticalScatterIntensity = 0.38f,
            noiseMultiplier = 1.50f
        )
    }

    /**
     * Estimates the prevailing scene illuminant using available capture metadata and/or image statistics.
     */
    fun estimateIlluminant(
        pixels: IntArray,
        width: Int,
        height: Int,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null
    ): DigicamIlluminant {
        if (isFlashActive) {
            return DigicamIlluminant.GOLDEN_DIRECT_FLASH
        }

        // Subsample pixels for ultra-fast 0ms statistical estimation
        val step = maxOf(1, (width * height) / 4096)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        var idx = 0
        val total = width * height
        while (idx < total) {
            val p = pixels[idx]
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
            count++
            idx += step
        }

        val avgR = (sumR.toDouble() / count).coerceAtLeast(1.0)
        val avgG = (sumG.toDouble() / count).coerceAtLeast(1.0)
        val avgB = (sumB.toDouble() / count).coerceAtLeast(1.0)

        val blueRatio = (avgB / avgR).toFloat()
        val greenRatio = (avgG / avgR).toFloat()
        val avgLuma = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB).toFloat()

        // 1. Amber Tungsten: severe blue attenuation (Refs 1, 3, 6: blueRatio in 0.14..0.32)
        if (blueRatio <= 0.35f) {
            return DigicamIlluminant.AMBER_TUNGSTEN
        }

        // 2. Yellow-Green Artificial: Green is prominent, Blue is subdued in dark indoor/night scenes (Refs 7, 8)
        if (avgLuma < 115.0f && greenRatio >= 0.82f && (avgG - avgB) / avgR >= 0.18f && blueRatio <= 0.72f) {
            return DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL
        }

        // 3. Fallback check on AWB gains if provided
        if (awbGains != null && awbGains.size >= 4) {
            val rGain = awbGains[0]
            val bGain = awbGains[3]
            if (rGain > 2.1f && bGain < 1.5f) {
                return DigicamIlluminant.AMBER_TUNGSTEN
            }
            if (bGain > 2.0f && rGain < 1.7f) {
                return DigicamIlluminant.GOLDEN_DAYLIGHT
            }
        }

        // 4. Default to Golden Daylight for balanced outdoor/illuminated scenes (Refs 4, 5)
        return DigicamIlluminant.GOLDEN_DAYLIGHT
    }

    /**
     * Resolves illuminant parameters blended with the selected camera model bias.
     */
    fun resolveParameters(
        illuminant: DigicamIlluminant,
        model: DigicamModel
    ): IlluminantParameters {
        val base = when (illuminant) {
            DigicamIlluminant.GOLDEN_DAYLIGHT -> DAYLIGHT_PARAMS
            DigicamIlluminant.AMBER_TUNGSTEN -> TUNGSTEN_PARAMS
            DigicamIlluminant.GOLDEN_DIRECT_FLASH -> FLASH_PARAMS
            DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL -> ARTIFICIAL_PARAMS
        }

        return when (model) {
            DigicamModel.CPM35 -> {
                // CPM35: Golden nostalgic digital compact (extra amber warmth, softer contrast, rich reds)
                base.copy(
                    gains = floatArrayOf(
                        base.gains[0] * 1.05f,
                        base.gains[1] * 0.98f,
                        base.gains[2] * 0.92f
                    ),
                    contrast = base.contrast * 0.98f,
                    saturation = base.saturation * 1.05f,
                    redSaturationBoost = base.redSaturationBoost * 1.10f,
                    opticalScatterIntensity = base.opticalScatterIntensity * 1.15f
                )
            }
            DigicamModel.CCD -> {
                // CCD: Classic pocket digicam (punchier contrast, deeper blacks, crisp digital response)
                base.copy(
                    gains = floatArrayOf(
                        base.gains[0] * 0.98f,
                        base.gains[1] * 1.02f,
                        base.gains[2] * 0.98f
                    ),
                    contrast = base.contrast * 1.06f,
                    saturation = base.saturation * 1.02f,
                    toeLift = (base.toeLift * 0.85f).coerceAtLeast(0.01f),
                    highlightRollOff = (base.highlightRollOff * 0.98f).coerceAtLeast(0.72f),
                    noiseMultiplier = base.noiseMultiplier * 1.10f
                )
            }
        }
    }

    /**
     * Processes input pixel buffer with illuminant-aware color rendering.
     *
     * @param pixels ARGB_8888 pixel buffer (modified in-place).
     * @param width Image width.
     * @param height Image height.
     * @param model Camera identity (CPM35 or CCD).
     * @param isFlashActive Whether hardware flash fired.
     * @param iso Sensor sensitivity.
     * @param awbGains Camera2 AWB gains if available.
     * @return Resolved illuminant profile applied.
     */
    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        model: DigicamModel = DigicamModel.CPM35,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null
    ): IlluminantParameters {
        val illuminant = estimateIlluminant(pixels, width, height, isFlashActive, iso, awbGains)
        val params = resolveParameters(illuminant, model)

        applyColorTransform(pixels, width, height, params)
        return params
    }

    /**
     * Core pixel processing transform combining:
     * - Partial white balance chromatic adaptation
     * - Hue-dependent red protection
     * - Non-linear compact digicam tone curve
     * - Shadow color tinting
     */
    private fun applyColorTransform(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: IlluminantParameters
    ) {
        val total = width * height
        val gainR = params.gains[0]
        val gainG = params.gains[1]
        val gainB = params.gains[2]

        val shadowR = params.shadowTint[0] * 255f
        val shadowG = params.shadowTint[1] * 255f
        val shadowB = params.shadowTint[2] * 255f

        val contrast = params.contrast
        val sat = params.saturation
        val redBoost = params.redSaturationBoost
        val shoulder = params.highlightRollOff
        val toe = params.toeLift

        // Precompute 256-entry S-curve LUT for 0ms CPU execution
        val toneLut = FloatArray(256) { i ->
            val x = i / 255f
            // Toe compression
            val xToe = toe + (1f - toe) * x.pow(1.06f)
            // S-curve contrast around midpoint 0.45
            val xCont = if (xToe < 0.45f) {
                0.45f * (xToe / 0.45f).pow(contrast)
            } else {
                1f - 0.55f * ((1f - xToe) / 0.55f).pow(contrast)
            }
            // Soft shoulder highlight rolloff
            if (xCont > shoulder) {
                val excess = xCont - shoulder
                val headroom = 1f - shoulder
                shoulder + headroom * (excess / (excess + headroom * 0.85f))
            } else {
                xCont
            }.coerceIn(0f, 1f)
        }

        for (i in 0 until total) {
            val p = pixels[i]
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF

            // 1. Partial White Balance Chromatic Adaptation
            var rf = (r * gainR).coerceIn(0f, 255f)
            var gf = (g * gainG).coerceIn(0f, 255f)
            var bf = (b * gainB).coerceIn(0f, 255f)

            // 2. Saturated Red Hue Protection (Section 9: retain rich scarlet Ref 2 #EF391E)
            if (rf > gf * 1.25f && rf > bf * 1.25f) {
                val redDominance = ((rf - maxOf(gf, bf)) / 255f).coerceIn(0f, 1f)
                val boost = 1f + (redBoost - 1f) * redDominance
                rf = (rf * boost).coerceAtMost(255f)
                // Keep green and blue from muddling the saturated red
                gf = (gf * (1f - redDominance * 0.08f)).coerceAtLeast(0f)
                bf = (bf * (1f - redDominance * 0.12f)).coerceAtLeast(0f)
            }

            // 3. Tonal Contrast Curve
            val rIdx = rf.roundToInt().coerceIn(0, 255)
            val gIdx = gf.roundToInt().coerceIn(0, 255)
            val bIdx = bf.roundToInt().coerceIn(0, 255)

            rf = toneLut[rIdx] * 255f
            gf = toneLut[gIdx] * 255f
            bf = toneLut[bIdx] * 255f

            // 4. Color Saturation Adjustment
            val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            rf = (luma + (rf - luma) * sat).coerceIn(0f, 255f)
            gf = (luma + (gf - luma) * sat).coerceIn(0f, 255f)
            bf = (luma + (bf - luma) * sat).coerceIn(0f, 255f)

            // 5. Ambient Shadow Tinting (warm/olive tint in deep shadows)
            val shadowWeight = ((60f - luma) / 60f).coerceIn(0f, 1f)
            if (shadowWeight > 0f) {
                rf = (rf + shadowR * shadowWeight).coerceIn(0f, 255f)
                gf = (gf + shadowG * shadowWeight).coerceIn(0f, 255f)
                bf = (bf + shadowB * shadowWeight).coerceIn(0f, 255f)
            }

            pixels[i] = (0xFF shl 24) or
                    (rf.roundToInt() shl 16) or
                    (gf.roundToInt() shl 8) or
                    bf.roundToInt()
        }
    }
}
