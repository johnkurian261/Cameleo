package com.craftcam.app.color.skin

import android.graphics.Color
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.portrait.FaceRegion
import kotlin.math.*

/**
 * Result data holder returned by the Skin Tone Intelligence Engine.
 */
data class SkinProcessingResult(
    val refinedSkinMask: FloatArray,
    val faceCount: Int,
    val skinPixelCount: Int,
    val averageSkinConfidence: Float,
    val averageSkinLuma: Float,
    val illuminant: DigicamIlluminant,
    val profileApplied: SkinToneProfile
)

/**
 * Master Skin Tone Intelligence Engine for CAMELEO (Phase 2).
 *
 * Implements a four-layer architecture:
 * - Layer 1: Illuminant-Aware Camera Colour Science.
 * - Layer 2: Continuous OKLab / OKLCh & Multi-Space Colour Probability.
 * - Layer 3: Anatomical Spatial Prior & Confidence-Aware Fusion.
 * - Layer 4: Camera-Specific Skin Protection & Confidence-Weighted Blending:
 *            Final Colour = Camera Grade + c_i * Correction_protective
 *
 * Strictly PROHIBITED:
 * - Beauty smoothing or pore erasure.
 * - Skin whitening or lightening.
 * - Facial reshaping or morphing.
 * - Forcing diverse human complexions toward a single peach hue.
 * - Artificial relighting.
 */
class SkinToneIntelligenceEngine(
    val colorEstimator: SkinColourEstimator = SkinColourEstimator(),
    val anatomyPrior: SkinAnatomyPrior = SkinAnatomyPrior(),
    val maskRefiner: SkinMaskRefiner = SkinMaskRefiner()
) {

    /**
     * Executes the complete Skin Tone Intelligence pipeline in-place on [pixels].
     *
     * @param pixels Image buffer in ARGB_8888 (modified in-place).
     * @param width Image width.
     * @param height Image height.
     * @param faces Detected faces from hardware ISP or image fallback.
     * @param illuminant Current scene lighting regime.
     * @param profile Camera-specific skin profile (e.g. D_FUNS, CPM35, CCD, FXNR).
     * @param isFlashActive Whether physical direct flash fired.
     * @param iso Live sensor ISO sensitivity.
     * @param protectionOnly Whether to bypass warmth/hue tinting and apply only protection bounds.
     * @return Execution metrics and refined skin confidence mask.
     */
    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        faces: List<FaceRegion> = emptyList(),
        illuminant: DigicamIlluminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
        profile: SkinToneProfile = SkinToneProfileCatalog.CPM35,
        isFlashActive: Boolean = false,
        iso: Int = 100,
        protectionOnly: Boolean = false
    ): SkinProcessingResult {
        val total = width * height
        val config = profile.config

        if (!config.enabled || total == 0) {
            return SkinProcessingResult(
                refinedSkinMask = FloatArray(total),
                faceCount = faces.size,
                skinPixelCount = 0,
                averageSkinConfidence = 0.0f,
                averageSkinLuma = 0.0f,
                illuminant = illuminant,
                profileApplied = profile
            )
        }

        // ── Layer 2: Continuous Multi-Space Colour Probability (OKLCh + YCbCr) ─
        val colorProb = if (config.detectionEnabled) {
            colorEstimator.estimateColourProbability(pixels, width, height, illuminant)
        } else {
            FloatArray(total) { 1.0f }
        }

        // ── Layer 3: Anatomical Spatial Prior & Confidence Fusion ─────────────────
        val spatialPrior = anatomyPrior.generateSpatialPrior(
            width = width,
            height = height,
            faces = faces,
            backgroundSuppression = config.backgroundSuppression
        )

        val fusedMask = anatomyPrior.fuseConfidence(
            colorProb = colorProb,
            spatialPrior = spatialPrior,
            wSpatial = config.spatialConfidenceWeight,
            wColor = config.colourConfidenceWeight
        )

        // ── Edge-Aware Mask Refinement ───────────────────────────────────────────
        val refinedMask = maskRefiner.refineMask(
            fusedMask = fusedMask,
            width = width,
            height = height,
            featherRadiusFrac = config.maskFeatherRadius
        )

        // ── Layer 4: Confidence-Weighted Skin Protection & Camera Response ───────
        // Final Colour = Camera Grade + c_i * Correction_protective
        var skinPixelsTotal = 0
        var skinConfidenceSum = 0.0f
        var skinLumaSum = 0.0f

        val skinGains = profile.skinGains
        val gainR = skinGains[0]
        val gainG = skinGains[1]
        val gainB = skinGains[2]

        val warmthStr = if (protectionOnly) 0.0f else config.warmthStrength
        val maxSat = profile.maxSkinSaturation
        val knee = profile.skinHighlightKnee
        val midLift = if (protectionOnly) 0.0f else profile.skinMidtoneLift

        for (i in 0 until total) {
            val conf = refinedMask[i]
            if (conf < 0.02f) continue

            skinPixelsTotal++
            skinConfidenceSum += conf

            val p = pixels[i]
            val r0 = (p shr 16) and 0xFF
            val g0 = (p shr 8) and 0xFF
            val b0 = p and 0xFF

            val luma = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
            skinLumaSum += luma

            var dr = 0.0f
            var dg = 0.0f
            var db = 0.0f

            // 1. Restrained Lower-Midtone Lift for Skin
            if (midLift > 0.001f && luma in 30.0f..160.0f) {
                val midNorm = (luma - 30.0f) / 130.0f
                val liftFactor = 4.0f * midNorm * (1.0f - midNorm) * midLift * 255.0f
                dr += liftFactor * 0.95f
                dg += liftFactor * 0.90f
                db += liftFactor * 0.80f
            }

            // 2. Camera-Specific Skin Warmth Tint
            if (warmthStr > 0.01f) {
                dr += (r0 * gainR - r0) * warmthStr
                dg += (g0 * gainG - g0) * warmthStr
                db += (b0 * gainB - b0) * warmthStr
            }

            // Candidate protected pixel
            var curR = (r0 + dr).coerceIn(0f, 255f)
            var curG = (g0 + dg).coerceIn(0f, 255f)
            var curB = (b0 + db).coerceIn(0f, 255f)

            // 3. Highlight Asymptotic Rolloff on Skin (prevents cheek/forehead blowout)
            val maxC = maxOf(curR, maxOf(curG, curB))
            val knee255 = knee * 255.0f
            if (maxC > knee255) {
                val excess = maxC - knee255
                val headroom = 255.0f - knee255
                val rolledMax = knee255 + headroom * (excess / (excess + headroom * 0.80f))
                val scale = rolledMax / maxC
                curR *= scale
                curG *= scale
                curB *= scale
            }

            // 4. Perceptual Saturation Protection (guards against sunburned, orange, or neon skin)
            val curLuma = 0.299f * curR + 0.587f * curG + 0.114f * curB
            val maxColor = maxOf(curR, maxOf(curG, curB))
            val minColor = minOf(curR, minOf(curG, curB))
            val curSat = if (maxColor > 0.001f) (maxColor - minColor) / maxColor else 0.0f

            if (curSat > maxSat && config.protectionEnabled) {
                val satScale = maxSat / curSat
                val satDamp = 1.0f - (1.0f - satScale) * config.saturationProtection
                curR = curLuma + (curR - curLuma) * satDamp
                curG = curLuma + (curG - curLuma) * satDamp
                curB = curLuma + (curB - curLuma) * satDamp
            }

            // 5. Cross-Processing Green Cast Guard (prevents creative film LUTs from turning skin sickly green)
            if (curG > curR * 1.02f && config.protectionEnabled) {
                val excessGreen = (curG - curR) * 0.60f
                curG -= excessGreen
                curR += excessGreen * 0.30f
            }

            // 6. Deep Skin Tone Shadow Protection (preserves rich dark complexions from crushing into muddy noise)
            if (curLuma < 45.0f && config.protectionEnabled) {
                val shadowLift = ((45.0f - curLuma) / 45.0f) * 12.0f * config.shadowProtection
                curR = (curR + shadowLift * 1.05f).coerceIn(0f, 255f)
                curG = (curG + shadowLift * 0.95f).coerceIn(0f, 255f)
                curB = (curB + shadowLift * 0.90f).coerceIn(0f, 255f)
            }

            // 7. Yellow Cast Guard (guards against blue-channel starvation and mustard/yellowish skin drift)
            if (config.yellowCastProtectionEnabled && config.protectionEnabled) {
                val rbRatio = curR / maxOf(1.0f, curB)
                val rgRatio = curR / maxOf(1.0f, curG)

                // Blue channel starvation recovery (target balanced R/B ratio ~ 1.65..1.75)
                if (rbRatio > 1.75f) {
                    val targetB = curR / 1.70f
                    val deltaB = (targetB - curB) * config.yellowCastProtectionStrength * 0.85f
                    curB = (curB + deltaB).coerceIn(0f, 255f)
                }

                // Green over-amplification dampening (target healthy R/G ratio ~ 1.30..1.35)
                if (rgRatio < 1.30f && curG > curB * 1.15f) {
                    val targetG = curR / 1.32f
                    val deltaG = (curG - targetG) * config.yellowCastProtectionStrength * 0.75f
                    curG = (curG - deltaG).coerceIn(0f, 255f)
                    curR = (curR - deltaG * 0.15f).coerceIn(0f, 255f)
                }
            }

            // 8. Confidence-Weighted Protection Blending: Final = Camera Grade + conf * Delta_protective
            val finalR = (r0 + (curR - r0) * conf).roundToInt().coerceIn(0, 255)
            val finalG = (g0 + (curG - g0) * conf).roundToInt().coerceIn(0, 255)
            val finalB = (b0 + (curB - b0) * conf).roundToInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }

        val avgConf = if (skinPixelsTotal > 0) skinConfidenceSum / skinPixelsTotal else 0.0f
        val avgLuma = if (skinPixelsTotal > 0) skinLumaSum / skinPixelsTotal else 0.0f

        return SkinProcessingResult(
            refinedSkinMask = refinedMask,
            faceCount = faces.size,
            skinPixelCount = skinPixelsTotal,
            averageSkinConfidence = avgConf,
            averageSkinLuma = avgLuma,
            illuminant = illuminant,
            profileApplied = profile
        )
    }
}
