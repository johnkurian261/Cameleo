package com.craftcam.app.thermal

import android.graphics.RectF
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.BorderStyle
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.portrait.FaceRegion
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class ThermalVisionTest {

    private fun packArgb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun unpackLuma(argb: Int): Float {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun unpackRedMinusBlue(argb: Int): Float {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return r - b
    }

    private fun createNormalizedRect(l: Float, t: Float, r: Float, b: Float): RectF =
        RectF().apply {
            left = l
            top = t
            right = r
            bottom = b
        }

    private fun buildSyntheticPortraitArgb(w: Int, h: Int): IntArray {
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val ny = y.toFloat() / h
            for (x in 0 until w) {
                val nx = x.toFloat() / w
                val inHead = nx in 0.40f..0.60f && ny in 0.20f..0.44f
                val inTorso = nx in 0.30f..0.70f && ny in 0.45f..0.92f
                pixels[y * w + x] = when {
                    inHead -> packArgb(215, 165, 138) // natural peach/warm skin tone
                    inTorso -> packArgb(118, 128, 136) // jacket/shirt
                    else -> packArgb(24, 38, 66) // cool twilight background
                }
            }
        }
        return pixels
    }

    @Test
    fun allFivePalettes_smoothContinuityAndDistinctColorScience() {
        val tex = ThermalPaletteEngine.createGlTextureBuffer()
        assertEquals(ThermalPaletteEngine.LUT_WIDTH * ThermalPaletteEngine.LUT_HEIGHT * 4, tex.capacity())

        val rgb = FloatArray(3)
        val prev = FloatArray(3)
        for (palette in ThermalPalette.entries) {
            var maxStepDelta = 0f
            for (i in 0 until ThermalPaletteEngine.LUT_WIDTH) {
                val t = i.toFloat() / (ThermalPaletteEngine.LUT_WIDTH - 1)
                ThermalPaletteEngine.sampleRgbNormalized(palette, t, inverted = false, outRgb = rgb)
                assertTrue("NaN/range R for $palette at $t", rgb[0] in 0f..1f)
                assertTrue("NaN/range G for $palette at $t", rgb[1] in 0f..1f)
                assertTrue("NaN/range B for $palette at $t", rgb[2] in 0f..1f)
                if (i > 0) {
                    val dr = rgb[0] - prev[0]
                    val dg = rgb[1] - prev[1]
                    val db = rgb[2] - prev[2]
                    val delta = sqrt(dr * dr + dg * dg + db * db)
                    if (delta > maxStepDelta) maxStepDelta = delta
                }
                prev[0] = rgb[0]
                prev[1] = rgb[1]
                prev[2] = rgb[2]
            }
            assertTrue(
                "Palette $palette max step $maxStepDelta exceeds smooth anti-banding threshold",
                maxStepDelta < 0.045f
            )
        }

        // Verify signature spectral endpoints per palette
        val rainbowCool = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.RAINBOW_THERMAL, 0.12f, false, it) }
        val rainbowMid = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.RAINBOW_THERMAL, 0.52f, false, it) }
        val rainbowHot = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.RAINBOW_THERMAL, 0.96f, false, it) }
        assertTrue("Rainbow cool must be blue/violet dominated", rainbowCool[2] > rainbowCool[0])
        assertTrue("Rainbow mid must hold green transition", rainbowMid[1] > rainbowMid[0] && rainbowMid[1] > rainbowMid[2])
        assertTrue("Rainbow hot must terminate warm red", rainbowHot[0] > rainbowHot[2])

        val ironDark = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.IRONBOW, 0.02f, false, it) }
        val ironWarm = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.IRONBOW, 0.85f, false, it) }
        assertTrue("Ironbow base must start dark", (ironDark[0] + ironDark[1] + ironDark[2]) < 0.25f)
        assertTrue("Ironbow hot end must glow yellow/orange", ironWarm[0] > 0.85f && ironWarm[1] > 0.55f && ironWarm[2] < 0.35f)

        val redCold = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.RED_HOT, 0.05f, false, it) }
        val redWarm = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.RED_HOT, 0.96f, false, it) }
        assertTrue("Red Hot cold must favor cool blue", redCold[2] > redCold[0])
        assertTrue("Red Hot warm end must favor intense red", redWarm[0] > redWarm[2] && redWarm[0] > 0.85f)

        val whCold = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.WHITE_HOT, 0.05f, false, it) }
        val whHot = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.WHITE_HOT, 0.95f, false, it) }
        val bhCold = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.BLACK_HOT, 0.05f, false, it) }
        val bhHot = FloatArray(3).also { ThermalPaletteEngine.sampleRgbNormalized(ThermalPalette.BLACK_HOT, 0.95f, false, it) }
        assertTrue("White Hot must climb dark -> bright", whHot[1] > whCold[1] + 0.6f)
        assertTrue("Black Hot must descend bright -> dark", bhCold[1] > bhHot[1] + 0.6f)
    }

    @Test
    fun hotColdInversion_reversesEveryPaletteSymmetrically() {
        val fwd = FloatArray(3)
        val rev = FloatArray(3)
        for (palette in ThermalPalette.entries) {
            for (step in 0..20) {
                val t = step / 20f
                ThermalPaletteEngine.sampleRgbNormalized(palette, t, inverted = false, outRgb = fwd)
                ThermalPaletteEngine.sampleRgbNormalized(palette, 1f - t, inverted = true, outRgb = rev)
                assertEquals("$palette R inversion symmetry failed at t=$t", fwd[0], rev[0], 0.015f)
                assertEquals("$palette G inversion symmetry failed at t=$t", fwd[1], rev[1], 0.015f)
                assertEquals("$palette B inversion symmetry failed at t=$t", fwd[2], rev[2], 0.015f)
            }
        }
    }

    @Test
    fun subjectEstimator_prioritizesHumanWarmthAndConvergesTemporally() {
        val w = ThermalSubjectEstimator.DEFAULT_MASK_WIDTH
        val h = ThermalSubjectEstimator.DEFAULT_MASK_HEIGHT
        val argb = buildSyntheticPortraitArgb(w, h)
        val face = FaceRegion(bounds = createNormalizedRect(0.40f, 0.20f, 0.60f, 0.44f), confidence = 0.94f)
        val estimator = ThermalSubjectEstimator(w, h)

        val snapshots = (0 until 6).map {
            estimator.analyzeArgbPixels(
                argbPixels = argb,
                width = w,
                height = h,
                faces = listOf(face),
                segmentationEnabled = true,
                temporalSmoothing = true
            )
        }

        val last = snapshots.last()
        assertEquals(1, last.detectedSubjectCount)
        assertEquals(w * h * 4, last.rgbaBuffer.remaining())
        assertTrue(last.adaptiveMinLuma in 0f..0.4f)
        assertTrue(last.adaptiveMaxLuma in 0.5f..1f && last.adaptiveMaxLuma > last.adaptiveMinLuma)

        // Head & torso pixels must have substantially higher human mask than distant blue corners
        var centerMaskSum = 0f
        var centerCount = 0
        var bgMaskSum = 0f
        var bgCount = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (x in 54..72 && y in 24..60) {
                    centerMaskSum += last.humanMask[idx]
                    centerCount++
                } else if ((x < 14 || x > w - 14) && y < 30) {
                    bgMaskSum += last.humanMask[idx]
                    bgCount++
                }
            }
        }
        val centerMean = centerMaskSum / centerCount
        val bgMean = bgMaskSum / bgCount
        assertTrue("Subject human mask ($centerMean) must clearly exceed cool background ($bgMean)", centerMean > bgMean + 0.35f)

        // Temporal EMA convergence: delta(frame 0->1) > delta(frame 4->5)
        fun meanMaskDelta(a: ThermalMaskSnapshot, b: ThermalMaskSnapshot): Float {
            var sum = 0f
            for (i in a.humanMask.indices) sum += abs(a.humanMask[i] - b.humanMask[i])
            return sum / a.humanMask.size
        }
        val earlyDelta = meanMaskDelta(snapshots[0], snapshots[1])
        val settledDelta = meanMaskDelta(snapshots[4], snapshots[5])
        assertTrue("EMA mask must settle smoothly (early=$earlyDelta, settled=$settledDelta)", earlyDelta > settledDelta * 3f)

        estimator.resetTemporalState()
        val postReset = estimator.analyzeArgbPixels(
            argbPixels = argb,
            width = w,
            height = h,
            faces = listOf(face),
            segmentationEnabled = true,
            temporalSmoothing = true
        )
        assertEquals(snapshots[0].humanMask[w * (h / 2) + w / 2], postReset.humanMask[w * (h / 2) + w / 2], 1e-4f)
    }

    @Test
    fun processor_handlesAiMaskAndGpuOnlyFallbackDeterministically() {
        val w = 96
        val h = 72
        val base = buildSyntheticPortraitArgb(w, h)
        val face = FaceRegion(bounds = createNormalizedRect(0.40f, 0.20f, 0.60f, 0.44f), confidence = 0.9f)

        // AI segmentation enabled across all 5 palettes
        for (palette in ThermalPalette.entries) {
            val aiPixels = base.copyOf()
            ThermalVisionProcessor.developArgbPixelsInPlace(
                pixels = aiPixels,
                width = w,
                height = h,
                config = ThermalVisionConfig(palette = palette, segmentationEnabled = true),
                faces = listOf(face)
            )
            // Repeat identically to verify deterministic parallel striping
            val aiRepeat = base.copyOf()
            ThermalVisionProcessor.developArgbPixelsInPlace(
                pixels = aiRepeat,
                width = w,
                height = h,
                config = ThermalVisionConfig(palette = palette, segmentationEnabled = true),
                faces = listOf(face)
            )
            assertTrue("Parallel thermal pass must be deterministic for $palette", aiPixels.contentEquals(aiRepeat))

            // Compare center skin vs background corner heat semantics
            val headPixel = aiPixels[(h * 0.30f).toInt() * w + (w * 0.50f).toInt()]
            val bgPixel = aiPixels[4 * w + 4]
            assertNotEquals("Subject and background must separate for $palette", headPixel, bgPixel)
        }

        // GPU-only luminance/chroma fallback (segmentationEnabled = false, faces empty)
        val fallbackPixels = base.copyOf()
        ThermalVisionProcessor.developArgbPixelsInPlace(
            pixels = fallbackPixels,
            width = w,
            height = h,
            config = ThermalVisionConfig(palette = ThermalPalette.RED_HOT, segmentationEnabled = false),
            faces = emptyList()
        )
        val fallbackHead = fallbackPixels[(h * 0.30f).toInt() * w + (w * 0.50f).toInt()]
        val fallbackBg = fallbackPixels[4 * w + 4]
        assertTrue(
            "Fallback RED_HOT subject should render warmer red-blue delta than cool corner",
            unpackRedMinusBlue(fallbackHead) > unpackRedMinusBlue(fallbackBg) + 0.4f
        )

        // Sensitivity & inversion check on WHITE_HOT
        val lowSens = base.copyOf()
        val highSens = base.copyOf()
        val invSens = base.copyOf()
        ThermalVisionProcessor.developArgbPixelsInPlace(
            lowSens, w, h,
            ThermalVisionConfig(palette = ThermalPalette.WHITE_HOT, sensitivity = 0.65f, segmentationEnabled = false)
        )
        ThermalVisionProcessor.developArgbPixelsInPlace(
            highSens, w, h,
            ThermalVisionConfig(palette = ThermalPalette.WHITE_HOT, sensitivity = 1.65f, segmentationEnabled = false)
        )
        ThermalVisionProcessor.developArgbPixelsInPlace(
            invSens, w, h,
            ThermalVisionConfig(palette = ThermalPalette.WHITE_HOT, inverted = true, segmentationEnabled = false)
        )
        val torsoIdx = (h * 0.65f).toInt() * w + (w * 0.50f).toInt()
        assertTrue(
            "Higher thermal sensitivity must raise heat response in White Hot",
            unpackLuma(highSens[torsoIdx]) > unpackLuma(lowSens[torsoIdx]) + 0.1f
        )
        assertTrue(
            "Hot/Cold inversion on White Hot must flip warm head darker than inverted cool corner",
            unpackLuma(invSens[4 * w + 4]) > unpackLuma(invSens[(h * 0.30f).toInt() * w + (w * 0.50f).toInt()]) + 0.25f
        )
    }

    @Test
    fun catalogCompatibilityAndHonesty_preservesAllExistingPresets() {
        val preset = PresetCatalog.allPresets.firstOrNull { it.id == "thermal_vision" }
        assertNotNull("THERMAL preset must exist in PresetCatalog", preset)
        assertEquals(preset!!, PresetCatalog.getPresetById("thermal_vision"))
        assertEquals("THERMAL", preset.name)
        assertEquals("Simulated IR Vision", preset.subtitle)
        assertEquals(CameraCategory.EXPERIMENTAL, preset.category)
        assertEquals("cam_thermal", preset.iconResName)
        assertFalse(preset.grain.enabled)
        assertEquals(0f, preset.grain.intensity, 1e-6f)
        assertFalse(preset.halation?.enabled ?: false)
        assertFalse(preset.lightLeak?.enabled ?: false)
        assertFalse(preset.dust?.enabled ?: false)
        assertFalse(preset.flashOverlay?.enabled ?: false)
        assertFalse(preset.dateStamp?.enabled ?: false)
        assertEquals(BorderStyle.NONE, preset.border?.style)
        assertEquals(0f, preset.lens.vignetteIntensity, 1e-6f)

        val profile = CameraProfileCatalog.getProfileForPreset(preset)
        assertEquals("thermal_vision", profile.id)
        val policy = CameraProfileCompatibility.getPolicy("thermal_vision")
        assertFalse(policy.isSupported)
        assertTrue(policy.supportsVideo)
        assertTrue(policy.supportsStill)
        assertFalse(CameraProfileCompatibility.isProfileSupported("thermal_vision"))
        assertEquals("", ShaderConstants.getLutAssetForPreset("thermal_vision"))
        assertEquals("", ShaderConstants.getGrainAssetForPreset("thermal_vision", false))

        // Honest simulation strings — zero fabricated temperature degrees
        for (p in ThermalPalette.entries) {
            assertFalse(p.displayName.contains("°C") || p.displayName.contains("°F"))
        }

        // Strict preservation of existing presets & LUTs
        assertEquals("lut_disp_classic.png", CameraProfileCatalog.DISPOSABLE_FUNS.filmProfile.lutAssetPath)
        assertEquals("lut_dfuns_v1.png", CameraProfileCatalog.D_FUNS.filmProfile.lutAssetPath)
        assertEquals("lut_olympus_mju2.png", CameraProfileCatalog.MJU_II.filmProfile.lutAssetPath)
        assertEquals("lut_fxn_r.png", CameraProfileCatalog.FXN_R_LEGACY.filmProfile.lutAssetPath)
        assertEquals("lut_ccd_r.png", CameraProfileCatalog.CCD_DIGITAL.filmProfile.lutAssetPath)
    }
}
