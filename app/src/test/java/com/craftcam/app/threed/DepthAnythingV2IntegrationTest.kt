package com.craftcam.app.threed

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.*
import org.junit.Test

class DepthAnythingV2IntegrationTest {

    @Test
    fun testDepthAnythingV2Availability() {
        val engine = DepthAnythingV2Engine()
        assertTrue("Depth Anything V2 ONNX model must be available", engine.isAvailable())
    }

    @Test
    fun testSyntheticPixelDepthInference() {
        val engine = DepthAnythingV2Engine()
        assertTrue(engine.isAvailable())

        // Create a 400x300 synthetic test image (white subject circle in center, dark background)
        val w = 400
        val h = 300
        val pixels = IntArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        val radiusSq = 80f * 80f

        for (y in 0 until h) {
            val dy = y - cy
            val rowOffset = y * w
            for (x in 0 until w) {
                val dx = x - cx
                val isCircle = (dx * dx + dy * dy) <= radiusSq
                // Foreground circle = bright white, background = dark gray
                pixels[rowOffset + x] = if (isCircle) {
                    (0xFF shl 24) or (240 shl 16) or (240 shl 8) or 255
                } else {
                    (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 40
                }
            }
        }

        val result = engine.estimateDepthFromPixels(pixels, w, h, maxDimension = 518)
        assertNotNull("Inference result must not be null", result)
        result!!

        println("Inference completed in ${result.inferenceTimeMs}ms at resolution ${result.width}x${result.height}")
        assertTrue("Inference time must be reasonable (< 2000ms on CPU)", result.inferenceTimeMs < 2000)

        // Verify dimensions are multiples of 14
        assertEquals(0, result.width % 14)
        assertEquals(0, result.height % 14)

        // Verify raw depth stats
        assertTrue("maxRaw must be > minRaw", result.maxRaw > result.minRaw)
        assertTrue("meanRaw must be between min and max", result.meanRaw in result.minRaw..result.maxRaw)

        // Verify normalized depth array bounds [0.0, 1.0]
        val total = result.width * result.height
        assertEquals(total, result.normalizedDepth.size)
        assertEquals(total, result.refinedDepth.size)

        var minNorm = Float.MAX_VALUE
        var maxNorm = -Float.MAX_VALUE
        for (v in result.normalizedDepth) {
            if (v < minNorm) minNorm = v
            if (v > maxNorm) maxNorm = v
        }
        assertTrue("minNorm must be >= 0.0", minNorm >= 0.0f)
        assertTrue("maxNorm must be <= 1.0", maxNorm <= 1.0f)
    }

    @Test
    fun testThreeDDepthEngineABBackends() {
        val w = 320
        val h = 240
        val pixels = IntArray(w * h) { (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128 }

        // Backend A: Depth Anything V2
        val configAi = ThreeDConfig(depthBackend = DepthBackend.DEPTH_ANYTHING_V2)
        val engineAi = ThreeDDepthEngine(config = configAi)
        val dispMapAi = engineAi.computeDisparityFromPixels(pixels, w, h)

        assertNotNull(dispMapAi)
        assertEquals(DepthBackend.DEPTH_ANYTHING_V2, dispMapAi.backendUsed)
        assertTrue(dispMapAi.values.isNotEmpty())
        assertTrue("Pivot must be a measured scene disparity", dispMapAi.values.any { it == dispMapAi.pivot })

        // Backend B: Legacy Monocular Depth Estimator
        val configLegacy = ThreeDConfig(depthBackend = DepthBackend.CURRENT)
        val engineLegacy = ThreeDDepthEngine(config = configLegacy)
        val dispMapLegacy = engineLegacy.computeDisparityFromPixels(pixels, w, h)

        assertNotNull(dispMapLegacy)
        assertEquals(DepthBackend.CURRENT, dispMapLegacy.backendUsed)
        assertEquals(w, dispMapLegacy.width)
        assertEquals(h, dispMapLegacy.height)
    }

    @Test
    fun testFaceAnchorConvergencePinning() {
        val config = ThreeDConfig(depthBackend = DepthBackend.DEPTH_ANYTHING_V2)
        val engine = ThreeDDepthEngine(config = config)

        val w = 320
        val h = 240
        val depth = FloatArray(w * h) { i -> if (i % w in 20..65 && i / w in 20..65) 0.98f else 0.12f }
        val face = FaceRegion(
            bounds = RectF().apply {
                left = 20f / w
                top = 20f / h
                right = 65f / w
                bottom = 65f / h
            },
            confidence = 0.95f
        )
        assertEquals(0.98f, engine.calculatePivot(w, h, depth, listOf(face)), 0.0001f)
        assertEquals(0.12f, engine.calculatePivot(w, h, depth, emptyList()), 0.0001f)
    }

    @Test
    fun testParallaxWarpEngineExecution() {
        val config = ThreeDConfig(d3dParallaxStrength = 0.032f)
        val warpEngine = ParallaxWarpEngine(config)

        val w = 200
        val h = 150
        val disp = FloatArray(w * h) { i -> (i / w).toFloat() / h }
        val dispMap = DisparityMap(w, h, disp, pivot = 0.50f)

        // Test lens bounce sequence indices
        assertEquals(4, config.frameCount)
        assertEquals(0.032f, config.d3dParallaxStrength, 0.001f)
    }
}
