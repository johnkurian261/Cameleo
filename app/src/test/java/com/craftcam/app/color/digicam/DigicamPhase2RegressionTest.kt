package com.craftcam.app.color.digicam

import com.craftcam.app.processor.digicam.DigicamOpticalEngine
import com.craftcam.app.processor.digicam.DigicamSensorEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

class DigicamPhase2RegressionTest {

    private lateinit var colorEngine: DigicamColorEngine
    private lateinit var hybridEstimator: HybridIlluminantEstimator
    private lateinit var opticalEngine: DigicamOpticalEngine
    private lateinit var sensorEngine: DigicamSensorEngine

    @Before
    fun setUp() {
        hybridEstimator = HybridIlluminantEstimator()
        colorEngine = DigicamColorEngine(hybridEstimator)
        opticalEngine = DigicamOpticalEngine()
        sensorEngine = DigicamSensorEngine()
    }

    private fun createPixel(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    @Test
    fun redClothingDoesNotTriggerTungstenInDaylight() {
        val width = 40
        val height = 40
        val total = width * height
        val pixels = IntArray(total)

        // 50% vivid scarlet red dress (Ref 2: R=230, G=35, B=25, sat ~ 0.89)
        // 50% neutral daylight outdoor sidewalk / wall (R=150, G=145, B=140, sat ~ 0.07)
        for (i in 0 until total) {
            pixels[i] = if (i % 2 == 0) {
                createPixel(230, 35, 25)
            } else {
                createPixel(150, 145, 140)
            }
        }

        val weights = hybridEstimator.estimateLightingRegime(pixels, width, height, isFlashActive = false)

        // Must NOT be tricked into AMBER_TUNGSTEN despite massive red pixels
        assertEquals(
            "Red dress in daylight must not cause tungsten classification",
            DigicamIlluminant.GOLDEN_DAYLIGHT,
            weights.primaryIlluminant
        )
        assertTrue("Daylight weight must dominate over tungsten", weights.daylightWeight > weights.tungstenWeight)
    }

    @Test
    fun greenFoliageDoesNotTriggerFluorescentInDaylight() {
        val width = 40
        val height = 40
        val total = width * height
        val pixels = IntArray(total)

        // 60% vivid green grass/trees (R=35, G=165, B=40, sat ~ 0.79)
        // 40% neutral daylight sky/concrete (R=160, G=158, B=155)
        for (i in 0 until total) {
            pixels[i] = if (i % 5 < 3) {
                createPixel(35, 165, 40)
            } else {
                createPixel(160, 158, 155)
            }
        }

        val weights = hybridEstimator.estimateLightingRegime(pixels, width, height, isFlashActive = false)

        assertEquals(
            "Green foliage in daylight must not cause fluorescent/artificial classification",
            DigicamIlluminant.GOLDEN_DAYLIGHT,
            weights.primaryIlluminant
        )
        assertTrue("Daylight weight must dominate over artificial", weights.daylightWeight > weights.artificialWeight)
    }

    @Test
    fun lowConfidenceSmoothlyFallsBackToBalancedDaylight() {
        val width = 20
        val height = 20
        // Uniform ambiguous pixel with zero neutral/highlight variance
        val pixels = IntArray(width * height) { createPixel(100, 95, 90) }

        val weights = hybridEstimator.estimateLightingRegime(pixels, width, height, isFlashActive = false)

        // Confidence damping should ensure safe daylight fallback
        assertEquals(DigicamIlluminant.GOLDEN_DAYLIGHT, weights.primaryIlluminant)
        assertTrue("Daylight weight should be preserved by confidence damping", weights.daylightWeight >= 0.50f)
    }

    @Test
    fun continuousRegimeInterpolationProducesSmoothTransitionsWithoutNaNs() {
        // Interpolate across a sweep of weights
        for (i in 0..10) {
            val t = i / 10.0f
            val weights = LightingRegimeWeights(
                daylightWeight = 1.0f - t,
                tungstenWeight = t,
                flashWeight = 0.0f,
                artificialWeight = 0.0f,
                confidence = 0.90f,
                primaryIlluminant = if (t > 0.5f) DigicamIlluminant.AMBER_TUNGSTEN else DigicamIlluminant.GOLDEN_DAYLIGHT
            )

            val params = colorEngine.resolveBlendedParameters(weights, DigicamModel.CPM35)

            assertFalse("Gains must not be NaN", params.gains[0].isNaN() || params.gains[2].isNaN())
            assertTrue("Red gain must be positive", params.gains[0] > 0.5f)
            assertTrue("Blue gain must be positive", params.gains[2] > 0.1f)
            assertTrue("Contrast must be positive", params.contrast > 0.5f)
        }
    }

    @Test
    fun continuousRedProtectionBoostsScarletWhileLeavingSkinAndWoodNatural() {
        val width = 3
        val height = 1
        // 1. Pure scarlet red dress (Hue ~ 0°, Sat ~ 0.85): R=235, G=45, B=35
        // 2. Warm skin tone (Hue ~ 28°, Sat ~ 0.48): R=205, G=145, B=105
        // 3. Brown teak wood (Hue ~ 32°, Sat ~ 0.38): R=140, G=95, B=65
        val pixels = intArrayOf(
            createPixel(235, 45, 35),
            createPixel(205, 145, 105),
            createPixel(140, 95, 65)
        )

        val params = colorEngine.resolveParameters(DigicamIlluminant.GOLDEN_DAYLIGHT, DigicamModel.CPM35)
        val copy = pixels.clone()

        colorEngine.applyColorTransform(copy, width, height, params)

        val scarletOut = copy[0]
        val scarletR = (scarletOut shr 16) and 0xFF
        val scarletG = (scarletOut shr 8) and 0xFF
        val scarletB = scarletOut and 0xFF

        val skinOut = copy[1]
        val skinR = (skinOut shr 16) and 0xFF
        val skinG = (skinOut shr 8) and 0xFF

        val woodOut = copy[2]
        val woodR = (woodOut shr 16) and 0xFF
        val woodG = (woodOut shr 8) and 0xFF

        // Scarlet red must retain strong red dominance
        assertTrue("Scarlet red must remain vivid and dominant", scarletR > 200 && scarletR > scarletG * 2.0f)

        // Skin and wood must maintain healthy, natural R/G ratio without turning neon red
        val skinRatio = skinR.toFloat() / max(1f, skinG.toFloat())
        assertTrue("Skin R/G ratio must remain natural (not oversaturated, current=$skinRatio)", skinRatio in 1.15f..1.80f)

        val woodRatio = woodR.toFloat() / max(1f, woodG.toFloat())
        assertTrue("Wood R/G ratio must remain natural (not oversaturated, current=$woodRatio)", woodRatio in 1.15f..2.00f)
    }

    @Test
    fun electronicNoiseFollowsSignalDependentPoissonGaussianModel() {
        val width = 20
        val height = 20
        val total = width * height

        // Low luma patch (deep shadow L ~ 20) vs high luma patch (sunlit wall L ~ 200)
        val darkPixels = IntArray(total) { createPixel(20, 20, 20) }
        val brightPixels = IntArray(total) { createPixel(200, 200, 200) }

        sensorEngine.applyElectronicNoise(darkPixels, width, height, iso = 400, noiseMultiplier = 1.0f, seed = 123)
        sensorEngine.applyElectronicNoise(brightPixels, width, height, iso = 400, noiseMultiplier = 1.0f, seed = 123)

        fun varianceOf(pixels: IntArray): Double {
            var sum = 0.0
            var sumSq = 0.0
            for (p in pixels) {
                val r = ((p shr 16) and 0xFF).toDouble()
                sum += r
                sumSq += r * r
            }
            val mean = sum / pixels.size
            return (sumSq / pixels.size) - (mean * mean)
        }

        val darkVar = varianceOf(darkPixels)
        val brightVar = varianceOf(brightPixels)

        assertTrue("Dark noise variance must be non-zero", darkVar > 0.05)
        assertTrue("Bright noise variance must be non-zero", brightVar > 0.05)
        // In Poisson-Gaussian model: Var(Y) = a*Y + b -> bright variance is higher than dark variance
        assertTrue("Signal-dependent variance must be higher for bright regions", brightVar > darkVar)
    }

    @Test
    fun highlightScatterDoesNotBloomModerateDaylightMidtones() {
        val width = 20
        val height = 20
        val total = width * height
        // Daylight midtone (Luma ~ 0.50): R=130, G=130, B=130
        val pixels = IntArray(total) { createPixel(130, 130, 130) }
        val original = pixels.clone()

        opticalEngine.applyHighlightScatter(pixels, width, height, scatterIntensity = 0.35f)

        // Pixels with Luma <= 0.65 should receive zero optical scatter
        for (i in 0 until total) {
            assertEquals("Daylight midtones must not bloom", original[i], pixels[i])
        }
    }

    @Test
    fun cpm35ColorimetricParityAndHighlightSoftKneePreventsHardClipping() {
        // Test fair skin highlight swatch (R=240, G=195, B=145)
        val pixels = intArrayOf(createPixel(240, 195, 145))
        val params = colorEngine.resolveParameters(DigicamIlluminant.GOLDEN_DAYLIGHT, DigicamModel.CPM35)
        
        colorEngine.applyColorTransform(pixels, 1, 1, params)
        
        val outP = pixels[0]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF
        
        // Assert that red does not hard-clip at 255 with loss of detail
        assertTrue("Highlight skin red must remain within realistic dynamic range", outR in 230..254)
        // Assert natural skin R > G > B order
        assertTrue("Natural chromatic order R > G > B must hold", outR > outG && outG > outB)
        // Assert blue channel is not starved
        val rbRatio = outR.toFloat() / max(1, outB).toFloat()
        assertTrue("R/B ratio must remain natural (was $rbRatio, expected <= 3.2)", rbRatio <= 3.2f)
    }
}
