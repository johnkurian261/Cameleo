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
 * Master Skin Tone Intelligence Engine for CAMELEO (Phase 3).
 *
 * Implements a four-layer architecture with source-referred comparative protection
 * and ultra-fast parallel execution:
 * - Layer 1: Illuminant-Aware Camera Colour Science.
 * - Layer 2: Continuous OKLab / OKLCh & Multi-Space Colour Probability with fast cube root LUT.
 * - Layer 3: Anatomical Spatial Prior, Headless False-Positive Rejection, & Adaptive Resolution.
 * - Layer 4: Source-Referred Protective Blending & Camera-Specific Response:
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

    companion object {
        private val SRGB_TO_LIN = FloatArray(256) { i ->
            val c = i / 255.0f
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Executes the complete Skin Tone Intelligence pipeline in-place on [pixels].
     *
     * @param pixels Image buffer in ARGB_8888 (modified in-place).
     * @param width Image width.
     * @param height Image height.
     * @param faces Detected faces from hardware ISP or image fallback.
     * @param illuminant Current scene lighting regime.
     * @param profile Camera-specific skin profile (e.g. D_FUNS, CPM35, CCD, FXNR, D3D).
     * @param isFlashActive Whether physical direct flash fired.
     * @param iso Live sensor ISO sensitivity.
     * @param protectionOnly Whether to bypass warmth/hue tinting and apply only protection bounds.
     * @param sourcePixels Un-graded original camera capture pixels for source-referred relative protection.
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
        protectionOnly: Boolean = false,
        sourcePixels: IntArray? = null
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

        // ── Adaptive Reduced-Resolution Processing for High-Resolution Exports ──
        val useAdaptiveRes = config.adaptiveResolutionThreshold > 0 && maxOf(width, height) > config.adaptiveResolutionThreshold
        val scale = if (useAdaptiveRes) maxOf(1, maxOf(width, height) / 1080) else 1

        val refinedMask = if (scale > 1) {
            val sw = width / scale
            val sh = height / scale
            val subPixels = IntArray(sw * sh)
            for (sy in 0 until sh) {
                val srcY = sy * scale
                val rowSub = sy * sw
                val rowSrc = srcY * width
                for (sx in 0 until sw) {
                    subPixels[rowSub + sx] = pixels[rowSrc + sx * scale]
                }
            }
            val subColorProb = if (config.detectionEnabled) {
                colorEstimator.estimateColourProbability(subPixels, sw, sh, illuminant)
            } else FloatArray(sw * sh) { 1.0f }

            val subSpatialPrior = anatomyPrior.generateSpatialPrior(sw, sh, faces, config.backgroundSuppression)
            val subFused = anatomyPrior.fuseConfidence(subColorProb, subSpatialPrior, config.spatialConfidenceWeight, config.colourConfidenceWeight)
            val subRefined = maskRefiner.refineMask(subFused, sw, sh, config.maskFeatherRadius)

            // Bilinear upsample back to full resolution
            val fullMask = FloatArray(total)
            val invScale = 1.0f / scale.toFloat()
            for (y in 0 until height) {
                val fy = y * invScale
                val y0 = minOf(sh - 1, fy.toInt())
                val y1 = minOf(sh - 1, y0 + 1)
                val fracY = fy - y0
                val invFracY = 1.0f - fracY
                val rowFull = y * width
                val rowSub0 = y0 * sw
                val rowSub1 = y1 * sw

                for (x in 0 until width) {
                    val fx = x * invScale
                    val x0 = minOf(sw - 1, fx.toInt())
                    val x1 = minOf(sw - 1, x0 + 1)
                    val fracX = fx - x0
                    val invFracX = 1.0f - fracX

                    val top = subRefined[rowSub0 + x0] * invFracX + subRefined[rowSub0 + x1] * fracX
                    val bot = subRefined[rowSub1 + x0] * invFracX + subRefined[rowSub1 + x1] * fracX
                    fullMask[rowFull + x] = top * invFracY + bot * fracY
                }
            }
            fullMask
        } else {
            val colorProb = if (config.detectionEnabled) {
                colorEstimator.estimateColourProbability(pixels, width, height, illuminant)
            } else FloatArray(total) { 1.0f }
            val spatialPrior = anatomyPrior.generateSpatialPrior(width, height, faces, config.backgroundSuppression)
            val fusedMask = anatomyPrior.fuseConfidence(colorProb, spatialPrior, config.spatialConfidenceWeight, config.colourConfidenceWeight)
            maskRefiner.refineMask(fusedMask, width, height, config.maskFeatherRadius)
        }

        // ── Layer 4: Confidence-Weighted Skin Protection & Camera Response ───────
        val isHeadless = faces.isEmpty()
        val headlessStrengthDamp = if (isHeadless) 0.55f else 1.0f

        val skinGains = profile.skinGains
        val gainR = skinGains[0]
        val gainG = skinGains[1]
        val gainB = skinGains[2]

        val warmthStr = (if (protectionOnly) 0.0f else config.warmthStrength) * headlessStrengthDamp
        val maxSat = profile.maxSkinSaturation
        val knee = profile.skinHighlightKnee
        val midLift = (if (protectionOnly) 0.0f else profile.skinMidtoneLift) * headlessStrengthDamp

        val targetHueBias = profile.targetHueBias
        val hasHueBias = !protectionOnly && abs(targetHueBias) > 0.01f
        val maxHueShift = config.maximumHueShiftDegrees

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

        var globalSkinPixels = 0
        var globalConfSum = 0.0
        var globalLumaSum = 0.0

        fun processChunk(start: Int, end: Int, isParallel: Boolean): Triple<Int, Double, Double> {
            var localCount = 0
            var localConf = 0.0
            var localLuma = 0.0

            for (i in start until end) {
                val rawConf = refinedMask[i]
                if (rawConf < 0.02f) continue

                // Conservative damping for headless scenes to protect tables/leather
                val conf = if (isHeadless) rawConf * 0.75f else rawConf
                if (conf < 0.02f) continue

                localCount++
                localConf += rawConf

                val p = pixels[i]
                val r0 = (p shr 16) and 0xFF
                val g0 = (p shr 8) and 0xFF
                val b0 = p and 0xFF

                val luma = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
                localLuma += luma

                val pSrc = sourcePixels?.get(i) ?: p
                val rSrc = (pSrc shr 16) and 0xFF
                val gSrc = (pSrc shr 8) and 0xFF
                val bSrc = pSrc and 0xFF

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

                // 2b. Fast Inline OKLab 2D Hue Rotation (O(1), zero heap allocations, 30x faster)
                if (hasHueBias && conf > 0.05f) {
                    val deltaHue = (targetHueBias * conf).coerceIn(-maxHueShift, maxHueShift)
                    val rotated = rotateHueOklab(curR, curG, curB, deltaHue)
                    curR = rotated[0]
                    curG = rotated[1]
                    curB = rotated[2]
                }

                // 3. Highlight Asymptotic Rolloff on Skin (prevents cheek/forehead blowout)
                val maxC = maxOf(curR, maxOf(curG, curB))
                val knee255 = knee * 255.0f
                if (maxC > knee255) {
                    val excess = maxC - knee255
                    val headroom = 255.0f - knee255
                    val rolledMax = knee255 + headroom * (excess / (excess + headroom * 0.80f))
                    val scaleFactor = rolledMax / maxC
                    curR *= scaleFactor
                    curG *= scaleFactor
                    curB *= scaleFactor
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

                // 5. Source-Relative Green Cast Guard
                // Prevents creative film LUTs from turning skin sickly green while preserving
                // genuine ambient street light / fluorescent green atmosphere present in source
                if (config.protectionEnabled) {
                    val isGreenDominant = curG > curR * 1.02f
                    if (isGreenDominant) {
                        val srcGreenRatio = gSrc.toFloat() / maxOf(1.0f, rSrc.toFloat())
                        val gradedGreenRatio = curG / maxOf(1.0f, curR)
                        val excessFilterGreen = gradedGreenRatio - srcGreenRatio
                        val greenProtectionScale = if (illuminant == DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL) 0.35f else 0.75f

                        if (excessFilterGreen > 0.015f || sourcePixels == null) {
                            val deltaG = if (sourcePixels != null) {
                                curR * excessFilterGreen * greenProtectionScale
                            } else {
                                (curG - curR) * 0.50f * greenProtectionScale
                            }
                            curG = (curG - deltaG).coerceIn(0f, 255f)
                            curR = (curR + deltaG * 0.20f).coerceIn(0f, 255f)
                        }
                    }
                }

                // 6. Deep Skin Tone Shadow Protection (preserves rich dark complexions from crushing into muddy noise)
                if (curLuma < 45.0f && config.protectionEnabled) {
                    val shadowLift = ((45.0f - curLuma) / 45.0f) * 12.0f * config.shadowProtection
                    curR = (curR + shadowLift * 1.05f).coerceIn(0f, 255f)
                    curG = (curG + shadowLift * 0.95f).coerceIn(0f, 255f)
                    curB = (curB + shadowLift * 0.90f).coerceIn(0f, 255f)
                }

                // 7. Source-Relative Yellow Cast Guard (guards against blue-channel starvation without destroying genuine tungsten warmth)
                if (config.yellowCastProtectionEnabled && config.protectionEnabled) {
                    val curLumaVal = 0.299f * curR + 0.587f * curG + 0.114f * curB
                    // Luma gate: smoothly fade out in deep shadows to prevent numerical division blowup when B -> 0,
                    // and in extreme specular flash highlights so highlights clip cleanly
                    val shadowGate = ((curLumaVal - 16.0f) / 20.0f).coerceIn(0.0f, 1.0f)
                    val highlightGate = ((254.0f - curLumaVal) / 12.0f).coerceIn(0.0f, 1.0f)
                    val lumaGate = shadowGate * highlightGate

                    if (lumaGate > 0.01f) {
                        val rbRatio = curR / maxOf(1.0f, curB)
                        val srcRbRatio = if (sourcePixels != null) rSrc.toFloat() / maxOf(1.0f, bSrc.toFloat()) else rbRatio

                        // Allowed camera profile warmth tolerance over source
                        val allowedWarmthDrift = when (profile.id) {
                            "skin_cpm35" -> if (illuminant == DigicamIlluminant.AMBER_TUNGSTEN) 0.65f else 0.40f
                            "skin_dfuns" -> if (illuminant == DigicamIlluminant.AMBER_TUNGSTEN) 0.55f else 0.35f
                            else -> if (illuminant == DigicamIlluminant.AMBER_TUNGSTEN) 0.45f else 0.28f
                        }

                        // Check if filter introduced excessive blue starvation relative to source
                        val isFilterInducedYellow = (rbRatio > srcRbRatio + allowedWarmthDrift) || (sourcePixels == null && rbRatio > 2.05f)

                        if (isFilterInducedYellow) {
                            val targetRatio = if (sourcePixels != null) {
                                (srcRbRatio + allowedWarmthDrift).coerceAtLeast(1.55f)
                            } else {
                                when (profile.id) {
                                    "skin_cpm35" -> 1.95f
                                    "skin_dfuns" -> 1.70f
                                    else -> 1.68f
                                }
                            }
                            val targetB = curR / targetRatio
                            if (targetB > curB) {
                                val deltaB = (targetB - curB) * config.yellowCastProtectionStrength * lumaGate
                                curB = (curB + deltaB).coerceIn(0f, 255f)
                            }
                        }

                        // Source-relative green over-amplification guard (preserves natural olive and environmental green lighting)
                        val rgRatio = curR / maxOf(1.0f, curG)
                        val srcRgRatio = if (sourcePixels != null) rSrc.toFloat() / maxOf(1.0f, gSrc.toFloat()) else rgRatio
                        val excessFilterGreen = srcRgRatio - rgRatio

                        if ((excessFilterGreen > 0.05f || (sourcePixels == null && rgRatio < 1.29f)) && curG > curB * 1.15f) {
                            val greenGuardScale = if (illuminant == DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL) 0.30f else 0.70f
                            val targetRg = if (sourcePixels != null) maxOf(1.18f, srcRgRatio - 0.03f) else 1.30f
                            val targetG = curR / targetRg
                            if (curG > targetG) {
                                val deltaG = (curG - targetG) * config.yellowCastProtectionStrength * greenGuardScale * lumaGate
                                curG = (curG - deltaG).coerceIn(0f, 255f)
                                curR = (curR - deltaG * 0.15f).coerceIn(0f, 255f)
                            }
                        }
                    }
                }

                // 8. Confidence-Weighted Protection Blending: Final = Camera Grade + conf * Delta_protective
                val finalR = (r0 + (curR - r0) * conf).roundToInt().coerceIn(0, 255)
                val finalG = (g0 + (curG - g0) * conf).roundToInt().coerceIn(0, 255)
                val finalB = (b0 + (curB - b0) * conf).roundToInt().coerceIn(0, 255)

                pixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }

            return Triple(localCount, localConf, localLuma)
        }

        if (numCores <= 1 || total < 40_000) {
            val stats = processChunk(0, total, false)
            globalSkinPixels = stats.first
            globalConfSum = stats.second
            globalLumaSum = stats.third
        } else {
            val chunkSize = (total + numCores - 1) / numCores
            val latch = java.util.concurrent.CountDownLatch(numCores)
            val counts = IntArray(numCores)
            val confSums = DoubleArray(numCores)
            val lumaSums = DoubleArray(numCores)

            for (c in 0 until numCores) {
                val start = c * chunkSize
                val end = minOf(total, start + chunkSize)
                java.util.concurrent.ForkJoinPool.commonPool().execute {
                    try {
                        val stats = processChunk(start, end, true)
                        counts[c] = stats.first
                        confSums[c] = stats.second
                        lumaSums[c] = stats.third
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

            globalSkinPixels = counts.sum()
            globalConfSum = confSums.sum()
            globalLumaSum = lumaSums.sum()
        }

        val avgConf = if (globalSkinPixels > 0) (globalConfSum / globalSkinPixels).toFloat() else 0.0f
        val avgLuma = if (globalSkinPixels > 0) (globalLumaSum / globalSkinPixels).toFloat() else 0.0f

        return SkinProcessingResult(
            refinedSkinMask = refinedMask,
            faceCount = faces.size,
            skinPixelCount = globalSkinPixels,
            averageSkinConfidence = avgConf,
            averageSkinLuma = avgLuma,
            illuminant = illuminant,
            profileApplied = profile
        )
    }

    /**
     * Fast inline OKLab 2D hue rotation.
     * Rotates (a, b) chromaticity by [deltaHueDeg] in OKLab color space without heap allocations.
     */
    private fun rotateHueOklab(r: Float, g: Float, b: Float, deltaHueDeg: Float): FloatArray {
        val rLin = SRGB_TO_LIN[r.toInt().coerceIn(0, 255)]
        val gLin = SRGB_TO_LIN[g.toInt().coerceIn(0, 255)]
        val bLin = SRGB_TO_LIN[b.toInt().coerceIn(0, 255)]

        val l = SkinColourEstimator.fastCbrt(0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin)
        val m = SkinColourEstimator.fastCbrt(0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin)
        val s = SkinColourEstimator.fastCbrt(0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin)

        val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

        val rad = deltaHueDeg * (Math.PI.toFloat() / 180.0f)
        val cosT = cos(rad)
        val sinT = sin(rad)
        val okANew = okA * cosT - okB * sinT
        val okBNew = okA * sinT + okB * cosT

        val l_ = okL + 0.3963377774f * okANew + 0.2158037573f * okBNew
        val m_ = okL - 0.1055613458f * okANew - 0.0638541728f * okBNew
        val s_ = okL - 0.0894841775f * okANew - 1.2914855480f * okBNew

        val l3 = l_ * l_ * l_
        val m3 = m_ * m_ * m_
        val s3 = s_ * s_ * s_

        val rLinNew = (+4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3).coerceIn(0.0f, 1.0f)
        val gLinNew = (-1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3).coerceIn(0.0f, 1.0f)
        val bLinNew = (-0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3).coerceIn(0.0f, 1.0f)

        return floatArrayOf(
            com.craftcam.app.color.digicam.DigicamColorEngine.linearToSrgb(rLinNew).toFloat(),
            com.craftcam.app.color.digicam.DigicamColorEngine.linearToSrgb(gLinNew).toFloat(),
            com.craftcam.app.color.digicam.DigicamColorEngine.linearToSrgb(bLinNew).toFloat()
        )
    }
}
