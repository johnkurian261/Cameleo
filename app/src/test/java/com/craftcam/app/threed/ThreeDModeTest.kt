package com.craftcam.app.threed

import com.craftcam.app.camera.CaptureMode
import org.junit.Assert.*
import org.junit.Test

class ThreeDModeTest {
    @Test
    fun lockedFocusOverridesCenterAndRejectsInvalidDepth() {
        val engine = ThreeDDepthEngine()
        val values = FloatArray(10000) { if (it % 100 < 30) 0.85f else 0.2f }
        assertEquals(.85f, engine.calculatePivot(100,100,values,emptyList(),ConvergencePoint(.14f,.6f)),.0001f)
        values.fill(Float.NaN)
        assertEquals(.5f, engine.calculatePivot(100,100,values,emptyList(),ConvergencePoint(.14f,.6f)),.0001f)
    }

    @Test
    fun motionBudgetRespectsFaceSizeAndDepthConfidence() {
        val warp = ParallaxWarpEngine(ThreeDConfig())
        val map = DisparityMap(1,1,floatArrayOf(.5f),foregroundDisparity=.95f,backgroundDisparity=.05f)
        val regular = warp.baselinePixels(800,map,null)
        assertTrue(regular * 2f * .9f <= 24f)
        val face = com.craftcam.app.portrait.FaceRegion(android.graphics.RectF().apply {
            left = .1f; top = .1f; right = .9f; bottom = .9f
        },1f)
        assertTrue(warp.baselinePixels(800,map,face) < regular)
        assertTrue(warp.baselinePixels(800,map.copy(backendUsed=DepthBackend.CURRENT),null) < regular)
        assertEquals(regular*2,warp.baselinePixels(1600,map,null),.001f)
    }

    @Test
    fun upsamplingKeepsAStrongSilhouetteAndSmoothSameColorGradients() {
        val engine = ParallaxWarpEngine(ThreeDConfig())
        val map = DisparityMap(2, 1, floatArrayOf(0f, 1f))
        val colors = IntArray(9) { if (it < 4) 0xff151515.toInt() else 0xffd5d5d5.toInt() }
        val aligned = engine.resampleDisparity(colors, 9, 1, map)
        assertTrue(aligned[3] < 0.03f)
        assertTrue(aligned[4] > 0.97f)
        colors.fill(colors[0])
        val smooth = engine.resampleDisparity(colors, 9, 1, map)
        for (i in smooth.indices) assertEquals(i / 8f, smooth[i], 0.0001f)
    }

    @Test
    fun newlyExposedBackgroundRetainsTextureBesidePinnedSubject() {
        val width = 100
        val colors = IntArray(width) { if (it in 42..58) 0xffff0000.toInt() else 0xff000000.toInt() or it }
        val depth = FloatArray(width) { if (it in 42..58) 1f else 0f }
        val engine = ParallaxWarpEngine(ThreeDConfig())
        for (direction in listOf(-1, 1)) {
            val view = engine.warpPixels(colors, depth, width, 1, direction * 13.5f, 1f)
            val gap = if (direction > 0) 32..41 else 59..68
            assertEquals(10, gap.map { view[it] }.distinct().size)
            assertTrue(gap.all { view[it] in colors && view[it] != colors[50] })
        }
    }

    @Test
    fun fractionalParallaxKeepsThinForegroundFreeOfBackgroundColour() {
        val near = 0xffff0000.toInt()
        val far = 0xff0000ff.toInt()
        val colors = IntArray(25) { if (it == 12) near else far }
        val depths = FloatArray(25) { if (it == 12) 1f else 0f }
        val engine = ParallaxWarpEngine()
        for (shift in listOf(-2.5f, 2.5f)) {
            val view = engine.warpPixels(colors, depths, 25, 1, shift, 0f)
            assertTrue(view.contains(near))
            assertTrue(view.all { it == near || it == far })
        }
    }

    @Test
    fun nearbySubjectStaysPinnedAgainstMovingBackground() {
        val width = 100
        val near = 0xffff0000.toInt()
        val far = 0xff001122.toInt()
        val marker = 0xff00ff00.toInt()
        val colors = IntArray(width) { if (it in 42..58) near else far }
        colors[20] = marker
        val depth = FloatArray(width) { if (it in 42..58) 1f else 0f }
        val pivot = ThreeDDepthEngine().calculatePivot(width, 1, depth, emptyList())
        assertEquals(1f, pivot, 0f)
        val engine = ParallaxWarpEngine(ThreeDConfig())
        for (direction in listOf(-1, 1)) {
            val view = engine.warpPixels(colors, depth, width, 1, direction * 13.5f, pivot)
            assertEquals(20 - direction * 10, view.indexOf(marker))
            assertEquals((42..58).toList(), view.indices.filter { view[it] == near })
        }
    }


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
        assertEquals(0.032f, config.maxParallaxDisplacement, 0.001f)
        assertEquals(0.032f, config.d3dParallaxStrength, 0.001f)
        assertEquals(0.50f, config.pivotDisparity, 0.001f)
        assertFalse(config.lightLeakEnabled)
        assertTrue(config.grainIntensity > 0f)
        assertTrue(config.rgbSeparationPx > 0f)
        assertFalse(config.exportDebugMaps)
    }

    @Test
    fun testD3DAnimationConfigurationAndBounds() {
        // Test calibrated stereo displacement bounds
        val defaultCfg = ThreeDConfig()
        assertTrue("Parallax displacement must be calibrated (between 2% and 5% width)", defaultCfg.d3dParallaxStrength in 0.02f..0.05f)

        // Configurable d3dParallaxStrength test
        val customCfg = ThreeDConfig(d3dParallaxStrength = 0.028f)
        assertEquals(0.028f, customCfg.d3dParallaxStrength, 0.0001f)
        assertEquals(0.028f, customCfg.maxParallaxDisplacement, 0.0001f)
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
        val bounceSequence = ParallaxWarpEngine.bounce(listOf(0, 1, 2, 3))

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

    @Test
    fun d3dCameraProfileStrictlyExcludesFisheyeLenses() {
        val d3dProfile = com.craftcam.app.camera.profile.CameraProfileCatalog.D_3D
        assertFalse(
            "D3D must not support Circular Fisheye F",
            d3dProfile.supportedLenses.contains(com.craftcam.app.camera.profile.LensType.FISHEYE_F)
        )
        assertFalse(
            "D3D must not support Full-Frame Fisheye W",
            d3dProfile.supportedLenses.contains(com.craftcam.app.camera.profile.LensType.FISHEYE_W)
        )
        assertTrue(
            "D3D must support Normal standard lens",
            d3dProfile.supportedLenses.contains(com.craftcam.app.camera.profile.LensType.NORMAL)
        )

        // Preset mapping check
        val preset = com.craftcam.app.presets.PresetCatalog.getPresetById("analog_cinema_film")
        assertNotNull(preset)
        val resolvedProfile = com.craftcam.app.camera.profile.CameraProfileCatalog.getProfileForPreset(preset!!)
        assertFalse(resolvedProfile.supportedLenses.contains(com.craftcam.app.camera.profile.LensType.FISHEYE_F))
        assertFalse(resolvedProfile.supportedLenses.contains(com.craftcam.app.camera.profile.LensType.FISHEYE_W))
    }
}
