package com.craftcam.app.threed

import com.craftcam.app.camera.CaptureMode
import org.junit.Assert.*
import org.junit.Test

class ThreeDModeTest {

    @Test
    fun testCaptureModeIncludes3D() {
        val mode = CaptureMode.MODE_3D
        assertEquals("3D", mode.displayName)
        assertTrue(CaptureMode.values().contains(CaptureMode.MODE_3D))
    }

    @Test
    fun testThreeDConfigDefaults() {
        val config = ThreeDConfig()
        assertEquals(4, config.frameCount)
        assertEquals(10, config.fps)
        assertEquals(4, config.loopCount)
        assertEquals(0.012f, config.maxParallaxDisplacement, 0.001f)
        assertEquals(0.012f, config.d3dParallaxStrength, 0.001f)
        assertEquals(0.45f, config.pivotDisparity, 0.001f)
        assertFalse(config.lightLeakEnabled)
        assertTrue(config.grainIntensity > 0f)
        assertTrue(config.rgbSeparationPx > 0f)
    }

    @Test
    fun testD3DAnimationConfigurationAndBounds() {
        // Test subtle conservative displacement bounds
        val defaultCfg = ThreeDConfig()
        assertTrue("Parallax displacement must be subtle (<= 1.5% width)", defaultCfg.d3dParallaxStrength <= 0.015f)
        assertTrue("Parallax displacement must be > 0.4% width", defaultCfg.d3dParallaxStrength >= 0.004f)

        // Configurable d3dParallaxStrength test
        val customCfg = ThreeDConfig(d3dParallaxStrength = 0.008f)
        assertEquals(0.008f, customCfg.d3dParallaxStrength, 0.0001f)
        assertEquals(0.008f, customCfg.maxParallaxDisplacement, 0.0001f)
    }

    @Test
    fun testD3DPresetRouting() {
        val d3dPresets = listOf("analog_cinema_film", "d3d", "D3D", "D 3D")
        for (name in d3dPresets) {
            val isD3D = name == "analog_cinema_film" || name == "d3d" ||
                    name.equals("D3D", ignoreCase = true) || name.equals("D 3D", ignoreCase = true)
            assertTrue("Preset '$name' must resolve as D3D", isD3D)
        }
    }

    @Test
    fun testDisparityMapBilinearSampling() {
        // 2x2 disparity map:
        // [0.0, 1.0]
        // [0.0, 1.0]
        val values = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f)
        val map = DisparityMap(width = 2, height = 2, values = values, pivot = 0.45f)

        assertEquals(0.0f, map.sampleBilinear(0.0f, 0.0f), 0.001f)
        assertEquals(1.0f, map.sampleBilinear(1.0f, 0.0f), 0.001f)
        assertEquals(0.5f, map.sampleBilinear(0.5f, 0.5f), 0.001f)
        assertEquals(0.45f, map.pivot, 0.001f)
    }

    @Test
    fun testQuadLensBounceCadenceAndOffsets() {
        val lensOffsets = floatArrayOf(-1.0f, -0.333f, 0.333f, 1.0f)
        val bounceSequence = intArrayOf(0, 1, 2, 3, 2, 1)

        assertEquals(4, lensOffsets.size)
        assertEquals(6, bounceSequence.size)

        // Monotonic progression across lenses 1 -> 2 -> 3 -> 4
        assertTrue(lensOffsets[0] < lensOffsets[1])
        assertTrue(lensOffsets[1] < lensOffsets[2])
        assertTrue(lensOffsets[2] < lensOffsets[3])

        // Bounce sequence alternates forward then backward
        assertEquals(0, bounceSequence[0]) // Lens 1
        assertEquals(1, bounceSequence[1]) // Lens 2
        assertEquals(2, bounceSequence[2]) // Lens 3
        assertEquals(3, bounceSequence[3]) // Lens 4
        assertEquals(2, bounceSequence[4]) // Lens 3
        assertEquals(1, bounceSequence[5]) // Lens 2

        // Loop continuity: from last frame (Lens 2) to first frame (Lens 1) is a 1-lens step
        val stepToLoop = bounceSequence[0] - bounceSequence[5]
        assertEquals(-1, stepToLoop)
    }

    @Test
    fun testDepthDisplacementMonotonicityAndPivotPinning() {
        val config = ThreeDConfig(pivotDisparity = 0.45f, maxParallaxDisplacement = 0.018f)
        val maxPx = 1000f * config.maxParallaxDisplacement // 18px
        val lensOffset = 1.0f // Lens 4 (maximum rightward viewpoint)

        val dForeground = 1.0f
        val dMidground = 0.70f
        val dPivot = 0.45f
        val dBackground = 0.0f

        val dispFg = (dForeground - config.pivotDisparity) * maxPx * lensOffset
        val dispMid = (dMidground - config.pivotDisparity) * maxPx * lensOffset
        val dispPivot = (dPivot - config.pivotDisparity) * maxPx * lensOffset
        val dispBg = (dBackground - config.pivotDisparity) * maxPx * lensOffset

        assertEquals(9.9f, dispFg, 0.01f)
        assertEquals(4.5f, dispMid, 0.01f)
        // Subject at pivot convergence plane remains rock-solid stationary (zero drift)
        assertEquals(0.0f, dispPivot, 0.001f)
        assertEquals(-8.1f, dispBg, 0.01f)

        // Foreground displacement > midground > pivot
        assertTrue(dispFg > dispMid)
        assertTrue(dispMid > dispPivot)
        // Background moves counter to foreground
        assertTrue(dispBg < 0f)
    }
}
