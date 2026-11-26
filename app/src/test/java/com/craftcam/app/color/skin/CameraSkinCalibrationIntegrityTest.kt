package com.craftcam.app.color.skin

import android.graphics.RectF
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Camera Skin Colour Science Calibration Integrity and Authenticity Tests.
 *
 * Verifies photographic authenticity, source-relative colour preservation,
 * lack of arbitrary universal RGB clamps, numerical stability, and profile isolation.
 */
class CameraSkinCalibrationIntegrityTest {

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
    fun dfuns_routesToDisposableFilmAndBypassesCCDProcessing() {
        val preset = requireNotNull(PresetCatalog.getPresetById("ccd_flash"))
        assertEquals("D Funs category must be DISPOSABLE", CameraCategory.DISPOSABLE, preset.category)
        assertEquals("cam_dfuns", preset.iconResName)
        assertEquals("D Funs", preset.name)

        val stock = CameraProfileCatalog.getProfileForPreset(preset)
        assertEquals("dfuns_negative_v1", stock.filmProfile.id)
        assertEquals("lut_dfuns_v1.png", stock.filmProfile.lutAssetPath)
        assertEquals("grains/grain_dfuns.jpg", stock.grainProfile.assetPath)

        // Verify skin profile resolution
        val skinProfile = SkinToneProfileCatalog.getProfileForPreset("ccd_flash", CameraCategory.DISPOSABLE)
        assertEquals("skin_dfuns", skinProfile.id)
        assertEquals("D Funs Disposable Film", skinProfile.name)
    }

    @Test
    fun preservesNaturalSkinHueDiversity_noUniversalNarrowClamp() {
        // Diverse natural human skin tones: fair rosy, golden olive, deep rich melanin
        val samples = listOf(
            Triple(230, 175, 155), // Fair cool undertone
            Triple(210, 160, 120), // Neutral golden warm undertone
            Triple(165, 115,  80), // Medium rich olive
            Triple(110,  70,  50)  // Deep melanin complexion
        )

        val width = 10
        val height = 10
        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        val hues = mutableListOf<Float>()
        for ((r, g, b) in samples) {
            val pixels = IntArray(width * height) { createPixel(r, g, b) }
            val srcPixels = pixels.clone()

            engine.process(
                pixels = pixels,
                width = width,
                height = height,
                faces = listOf(face),
                illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
                profile = SkinToneProfileCatalog.D_FUNS,
                sourcePixels = srcPixels
            )

            val p = pixels[5 * width + 5]
            val oklch = SkinColourEstimator.rgbToOklch((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            hues.add(oklch[2])
        }

        // Verify hues are NOT homogenized into a single narrow band
        val hueSpread = hues.maxOrNull()!! - hues.minOrNull()!!
        assertTrue("Natural skin diversity must maintain substantial hue spread (was $hueSpread)", hueSpread > 8.0f)
    }

    @Test
    fun preservesGenuineTungstenWarmth_noForcedDaylightRatio() {
        val width = 10
        val height = 10
        // Natural skin under authentic warm tungsten lighting (R/B = 2.75)
        val srcP = createPixel(220, 140, 80)
        val srcPixels = IntArray(width * height) { srcP }

        // Camera grade adds realistic film tone (R/B = 3.06)
        val gradedP = createPixel(230, 145, 75)
        val pixels = IntArray(width * height) { gradedP }

        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.AMBER_TUNGSTEN,
            profile = SkinToneProfileCatalog.D_FUNS,
            sourcePixels = srcPixels
        )

        val outP = pixels[5 * width + 5]
        val outR = (outP shr 16) and 0xFF
        val outB = outP and 0xFF
        val outRb = outR.toFloat() / outB.toFloat()

        // Must retain authentic tungsten warmth (R/B >= 2.50), NEVER forced down to daylight (1.70)
        assertTrue("Genuine tungsten warmth must be retained (was $outRb, expected >= 2.50)", outRb >= 2.50f)
    }

    @Test
    fun preservesGenuineYellowGreenIllumination_noArtificialOvercorrection() {
        val width = 10
        val height = 10
        // Skin under ambient sodium/fluorescent street lighting (G/R ratio ~ 0.88, warm yellow-green cast)
        val srcP = createPixel(200, 175, 115)
        val srcPixels = IntArray(width * height) { srcP }
        val gradedP = createPixel(205, 180, 110)
        val pixels = IntArray(width * height) { gradedP }

        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL,
            profile = SkinToneProfileCatalog.CPM35,
            sourcePixels = srcPixels
        )

        val outP = pixels[5 * width + 5]
        val outG = (outP shr 8) and 0xFF
        val outR = (outP shr 16) and 0xFF

        // Environmental lighting must remain present (G/R must not be artificially crushed below 0.80)
        val grRatio = outG.toFloat() / outR.toFloat()
        assertTrue("Yellow-green ambient atmosphere must be preserved (G/R was $grRatio)", grRatio >= 0.82f)
    }

    @Test
    fun nearZeroBlueChannel_numericalStabilityInDeepShadows() {
        val width = 10
        val height = 10
        // Deep shadow dark hair with near-zero blue channel (R=20, G=18, B=2)
        val shadowP = createPixel(20, 18, 2)
        val pixels = IntArray(width * height) { shadowP }
        val srcPixels = pixels.clone()
        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D3D,
            sourcePixels = srcPixels
        )

        val outP = pixels[5 * width + 5]
        val outB = outP and 0xFF
        // Luma gate must prevent division explosion: blue must remain deep neutral dark, not noisy blue
        assertTrue("Deep shadow blue channel must remain stable (was $outB, expected <= 6)", outB <= 6)
    }

    @Test
    fun zeroConfidence_leavesPixelExactlyIdenticalToCameraGrade() {
        val width = 10
        val height = 10
        // Neutral grey background pixel completely outside skin locus (R=100, G=100, B=100)
        val greyP = createPixel(100, 100, 100)
        val pixels = IntArray(width * height) { greyP }
        val face = FaceRegion(bounds = createRect(0.1f, 0.1f, 0.9f, 0.9f), confidence = 0.95f)

        engine.process(
            pixels = pixels,
            width = width,
            height = height,
            faces = listOf(face),
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.D_FUNS
        )

        for (p in pixels) {
            assertEquals("Zero confidence pixel must remain 100% byte-identical to camera grade", greyP, p)
        }
    }

    @Test
    fun woodenAndLeatherObjects_rejectedBySpatialAndManifoldPriors() {
        val width = 100
        val height = 100
        // Place a detected face in upper center [35..65, 20..50]
        val face = FaceRegion(
            bounds = createRect(0.35f, 0.20f, 0.65f, 0.50f),
            confidence = 0.95f
        )
        val prior = anatomyPrior.generateSpatialPrior(width, height, listOf(face), backgroundSuppression = 0.80f)

        // Wooden desk/chair in lower corner (x=10, y=90)
        val cornerPrior = prior[90 * width + 10]
        assertTrue("Background furniture/wood zone must receive heavy spatial suppression <= 0.25 (was $cornerPrior)", cornerPrior <= 0.25f)
    }

    @Test
    fun independentProfileBehavior_cpm35RetainsDigitalFlashHighlightKnee() {
        val cpm35 = SkinToneProfileCatalog.CPM35
        val ccd = SkinToneProfileCatalog.CCD
        val dfuns = SkinToneProfileCatalog.D_FUNS

        // CPM35 and CCD use crisp digital direct-flash highlight clipping (knee >= 0.95)
        assertEquals(0.96f, cpm35.skinHighlightKnee, 0.001f)
        assertEquals(0.96f, ccd.skinHighlightKnee, 0.001f)

        // D FUNS uses authentic soft negative film shoulder compression (knee = 0.88)
        assertEquals(0.88f, dfuns.skinHighlightKnee, 0.001f)
    }
}
