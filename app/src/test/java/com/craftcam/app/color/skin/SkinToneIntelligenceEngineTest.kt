package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SkinToneIntelligenceEngineTest {

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
    fun colorEstimator_detectsFairToDeepSkinComplexions() {
        val width = 1
        val height = 4
        // 1. Fair Peach (Fitzpatrick I-II): R=225, G=175, B=145
        // 2. Olive / Tan (Fitzpatrick III-IV): R=185, G=130, B=95
        // 3. Deep Brown (Fitzpatrick V-VI): R=110, G=70, B=50
        // 4. Pure Grass Green (Non-skin): R=45, G=165, B=40
        val pixels = intArrayOf(
            createPixel(225, 175, 145),
            createPixel(185, 130, 95),
            createPixel(110, 70, 50),
            createPixel(45, 165, 40)
        )

        val mask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.GOLDEN_DAYLIGHT)

        assertTrue("Fair skin must have high probability", mask[0] > 0.40f)
        assertTrue("Olive/Tan skin must have high probability", mask[1] > 0.40f)
        assertTrue("Deep brown skin must have significant probability (not clipped)", mask[2] > 0.30f)
        assertEquals("Foliage must have 0.0 probability", 0.0f, mask[3], 0.001f)
    }

    @Test
    fun colorEstimator_rejectsSkyBlueAndVibrantRedFabric() {
        val width = 1
        val height = 2
        // 1. Sky blue: R=100, G=160, B=230 (B > R)
        // 2. Highly saturated scarlet red dress (Ref 2): R=239, G=50, B=30 (R >> G, rNorm ~ 0.75 outside skin locus)
        val pixels = intArrayOf(
            createPixel(100, 160, 230),
            createPixel(239, 50, 30)
        )

        val mask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.GOLDEN_DAYLIGHT)

        assertEquals("Sky blue must be rejected", 0.0f, mask[0], 0.001f)
        assertTrue("Scarlet red fabric must be rejected or have very low skin confidence", mask[1] < 0.15f)
    }

    @Test
    fun colorEstimator_adaptsToAmberTungstenLighting() {
        val width = 1
        val height = 1
        // Under heavy amber tungsten (Ref 1/3/6), skin shifts with high red and attenuated blue: R=195, G=125, B=45
        val pixels = intArrayOf(createPixel(195, 125, 45))

        val daylightMask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.GOLDEN_DAYLIGHT)
        val tungstenMask = colorEstimator.estimateColourProbability(pixels, width, height, DigicamIlluminant.AMBER_TUNGSTEN)

        assertTrue("Tungsten-adapted locus must recognize tungsten-lit skin better than daylight locus", tungstenMask[0] > daylightMask[0])
        assertTrue("Tungsten skin confidence must be robust", tungstenMask[0] > 0.45f)
    }

    @Test
    fun anatomyPrior_suppressesBackgroundWoodAndWallsWhenFacesDetected() {
        val width = 100
        val height = 100

        // Place a detected face in center [35..65, 20..50]
        val face = FaceRegion(
            bounds = createRect(0.35f, 0.20f, 0.65f, 0.50f),
            confidence = 0.95f
        )
        val faces = listOf(face)

        val prior = anatomyPrior.generateSpatialPrior(width, height, faces, backgroundSuppression = 0.80f)

        // Inside face center (x=50, y=35)
        val faceVal = prior[35 * width + 50]
        assertEquals("Face zone must have 1.0 spatial prior", 1.0f, faceVal, 0.001f)

        // Neck zone below face (x=50, y=55)
        val neckVal = prior[55 * width + 50]
        assertTrue("Neck zone must have high spatial prior", neckVal >= 0.70f)

        // Distant background corner (x=5, y=5) - representing wooden furniture or brown wall
        val bgVal = prior[5 * width + 5]
        assertTrue("Distant background must be heavily suppressed by ~80%", bgVal <= 0.25f)
    }

    @Test
    fun anatomyPrior_fallsBackToNeutralWhenNoFacesDetected() {
        val width = 20
        val height = 20
        val prior = anatomyPrior.generateSpatialPrior(width, height, emptyList())

        // When no faces are present (e.g. photos of hands or legs), spatial prior must return 1.0 everywhere
        for (v in prior) {
            assertEquals("Headless fallback must be 1.0 everywhere", 1.0f, v, 0.001f)
        }
    }

    @Test
    fun maskRefiner_producesSmoothContinuityWithoutNaNs() {
        val width = 40
        val height = 40
        val raw = FloatArray(width * height) { idx ->
            val x = idx % width
            if (x in 15..25) 1.0f else 0.0f
        }

        val refined = maskRefiner.refineMask(raw, width, height, featherRadiusFrac = 0.03f)

        assertEquals(raw.size, refined.size)
        for (v in refined) {
            assertFalse("Mask must not contain NaN", v.isNaN())
            assertTrue("Mask must be in [0.0, 1.0]", v in 0.0f..1.0f)
        }

        // Boundary x=14 and x=15 should have feathered intermediate values
        val edgeVal = refined[20 * width + 14]
        assertTrue("Boundary pixel must be smoothly feathered", edgeVal > 0.0f && edgeVal < 1.0f)
    }

    @Test
    fun engine_protectsOversaturatedSkinWithoutGrayingOrBleaching() {
        val width = 20
        val height = 20
        val total = width * height
        // Oversaturated neon/sunburned skin pixel (R=255, G=120, B=50 -> sat ~ 0.80)
        val pixels = IntArray(total) { createPixel(255, 120, 50) }

        val face = FaceRegion(bounds = createRect(0.0f, 0.0f, 1.0f, 1.0f), confidence = 0.95f)
        val profile = SkinToneProfileCatalog.CPM35

        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.AMBER_TUNGSTEN,
            profile = profile
        )

        assertTrue("Skin pixels must be detected", result.skinPixelCount > 0)

        val outP = pixels[5 * width + 5]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        val maxC = maxOf(outR, maxOf(outG, outB))
        val minC = minOf(outR, minOf(outG, outB))
        val postSat = (maxC - minC).toFloat() / maxC.toFloat()

        assertTrue("Post saturation must be dampened to prevent sunburned look", postSat < 0.80f)
        assertTrue("Skin must retain healthy color (not bleached/gray)", postSat > 0.35f)
        assertTrue("Red must remain dominant", outR > outG && outG > outB)
    }

    @Test
    fun engine_preservesDeepComplexionFromMuddyBlackCrush() {
        val width = 10
        val height = 10
        val total = width * height
        // Deep skin tone (Fitzpatrick VI): R=60, G=40, B=28 (low luma ~ 42)
        val pixels = IntArray(total) { createPixel(60, 40, 28) }

        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.90f)
        val profile = SkinToneProfileCatalog.CCD

        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = profile
        )

        val outP = pixels[5 * width + 5]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        // Shadow protection ensures the deep complexion is preserved and gently lifted, not crushed to 0
        assertTrue("Deep skin tone must retain warm separation", outR >= 60)
        assertTrue("Deep skin tone must not crush into pure black", outG >= 38)
        assertTrue("Skin tone must maintain natural hue hierarchy R > G > B", outR > outG && outG > outB)
    }

    @Test
    fun profileCatalog_resolvesProfilesForPresets() {
        val dfuns = SkinToneProfileCatalog.getProfileForPreset("ccd_flash", CameraCategory.CCD_DIGITAL)
        val cpm35 = SkinToneProfileCatalog.getProfileForPreset("disp_flash", CameraCategory.DISPOSABLE)
        val ccd = SkinToneProfileCatalog.getProfileForPreset("ccd_r", CameraCategory.CCD_DIGITAL)
        val fxnr = SkinToneProfileCatalog.getProfileForPreset("analog_fxn_r", CameraCategory.ANALOG_FILM)
        val natural = SkinToneProfileCatalog.getProfileForPreset("zero")

        assertEquals("skin_dfuns", dfuns.id)
        assertEquals("skin_cpm35", cpm35.id)
        assertEquals("skin_ccd", ccd.id)
        assertEquals("skin_fxnr", fxnr.id)
        assertEquals("skin_natural", natural.id)
    }
}
