package com.craftcam.app.color.skin

import com.craftcam.app.color.digicam.DigicamIlluminant
import kotlin.math.*

/**
 * Layer 2: Fast, Perceptually Uniform Continuous Skin Colour Probability Estimator.
 *
 * Employs continuous colorimetric modeling in both OKLab / OKLCh perceptual color space
 * and illuminant-adapted normalized chromaticity + YCbCr spaces.
 *
 * Core Color Science Foundations:
 * 1. Continuous Complexion Manifold: Human skin across all levels of melanin density occupies
 *    a tight, continuous locus in OKLCh:
 *      Lightness  L in [0.12, 0.95] (from deep rich melanin to fair complexion)
 *      Chroma     C in [0.02, 0.19] (rejects desaturated neutrals and hyper-saturated red/orange objects)
 *      Hue angle  h in [25.0 deg, 70.0 deg] (calibrated across diverse illuminants)
 * 2. Continuous Confidence: Continuous Gaussian and smoothstep distributions in [0.0f, 1.0f],
 *    eliminating discrete demographic categories or harsh binary clipping boundaries.
 * 3. Multi-Space Consistency: A pixel must exhibit coherent chromaticity across OKLCh,
 *    normalized (r, g), and YCbCr to receive high skin confidence.
 * 4. Specular Highlight Tolerance: Smooth asymptotic rolloff prevents cheek and forehead dropouts
 *    under direct flash or harsh sunlight.
 */
class SkinColourEstimator {

    companion object {
        private const val PI_F = Math.PI.toFloat()
        private const val RAD_TO_DEG = 180.0f / PI_F

        /**
         * Precomputed sRGB to linear sRGB transfer function lookup table for 8-bit channels.
         */
        private val SRGB_TO_LINEAR_LUT = FloatArray(256) { i ->
            val c = i / 255.0f
            if (c <= 0.04045f) {
                c / 12.92f
            } else {
                ((c + 0.055f) / 1.055f).pow(2.4f)
            }
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
            return t * t * (3.0f - 2.0f * t)
        }

        /**
         * Converts 8-bit sRGB channels to OKLCh [L, C, h_degrees].
         * L in [0.0, 1.0], C in [0.0, ~0.4], h in [0.0, 360.0].
         */
        fun rgbToOklch(r: Int, g: Int, b: Int): FloatArray {
            val rLin = SRGB_TO_LINEAR_LUT[r.coerceIn(0, 255)]
            val gLin = SRGB_TO_LINEAR_LUT[g.coerceIn(0, 255)]
            val bLin = SRGB_TO_LINEAR_LUT[b.coerceIn(0, 255)]

            // Linear sRGB to cone responses (LMS)
            val l = cbrt((0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin).toDouble()).toFloat()
            val m = cbrt((0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin).toDouble()).toFloat()
            val s = cbrt((0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin).toDouble()).toFloat()

            // LMS to OKLab
            val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
            val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
            val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

            // OKLab to OKLCh
            val chroma = sqrt(okA * okA + okB * okB)
            var hue = atan2(okB, okA) * RAD_TO_DEG
            if (hue < 0.0f) hue += 360.0f

            return floatArrayOf(okL, chroma, hue)
        }
    }

    /**
     * Calibrated locus parameters for a given lighting regime across both YCbCr and OKLCh.
     */
    data class LocusCenter(
        val cbCenter: Float,
        val crCenter: Float,
        val cbSigma: Float,
        val crSigma: Float,
        val minRnorm: Float,
        val maxRnorm: Float,
        val minGnorm: Float,
        val maxGnorm: Float,
        val oklchHueCenter: Float,
        val oklchHueSigma: Float,
        val oklchMinChroma: Float,
        val oklchMaxChroma: Float
    )

    private fun resolveLocus(illuminant: DigicamIlluminant): LocusCenter {
        return when (illuminant) {
            DigicamIlluminant.GOLDEN_DAYLIGHT -> LocusCenter(
                cbCenter = 108f, crCenter = 152f, cbSigma = 20f, crSigma = 24f,
                minRnorm = 0.34f, maxRnorm = 0.58f, minGnorm = 0.26f, maxGnorm = 0.39f,
                oklchHueCenter = 50.0f, oklchHueSigma = 16.0f,
                oklchMinChroma = 0.025f, oklchMaxChroma = 0.175f
            )
            DigicamIlluminant.AMBER_TUNGSTEN -> LocusCenter(
                cbCenter = 94f, crCenter = 162f, cbSigma = 22f, crSigma = 26f,
                minRnorm = 0.40f, maxRnorm = 0.65f, minGnorm = 0.25f, maxGnorm = 0.37f,
                oklchHueCenter = 44.0f, oklchHueSigma = 15.0f,
                oklchMinChroma = 0.030f, oklchMaxChroma = 0.190f
            )
            DigicamIlluminant.GOLDEN_DIRECT_FLASH -> LocusCenter(
                cbCenter = 104f, crCenter = 156f, cbSigma = 20f, crSigma = 24f,
                minRnorm = 0.36f, maxRnorm = 0.58f, minGnorm = 0.26f, maxGnorm = 0.38f,
                oklchHueCenter = 48.0f, oklchHueSigma = 14.0f,
                oklchMinChroma = 0.028f, oklchMaxChroma = 0.180f
            )
            DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL -> LocusCenter(
                cbCenter = 102f, crCenter = 144f, cbSigma = 22f, crSigma = 25f,
                minRnorm = 0.33f, maxRnorm = 0.52f, minGnorm = 0.29f, maxGnorm = 0.42f,
                oklchHueCenter = 56.0f, oklchHueSigma = 16.0f,
                oklchMinChroma = 0.025f, oklchMaxChroma = 0.165f
            )
        }
    }

    /**
     * Estimates continuous skin probability confidence map for [pixels].
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
        val invHueSigma2 = 1.0f / (locus.oklchHueSigma * locus.oklchHueSigma)

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            val sum = (r + g + b).toFloat()
            // Suppress deep sensor noise and pure blacks (< 25 sum)
            if (sum < 25.0f) {
                mask[i] = 0.0f
                continue
            }

            // Universal physiological rule for oxygenated / deoxygenated hemoglobin & melanin:
            // Under daylight, tungsten, and flash, Red channel strictly dominates Green and Blue
            if (r <= g || r <= b) {
                mask[i] = 0.0f
                continue
            }

            // 1. Normalized Chromaticity Bounds
            val rNorm = r / sum
            val gNorm = g / sum

            val rWeight = smoothstep(locus.minRnorm - 0.04f, locus.minRnorm + 0.02f, rNorm) *
                    (1.0f - smoothstep(locus.maxRnorm - 0.02f, locus.maxRnorm + 0.06f, rNorm))
            val gWeight = smoothstep(locus.minGnorm - 0.03f, locus.minGnorm + 0.02f, gNorm) *
                    (1.0f - smoothstep(locus.maxGnorm - 0.02f, locus.maxGnorm + 0.04f, gNorm))

            val chromaConf = (rWeight * gWeight).coerceIn(0.0f, 1.0f)
            if (chromaConf <= 0.005f) {
                mask[i] = 0.0f
                continue
            }

            // 2. YCbCr Rec.601 Transform & Ellipsoidal Distance
            val yLuma = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = -0.168736f * r - 0.331264f * g + 0.500f * b + 128.0f
            val cr = 0.500f * r - 0.418688f * g - 0.081312f * b + 128.0f

            val dCb = cb - locus.cbCenter
            val dCr = cr - locus.crCenter
            val dist2Ycbcr = (dCb * dCb * invCbSigma2) + (dCr * dCr * invCrSigma2)
            val ycbcrConf = exp(-0.5f * dist2Ycbcr)

            // 3. OKLab / OKLCh Perceptual Uniform Model
            val rLin = SRGB_TO_LINEAR_LUT[r]
            val gLin = SRGB_TO_LINEAR_LUT[g]
            val bLin = SRGB_TO_LINEAR_LUT[b]

            val lCone = cbrt((0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin).toDouble()).toFloat()
            val mCone = cbrt((0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin).toDouble()).toFloat()
            val sCone = cbrt((0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin).toDouble()).toFloat()

            val okL = 0.2104542553f * lCone + 0.7936177850f * mCone - 0.0040720468f * sCone
            val okA = 1.9779984951f * lCone - 2.4285922050f * mCone + 0.4505937099f * sCone
            val okB = 0.0259040371f * lCone + 0.7827717662f * mCone - 0.8086757660f * sCone

            val okChroma = sqrt(okA * okA + okB * okB)
            var okHue = atan2(okB, okA) * RAD_TO_DEG
            if (okHue < 0.0f) okHue += 360.0f

            // A. OKLCh Hue Gaussian Confidence
            val dHue = okHue - locus.oklchHueCenter
            val oklchHueConf = exp(-0.5f * (dHue * dHue) * invHueSigma2)

            // B. OKLCh Chroma Boundary Confidence
            // Gracefully suppresses neutral gray walls (C < minChroma) and vibrant red clothing/neon objects (C > maxChroma)
            val oklchChromaConf = smoothstep(locus.oklchMinChroma - 0.015f, locus.oklchMinChroma + 0.010f, okChroma) *
                    (1.0f - smoothstep(locus.oklchMaxChroma - 0.020f, locus.oklchMaxChroma + 0.035f, okChroma))

            // C. OKLCh Lightness Confidence
            // Broad continuous envelope preserving deep melanin (L down to 0.15) while allowing flash highlights up to 0.96
            val oklchLightnessConf = smoothstep(0.10f, 0.22f, okL) *
                    (1.0f - smoothstep(0.92f, 0.98f, okL) * 0.45f)

            val oklchFused = (oklchHueConf * oklchChromaConf * oklchLightnessConf).coerceIn(0.0f, 1.0f)

            // 4. Perceptual Luminance Weighting
            val lumaConf = smoothstep(12.0f, 30.0f, yLuma) * (1.0f - smoothstep(248.0f, 255.0f, yLuma) * 0.40f)

            // 5. Multi-Space Fused Continuous Probability
            // Balanced geometric blend of normalized chromaticity, YCbCr, and OKLCh
            val rawFused = chromaConf * 0.30f + ycbcrConf * 0.35f + oklchFused * 0.35f
            mask[i] = (rawFused * lumaConf).coerceIn(0.0f, 1.0f)
        }

        return mask
    }
}
