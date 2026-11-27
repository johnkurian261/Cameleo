package com.craftcam.app.color

import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.color.profile.DazzColorProfileCatalog
import com.craftcam.app.gl.shaders.ShaderConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class CreativeTemperatureTest {

    private data class ColorPatch(val name: String, val r: Float, val g: Float, val b: Float)

    private val testSwatches = listOf(
        ColorPatch("pure_black", 0.00f, 0.00f, 0.00f),
        ColorPatch("shadow_gray", 0.08f, 0.08f, 0.08f),
        ColorPatch("mid_gray", 0.50f, 0.50f, 0.50f),
        ColorPatch("bright_highlight", 0.92f, 0.92f, 0.92f),
        ColorPatch("pure_white", 1.00f, 1.00f, 1.00f),
        ColorPatch("skin_light", 0.86f, 0.68f, 0.57f),
        ColorPatch("skin_medium", 0.72f, 0.51f, 0.40f),
        ColorPatch("skin_deep", 0.42f, 0.28f, 0.21f),
        ColorPatch("sky_blue", 0.25f, 0.55f, 0.85f),
        ColorPatch("leaf_green", 0.22f, 0.52f, 0.20f)
    )

    @Test
    fun zeroTemperatureIsBitExactIdentityAcrossSwatchesAndPixels() {
        val out = FloatArray(3)
        for (swatch in testSwatches) {
            for (skinProtect in listOf(true, false)) {
                CreativeTemperatureEngine.applyRgbNormalized(
                    swatch.r, swatch.g, swatch.b,
                    tempValue = 0.0f,
                    skinProtect = skinProtect,
                    outRgb = out
                )
                assertEquals("${swatch.name} R identity", swatch.r, out[0], 0.0f)
                assertEquals("${swatch.name} G identity", swatch.g, out[1], 0.0f)
                assertEquals("${swatch.name} B identity", swatch.b, out[2], 0.0f)
            }
        }

        // Near-zero deadband (< 0.05) should also produce exact unmodified ARGB pixels
        val width = 16
        val height = 8
        val original = IntArray(width * height) { i ->
            val r = (i * 19) and 0xFF
            val g = (i * 37) and 0xFF
            val b = (i * 71) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val copy = original.copyOf()
        CreativeTemperatureEngine.applyArgbPixelsInPlace(
            pixels = copy,
            width = width,
            height = height,
            tempValue = 0.0f,
            skinProtect = true,
            isMonochrome = false
        )
        assertArrayEquals("T=0 ARGB pixel buffer must be unmodified bit-for-bit", original, copy)
    }

    @Test
    fun eightTemperatureMilestonesProgressMonotonicallyWithoutClipOrBlackLift() {
        val out = FloatArray(3)
        val milestones = listOf(-100f, -65f, -25f, 0f, 25f, 50f, 75f, 100f)

        // Pure black stays anchored at (0, 0, 0) across all extremes
        for (t in milestones) {
            CreativeTemperatureEngine.applyRgbNormalized(0f, 0f, 0f, t, skinProtect = true, outRgb = out)
            assertEquals("Black anchor R at T=$t", 0.0f, out[0], 1e-5f)
            assertEquals("Black anchor G at T=$t", 0.0f, out[1], 1e-5f)
            assertEquals("Black anchor B at T=$t", 0.0f, out[2], 1e-5f)
        }

        // Neutral mid-gray shifts monotonically from Cold Blue (-100) to Deep Red (+100)
        val bMinusR = milestones.map { t ->
            CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, t, skinProtect = true, outRgb = out)
            out[2] - out[0]
        }
        for (i in 1 until bMinusR.size) {
            assertTrue(
                "B-R warmth index must decrease monotonically from ${milestones[i - 1]} to ${milestones[i]}: ${bMinusR[i - 1]} -> ${bMinusR[i]}",
                bMinusR[i] < bMinusR[i - 1]
            )
        }

        // Check -100 Frozen Blue characteristics on mid-gray
        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, -100f, skinProtect = true, outRgb = out)
        assertTrue("Frozen blue (-100) must strongly favor B over R (${out[2]} vs ${out[0]})", out[2] > out[0] + 0.55f)
        assertTrue("Frozen blue (-100) must retain cyan/green hierarchy B > G > R", out[2] > out[1] && out[1] > out[0])

        // Check +25 Warm Amber, +50 Golden Orange, +75 Hot Orange-Red, +100 Deep Red on mid-gray
        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 25f, skinProtect = true, outRgb = out)
        assertTrue("Warm amber (+25) must satisfy R > G > B", out[0] > out[1] && out[1] > out[2])

        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 50f, skinProtect = true, outRgb = out)
        val gAt50 = out[1]
        assertTrue("Golden orange (+50) R > G > B", out[0] > out[1] && out[1] > out[2])

        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 75f, skinProtect = true, outRgb = out)
        assertTrue("Hot orange-red (+75) deepens red by lowering green relative to +50", out[1] < gAt50 && out[0] > out[1])

        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 100f, skinProtect = true, outRgb = out)
        assertTrue("Deep red (+100) strong red separation R - G > 0.50 (${out[0]} - ${out[1]})", out[0] - out[1] > 0.50f)
        assertTrue("Deep red (+100) avoids highlight hard clipping to 1.0 (${out[0]})", out[0] < 0.99f)

        // Bright highlight retains headroom at extremes
        CreativeTemperatureEngine.applyRgbNormalized(0.92f, 0.92f, 0.92f, 100f, skinProtect = true, outRgb = out)
        assertTrue("Bright highlight compressed smoothly below 0.985 at +100 (got ${out[0]})", out[0] < 0.985f)
    }

    @Test
    fun continuousCurveAcrossAll201IntegerStepsHasNoSpikesOrInvalidOutputs() {
        val out = FloatArray(3)
        var prevR = -1f
        var prevG = -1f
        var prevB = -1f

        for (step in -100..100) {
            CreativeTemperatureEngine.applyRgbNormalized(
                0.5f, 0.5f, 0.5f,
                tempValue = step.toFloat(),
                skinProtect = true,
                outRgb = out
            )
            for (ch in 0..2) {
                assertFalse("NaN at step=$step", out[ch].isNaN())
                assertTrue("Channel $ch out of [0,1] at step=$step: ${out[ch]}", out[ch] in 0.0f..1.0f)
            }
            if (step > -100) {
                val maxDelta = max(abs(out[0] - prevR), max(abs(out[1] - prevG), abs(out[2] - prevB)))
                assertTrue(
                    "Discontinuity at step=$step (delta=$maxDelta)",
                    maxDelta < 0.03f
                )
            }
            prevR = out[0]
            prevG = out[1]
            prevB = out[2]
        }
    }

    @Test
    fun skinProtectionModulatesPortraitsWhileLeavingNeutralsUnchanged() {
        val outProtected = FloatArray(3)
        val outUnprotected = FloatArray(3)

        // Neutral gray should show virtually zero difference between Skin Guard ON vs OFF
        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 100f, skinProtect = true, outRgb = outProtected)
        CreativeTemperatureEngine.applyRgbNormalized(0.5f, 0.5f, 0.5f, 100f, skinProtect = false, outRgb = outUnprotected)
        assertEquals(outUnprotected[0], outProtected[0], 1e-4f)
        assertEquals(outUnprotected[1], outProtected[1], 1e-4f)
        assertEquals(outUnprotected[2], outProtected[2], 1e-4f)

        // Skin swatches at +100 and -100 preserve noticeably more natural green/blue than unprotected
        for (skin in listOf(
            ColorPatch("skin_light", 0.86f, 0.68f, 0.57f),
            ColorPatch("skin_medium", 0.72f, 0.51f, 0.40f),
            ColorPatch("skin_deep", 0.42f, 0.28f, 0.21f)
        )) {
            CreativeTemperatureEngine.applyRgbNormalized(skin.r, skin.g, skin.b, 100f, skinProtect = true, outRgb = outProtected)
            CreativeTemperatureEngine.applyRgbNormalized(skin.r, skin.g, skin.b, 100f, skinProtect = false, outRgb = outUnprotected)
            assertTrue(
                "${skin.name} at +100 with skin guard must retain higher green (${outProtected[1]} > ${outUnprotected[1]})",
                outProtected[1] > outUnprotected[1] + 0.05f
            )

            CreativeTemperatureEngine.applyRgbNormalized(skin.r, skin.g, skin.b, -100f, skinProtect = true, outRgb = outProtected)
            CreativeTemperatureEngine.applyRgbNormalized(skin.r, skin.g, skin.b, -100f, skinProtect = false, outRgb = outUnprotected)
            assertTrue(
                "${skin.name} at -100 with skin guard must preserve more red warmth (${outProtected[0]} > ${outUnprotected[0]})",
                outProtected[0] > outUnprotected[0] + 0.08f
            )
        }
    }

    @Test
    fun monochromePresetAnd22MColorProfileLockCreativeTemperatureToNeutralZero() {
        val monoPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = "analog_dblack",
            colorProfileIndex = DazzColorProfileCatalog.getById("C400").index
        )
        assertTrue("D BLACK must resolve monochrome", monoPlan.isMonochrome)
        assertEquals(0.0f, CreativeTemperatureEngine.resolveEffectiveTemperature(75.0f, monoPlan.isMonochrome), 0.0f)

        val profile22m = DazzColorProfileCatalog.getById("22M")
        assertEquals("22M", profile22m.id)
        val colorPresetWith22m = CameraProfileCompatibility.resolveLutPlan(
            presetId = "disp_classic",
            colorProfileIndex = profile22m.index
        )
        assertTrue("22M profile on Golf must resolve monochrome", colorPresetWith22m.isMonochrome)
        assertEquals(0.0f, CreativeTemperatureEngine.resolveEffectiveTemperature(-85.0f, colorPresetWith22m.isMonochrome), 0.0f)

        val golfDefaultPlan = CameraProfileCompatibility.resolveLutPlan(
            presetId = "disp_classic",
            colorProfileIndex = DazzColorProfileCatalog.getById("C400").index
        )
        assertFalse("Golf default profile must allow color Creative Temperature", golfDefaultPlan.isMonochrome)
        assertEquals(50.0f, CreativeTemperatureEngine.resolveEffectiveTemperature(50.0f, golfDefaultPlan.isMonochrome), 0.0f)

        // Pixel batch processing with isMonochrome=true must leave pixels bit-identical
        val pixels = intArrayOf(0xFF404040.toInt(), 0xFF808080.toInt(), 0xFFC0C0C0.toInt(), 0xFFE0E0E0.toInt())
        val expected = pixels.copyOf()
        CreativeTemperatureEngine.applyArgbPixelsInPlace(
            pixels = pixels,
            width = 2,
            height = 2,
            tempValue = 100.0f,
            skinProtect = false,
            isMonochrome = true
        )
        assertArrayEquals(expected, pixels)
    }

    @Test
    fun creativeTemperatureRemainsStrictlyIndependentOfHardwareWhiteBalance() {
        val defaultTuning = UserTuningConfig()
        val tuned = defaultTuning.copy(creativeTemperature = -75.0f, creativeTempSkinProtection = false)
        assertEquals(0.0f, tuned.temperatureOffset, 0.0f)
        assertEquals(0.0f, tuned.tintOffset, 0.0f)
        assertFalse(tuned.isHardwareWbActive)

        val gainsA = HardwareWhiteBalanceCalibrator.computeCalibratedGains(5500, 0.0f)
        val gainsB = HardwareWhiteBalanceCalibrator.computeCalibratedGains(5500, defaultTuning.tintOffset)
        assertEquals(gainsA.red, gainsB.red, 0.0f)
        assertEquals(gainsA.greenEven, gainsB.greenEven, 0.0f)
        assertEquals(gainsA.blue, gainsB.blue, 0.0f)
    }

    @Test
    fun shadersContainCreativeTemperatureStageBeforeFilmGrain() {
        for ((name, src) in listOf(
            "FRAGMENT_SHADER_VIEWFINDER" to ShaderConstants.FRAGMENT_SHADER_VIEWFINDER,
            "FRAGMENT_SHADER_STILL" to ShaderConstants.FRAGMENT_SHADER_STILL
        )) {
            assertTrue("$name missing uCreativeTemperature", src.contains("uniform float uCreativeTemperature;"))
            assertTrue("$name missing uCreativeTempSkinProtect", src.contains("uniform bool uCreativeTempSkinProtect;"))
            assertTrue("$name missing applyCreativeTemperature", src.contains("vec3 applyCreativeTemperature(vec3 cIn, float tempValue, bool skinProtect)"))
            val fadeIdx = src.indexOf("// 9. Film Fade")
            val creativeIdx = src.indexOf("// 9b. Creative Temperature Filter")
            val halationIdx = src.indexOf("// 10. Concentric Dual-Radius Film Halation")
            val grainIdx = src.indexOf("// 13. Authentic Scanned Photographic Film Halide Grain")
            assertTrue("$name missing Stage 9", fadeIdx >= 0)
            assertTrue("$name missing Stage 9b", creativeIdx > fadeIdx)
            assertTrue("$name missing Stage 10 Halation ($halationIdx)", halationIdx > creativeIdx)
            assertTrue("$name missing Stage 13 Grain ($grainIdx)", grainIdx > halationIdx)
        }
    }

    @Test
    fun parallelArgbPixelProcessingMatchesScalarNormalizedOutput() {
        val width = 128
        val height = 64
        val pixels = IntArray(width * height) { i ->
            val r = (i * 13) and 0xFF
            val g = ((i * 29) + 40) and 0xFF
            val b = ((i * 47) + 80) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val expected = pixels.copyOf()
        val rgbOut = FloatArray(3)
        for (i in expected.indices) {
            val c = expected[i]
            val a = c ushr 24
            val r = ((c ushr 16) and 0xFF) / 255.0f
            val g = ((c ushr 8) and 0xFF) / 255.0f
            val b = (c and 0xFF) / 255.0f
            CreativeTemperatureEngine.applyRgbNormalized(r, g, b, 65.0f, skinProtect = true, outRgb = rgbOut)
            val ro = (rgbOut[0] * 255.0f).roundToInt().coerceIn(0, 255)
            val go = (rgbOut[1] * 255.0f).roundToInt().coerceIn(0, 255)
            val bo = (rgbOut[2] * 255.0f).roundToInt().coerceIn(0, 255)
            expected[i] = (a shl 24) or (ro shl 16) or (go shl 8) or bo
        }

        CreativeTemperatureEngine.applyArgbPixelsInPlace(
            pixels = pixels,
            width = width,
            height = height,
            tempValue = 65.0f,
            skinProtect = true,
            isMonochrome = false
        )
        assertArrayEquals("Parallel ARGB stripe output must match scalar evaluation", expected, pixels)

        assertEquals("-100", CreativeTemperatureEngine.formatValue(-100f))
        assertEquals("0", CreativeTemperatureEngine.formatValue(0.2f))
        assertEquals("+50", CreativeTemperatureEngine.formatValue(50f))
        assertEquals("Frozen Blue", CreativeTemperatureEngine.describeTemperature(-100f))
        assertEquals("Original", CreativeTemperatureEngine.describeTemperature(0f))
        assertEquals("Golden Orange", CreativeTemperatureEngine.describeTemperature(50f))
        assertEquals("Deep Red", CreativeTemperatureEngine.describeTemperature(100f))
    }
}
