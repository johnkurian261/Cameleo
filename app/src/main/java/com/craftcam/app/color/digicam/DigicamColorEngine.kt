package com.craftcam.app.color.digicam

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.*

/**
 * Illuminant-Aware Golden Y2K Digicam Color Engine (Phase 2 Upgrade).
 *
 * Implements:
 * 1. Hybrid Multi-Cue Illuminant Estimation with continuous regime weighting:
 *    - [DigicamIlluminant.GOLDEN_DAYLIGHT] (Refs 4, 5)
 *    - [DigicamIlluminant.AMBER_TUNGSTEN] (Refs 1, 3, 6)
 *    - [DigicamIlluminant.GOLDEN_DIRECT_FLASH] (Refs 2, 5)
 *    - [DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL] (Refs 7, 8)
 * 2. Physically meaningful Partial White Balance in linear color space with logarithmic gain interpolation.
 * 3. Continuous Hue-Chroma-Luminance Red Protection preventing warm skin/wood oversaturation (Ref 2).
 * 4. Smooth continuous lighting regime interpolation with confidence-aware damping.
 * 5. Distinct camera-specific calibrations for CPM35 (amber warmth) and CCD (punchy pocket digicam).
 */
class DigicamColorEngine(
    private val hybridEstimator: HybridIlluminantEstimator = HybridIlluminantEstimator()
) {

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
            gains = floatArrayOf(1.02f, 1.00f, 0.78f),
            shadowTint = floatArrayOf(0.014f, 0.014f, 0.016f),
            retentionRatio = 0.65f,
            redSaturationBoost = 1.12f,
            contrast = 1.18f,
            saturation = 1.06f,
            highlightRollOff = 0.84f,
            toeLift = 0.020f,
            opticalScatterIntensity = 0.38f,
            noiseMultiplier = 1.50f
        )

        // Precomputed sRGB <-> Linear RGB lookup tables for 0ms execution
        private val SRGB_TO_LIN = FloatArray(256) { i ->
            val c = i / 255.0f
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }

        private val LIN_TO_SRGB_TABLE_SIZE = 1024
        private val LIN_TO_SRGB = IntArray(LIN_TO_SRGB_TABLE_SIZE + 1) { i ->
            val c = i.toFloat() / LIN_TO_SRGB_TABLE_SIZE.toFloat()
            val srgb = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1.0f / 2.4f) - 0.055f
            (srgb * 255.0f).roundToInt().coerceIn(0, 255)
        }

        @JvmStatic
        fun linearToSrgb(linear: Float): Int {
            val idx = (linear.coerceIn(0.0f, 1.0f) * LIN_TO_SRGB_TABLE_SIZE).toInt()
            return LIN_TO_SRGB[idx]
        }
    }

    /**
     * Estimates the prevailing scene illuminant using the robust hybrid multi-cue estimator.
     */
    fun estimateIlluminant(
        pixels: IntArray,
        width: Int,
        height: Int,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null
    ): DigicamIlluminant {
        return hybridEstimator.estimateIlluminant(pixels, width, height, isFlashActive, iso, awbGains)
    }

    /**
     * Estimates continuous lighting regime weights for smooth transitions and confidence analysis.
     */
    fun estimateLightingRegime(
        pixels: IntArray,
        width: Int,
        height: Int,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null,
        isLivePreview: Boolean = false
    ): LightingRegimeWeights {
        return hybridEstimator.estimateLightingRegime(
            pixels = pixels,
            width = width,
            height = height,
            isFlashActive = isFlashActive,
            iso = iso,
            awbGains = awbGains,
            applyTemporalSmoothing = isLivePreview
        )
    }

    /**
     * Resolves illuminant parameters blended continuously across the active lighting weights and camera model.
     */
    fun resolveBlendedParameters(
        weights: LightingRegimeWeights,
        model: DigicamModel
    ): IlluminantParameters {
        val wDay = weights.daylightWeight
        val wTung = weights.tungstenWeight
        val wFlash = weights.flashWeight
        val wArt = weights.artificialWeight

        // Interpolate base gains and parameters across regimes
        val baseGainR = DAYLIGHT_PARAMS.gains[0] * wDay + TUNGSTEN_PARAMS.gains[0] * wTung +
                FLASH_PARAMS.gains[0] * wFlash + ARTIFICIAL_PARAMS.gains[0] * wArt
        val baseGainG = DAYLIGHT_PARAMS.gains[1] * wDay + TUNGSTEN_PARAMS.gains[1] * wTung +
                FLASH_PARAMS.gains[1] * wFlash + ARTIFICIAL_PARAMS.gains[1] * wArt
        val baseGainB = DAYLIGHT_PARAMS.gains[2] * wDay + TUNGSTEN_PARAMS.gains[2] * wTung +
                FLASH_PARAMS.gains[2] * wFlash + ARTIFICIAL_PARAMS.gains[2] * wArt

        val shadowR = DAYLIGHT_PARAMS.shadowTint[0] * wDay + TUNGSTEN_PARAMS.shadowTint[0] * wTung +
                FLASH_PARAMS.shadowTint[0] * wFlash + ARTIFICIAL_PARAMS.shadowTint[0] * wArt
        val shadowG = DAYLIGHT_PARAMS.shadowTint[1] * wDay + TUNGSTEN_PARAMS.shadowTint[1] * wTung +
                FLASH_PARAMS.shadowTint[1] * wFlash + ARTIFICIAL_PARAMS.shadowTint[1] * wArt
        val shadowB = DAYLIGHT_PARAMS.shadowTint[2] * wDay + TUNGSTEN_PARAMS.shadowTint[2] * wTung +
                FLASH_PARAMS.shadowTint[2] * wFlash + ARTIFICIAL_PARAMS.shadowTint[2] * wArt

        val retention = DAYLIGHT_PARAMS.retentionRatio * wDay + TUNGSTEN_PARAMS.retentionRatio * wTung +
                FLASH_PARAMS.retentionRatio * wFlash + ARTIFICIAL_PARAMS.retentionRatio * wArt
        val redBoost = DAYLIGHT_PARAMS.redSaturationBoost * wDay + TUNGSTEN_PARAMS.redSaturationBoost * wTung +
                FLASH_PARAMS.redSaturationBoost * wFlash + ARTIFICIAL_PARAMS.redSaturationBoost * wArt
        val contrast = DAYLIGHT_PARAMS.contrast * wDay + TUNGSTEN_PARAMS.contrast * wTung +
                FLASH_PARAMS.contrast * wFlash + ARTIFICIAL_PARAMS.contrast * wArt
        val saturation = DAYLIGHT_PARAMS.saturation * wDay + TUNGSTEN_PARAMS.saturation * wTung +
                FLASH_PARAMS.saturation * wFlash + ARTIFICIAL_PARAMS.saturation * wArt
        val rollOff = DAYLIGHT_PARAMS.highlightRollOff * wDay + TUNGSTEN_PARAMS.highlightRollOff * wTung +
                FLASH_PARAMS.highlightRollOff * wFlash + ARTIFICIAL_PARAMS.highlightRollOff * wArt
        val toe = DAYLIGHT_PARAMS.toeLift * wDay + TUNGSTEN_PARAMS.toeLift * wTung +
                FLASH_PARAMS.toeLift * wFlash + ARTIFICIAL_PARAMS.toeLift * wArt
        val scatter = DAYLIGHT_PARAMS.opticalScatterIntensity * wDay + TUNGSTEN_PARAMS.opticalScatterIntensity * wTung +
                FLASH_PARAMS.opticalScatterIntensity * wFlash + ARTIFICIAL_PARAMS.opticalScatterIntensity * wArt
        val noise = DAYLIGHT_PARAMS.noiseMultiplier * wDay + TUNGSTEN_PARAMS.noiseMultiplier * wTung +
                FLASH_PARAMS.noiseMultiplier * wFlash + ARTIFICIAL_PARAMS.noiseMultiplier * wArt

        val blended = IlluminantParameters(
            illuminant = weights.primaryIlluminant,
            gains = floatArrayOf(baseGainR, baseGainG, baseGainB),
            shadowTint = floatArrayOf(shadowR, shadowG, shadowB),
            retentionRatio = retention,
            redSaturationBoost = redBoost,
            contrast = contrast,
            saturation = saturation,
            highlightRollOff = rollOff,
            toeLift = toe,
            opticalScatterIntensity = scatter,
            noiseMultiplier = noise
        )

        return when (model) {
            DigicamModel.CPM35 -> {
                // CPM35: Golden nostalgic digital compact (warm amber cast, punchier digicam midtones, rich reds, clean black point)
                blended.copy(
                    gains = floatArrayOf(
                        blended.gains[0] * 1.05f,
                        blended.gains[1] * 0.98f,
                        blended.gains[2] * 0.92f
                    ),
                    contrast = blended.contrast * 1.00f,
                    saturation = blended.saturation * 1.05f,
                    toeLift = blended.toeLift * 0.95f,
                    highlightRollOff = 0.94f,
                    redSaturationBoost = blended.redSaturationBoost * 1.08f,
                    opticalScatterIntensity = blended.opticalScatterIntensity * 1.15f
                )
            }
            DigicamModel.CCD -> {
                // CCD: Classic pocket digicam (punchier contrast, deeper blacks, crisp digital response)
                blended.copy(
                    gains = floatArrayOf(
                        blended.gains[0] * 1.01f,
                        blended.gains[1] * 0.99f,
                        blended.gains[2] * 0.96f
                    ),
                    contrast = blended.contrast * 1.06f,
                    saturation = blended.saturation * 1.02f,
                    toeLift = (blended.toeLift * 0.85f).coerceAtLeast(0.01f),
                    highlightRollOff = (blended.highlightRollOff * 0.98f).coerceAtLeast(0.72f),
                    noiseMultiplier = blended.noiseMultiplier * 1.10f
                )
            }
        }
    }

    /**
     * Resolves illuminant parameters for a discrete illuminant (backward-compatibility).
     */
    fun resolveParameters(
        illuminant: DigicamIlluminant,
        model: DigicamModel
    ): IlluminantParameters {
        val weights = when (illuminant) {
            DigicamIlluminant.GOLDEN_DAYLIGHT -> LightingRegimeWeights.BALANCED_DAYLIGHT
            DigicamIlluminant.AMBER_TUNGSTEN -> LightingRegimeWeights(0f, 1f, 0f, 0f, 1f, DigicamIlluminant.AMBER_TUNGSTEN)
            DigicamIlluminant.GOLDEN_DIRECT_FLASH -> LightingRegimeWeights(0f, 0f, 1f, 0f, 1f, DigicamIlluminant.GOLDEN_DIRECT_FLASH)
            DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL -> LightingRegimeWeights(0f, 0f, 0f, 1f, 1f, DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL)
        }
        return resolveBlendedParameters(weights, model)
    }

    /**
     * Processes input pixel buffer with illuminant-aware color rendering.
     */
    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        model: DigicamModel = DigicamModel.CPM35,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null,
        isLivePreview: Boolean = false
    ): IlluminantParameters {
        val weights = estimateLightingRegime(pixels, width, height, isFlashActive, iso, awbGains, isLivePreview)
        val params = resolveBlendedParameters(weights, model)

        applyColorTransform(pixels, width, height, params)
        return params
    }

    /**
     * Core pixel processing transform combining:
     * - Physically motivated partial white balance in linear color space with logarithmic gain interpolation
     * - Continuous hue-chroma-luminance red protection
     * - Non-linear compact digicam tone curve
     * - Deep shadow color tinting
     */
    internal fun applyColorTransform(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: IlluminantParameters
    ) {
        val total = width * height

        // 1. Partial White Balance Gains via Logarithmic Interpolation
        // partialGain = exp(retentionRatio * ln(targetGain))
        val targetR = params.gains[0]
        val targetG = params.gains[1]
        val targetB = params.gains[2]
        val retention = params.retentionRatio.coerceIn(0.10f, 1.0f)

        val linGainR = exp(retention * ln(max(targetR.toDouble(), 0.10))).toFloat()
        val linGainG = exp(retention * ln(max(targetG.toDouble(), 0.10))).toFloat()
        val linGainB = exp(retention * ln(max(targetB.toDouble(), 0.10))).toFloat()

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
            val xToe = toe + (1f - toe) * x.pow(1.06f)
            val xCont = if (xToe < 0.45f) {
                0.45f * (xToe / 0.45f).pow(contrast)
            } else {
                1f - 0.55f * ((1f - xToe) / 0.55f).pow(contrast)
            }
            if (xCont > shoulder) {
                val excess = xCont - shoulder
                val headroom = 1f - shoulder
                shoulder + headroom * (excess / (excess + headroom * 0.45f))
            } else {
                xCont
            }.coerceIn(0f, 1f)
        }

        // Precompute 256-entry linear sRGB + partial WB gain LUTs to avoid millions of pow/ln calls
        val rLinLut = FloatArray(256) { linearToSrgb(SRGB_TO_LIN[it] * linGainR).toFloat() }
        val gLinLut = FloatArray(256) { linearToSrgb(SRGB_TO_LIN[it] * linGainG).toFloat() }
        val bLinLut = FloatArray(256) { linearToSrgb(SRGB_TO_LIN[it] * linGainB).toFloat() }

        fun processRow(y: Int) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val i = rowOffset + x
                val p = pixels[i]
                val r8 = (p shr 16) and 0xFF
                val g8 = (p shr 8) and 0xFF
                val b8 = p and 0xFF

                // 1. Linear Color Space Partial White Balance (LUT accelerated)
                var rf = rLinLut[r8]
                var gf = gLinLut[g8]
                var bf = bLinLut[b8]

                // 2. Continuous Hue-Chroma-Luminance Red Protection (Ref 2 scarlet preservation)
                // Eliminates hard threshold rf > 1.25*gf, replacing with smooth angular falloff
                val maxC = maxOf(rf, maxOf(gf, bf))
                val minC = minOf(rf, minOf(gf, bf))
                val chroma = maxC - minC
                val saturation = if (maxC > 0.001f) chroma / maxC else 0.0f

                if (saturation > 0.30f && rf > gf && rf > bf) {
                    // Approximate fast hue angle in degrees [0, 360]
                    val delta = maxC - minC
                    val hueDegrees = if (delta > 0.001f) {
                        val rawHue = ((gf - bf) / delta) * 60f
                        if (rawHue < 0f) rawHue + 360f else rawHue
                    } else 0.0f

                    // Distance from pure scarlet (0° / 360°)
                    val distFromRed = minOf(hueDegrees, 360f - hueDegrees)

                    // Smooth Gaussian falloff (sigma = 18°): full at 0° (scarlet), decays to zero at 35° (orange/skin)
                    val hueFactor = exp(- (distFromRed * distFromRed) / (2f * 18f * 18f))

                    // Saturation gating: only vivid fabrics receive boost, not muted woods or subtle skin flushes
                    val satGating = ((saturation - 0.35f) / 0.30f).coerceIn(0.0f, 1.0f)

                    val effectiveBoost = 1.0f + (redBoost - 1.0f) * hueFactor * satGating

                    // Apply scarlet boost with soft-knee ceiling preserving fabric texture
                    val boostedR = rf * effectiveBoost
                    val rExcess = max(0f, boostedR - 220f)
                    rf = if (rExcess > 0f) 220f + 35f * (rExcess / (rExcess + 35f * 0.70f)) else boostedR

                    // Controlled slight green/blue attenuation on pure scarlet to prevent color fogging
                    val attenuation = hueFactor * satGating * 0.08f
                    gf = (gf * (1f - attenuation)).coerceAtLeast(0f)
                    bf = (bf * (1f - attenuation * 1.2f)).coerceAtLeast(0f)
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

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        if (numCores <= 1 || total < 40_000) {
            for (y in 0 until height) {
                processRow(y)
            }
        } else {
            val rowsPerCore = (height + numCores - 1) / numCores
            val latch = java.util.concurrent.CountDownLatch(numCores)
            for (c in 0 until numCores) {
                val startY = c * rowsPerCore
                val endY = minOf(height, startY + rowsPerCore)
                java.util.concurrent.ForkJoinPool.commonPool().execute {
                    try {
                        for (y in startY until endY) {
                            processRow(y)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            try {
                latch.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
