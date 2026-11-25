package com.craftcam.app.processor.digicam

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DigicamOpticalAndSensorTest {

    @Test
    fun opticalHighlightScatter_bloomsBrightHighlightsWithoutAffectingDistantDarkPixels() {
        val opticalEngine = DigicamOpticalEngine()
        val width = 40
        val height = 40
        val total = width * height
        val pixels = IntArray(total) { (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 30 }

        // Place a bright light source in center (10x10 square of pure white)
        for (y in 15..24) {
            for (x in 15..24) {
                pixels[y * width + x] = (0xFF shl 24) or (255 shl 16) or (255 shl 8) or 255
            }
        }

        val cornerPixelBefore = pixels[0]
        val adjacentY = 13 // 2 pixels above bright square
        val adjacentX = 20
        val adjacentBefore = pixels[adjacentY * width + adjacentX]

        opticalEngine.applyHighlightScatter(pixels, width, height, scatterIntensity = 0.50f, threshold = 0.78f)

        // The adjacent pixel should receive warm optical scatter glow
        val adjacentAfter = pixels[adjacentY * width + adjacentX]
        val adjR = (adjacentAfter shr 16) and 0xFF
        val adjOrigR = (adjacentBefore shr 16) and 0xFF
        assertTrue("Pixel near highlight must receive optical glow", adjR > adjOrigR)

        // The corner pixel is far away from the light source and should be virtually unchanged
        val cornerAfter = pixels[0]
        val cornerDiff = abs(((cornerAfter shr 16) and 0xFF) - ((cornerPixelBefore shr 16) and 0xFF))
        assertTrue("Distant corner pixel should have negligible scatter", cornerDiff <= 3)
    }

    @Test
    fun softDigitalDetail_preservesEdgeContrastWithoutExcessiveBlur() {
        val opticalEngine = DigicamOpticalEngine()
        val width = 30
        val height = 30
        val total = width * height
        // Half black, half white sharp step edge
        val pixels = IntArray(total) { idx ->
            val x = idx % width
            val v = if (x < 15) 20 else 220
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        opticalEngine.applySoftDigitalDetail(pixels, width, height, softness = 0.12f, sharpening = 0.10f)

        // Far left and far right should remain dark and bright
        val leftVal = (pixels[15 * width + 5] shr 16) and 0xFF
        val rightVal = (pixels[15 * width + 25] shr 16) and 0xFF
        assertTrue("Far left should remain dark", leftVal <= 30)
        assertTrue("Far right should remain bright", rightVal >= 200)

        // Transition at boundary x=14 and x=15 should be slightly softened
        val edgeLeft = (pixels[15 * width + 14] shr 16) and 0xFF
        val edgeRight = (pixels[15 * width + 15] shr 16) and 0xFF
        assertTrue("Step edge should retain significant contrast (>100)", (edgeRight - edgeLeft) > 100)
    }

    @Test
    fun electronicNoise_isDeterministicWithFixedSeed() {
        val sensorEngine = DigicamSensorEngine()
        val width = 20
        val height = 20
        val total = width * height

        val bufferA = IntArray(total) { (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100 }
        val bufferB = IntArray(total) { (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100 }

        sensorEngine.applyElectronicNoise(bufferA, width, height, iso = 400, noiseMultiplier = 1.0f, seed = 12345)
        sensorEngine.applyElectronicNoise(bufferB, width, height, iso = 400, noiseMultiplier = 1.0f, seed = 12345)

        assertArrayEquals("Noise with identical seed must produce identical pixels", bufferA, bufferB)
    }

    @Test
    fun electronicNoise_increasesWithIso() {
        val sensorEngine = DigicamSensorEngine()
        val width = 30
        val height = 30
        val total = width * height

        val lowIsoPixels = IntArray(total) { (0xFF shl 24) or (80 shl 16) or (80 shl 8) or 80 }
        val highIsoPixels = IntArray(total) { (0xFF shl 24) or (80 shl 16) or (80 shl 8) or 80 }

        sensorEngine.applyElectronicNoise(lowIsoPixels, width, height, iso = 100, noiseMultiplier = 1.0f, seed = 42)
        sensorEngine.applyElectronicNoise(highIsoPixels, width, height, iso = 1600, noiseMultiplier = 1.0f, seed = 42)

        var lowDiffSum = 0L
        var highDiffSum = 0L
        for (i in 0 until total) {
            val lowLuma = (lowIsoPixels[i] shr 16) and 0xFF
            val highLuma = (highIsoPixels[i] shr 16) and 0xFF
            lowDiffSum += abs(lowLuma - 80)
            highDiffSum += abs(highLuma - 80)
        }

        assertTrue("High ISO noise variance must exceed Low ISO noise variance", highDiffSum > lowDiffSum * 1.5)
    }
}
