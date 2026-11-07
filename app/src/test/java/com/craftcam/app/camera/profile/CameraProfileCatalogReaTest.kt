package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraProfileCatalogReaTest {

    @Test
    fun fxnrProfile_calibratedWithWarmAmberPhotochemicalCharacteristics() {
        val preset = PresetCatalog.getPresetById("analog_fxn_r")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("FXNR", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_fxn_r.png", film.lutAssetPath)
        assertEquals(0.040f, film.toeLift, 0.001f)
        assertEquals(0.965f, film.shoulderClip, 0.001f)
        assertEquals(0.78f, film.highlightRollOff, 0.001f)
        assertEquals(0.75f, film.halationThreshold, 0.001f)
        assertEquals(0.28f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.35f, film.halationColorTintG, 0.001f)
        assertEquals(0.08f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertTrue(grain.luminanceWeightingEnabled)
        assertEquals(1.0f, grain.luminanceWeightingPower, 0.001f)
    }

    @Test
    fun dClassicProfile_calibratedWithUniversal35mmRubyHalation() {
        val preset = PresetCatalog.getPresetById("analog_35mm_classic")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("D Classic", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_analog_35mm_classic.png", film.lutAssetPath)
        assertEquals(0.035f, film.toeLift, 0.001f)
        assertEquals(0.972f, film.shoulderClip, 0.001f)
        assertEquals(0.84f, film.highlightRollOff, 0.001f)
        assertEquals(0.78f, film.halationThreshold, 0.001f)
        assertEquals(0.22f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.28f, film.halationColorTintG, 0.001f)
        assertEquals(0.06f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals(0.22f, grain.intensity, 0.001f)
        assertTrue(grain.luminanceWeightingEnabled)
    }

    @Test
    fun ccdDigitalProfile_calibratedWithDigitalHardShoulderAndProceduralNoise() {
        val preset = PresetCatalog.getPresetById("ccd_r")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        val film = profile.filmProfile
        assertEquals("lut_ccd_r.png", film.lutAssetPath)
        assertEquals(0.015f, film.toeLift, 0.001f)
        assertEquals(0.992f, film.shoulderClip, 0.001f)
        assertEquals(0.94f, film.highlightRollOff, 0.001f)
        assertEquals(0.92f, film.halationThreshold, 0.001f)
        assertEquals(0.08f, film.halationIntensity, 0.001f)
        assertEquals(0.85f, film.halationColorTintR, 0.001f)
        assertEquals(0.85f, film.halationColorTintG, 0.001f)
        assertEquals(1.0f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.PROCEDURAL, grain.mode)
        assertFalse("CCD sensor noise must not use midtone parabola gate", grain.luminanceWeightingEnabled)
        assertEquals(0.0f, grain.luminanceWeightingPower, 0.001f)
    }

    @Test
    fun instantCameraProfile_calibratedWithLiftedMilkyToeAndPeachBloom() {
        val preset = PresetCatalog.getPresetById("inst_vintage")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("Instant V", profile.name)
        val film = profile.filmProfile
        assertEquals(0.085f, film.toeLift, 0.001f)
        assertEquals(0.920f, film.shoulderClip, 0.001f)
        assertEquals(0.70f, film.halationThreshold, 0.001f)
        assertEquals(0.30f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.40f, film.halationColorTintG, 0.001f)
        assertEquals(0.15f, film.halationColorTintB, 0.001f)

        assertEquals(AspectRatioMode.RATIO_1_1, profile.defaultRatio)
    }

    @Test
    fun disposableFunsProfile_calibratedWithPushedFlashCharacteristics() {
        val preset = PresetCatalog.getPresetById("disp_classic")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("D FunS", profile.name)
        val film = profile.filmProfile
        assertEquals(0.050f, film.toeLift, 0.001f)
        assertEquals(0.955f, film.shoulderClip, 0.001f)
        assertEquals(0.74f, film.highlightRollOff, 0.001f)
        assertEquals(0.72f, film.halationThreshold, 0.001f)
        assertEquals(0.32f, film.halationIntensity, 0.001f)
    }

    @Test
    fun oneThreeFiveNeProfile_calibratedWith135NegativeAsset() {
        val preset = PresetCatalog.getPresetById("analog_135ne")
        assertNotNull(preset)
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("135 NE", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_135ne.png", film.lutAssetPath)
        assertEquals(0.038f, film.toeLift, 0.001f)
        assertEquals(0.968f, film.shoulderClip, 0.001f)
        assertEquals(0.24f, film.halationIntensity, 0.001f)
        assertEquals(AspectRatioMode.RATIO_3_2, profile.defaultRatio)
    }

    @Test
    fun fisheyeCameraProfile_calibratedWithCurvilinearOptics() {
        val preset = PresetCatalog.getPresetById("analog_fisheye_f")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("Fisheye", profile.name)
        val optics = profile.opticalProfile
        assertEquals(0.92f, optics.distortionStrength, 0.001f)
        assertEquals(0.45f, optics.edgeCurvature, 0.001f)
        assertEquals(0.022f, optics.chromaticAberration, 0.001f)
        assertEquals(0.65f, optics.vignetteStrength, 0.001f)
    }

    @Test
    fun getUnifiedProfile_convertsCleanlyWithoutDataLoss() {
        val preset = PresetCatalog.getPresetById("analog_fxn_r")
        val unified = CameraProfileCatalog.getUnifiedProfile(preset)

        assertEquals("FXNR", unified.name)
        assertEquals(0.040f, unified.filmProfile.toeLift, 0.001f)
        assertEquals(0.965f, unified.filmProfile.shoulderClip, 0.001f)
        assertEquals(0.28f, unified.filmProfile.halationIntensity, 0.001f)
        assertEquals(GrainMode.SCANNED, unified.grainProfile.mode)
    }

    @Test
    fun zeroProfile_calibratedForHighFidelityPhotographicNaturalRendering() {
        val preset = PresetCatalog.getPresetById("zero")
        assertNotNull("Preset zero must be present in catalog", preset)
        val profile = CameraProfileCatalog.getProfileForPreset(preset!!)

        assertEquals("Zero", profile.name)
        assertEquals("Natural Photographic Sensor", profile.subtitle)

        val film = profile.filmProfile
        assertEquals("lut_zero.png", film.lutAssetPath)
        assertEquals(LutInputSpace.CUSTOM_PRETRANSFORM, film.lutInputSpace)
        assertEquals(1.04f, film.contrast, 0.001f)
        assertEquals(1.02f, film.saturation, 0.001f)
        assertEquals(0.028f, film.toeLift, 0.001f)
        assertEquals(0.965f, film.shoulderClip, 0.001f)
        assertEquals(0.94f, film.highlightRollOff, 0.001f)
        assertEquals(-0.06f, film.clarityStrength, 0.001f)
        assertEquals(0.88f, film.skinProtection, 0.001f)
        assertEquals(1.0f, film.halationThreshold, 0.001f)
        assertEquals(0.0f, film.halationIntensity, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals(0.06f, grain.intensity, 0.001f)
        assertTrue(grain.luminanceWeightingEnabled)
        assertEquals(1.2f, grain.luminanceWeightingPower, 0.001f)
    }
}

