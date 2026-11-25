package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

class D3DSkinToneCorrectionTest {

    private lateinit var colorEstimator: SkinColourEstimator
    private lateinit var anatomyPrior: SkinAnatomyPrior
    private lateinit var maskRefiner: SkinMaskRefiner
    private lateinit var engine: SkinToneIntelligenceEngine

    @Before
    fun setUp() {
        colorEstimator = SkinColourEstimator()
        anatomyPrior = SkinAnatomyPrior()
        maskRefiner = SkinMaskRefiner()
        engine = SkinToneIntelligenceEngine(colorEstimator, anatomyPrior, maskRefiner)
    }

    private fun createPixel(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private fun createRect(l: Float, t: Float, r: Float, b: Float): RectF {
        return RectF().apply {
            left = l
            top = t
            right = r
            bottom = b
        }
    }

    @Test
    fun d3d_resolvesDedicatedSkinProfile() {
        val d3dProfileByPreset = SkinToneProfileCatalog.getProfileForPreset("analog_cinema_film", CameraCategory.ANALOG_FILM)
        val d3dProfileById = SkinToneProfileCatalog.getProfileForPreset("d3d")
        val d3dProfileByAlias = SkinToneProfileCatalog.getProfileForPreset("d_3d")

        assertEquals("skin_d3d", d3dProfileByPreset.id)
        assertEquals("skin_d3d", d3dProfileById.id)
        assertEquals("skin_d3d", d3dProfileByAlias.id)

        // Must NOT inherit CPM35, CCD, or generic FXNR
        assertNotEquals("skin_cpm35", d3dProfileByPreset.id)
        assertNotEquals("skin_ccd", d3dProfileByPreset.id)
        assertNotEquals("skin_fxnr", d3dProfileByPreset.id)

        // Verify D3D specific parameters
        assertTrue("D3D must have yellowCastProtectionEnabled", d3dProfileByPreset.config.yellowCastProtectionEnabled)
        assertEquals("D3D target hue bias must be 0.0 (neutral)", 0.0f, d3dProfileByPreset.targetHueBias, 0.001f)
        assertEquals("D3D skin gains must be unity", 1.0f, d3dProfileByPreset.skinGains[0], 0.001f)
        assertEquals("D3D skin gains must be unity", 1.0f, d3dProfileByPreset.skinGains[1], 0.001f)
        assertEquals("D3D skin gains must be unity", 1.0f, d3dProfileByPreset.skinGains[2], 0.001f)
    }

    @Test
    fun d3d_correctsExcessiveYellowCastWithoutTurningPink() {
        val width = 10
        val height = 10
        val total = width * height
        // Graded D3D pixel that suffered from excessive yellow/mustard cast:
        // R=236, G=188, B=110 (R/B = 2.15, R/G = 1.26, b* ~ 45.7)
        val pixels = IntArray(total) { createPixel(236, 188, 110) }
        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D3D
        )

        assertTrue("Skin pixels must be detected", result.skinPixelCount > 0)

        val outP = pixels[5 * width + 5]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        val rbRatio = outR.toFloat() / outB.toFloat()
        val rgRatio = outR.toFloat() / outG.toFloat()

        // 1. Blue channel starvation must be healed: R/B restored to balanced range [1.50..1.95]
        assertTrue("R/B ratio must be brought into balanced photographic range (was 2.77)", rbRatio in 1.50f..2.10f)

        // 2. Green over-amplification must be dampening: R/G healthy [1.22..1.40]
        assertTrue("R/G ratio must be healthy (was 1.21)", rgRatio >= 1.23f)

        // 3. Must NOT overcorrect into pink: Red must remain dominant, Blue must remain lowest
        assertTrue("Must preserve natural skin hierarchy R > G > B", outR > outG && outG > outB)

        // 4. Check OKLCh hue: must be in balanced warm locus [48..72 deg], NEVER pink (< 40 deg)
        val oklch = SkinColourEstimator.rgbToOklch(outR, outG, outB)
        println("D3D corrected pixel: RGB=($outR, $outG, $outB), OKLCh=(L=${oklch[0]}, C=${oklch[1]}, h=${oklch[2]})")
        assertTrue("Skin hue must be in balanced warm locus (was ${oklch[2]}, not pink < 45)", oklch[2] in 48.0f..72.0f)
        assertTrue("Chroma must be controlled (not neon mustard > 0.12)", oklch[1] < 0.11f)
    }

    @Test
    fun d3d_preservesVibrantYellowFabricsWithoutModification() {
        val width = 1
        val height = 1
        // Electric yellow raincoat/fabric: R=240, G=220, B=30 (outside skin locus g_norm > 0.44)
        val pixels = intArrayOf(createPixel(240, 220, 30))

        val mask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.GOLDEN_DAYLIGHT)
        assertEquals("Yellow clothing must have 0.0 skin confidence", 0.0f, mask[0], 0.01f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = emptyList(),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D3D
        )

        val outP = pixels[0]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        assertEquals("Yellow fabric R must be untouched", 240, outR)
        assertEquals("Yellow fabric G must be untouched", 220, outG)
        assertEquals("Yellow fabric B must be untouched", 30, outB)
    }

    @Test
    fun d3d_preservesRedClothingSaturation() {
        val width = 1
        val height = 1
        // Saturated red dress: R=230, G=45, B=35
        val pixels = intArrayOf(createPixel(230, 45, 35))

        val mask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.GOLDEN_DAYLIGHT)
        assertTrue("Red dress must have very low/zero skin confidence", mask[0] < 0.10f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = emptyList(),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D3D
        )

        val outP = pixels[0]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        assertTrue("Red dress must remain vividly red", outR >= 225 && outG <= 50 && outB <= 40)
    }

    @Test
    fun d3d_preservesDeepMelaninSkinTones() {
        val width = 10
        val height = 10
        val total = width * height
        // Deep melanin complexion: R=110, G=70, B=50
        val pixels = IntArray(total) { createPixel(110, 70, 50) }
        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.90f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D3D
        )

        val outP = pixels[5 * width + 5]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        assertTrue("Deep skin must maintain warm separation", outR >= 105)
        assertTrue("Deep skin must preserve natural melanin ratio R > G > B", outR > outG && outG > outB)
    }

    @Test
    fun unrelatedProfiles_remainCompletelyUnchanged() {
        val cpm35 = SkinToneProfileCatalog.getProfileForPreset("disp_flash")
        val ccd = SkinToneProfileCatalog.getProfileForPreset("ccd_r")
        val dfuns = SkinToneProfileCatalog.getProfileForPreset("ccd_flash")
        val natural = SkinToneProfileCatalog.getProfileForPreset("zero")

        assertEquals("skin_cpm35", cpm35.id)
        assertEquals("skin_ccd", ccd.id)
        assertEquals("skin_dfuns", dfuns.id)
        assertEquals("skin_natural", natural.id)

        // CPM35 golden target hue bias must remain authentic
        assertEquals(3.5f, cpm35.targetHueBias, 0.001f)
        assertEquals(1.0f, ccd.targetHueBias, 0.001f)
    }
}
