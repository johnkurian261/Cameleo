package com.craftcam.app.processor

import android.graphics.Bitmap
import com.craftcam.app.camera.profile.CameraProfileCatalog
import com.craftcam.app.camera.profile.GrainMode
import com.craftcam.app.color.digicam.DigicamIlluminant
import com.craftcam.app.color.skin.SkinToneIntelligenceEngine
import com.craftcam.app.color.skin.SkinToneProfileCatalog
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test

class FilterDevelopmentIntegrityTest {

    @Test
    fun skinToneEngine_runsHighResolutionWithoutDeadlock() {
        val width = 1200
        val height = 900
        val total = width * height
        val pixels = IntArray(total) { i ->
            // Warm skin tone pixel: R=210, G=150, B=120
            (0xFF shl 24) or (210 shl 16) or (150 shl 8) or 120
        }

        val engine = SkinToneIntelligenceEngine()
        val result = engine.process(
            pixels = pixels,
            width = width,
            height = height,
            illuminant = DigicamIlluminant.GOLDEN_DAYLIGHT,
            profile = SkinToneProfileCatalog.FXNR
        )

        assertNotNull(result)
        assertTrue(result.skinPixelCount > 0)
        assertTrue(result.averageSkinConfidence > 0f)
    }

    @Test
    fun grdProfile_correctlyConfiguredWithLutAndCCDCharacteristics() {
        val preset = PresetCatalog.getPresetById("ccd_grd")
        assertNotNull(preset)
        assertEquals("GR D", preset.name)

        val legacyPreset = PresetCatalog.getPresetById("analog_warm_film")
        assertNotNull(legacyPreset)
        assertEquals("GR D", legacyPreset.name)

        val profile = CameraProfileCatalog.getProfileForPreset(preset)
        assertNotNull(profile)
        assertEquals("GR D", profile.name)
        assertEquals("lut_ccd_grd.png", profile.filmProfile.lutAssetPath)
        assertEquals(GrainMode.PROCEDURAL, profile.grainProfile.mode)
        assertEquals("", profile.grainProfile.assetPath)
        assertTrue(profile.grainProfile.isColorNoise)
    }

    @Test
    fun intArrayPool_accommodatesThreeLargeBuffers() {
        val minSize = 8_500_000 // > LARGE_INT_THRESHOLD (8M)
        val arr1 = IntArrayPool.acquire(minSize)
        val arr2 = IntArrayPool.acquire(minSize)
        val arr3 = IntArrayPool.acquire(minSize)

        assertNotNull(arr1)
        assertNotNull(arr2)
        assertNotNull(arr3)

        IntArrayPool.release(arr1)
        IntArrayPool.release(arr2)
        IntArrayPool.release(arr3)

        // Verify re-acquisition reuses one of the pooled arrays
        val reused = IntArrayPool.acquire(minSize)
        assertTrue(reused === arr1 || reused === arr2 || reused === arr3)
        IntArrayPool.release(reused)
    }

    @Test
    fun all34Presets_haveValidProfilesAndGradingConfigurations() {
        val allPresets = PresetCatalog.allPresets
        assertEquals(34, allPresets.size)

        for (preset in allPresets) {
            assertNotNull("Preset ID should not be empty: ${preset.name}", preset.id)
            assertNotNull("Preset Name should not be empty: ${preset.id}", preset.name)
            assertNotNull("Preset color grading should not be null: ${preset.name}", preset.colorGrading)

            val profile = CameraProfileCatalog.getProfileForPreset(preset)
            assertNotNull("Profile must be resolvable for preset: ${preset.name}", profile)
            assertNotNull("Film profile must not be null for preset: ${preset.name}", profile.filmProfile)
        }
    }
}
