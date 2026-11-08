package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(0.78f, film.halationThreshold, 0.001f)
        assertEquals(0.26f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.35f, film.halationColorTintG, 0.001f)
        assertEquals(0.08f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals(0.20f, grain.intensity, 0.001f)
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
        assertEquals(0.965f, film.shoulderClip, 0.001f)
        assertEquals(0.80f, film.highlightRollOff, 0.001f)
        assertEquals(0.74f, film.halationThreshold, 0.001f)
        assertEquals(0.26f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.28f, film.halationColorTintG, 0.001f)
        assertEquals(0.06f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals(0.22f, grain.intensity, 0.001f)
        assertTrue(grain.luminanceWeightingEnabled)

        val optics = profile.opticalProfile
        assertEquals(0.35f, optics.vignetteStrength, 0.001f)
        assertEquals(0.80f, optics.vignetteRadius, 0.001f)
        assertEquals(0.0025f, optics.chromaticAberration, 0.0001f)
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
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_ccdr.jpg", grain.assetPath)
        assertEquals(0.24f, grain.intensity, 0.001f)
    }

    @Test
    fun dqsProfile_calibratedWithDedicatedCcdCompactProfileAndScannedGrain() {
        val preset = PresetCatalog.getPresetById("ccd_retro_compact")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("DQS", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_ccd_retro_compact.png", film.lutAssetPath)
        assertEquals(0.015f, film.toeLift, 0.001f)
        assertEquals(0.990f, film.shoulderClip, 0.001f)
        assertEquals(0.88f, film.highlightRollOff, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_dqs.jpg", grain.assetPath)
        assertEquals(0.18f, grain.intensity, 0.001f)
    }

    @Test
    fun classicUProfile_calibratedWithDedicatedY2kDigicamAndScannedGrain() {
        val preset = PresetCatalog.getPresetById("ccd_y2k_digital")
        val profile = CameraProfileCatalog.getProfileForPreset(preset)

        assertEquals("Classic U", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_ccd_y2k_digital.png", film.lutAssetPath)
        assertEquals(0.020f, film.toeLift, 0.001f)
        assertEquals(0.990f, film.shoulderClip, 0.001f)
        assertEquals(0.86f, film.highlightRollOff, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_classicu.jpg", grain.assetPath)
        assertEquals(0.22f, grain.intensity, 0.001f)
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
        assertEquals(0.26f, unified.filmProfile.halationIntensity, 0.001f)
        assertEquals(GrainMode.SCANNED, unified.grainProfile.mode)
    }

    @Test
    fun zeroProfile_calibratedForHighFidelityPhotographicNaturalRendering() {
        val preset = PresetCatalog.getPresetById("zero")
        assertNotNull("Preset zero must be present in catalog", preset)
        val profile = CameraProfileCatalog.getProfileForPreset(preset!!)

        assertEquals("Zero", profile.name)
        assertEquals("Natural Photographic Sensor", profile.subtitle)

        // Zero bypasses all film simulation and LUT
        val film = profile.filmProfile
        assertNull("Zero must not use a film LUT", film.lutAssetPath)
        assertEquals(0.0f, film.lutStrength, 0.001f)
        assertEquals(1.0f, film.contrast, 0.001f)
        assertEquals(1.0f, film.saturation, 0.001f)
        assertEquals(0.92f, film.highlightRollOff, 0.001f)
        assertEquals(1.0f, film.halationThreshold, 0.001f)
        assertEquals(0.0f, film.halationIntensity, 0.001f)

        // Zero has zero grain
        val grain = profile.grainProfile
        assertEquals(0.0f, grain.intensity, 0.001f)

        // Zero has standard clean optical profile
        val optics = profile.opticalProfile
        assertEquals(0.0f, optics.distortionStrength, 0.001f)
        assertEquals(0.0f, optics.chromaticAberration, 0.001f)
        assertEquals(0.0f, optics.vignetteStrength, 0.001f)
    }

    @Test
    fun ccdFlashProfile_calibratedWithDirectFlashAndRadialFalloff() {
        val preset = PresetCatalog.getPresetById("ccd_flash")
        assertNotNull("Preset ccd_flash must be present in catalog", preset)
        val profile = CameraProfileCatalog.getProfileForPreset(preset!!)

        assertEquals("CCD Flash", profile.name)
        val film = profile.filmProfile
        assertEquals("lut_ccd_flash.png", film.lutAssetPath)
        assertEquals(1.30f, film.contrast, 0.001f)
        assertEquals(1.18f, film.saturation, 0.001f)
        assertEquals(0.88f, film.highlightRollOff, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_dfuns.jpg", grain.assetPath)
        assertEquals(0.22f, grain.intensity, 0.001f)

        val optics = profile.opticalProfile
        assertEquals(0.45f, optics.vignetteStrength, 0.001f)
        assertEquals(0.72f, optics.vignetteRadius, 0.001f)
    }

    @Test
    fun vscoProfiles_calibratedWithAuthenticFilmCharacteristics() {
        val a6 = PresetCatalog.getPresetById("vsco_a6")
        assertNotNull("Preset vsco_a6 must be present", a6)
        val a6Profile = CameraProfileCatalog.getProfileForPreset(a6!!)
        assertEquals("A6", a6Profile.name)
        assertEquals("lut_vsco_a6.png", a6Profile.filmProfile.lutAssetPath)
        assertEquals(0.038f, a6Profile.filmProfile.toeLift, 0.001f)

        val c1 = PresetCatalog.getPresetById("vsco_c1")
        assertNotNull("Preset vsco_c1 must be present", c1)
        val c1Profile = CameraProfileCatalog.getProfileForPreset(c1!!)
        assertEquals("C1", c1Profile.name)
        assertEquals("lut_vsco_c1.png", c1Profile.filmProfile.lutAssetPath)

        val handycam = PresetCatalog.getPresetById("vsco_handycam")
        assertNotNull("Preset vsco_handycam must be present", handycam)
        val handycamProfile = CameraProfileCatalog.getProfileForPreset(handycam!!)
        assertEquals("Handycam 85", handycamProfile.name)
        assertEquals("lut_vsco_handycam.png", handycamProfile.filmProfile.lutAssetPath)
        assertEquals(GrainMode.SCANNED, handycamProfile.grainProfile.mode)
        assertEquals("grains/grain8mm_01.jpg", handycamProfile.grainProfile.assetPath)
    }
}

