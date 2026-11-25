package com.craftcam.app.color.digicam

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DigicamColorEngineTest {

    private lateinit var colorEngine: DigicamColorEngine

    @Before
    fun setUp() {
        colorEngine = DigicamColorEngine()
    }

    private fun createSyntheticPixels(r: Int, g: Int, b: Int, count: Int = 1024): IntArray {
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        return IntArray(count) { color }
    }

    @Test
    fun estimateIlluminant_detectsDaylight() {
        // Ref 4 (Sunlight portrait): R=135, G=113, B=94 -> Blue ratio ~ 0.70, Green ratio ~ 0.84, Luma ~ 117
        val pixels = createSyntheticPixels(135, 113, 94)
        val illuminant = colorEngine.estimateIlluminant(pixels, 32, 32, isFlashActive = false)
        assertEquals(DigicamIlluminant.GOLDEN_DAYLIGHT, illuminant)
    }

    @Test
    fun estimateIlluminant_detectsAmberTungsten() {
        // Ref 1 (Restaurant): R=161, G=95, B=25 -> Blue ratio ~ 0.16 <= 0.35
        val pixels = createSyntheticPixels(161, 95, 25)
        val illuminant = colorEngine.estimateIlluminant(pixels, 32, 32, isFlashActive = false)
        assertEquals(DigicamIlluminant.AMBER_TUNGSTEN, illuminant)
    }

    @Test
    fun estimateIlluminant_detectsDirectFlashWhenActive() {
        // Any scene with flash active should map to GOLDEN_DIRECT_FLASH
        val pixels = createSyntheticPixels(140, 120, 100)
        val illuminant = colorEngine.estimateIlluminant(pixels, 32, 32, isFlashActive = true)
        assertEquals(DigicamIlluminant.GOLDEN_DIRECT_FLASH, illuminant)
    }

    @Test
    fun estimateIlluminant_detectsYellowGreenArtificial() {
        // Ref 7 (Train): R=120, G=108, B=82 -> avgLuma ~ 108.7 (<115), greenRatio ~ 0.90 (>=0.82), (G-B)/R ~ 0.217 (>=0.18), blueRatio ~ 0.68 (<=0.72)
        val pixels = createSyntheticPixels(120, 108, 82)
        val illuminant = colorEngine.estimateIlluminant(pixels, 32, 32, isFlashActive = false)
        assertEquals(DigicamIlluminant.YELLOW_GREEN_ARTIFICIAL, illuminant)
    }

    @Test
    fun resolveParameters_distinguishesCPM35FromCCD() {
        val cpmParams = colorEngine.resolveParameters(DigicamIlluminant.AMBER_TUNGSTEN, DigicamModel.CPM35)
        val ccdParams = colorEngine.resolveParameters(DigicamIlluminant.AMBER_TUNGSTEN, DigicamModel.CCD)

        // CPM35 has warmer red gain, higher red saturation boost, and higher optical scatter
        assertTrue("CPM35 Red gain should be higher than CCD", cpmParams.gains[0] > ccdParams.gains[0])
        assertTrue("CPM35 Red saturation boost should be higher than CCD", cpmParams.redSaturationBoost > ccdParams.redSaturationBoost)
        assertTrue("CPM35 Optical scatter should be higher than CCD", cpmParams.opticalScatterIntensity > ccdParams.opticalScatterIntensity)

        // CCD has punchier contrast and deeper blacks (lower toe lift)
        assertTrue("CCD contrast should be higher than CPM35", ccdParams.contrast > cpmParams.contrast)
        assertTrue("CCD toe lift should be lower (deeper blacks) than CPM35", ccdParams.toeLift < cpmParams.toeLift)
    }

    @Test
    fun process_protectsSaturatedRedChannel() {
        // Pure scarlet red pixel (Ref 2 dress: R=220, G=40, B=30)
        val width = 16
        val height = 16
        val pixels = createSyntheticPixels(220, 40, 30, width * height)

        val params = colorEngine.process(pixels, width, height, model = DigicamModel.CPM35, isFlashActive = true)
        assertEquals(DigicamIlluminant.GOLDEN_DIRECT_FLASH, params.illuminant)

        // Check the processed pixel
        val outP = pixels[0]
        val outR = (outP shr 16) and 0xFF
        val outG = (outP shr 8) and 0xFF
        val outB = outP and 0xFF

        // Red should remain heavily dominant over green and blue without skewing to yellow/orange
        assertTrue("Processed Red should be strongly dominant", outR > 200)
        assertTrue("Processed Green should remain low", outG < 100)
        assertTrue("Processed Blue should remain low", outB < 100)
        assertTrue("Red-Green separation must be preserved", (outR - outG) > 100)
    }

    @Test
    fun process_preservesPartialWhiteBalanceWarmth() {
        // Neutral gray patch in amber tungsten lighting (R=160, G=100, B=35)
        val width = 16
        val height = 16
        val pixels = createSyntheticPixels(160, 100, 35, width * height)

        val params = colorEngine.process(pixels, width, height, model = DigicamModel.CPM35, isFlashActive = false)
        assertEquals(DigicamIlluminant.AMBER_TUNGSTEN, params.illuminant)

        val outP = pixels[0]
        val outR = (outP shr 16) and 0xFF
        val outB = outP and 0xFF

        // In amber tungsten, blue must remain attenuated relative to red (warmth retained, not fully neutralized)
        assertTrue("Blue must remain significantly lower than red to preserve amber warmth", outB < outR * 0.70f)
    }
}
