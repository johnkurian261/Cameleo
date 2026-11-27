package com.craftcam.app.camera.profile

import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.model.CameraCategory
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
        assertEquals("lut_dazzclassic.png", film.lutAssetPath)
        assertEquals(0.025f, film.toeLift, 0.001f)
        assertEquals(0.992f, film.shoulderClip, 0.001f)
        assertEquals(0.92f, film.highlightRollOff, 0.001f)
        assertEquals(0.82f, film.halationThreshold, 0.001f)
        assertEquals(0.18f, film.halationIntensity, 0.001f)
        assertEquals(1.0f, film.halationColorTintR, 0.001f)
        assertEquals(0.88f, film.halationColorTintG, 0.001f)
        assertEquals(0.70f, film.halationColorTintB, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals(0.18f, grain.intensity, 0.001f)
        assertTrue(grain.luminanceWeightingEnabled)

        val optics = profile.opticalProfile
        assertEquals(0.18f, optics.vignetteStrength, 0.001f)
        assertEquals(0.86f, optics.vignetteRadius, 0.001f)
        assertEquals(0.001f, optics.chromaticAberration, 0.0001f)
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
        assertEquals("lut_dqs_preserved.png", film.lutAssetPath)
        assertEquals(1.0f, film.highlightRollOff, 0.001f)

        val grain = profile.grainProfile
        assertEquals(GrainMode.SCANNED, grain.mode)
        assertEquals("grains/grain_dfuns.jpg", grain.assetPath)
        assertEquals(0.16f, grain.intensity, 0.001f)
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
    fun dFunsResolvesLegacyKeyToNegativeAndRetainsGrainControls() {
        val preset = requireNotNull(PresetCatalog.getPresetById("ccd_flash"))
        val profile = CameraProfileCatalog.getProfileForPreset(preset)
        assertEquals("D Funs", profile.name)
        assertEquals("lut_dfuns_v1.png", profile.filmProfile.lutAssetPath)
        assertEquals(LutInputSpace.SRGB, profile.filmProfile.lutInputSpace)
        assertEquals(GrainMode.SCANNED, profile.grainProfile.mode)
        assertEquals("grains/grain_dfuns.jpg", profile.grainProfile.assetPath)
        assertTrue(preset.dust?.enabled == false)
        assertTrue(preset.flashOverlay?.enabled != true)
        assertTrue(profile.filmProfile.halationIntensity == 0f)
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

    @Test
    fun cpm35Profile_calibratedToPreventMustardYellowAndDoubleGradingInPreview() {
        val cpm = PresetCatalog.getPresetById("disp_flash")
        assertNotNull("Preset disp_flash must be present", cpm)
        val profile = CameraProfileCatalog.getProfileForPreset(cpm!!)
        assertEquals("CPM 35", profile.name)

        val film = profile.filmProfile
        assertEquals("lut_disp_flash.png", film.lutAssetPath)
        // Must avoid double-grading warmth on top of calibrated LUT
        assertEquals(0.0f, film.temperature, 0.001f)
        assertEquals(0.0f, film.tint, 0.001f)
        assertEquals(0.0f, film.shadowTintR, 0.001f)
        assertEquals(0.0f, film.shadowTintG, 0.001f)
        assertEquals(0.0f, film.shadowTintB, 0.001f)
        // Normalized contrast and saturation
        assertEquals(1.04f, film.contrast, 0.001f)
        assertEquals(1.02f, film.saturation, 0.001f)
        // Skin tone protection active for live viewfinder
        assertEquals(0.85f, film.skinProtection, 0.001f)
    }

    @Test
    fun grdProfile_calibratedForAuthenticRicohGRDigitalAndPreviewParity() {
        val grd = PresetCatalog.getPresetById("ccd_grd")
        assertNotNull("Preset ccd_grd must be present", grd)
        val legacyGrd = PresetCatalog.getPresetById("analog_warm_film")
        assertNotNull("Legacy alias analog_warm_film must resolve", legacyGrd)

        assertEquals("GR D", grd!!.name)
        assertEquals("Ricoh GR Digital Snap", grd.subtitle)
        assertEquals(CameraCategory.CCD_DIGITAL, grd.category)
        assertEquals("2005", grd.year)

        // Verify clean optical profile and lens settings
        assertEquals(0.16f, grd.lens.vignetteIntensity, 0.001f)
        assertEquals(0.88f, grd.lens.vignetteRadius, 0.001f)
        assertFalse("Dust must be disabled on digital CCD", grd.dust?.enabled == true)
        assertFalse("Light leaks must be disabled on digital CCD", grd.lightLeak?.enabled == true)

        val profile = CameraProfileCatalog.getProfileForPreset(grd)
        assertEquals("GR D", profile.name)
        assertEquals("ccd_grd", profile.id)
        assertEquals("ccd_grd", profile.presetId)

        // 28mm wide prime with 47mm digital crop
        assertEquals(2, profile.focalLengths.size)
        assertEquals("28mm", profile.focalLengths[0].label)
        assertEquals(28f, profile.focalLengths[0].focalLengthMm, 0.1f)
        assertEquals("47mm (Crop)", profile.focalLengths[1].label)
        assertEquals(47f, profile.focalLengths[1].focalLengthMm, 0.1f)

        // Calibrated film and color response
        val film = profile.filmProfile
        assertEquals("lut_ccd_grd.png", film.lutAssetPath)
        assertEquals(1.04f, film.contrast, 0.001f)
        assertEquals(1.02f, film.saturation, 0.001f)
        assertEquals(0.0f, film.temperature, 0.001f)
        assertEquals(0.0f, film.tint, 0.001f)
        assertEquals(0.0f, film.halationIntensity, 0.001f)

        // Procedural CCD electronic sensor noise
        val grain = profile.grainProfile
        assertEquals(GrainMode.PROCEDURAL, grain.mode)
        assertEquals("", grain.assetPath)
        assertTrue(grain.isColorNoise)
        assertEquals(0.09f, grain.intensity, 0.001f)

        // Backward compatibility: alias resolves to identical profile
        val legacyProfile = CameraProfileCatalog.getProfileForPreset(legacyGrd!!)
        assertEquals(profile.id, legacyProfile.id)
        assertEquals(profile.filmProfile.lutAssetPath, legacyProfile.filmProfile.lutAssetPath)
    }
}

