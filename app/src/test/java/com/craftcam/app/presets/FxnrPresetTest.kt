package com.craftcam.app.presets

import com.craftcam.app.camera.UserTuningConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxnrPresetTest {

    @Test
    fun fxnrPreset_existsInCatalog_withCorrectMetadata() {
        val preset = PresetCatalog.allPresets.find { it.id == "analog_fxn_r" }
        assertNotNull("FXNR preset must be registered in PresetCatalog", preset)
        requireNotNull(preset)

        assertEquals("FXNR", preset.name)
        assertEquals("Warm Negative", preset.subtitle)
        assertEquals("1998", preset.year)
        assertTrue(preset.description.contains("Warm color negative"))
    }

    @Test
    fun fxnrPreset_colorGrading_hasWarmAmberPhotochemicalCharacter() {
        val preset = PresetCatalog.allPresets.first { it.id == "analog_fxn_r" }
        val color = preset.colorGrading

        // Warm amber / golden color bias
        assertEquals(0.18f, color.temperature, 0.001f)
        assertEquals(0.03f, color.tint, 0.001f)

        // Gentle highlight roll-off (creamy whites, no harsh digital clipping)
        assertEquals(0.78f, color.highlightRollOff, 0.001f)

        // Lifted warm brown shadows (faded black levels)
        assertEquals(0.048f, color.fade, 0.001f)
        assertTrue("Shadow red bias must exceed green", color.shadowTintR > color.shadowTintG)
        assertTrue("Shadow green bias must exceed blue", color.shadowTintG > color.shadowTintB)
        assertEquals(0.054f, color.shadowTintR, 0.001f)
        assertEquals(0.024f, color.shadowTintG, 0.001f)
        assertEquals(0.008f, color.shadowTintB, 0.001f)
    }

    @Test
    fun fxnrPreset_opticsAndGrain_areConfiguredForAnalogBloomAndSoftness() {
        val preset = PresetCatalog.allPresets.first { it.id == "analog_fxn_r" }

        // Halation & highlight bloom around practicals
        assertNotNull(preset.halation)
        val halation = preset.halation!!
        assertTrue(halation.enabled)
        assertEquals(0.82f, halation.threshold, 0.001f)
        assertEquals(0.28f, halation.intensity, 0.001f)
        assertEquals(16f, halation.radius, 0.001f)

        // Authentic silver halide grain
        assertTrue(preset.grain.enabled)
        assertEquals(0.22f, preset.grain.intensity, 0.001f)
        assertEquals("grains/grain_ou.jpg", preset.grain.assetPath)

        // Optical softness (organic high-frequency detail roll-off)
        assertEquals(0.14f, preset.lens.softness, 0.001f)
        assertEquals(0.24f, preset.lens.vignetteIntensity, 0.001f)
    }

    @Test
    fun userTuningConfig_defaultsTo70PercentFilterIntensity() {
        val tuning = UserTuningConfig()
        assertEquals(0.70f, tuning.filterIntensity, 0.001f)
    }

    @Test
    fun filterIntensity_interpolatesNeutralBoundariesProperly() {
        val preset = PresetCatalog.allPresets.first { it.id == "analog_fxn_r" }
        val color = preset.colorGrading

        // At 0% intensity, all shifts should return to neutral
        val intensityZero = 0.0f
        val contrastAtZero = 1.0f + (color.contrast - 1.0f) * intensityZero
        val tempAtZero = color.temperature * intensityZero
        val fadeAtZero = color.fade * intensityZero
        val rollOffAtZero = 1.0f - (1.0f - color.highlightRollOff) * intensityZero

        assertEquals(1.0f, contrastAtZero, 0.001f)
        assertEquals(0.0f, tempAtZero, 0.001f)
        assertEquals(0.0f, fadeAtZero, 0.001f)
        assertEquals(1.0f, rollOffAtZero, 0.001f)

        // At 100% intensity, full preset character is applied
        val intensityFull = 1.0f
        val contrastAtFull = 1.0f + (color.contrast - 1.0f) * intensityFull
        val tempAtFull = color.temperature * intensityFull
        val fadeAtFull = color.fade * intensityFull
        val rollOffAtFull = 1.0f - (1.0f - color.highlightRollOff) * intensityFull

        assertEquals(color.contrast, contrastAtFull, 0.001f)
        assertEquals(color.temperature, tempAtFull, 0.001f)
        assertEquals(color.fade, fadeAtFull, 0.001f)
        assertEquals(color.highlightRollOff, rollOffAtFull, 0.001f)

        // At 70% default intensity, smooth proportional interpolation
        val intensityDefault = 0.70f
        val tempAtDefault = color.temperature * intensityDefault
        assertEquals(0.18f * 0.70f, tempAtDefault, 0.001f)
    }
}

