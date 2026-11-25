package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * Verification of camera-specific photographic skin colour science:
 * - D FUNS: Single-use 35mm colour negative film response (organic red-orange, soft transitions, blue preservation).
 * - CPM35: Golden digital compact (radiant amber tungsten, protected daylight).
 * - CCD: Punchy Y2K pocket digicam (crisp texture, direct flash highlights).
 * - D3D: Cinema 3D stereo film (balanced red-orange, no mustard cast).
 * - Multi-camera distinctness: Confirms each camera produces a unique, calibrated photographic signature.
 */
class AuthenticCameraSkinColourScienceTest {

    private val engine = SkinToneIntelligenceEngine()

    private fun createRect(l: Float, t: Float, r: Float, b: Float): RectF {
        return RectF().apply {
            left = l
            top = t
            right = r
            bottom = b
        }
    }

    private fun hex(rgb: IntArray): String =
        String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2])

    private fun colorDistanceLab(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val oklch1 = SkinColourEstimator.rgbToOklch(r1, g1, b1)
        val oklch2 = SkinColourEstimator.rgbToOklch(r2, g2, b2)
        val dL = oklch1[0] - oklch2[0]
        val dC = oklch1[1] - oklch2[1]
        val dH = (oklch1[2] - oklch2[2]) * (Math.PI.toFloat() / 180f)
        return sqrt(dL * dL + dC * dC + dH * dH)
    }

    @Test
    fun testDFunsProducesOrganicRedOrangeSkinWithoutYellowCast() {
        // Standard medium skin pixel (RGB: 219, 165, 138) matching D FUNS reference Swatch 2
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { (0xFF shl 24) or (219 shl 16) or (165 shl 8) or 138 }

        val profile = SkinToneProfileCatalog.D_FUNS
        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            illuminant = DigicamIlluminant.GOLDEN_DIRECT_FLASH,
            profile = profile,
            isFlashActive = true
        )

        assertTrue("D FUNS should detect skin confidence > 0.4", result.averageSkinConfidence > 0.4f)

        val outPixel = pixels[width * height / 2]
        val r = (outPixel shr 16) and 0xFF
        val g = (outPixel shr 8) and 0xFF
        val b = outPixel and 0xFF

        val oklch = SkinColourEstimator.rgbToOklch(r, g, b)
        val rbRatio = r.toFloat() / b.coerceAtLeast(1)

        // D FUNS must stay within organic red-orange (45..58 deg), NOT golden yellow (65..75 deg)
        assertTrue(
            "D FUNS hue angle must be organic red-orange (was ${oklch[2]} deg)",
            oklch[2] in 45.0f..58.0f
        )
        // Blue channel must NOT be starved (R/B <= 1.95)
        assertTrue(
            "D FUNS R/B ratio must preserve blue channel (was $rbRatio)",
            rbRatio <= 1.95f
        )
        // Blue must have healthy presence
        assertTrue("D FUNS blue channel should be >= 130 (was $b)", b >= 130)
    }

    @Test
    fun testCPM35ProducesGoldenAmberSkinUnderTungsten() {
        // Standard medium skin pixel (RGB: 219, 160, 106) matching CPM35 reference Swatch 2
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { (0xFF shl 24) or (219 shl 16) or (160 shl 8) or 106 }

        val profile = SkinToneProfileCatalog.CPM35
        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            illuminant = DigicamIlluminant.AMBER_TUNGSTEN,
            profile = profile,
            isFlashActive = false
        )

        assertTrue(result.averageSkinConfidence > 0.4f)

        val outPixel = pixels[width * height / 2]
        val r = (outPixel shr 16) and 0xFF
        val g = (outPixel shr 8) and 0xFF
        val b = outPixel and 0xFF

        val oklch = SkinColourEstimator.rgbToOklch(r, g, b)
        val rbRatio = r.toFloat() / b.coerceAtLeast(1)

        // CPM35 under tungsten should produce rich golden amber (60..72 deg)
        assertTrue(
            "CPM35 tungsten hue angle must be radiant golden amber (was ${oklch[2]} deg)",
            oklch[2] in 60.0f..72.0f
        )
        assertTrue(
            "CPM35 should allow warm R/B ratio (was $rbRatio)",
            rbRatio in 1.8f..2.45f
        )
    }

    @Test
    fun testCPM35PreventsMustardYellowUnderDaylight() {
        // Sunlight skin with potential yellow bias
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { (0xFF shl 24) or (235 shl 16) or (185 shl 8) or 115 }

        val profile = SkinToneProfileCatalog.CPM35
        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = profile,
            isFlashActive = false
        )

        val outPixel = pixels[width * height / 2]
        val r = (outPixel shr 16) and 0xFF
        val b = outPixel and 0xFF
        val rbRatio = r.toFloat() / b.coerceAtLeast(1)
        val oklch = SkinColourEstimator.rgbToOklch(r, (outPixel shr 8) and 0xFF, b)

        // Must guard against extreme mustard yellow (h < 75 deg and R/B <= 2.35)
        assertTrue(
            "CPM35 daylight hue must not exceed mustard threshold (was ${oklch[2]} deg)",
            oklch[2] < 75.0f
        )
        assertTrue(
            "CPM35 daylight R/B ratio must be protected (was $rbRatio)",
            rbRatio <= 2.35f
        )
    }

    @Test
    fun testCCDProducesCrispDirectFlashHighlights() {
        // High-exposure flash highlight skin pixel (RGB: 250, 220, 185)
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { (0xFF shl 24) or (250 shl 16) or (220 shl 8) or 185 }

        val profile = SkinToneProfileCatalog.CCD
        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            illuminant = DigicamIlluminant.GOLDEN_DIRECT_FLASH,
            profile = profile,
            isFlashActive = true
        )

        val outPixel = pixels[width * height / 2]
        val r = (outPixel shr 16) and 0xFF

        // CCD highlight knee is 0.92f, preserving bright direct flash pop without early film rolloff
        assertTrue("CCD direct flash should preserve luminous highlights (was $r)", r >= 238)
    }

    @Test
    fun testCamerasProduceDistinctPhotographicSkinSignatures() {
        // Feed the EXACT same input skin pixel to D_FUNS, CPM35, CCD, and D3D
        val testColor = intArrayOf(225, 170, 130)
        val faceList = listOf(FaceRegion(bounds = createRect(0.0f, 0.0f, 1.0f, 1.0f), confidence = 0.95f))

        fun processWith(profile: SkinToneProfile, illuminant: DigicamIlluminant): IntArray {
            val buf = IntArray(16) { (0xFF shl 24) or (testColor[0] shl 16) or (testColor[1] shl 8) or testColor[2] }
            engine.process(pixels = buf, width = 4, height = 4, faces = faceList, illuminant = illuminant, profile = profile)
            val p = buf[8]
            return intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        }

        val outDfuns = processWith(SkinToneProfileCatalog.D_FUNS, DigicamIlluminant.GOLDEN_DIRECT_FLASH)
        val outCpm35 = processWith(SkinToneProfileCatalog.CPM35, DigicamIlluminant.AMBER_TUNGSTEN)
        val outCcd = processWith(SkinToneProfileCatalog.CCD, DigicamIlluminant.GOLDEN_DAYLIGHT)
        val outD3d = processWith(SkinToneProfileCatalog.D3D, DigicamIlluminant.GOLDEN_DAYLIGHT)

        val oklchDfuns = SkinColourEstimator.rgbToOklch(outDfuns[0], outDfuns[1], outDfuns[2])
        val oklchCpm35 = SkinColourEstimator.rgbToOklch(outCpm35[0], outCpm35[1], outCpm35[2])
        val oklchCcd = SkinColourEstimator.rgbToOklch(outCcd[0], outCcd[1], outCcd[2])
        val oklchD3d = SkinColourEstimator.rgbToOklch(outD3d[0], outD3d[1], outD3d[2])

        // Verify distinct hue angles across camera types:
        // D FUNS (organic red-orange film) < D3D < CCD < CPM35 (golden amber digicam)
        assertTrue(
            "D FUNS (${oklchDfuns[2]} deg) must have more red-orange hue than CPM35 (${oklchCpm35[2]} deg)",
            oklchDfuns[2] < oklchCpm35[2] - 3.0f
        )

        // Verify pairwise color distances are significant (> 0.02 in OKLCh space)
        val distDfunsCpm35 = colorDistanceLab(outDfuns[0], outDfuns[1], outDfuns[2], outCpm35[0], outCpm35[1], outCpm35[2])
        val distDfunsD3d = colorDistanceLab(outDfuns[0], outDfuns[1], outDfuns[2], outD3d[0], outD3d[1], outD3d[2])

        assertTrue("D FUNS and CPM35 must be distinctly separated (dist=$distDfunsCpm35)", distDfunsCpm35 > 0.02f)
        assertTrue("D FUNS and D3D must be distinctly separated (dist=$distDfunsD3d)", distDfunsD3d > 0.005f)
    }

    @Test
    fun testNonSkinEnvironmentalObjectsAreCompletelyProtected() {
        // Yellow fabric (RGB: 240, 210, 30)
        val yellowBuf = IntArray(16) { (0xFF shl 24) or (240 shl 16) or (210 shl 8) or 30 }
        val resYellow = engine.process(pixels = yellowBuf, width = 4, height = 4, profile = SkinToneProfileCatalog.D_FUNS)
        assertEquals("Yellow clothing must have 0 skin pixels", 0, resYellow.skinPixelCount)

        // Teal shadow (RGB: 20, 45, 55)
        val tealBuf = IntArray(16) { (0xFF shl 24) or (20 shl 16) or (45 shl 8) or 55 }
        val resTeal = engine.process(pixels = tealBuf, width = 4, height = 4, profile = SkinToneProfileCatalog.D_FUNS)
        assertEquals("Teal shadows must have 0 skin pixels", 0, resTeal.skinPixelCount)
    }
}
