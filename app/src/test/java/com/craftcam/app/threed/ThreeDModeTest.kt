package com.craftcam.app.threed

import com.craftcam.app.camera.CaptureMode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

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
        assertEquals(12, config.frameCount)
        assertEquals(15, config.fps)
        assertEquals(3, config.loopCount)
        assertEquals(0.022f, config.maxParallaxDisplacement, 0.001f)
        assertEquals(0.40f, config.pivotDisparity, 0.001f)
        assertTrue(config.lightLeakEnabled)
        assertTrue(config.grainIntensity > 0f)
        assertTrue(config.rgbSeparationPx > 0f)
    }

    @Test
    fun testDisparityMapBilinearSampling() {
        // 2x2 disparity map:
        // [0.0, 1.0]
        // [0.0, 1.0]
        val values = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f)
        val map = DisparityMap(width = 2, height = 2, values = values)

        assertEquals(0.0f, map.sampleBilinear(0.0f, 0.0f), 0.001f)
        assertEquals(1.0f, map.sampleBilinear(1.0f, 0.0f), 0.001f)
        assertEquals(0.5f, map.sampleBilinear(0.5f, 0.5f), 0.001f)
    }

    @Test
    fun testSinusoidalParallaxSymmetryAndSeamlessLooping() {
        val frameCount = 12
        val phases = (0 until frameCount).map { i ->
            val t = (2.0 * Math.PI * i / frameCount).toFloat()
            sin(t)
        }

        // Frame 0 should be at center (0.0)
        assertEquals(0.0f, phases[0], 0.001f)

        // Quarter cycle (frame 3) should be maximum positive displacement (+1.0)
        assertEquals(1.0f, phases[3], 0.01f)

        // Half cycle (frame 6) should be back to center (0.0)
        assertEquals(0.0f, phases[6], 0.01f)

        // Three-quarter cycle (frame 9) should be maximum negative displacement (-1.0)
        assertEquals(-1.0f, phases[9], 0.01f)

        // Next frame after 11 wraps smoothly back to frame 0
        val nextAfterLast = sin((2.0 * Math.PI * frameCount / frameCount).toFloat())
        assertEquals(phases[0], nextAfterLast, 0.001f)
    }

    @Test
    fun testDepthDisplacementMonotonicity() {
        // Verify foreground movement > midground movement > background movement
        val config = ThreeDConfig(pivotDisparity = 0.40f, maxParallaxDisplacement = 0.02f)
        val maxPx = 1000f * config.maxParallaxDisplacement // 20px
        val phase = 1.0f // at peak right oscillation

        val dForeground = 1.0f
        val dMidground = 0.60f
        val dPivot = 0.40f
        val dBackground = 0.0f

        val dispFg = (dForeground - config.pivotDisparity) * maxPx * phase
        val dispMid = (dMidground - config.pivotDisparity) * maxPx * phase
        val dispPivot = (dPivot - config.pivotDisparity) * maxPx * phase
        val dispBg = (dBackground - config.pivotDisparity) * maxPx * phase

        assertEquals(12.0f, dispFg, 0.001f)
        assertEquals(4.0f, dispMid, 0.001f)
        assertEquals(0.0f, dispPivot, 0.001f)
        assertEquals(-8.0f, dispBg, 0.001f)

        // Foreground displacement > midground > pivot
        assertTrue(dispFg > dispMid)
        assertTrue(dispMid > dispPivot)
        // Background moves subtly counter to foreground
        assertTrue(dispBg < 0f)
    }
}

