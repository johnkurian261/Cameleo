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
 * Master Skin Tone Intelligence Engine for CAMELEO.
 *
 * Implements a four-layer architecture:
 * - Layer 1: Illuminant-Aware Camera Colour Science.
 * - Layer 2: Multi-Space Soft Colour Probability (YCbCr + normalized chromaticity).
 * - Layer 3: Anatomical Spatial Prior & Confidence-Aware Fusion.
 * - Layer 4: Camera-Specific Skin Rendering & Photographic Protection.
 *
 * Strictly PROHIBITED:
 * - Beauty smoothing or pore erasure.
 * - Skin whitening or lightening.
 * - Facial reshaping or morphing.
 * - Forcing diverse human complexions toward a single peach hue.
 * - Artificial relighting.
 */
class SkinToneIntelligenceEngine(
    private val colorEstimator: SkinColourEstimator = SkinColourEstimator(),
    private val anatomyPrior: SkinAnatomyPrior = SkinAnatomyPrior(),
    private val maskRefiner: SkinMaskRefiner = SkinMaskRefiner()
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
        iso: Int = 100
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

        // ── Layer 2: Multi-Space Soft Colour Probability ─────────────────────────
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

        // ── Layer 4: Camera-Specific Skin Rendering & Protection ─────────────────
        var skinPixelsTotal = 0
        var skinConfidenceSum = 0.0f
        var skinLumaSum = 0.0f

        val skinGains = profile.skinGains
        val gainR = skinGains[0]
        val gainG = skinGains[1]
        val gainB = skinGains[2]

        val warmthStr = config.warmthStrength
        val maxSat = profile.maxSkinSaturation
        val knee = profile.skinHighlightKnee
        val midLift = profile.skinMidtoneLift
        val maxHueShift = config.maximumHueShiftDegrees

        for (i in 0 until total) {
            val conf = refinedMask[i]
            if (conf < 0.03f) continue

            skinPixelsTotal++
            skinConfidenceSum += conf

            val p = pixels[i]
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF

            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            skinLumaSum += luma

            var rf = r.toFloat()
            var gf = g.toFloat()
            var bf = b.toFloat()

            // 1. Lower-Midtone Lift for Skin (prevents harsh, unflattering facial shadows)
            if (midLift > 0.001f && luma in 30.0f..160.0f) {
                val midNorm = (luma - 30.0f) / 130.0f
                val liftFactor = 4.0f * midNorm * (1.0f - midNorm) * midLift * conf * 255.0f
                rf = (rf + liftFactor * 0.95f).coerceAtMost(255f)
                gf = (gf + liftFactor * 0.90f).coerceAtMost(255f)
                bf = (bf + liftFactor * 0.80f).coerceAtMost(255f)
            }

            // 2. Camera-Specific Skin Gains (Warmth, Amber, or Golden Daylight Response)
            if (warmthStr > 0.01f) {
                val effGainR = 1.0f + (gainR - 1.0f) * warmthStr * conf
                val effGainG = 1.0f + (gainG - 1.0f) * warmthStr * conf
                val effGainB = 1.0f + (gainB - 1.0f) * warmthStr * conf
                rf = (rf * effGainR).coerceIn(0f, 255f)
                gf = (gf * effGainG).coerceIn(0f, 255f)
                bf = (bf * effGainB).coerceIn(0f, 255f)
            }

            // 3. Highlight Asymptotic Rolloff on Skin (protects cheeks/foreheads from hard clipping)
            val maxC = maxOf(rf, maxOf(gf, bf))
            val knee255 = knee * 255.0f
            if (maxC > knee255) {
                val excess = maxC - knee255
                val headroom = 255.0f - knee255
                val rolledMax = knee255 + headroom * (excess / (excess + headroom * 0.80f))
                val scale = rolledMax / maxC
                val blendScale = 1.0f - (1.0f - scale) * conf
                rf *= blendScale
                gf *= blendScale
                bf *= blendScale
            }

            // 4. Perceptual Saturation Protection (guards against sunburned, orange, or neon skin)
            val curLuma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            val maxColor = maxOf(rf, maxOf(gf, bf))
            val minColor = minOf(rf, minOf(gf, bf))
            val curSat = if (maxColor > 0.001f) (maxColor - minColor) / maxColor else 0.0f

            if (curSat > maxSat && config.protectionEnabled) {
                val satScale = maxSat / curSat
                val satBlend = 1.0f - (1.0f - satScale) * conf * config.saturationProtection
                rf = curLuma + (rf - curLuma) * satBlend
                gf = curLuma + (gf - curLuma) * satBlend
                bf = curLuma + (bf - curLuma) * satBlend
            }

            // 5. Cross-Processing Green Cast Guard (prevents creative film LUTs from turning skin sickly green)
            if (gf > rf * 1.02f && config.protectionEnabled) {
                val excessGreen = (gf - rf) * conf * 0.60f
                gf -= excessGreen
                rf += excessGreen * 0.30f
            }

            // 6. Deep Skin Tone Shadow Protection (preserves rich dark complexions from crushing into muddy noise)
            if (curLuma < 45.0f && config.protectionEnabled) {
                val shadowLift = ((45.0f - curLuma) / 45.0f) * 12.0f * conf * config.shadowProtection
                rf = (rf + shadowLift * 1.05f).coerceIn(0f, 255f)
                gf = (gf + shadowLift * 0.95f).coerceIn(0f, 255f)
                bf = (bf + shadowLift * 0.90f).coerceIn(0f, 255f)
            }

            pixels[i] = (0xFF shl 24) or
                    (rf.roundToInt().coerceIn(0, 255) shl 16) or
                    (gf.roundToInt().coerceIn(0, 255) shl 8) or
                    bf.roundToInt().coerceIn(0, 255)
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
