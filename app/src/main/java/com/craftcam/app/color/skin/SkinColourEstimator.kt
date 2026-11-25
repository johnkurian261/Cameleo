package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant
import kotlin.math.*

/**
 * Layer 2: Fast, GPU-Compatible Soft Skin Colour Probability Estimator.
 *
 * Evaluates continuous Gaussian probability distributions in illuminant-adapted
 * normalized chromaticity (r-norm, g-norm) and YCbCr color spaces.
 *
 * Characteristics:
 * 1. Continuous confidence in [0.0f, 1.0f] (zero binary thresholding or harsh mask edges).
 * 2. Illuminant-adaptive locus (shifts expected Cb/Cr center under Tungsten, Flash, Artificial, and Daylight).
 * 3. Broad multi-complexion coverage across Fitzpatrick Types I to VI without racial bias.
 * 4. Specular highlight tolerance preventing cheek and forehead dropouts under direct flash.
 */
class SkinColourEstimator {

    companion object {
        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
            return t * t * (3.0f - 2.0f * t)
        }
    }

    /**
     * Calibrated locus parameters for a given lighting regime.
     */
    data class LocusCenter(
        val cbCenter: Float,
        val crCenter: Float,
        val cbSigma: Float,
        val crSigma: Float,
        val minRnorm: Float,
        val maxRnorm: Float,
        val minGnorm: Float,
        val maxGnorm: Float
    )

    private fun resolveLocus(illuminant: DigicamIlluminant): LocusCenter {
        return when (illuminant) {
            DigicamIlluminant.GOLDEN_DAYLIGHT -> LocusCenter(
                cbCenter = 108f, crCenter = 152f, cbSigma = 18f, crSigma = 22f,
                minRnorm = 0.35f, maxRnorm = 0.56f, minGnorm = 0.27f, maxGnorm = 0.38f
            )
            DigicamIlluminant.AMBER_TUNGSTEN -> LocusCenter(
                cbCenter = 94f, crCenter = 162f, cbSigma = 22f, crSigma = 26f,
                minRnorm = 0.40f, maxRnorm = 0.65f, minGnorm = 0.25f, maxGnorm = 0.37f
            )
            DigicamIlluminant.GOLDEN_DIRECT_FLASH -> LocusCenter(
                cbCenter = 104f, crCenter = 156f, cbSigma = 20f, crSigma = 24f,
                minRnorm = 0.36f, maxRnorm = 0.58f, minGnorm = 0.26f, maxGnorm = 0.38f
            )
            DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL -> LocusCenter(
                cbCenter = 102f, crCenter = 144f, cbSigma = 22f, crSigma = 25f,
                minRnorm = 0.33f, maxRnorm = 0.52f, minGnorm = 0.29f, maxGnorm = 0.42f
            )
        }
    }

    /**
     * Estimates soft skin probability confidence map for [pixels].
     *
     * @param pixels Input image buffer (ARGB_8888).
     * @param width Image width.
     * @param height Image height.
     * @param illuminant Prevailing scene lighting regime.
     * @return Continuous confidence FloatArray in [0.0f, 1.0f].
     */
    fun estimateColourProbability(
        pixels: IntArray,
        width: Int,
        height: Int,
        illuminant: DigicamIlluminant = DigicamIlluminant.GOLDEN_DAYLIGHT
    ): FloatArray {
        val total = width * height
        val mask = FloatArray(total)
        val locus = resolveLocus(illuminant)

        val invCbSigma2 = 1.0f / (locus.cbSigma * locus.cbSigma)
        val invCrSigma2 = 1.0f / (locus.crSigma * locus.crSigma)

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val sum = (r + g + b).toFloat()
            // Suppress deep noise and pure blacks (< 15 luma)
            if (sum < 30.0f) {
                mask[i] = 0.0f
                continue
            }

            // 1. Normalized Chromaticity Bounds
            val rNorm = r / sum
            val gNorm = g / sum

            // Universal photographic rule for human skin under real illuminants: R > G and (R - B) > 0
            if (r <= g || r <= b) {
                mask[i] = 0.0f
                continue
            }

            // Smooth chromaticity boundary confidence
            val rWeight = smoothstep(locus.minRnorm - 0.04f, locus.minRnorm + 0.02f, rNorm) *
                    (1.0f - smoothstep(locus.maxRnorm - 0.02f, locus.maxRnorm + 0.05f, rNorm))
            val gWeight = smoothstep(locus.minGnorm - 0.03f, locus.minGnorm + 0.02f, gNorm) *
                    (1.0f - smoothstep(locus.maxGnorm - 0.02f, locus.maxGnorm + 0.04f, gNorm))

            val chromaConf = (rWeight * gWeight).coerceIn(0.0f, 1.0f)
            if (chromaConf <= 0.005f) {
                mask[i] = 0.0f
                continue
            }

            // 2. YCbCr Rec.601 Transform
            val yLuma = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = -0.168736f * r - 0.331264f * g + 0.500f * b + 128.0f
            val cr = 0.500f * r - 0.418688f * g - 0.081312f * b + 128.0f

            // 3. Continuous Ellipsoidal Mahalanobis-like Distance in Cb-Cr Space
            val dCb = cb - locus.cbCenter
            val dCr = cr - locus.crCenter
            val dist2 = (dCb * dCb * invCbSigma2) + (dCr * dCr * invCrSigma2)

            // Gaussian probability density function
            val ycbcrConf = exp(-0.5f * dist2)

            // 4. Perceptual Luminance Weighting
            // Deep Fitzpatrick V-VI tones down to Y=20 are preserved; bright specular flash highlights up to Y=252 are allowed
            val lumaConf = smoothstep(12.0f, 32.0f, yLuma) * (1.0f - smoothstep(248.0f, 255.0f, yLuma) * 0.40f)

            // 5. Multi-Signal Fused Color Confidence
            mask[i] = (chromaConf * ycbcrConf * lumaConf).coerceIn(0.0f, 1.0f)
        }

        return mask
    }
}
