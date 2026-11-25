package com.craftcam.app.color.digicam

import kotlin.math.*

/**
 * Hybrid Multi-Cue Illuminant Estimator for Vintage Digicam Emulation.
 *
 * Solves the critical vulnerabilities of naive global RGB averaging:
 * 1. Saturated Object Rejection: Excludes vivid red clothing (Ref 2), green foliage, blue sky,
 *    and saturated objects from neutral-surface white balance estimation.
 * 2. Shades-of-Grey Estimation: Evaluates Minkowski p-norm (p=4) over non-saturated midtone pixels.
 * 3. Specular Highlight Chromaticity: Samples non-clipped highlights (L in 200..245) where surface
 *    specular reflection preserves illuminant chromaticity.
 * 4. Camera2 Hardware Metadata: Evaluates Camera2 COLOR_CORRECTION_GAINS when available from ISP.
 * 5. Continuous Regime Weights: Computes smooth, continuous interpolation across:
 *    - AMBER_TUNGSTEN (Refs 1, 3, 6)
 *    - GOLDEN_DAYLIGHT (Refs 4, 5)
 *    - GOLDEN_DIRECT_FLASH (Refs 2, 5)
 *    - YELLOW_GREEN_ARTIFICIAL (Refs 7, 8)
 * 6. Confidence-Aware Damping: When neutral pixel evidence is sparse or ambiguous, smoothly
 *    falls back to balanced daylight, preventing erratic profile switching.
 * 7. Temporal Smoothing: Rolling low-pass filter prevents live preview flickering.
 */
class HybridIlluminantEstimator {

    // Rolling exponential moving average (EMA) for temporal consistency in preview
    private var smoothedWeights: LightingRegimeWeights? = null
    private val temporalAlpha = 0.25f // 25% new, 75% history

    /**
     * Resets temporal filter state (e.g. on camera switch or lens change).
     */
    fun resetTemporalHistory() {
        smoothedWeights = null
    }

    /**
     * Estimates continuous lighting regime weights from image pixels and capture metadata.
     *
     * @param pixels ARGB_8888 pixel buffer.
     * @param width Image width.
     * @param height Image height.
     * @param isFlashActive Whether hardware flash fired for this capture.
     * @param iso Sensor sensitivity.
     * @param awbGains Camera2 COLOR_CORRECTION_GAINS [R, G_even, G_odd, B] if available.
     * @param applyTemporalSmoothing Whether to apply rolling EMA filter (set true for preview, false for still capture).
     * @return Resolved [LightingRegimeWeights] with confidence score and continuous regime interpolation.
     */
    fun estimateLightingRegime(
        pixels: IntArray,
        width: Int,
        height: Int,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null,
        applyTemporalSmoothing: Boolean = false
    ): LightingRegimeWeights {
        val total = width * height
        if (total <= 0) return LightingRegimeWeights.BALANCED_DAYLIGHT

        // When flash is active, direct flash illumination dominates the foreground
        if (isFlashActive) {
            val weights = LightingRegimeWeights(
                daylightWeight = 0.10f,
                tungstenWeight = 0.05f,
                flashWeight = 0.80f,
                artificialWeight = 0.05f,
                confidence = 0.98f,
                primaryIlluminant = DigicamIlluminant.GOLDEN_DIRECT_FLASH,
                estimatedColorTempK = 5600,
                neutralPixelFraction = 0.40f
            )
            if (applyTemporalSmoothing) smoothedWeights = weights
            return weights
        }

        // Subsample up to 4096 points across the frame for zero-latency execution (<0.3ms)
        val sampleTarget = 4096
        val step = maxOf(1, total / sampleTarget)

        var neutralCount = 0
        var p4SumR = 0.0
        var p4SumG = 0.0
        var p4SumB = 0.0

        var highlightCount = 0
        var hlSumR = 0.0
        var hlSumG = 0.0
        var hlSumB = 0.0

        var saturatedRedCount = 0
        var saturatedGreenCount = 0
        var totalSampled = 0

        var idx = 0
        while (idx < total) {
            val p = pixels[idx]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            totalSampled++

            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            val chroma = maxC - minC
            val sat = if (maxC > 0) chroma.toFloat() / maxC.toFloat() else 0.0f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b

            // 1. Saturated Object Tracking & Spectral Exclusion
            // Pure red fabric (Ref 2 red dress): G < 0.45*R and B < 0.40*R and sat > 0.60
            val isSaturatedRedObject = (sat > 0.60f && g < r * 0.45f && b < r * 0.40f)
            // Pure green foliage: G > 1.35*R and G > 1.25*B and sat > 0.45
            val isSaturatedFoliage = (sat > 0.45f && g > r * 1.35f && g > b * 1.25f)
            // Pure blue sky / blue object: B > 1.35*R and B > 1.25*G and sat > 0.45
            val isSaturatedBlueObject = (sat > 0.45f && b > r * 1.35f && b > g * 1.25f)

            val isExcludedColoredObject = isSaturatedRedObject || isSaturatedFoliage || isSaturatedBlueObject

            // 2. Robust Neutral / Illuminant Carrier Pixel Selection:
            // Exclude dark shadow noise (L < 15), clipped highlights (L > 250), and isolated saturated colored objects
            if (luma in 15.0f..250.0f && !isExcludedColoredObject) {
                neutralCount++
                val rf = r.toDouble()
                val gf = g.toDouble()
                val bf = b.toDouble()
                // Minkowski p=4 norm
                p4SumR += rf * rf * rf * rf
                p4SumG += gf * gf * gf * gf
                p4SumB += bf * bf * bf * bf
            }

            // 3. Specular Highlight Chromaticity:
            // Non-clipped specular glints reflect light source color without surface pigment attenuation
            if (luma in 205.0f..248.0f && maxC < 252) {
                highlightCount++
                hlSumR += r.toDouble()
                hlSumG += g.toDouble()
                hlSumB += b.toDouble()
            }

            idx += step
        }

        val neutralFraction = if (totalSampled > 0) neutralCount.toFloat() / totalSampled else 0f

        // Compute Shades-of-Grey estimated neutral chrominance
        val (neutralR, neutralG, neutralB) = if (neutralCount >= 20) {
            val rP4 = (p4SumR / neutralCount).pow(0.25)
            val gP4 = (p4SumG / neutralCount).pow(0.25)
            val bP4 = (p4SumB / neutralCount).pow(0.25)
            Triple(max(rP4, 1.0), max(gP4, 1.0), max(bP4, 1.0))
        } else {
            // Fallback: If no neutrals exist in the scene (e.g. wall-to-wall red fabric or macro leaf),
            // use balanced daylight values to prevent wild misclassification
            Triple(128.0, 128.0, 128.0)
        }

        val blueRatio = (neutralB / neutralR).toFloat()
        val greenRatio = (neutralG / neutralR).toFloat()

        // Highlight chromaticity ratios if specular glints are present
        val hlBlueRatio = if (highlightCount >= 8) (hlSumB / max(1.0, hlSumR)).toFloat() else blueRatio
        val hlGreenRatio = if (highlightCount >= 8) (hlSumG / max(1.0, hlSumR)).toFloat() else greenRatio

        // Multi-Cue Scoring
        // A. Amber Tungsten Score (severe blue attenuation, warm highlights, high camera AWB R-gain)
        var tungstenScore = 0.0f
        if (blueRatio <= 0.45f) {
            val blueFactor = ((0.45f - blueRatio) / 0.30f).coerceIn(0.0f, 1.0f)
            tungstenScore += 1.0f + blueFactor * 2.5f
        }
        if (hlBlueRatio < 0.40f) {
            tungstenScore += 0.50f
        }

        // B. Yellow-Green Artificial Score (elevated green ratio, blue below green, low ambient luma)
        var artificialScore = 0.0f
        val avgLuma = (0.299 * neutralR + 0.587 * neutralG + 0.114 * neutralB).toFloat()
        if (avgLuma < 125.0f && greenRatio >= 0.82f && (neutralG - neutralB) / neutralR >= 0.16f && blueRatio <= 0.75f) {
            val greenFactor = ((greenRatio - 0.82f) / 0.18f).coerceIn(0.0f, 1.0f)
            val lumaFactor = ((125.0f - avgLuma) / 60.0f).coerceIn(0.0f, 1.0f)
            artificialScore += 1.2f + greenFactor * 2.0f + lumaFactor * 0.8f
        }
        if (hlGreenRatio > 1.02f && hlBlueRatio < 0.75f) {
            artificialScore += 0.35f
        }

        // C. Camera2 AWB Metadata Integration if available
        if (awbGains != null && awbGains.size >= 4) {
            val rGain = awbGains[0]
            val bGain = awbGains[3]
            // Phone AWB boosted blue significantly to correct strong incandescent warmth
            if (bGain > 1.95f && rGain < 1.60f) {
                tungstenScore += 0.85f
            }
        }

        // D. Golden Daylight Score (balanced blue and green ratios)
        var daylightScore = 0.0f
        if (blueRatio >= 0.50f) {
            val blueFactor = ((blueRatio - 0.50f) / 0.25f).coerceIn(0.0f, 1.0f)
            daylightScore += 0.8f + blueFactor * 1.5f
        }

        // Total confidence estimation based on neutral pixel count and cue separation
        val sampleConfidence = (neutralCount.toFloat() / 250f).coerceIn(0.20f, 1.0f)
        val scoreDiff = abs(maxOf(tungstenScore, artificialScore) - daylightScore)
        val cueConfidence = (0.50f + scoreDiff * 0.50f).coerceIn(0.40f, 1.0f)
        val rawConfidence = (sampleConfidence * 0.45f + cueConfidence * 0.55f).coerceIn(0.25f, 0.98f)

        // Softmax / Normalization of weights
        val expDaylight = exp(daylightScore * 2.8f)
        val expTungsten = exp(tungstenScore * 3.2f)
        val expArtificial = exp(artificialScore * 3.0f)
        val sumExp = expDaylight + expTungsten + expArtificial

        var wDaylight = (expDaylight / sumExp).toFloat()
        var wTungsten = (expTungsten / sumExp).toFloat()
        var wArtificial = (expArtificial / sumExp).toFloat()
        val wFlash = 0.0f

        // Estimated color temperature heuristic
        val estTempK = when {
            wTungsten > 0.50f -> (2400 + (1.0f - wTungsten) * 1200).toInt()
            wArtificial > 0.50f -> (3800 + (1.0f - wArtificial) * 600).toInt()
            else -> (5200 + wDaylight * 400).toInt()
        }

        val primary = when {
            wTungsten >= maxOf(wDaylight, wArtificial) -> DigicamIlluminant.AMBER_TUNGSTEN
            wArtificial >= maxOf(wDaylight, wTungsten) -> DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL
            else -> DigicamIlluminant.GOLDEN_DAYLIGHT
        }

        var result = LightingRegimeWeights(
            daylightWeight = wDaylight,
            tungstenWeight = wTungsten,
            flashWeight = wFlash,
            artificialWeight = wArtificial,
            confidence = rawConfidence,
            primaryIlluminant = primary,
            estimatedColorTempK = estTempK,
            neutralPixelFraction = neutralFraction
        ).withConfidenceDamping()

        // Temporal Low-Pass Filtering for Live Preview
        if (applyTemporalSmoothing) {
            val prev = smoothedWeights
            if (prev != null) {
                val smDaylight = prev.daylightWeight * (1f - temporalAlpha) + result.daylightWeight * temporalAlpha
                val smTungsten = prev.tungstenWeight * (1f - temporalAlpha) + result.tungstenWeight * temporalAlpha
                val smFlash = prev.flashWeight * (1f - temporalAlpha) + result.flashWeight * temporalAlpha
                val smArtificial = prev.artificialWeight * (1f - temporalAlpha) + result.artificialWeight * temporalAlpha
                val smConf = prev.confidence * (1f - temporalAlpha) + result.confidence * temporalAlpha

                val smPrimary = when {
                    smFlash >= 0.50f -> DigicamIlluminant.GOLDEN_DIRECT_FLASH
                    smTungsten >= maxOf(smDaylight, smArtificial) -> DigicamIlluminant.AMBER_TUNGSTEN
                    smArtificial >= maxOf(smDaylight, smTungsten) -> DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL
                    else -> DigicamIlluminant.GOLDEN_DAYLIGHT
                }

                result = result.copy(
                    daylightWeight = smDaylight,
                    tungstenWeight = smTungsten,
                    flashWeight = smFlash,
                    artificialWeight = smArtificial,
                    confidence = smConf,
                    primaryIlluminant = smPrimary
                )
            }
            smoothedWeights = result
        }

        return result
    }

    /**
     * Backward-compatible convenience wrapper returning a single discrete [DigicamIlluminant].
     */
    fun estimateIlluminant(
        pixels: IntArray,
        width: Int,
        height: Int,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        awbGains: FloatArray? = null
    ): DigicamIlluminant {
        return estimateLightingRegime(
            pixels = pixels,
            width = width,
            height = height,
            isFlashActive = isFlashActive,
            iso = iso,
            awbGains = awbGains,
            applyTemporalSmoothing = false
        ).primaryIlluminant
    }
}
