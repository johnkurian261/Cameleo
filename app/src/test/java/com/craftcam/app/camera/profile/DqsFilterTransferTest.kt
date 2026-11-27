package com.craftcam.app.camera.profile

import com.craftcam.app.color.profile.CameraProfileCompatibility
import com.craftcam.app.color.profile.DazzColorProfileCatalog
import com.craftcam.app.color.skin.SkinToneProfileCatalog
import com.craftcam.app.gl.shaders.ShaderConstants
import com.craftcam.app.model.CameraCategory
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit verification tests confirming the complete transfer of D FUNS photographic rendering
 * to DQS while strictly preserving DQS camera identity and keeping D FUNS unaltered.
 */
class DqsFilterTransferTest {

    @Test
    fun testDqsPreservesCameraIdentity() {
        val dqs = PresetCatalog.getPresetById("ccd_retro_compact")
        assertNotNull("DQS preset must exist", dqs)
        assertEquals("DQS", dqs!!.name)
        assertEquals("ccd_retro_compact", dqs.id)
        assertEquals("cam_dqs", dqs.iconResName)
        assertEquals("Digital QuickSnap Hybrid", dqs.subtitle)
        assertEquals(CameraCategory.CCD_DIGITAL, dqs.category)
        assertEquals("2003", dqs.year)
        assertTrue(dqs.description.contains("Hybrid digital point-and-shoot"))
        assertNotNull(dqs.dateStamp)
        assertEquals(-21, dqs.dateStamp?.customYearOffset)

        val profile = CameraProfileCatalog.getProfileForPreset(dqs)
        assertEquals("DQS", profile.name)
        assertEquals("ccd_retro_compact", profile.presetId)
    }

    @Test
    fun testDqsAdoptsCompleteDFunsPhotographicConfiguration() {
        val dqsPreset = PresetCatalog.getPresetById("ccd_retro_compact")!!
        val dfunsPreset = PresetCatalog.getPresetById("ccd_flash")!!

        val dqsProfile = CameraProfileCatalog.getProfileForPreset(dqsPreset)
        val dfunsProfile = CameraProfileCatalog.getProfileForPreset(dfunsPreset)

        // 1. Film Profile (3D LUT, input space, tone curves, roll-off)
        assertEquals(dfunsProfile.filmProfile.lutAssetPath, dqsProfile.filmProfile.lutAssetPath)
        assertEquals("lut_dfuns_v1.png", dqsProfile.filmProfile.lutAssetPath)
        assertEquals(dfunsProfile.filmProfile.lutInputSpace, dqsProfile.filmProfile.lutInputSpace)
        assertEquals(LutInputSpace.SRGB, dqsProfile.filmProfile.lutInputSpace)
        assertEquals(dfunsProfile.filmProfile.highlightRollOff, dqsProfile.filmProfile.highlightRollOff, 1e-4f)
        assertEquals(dfunsProfile.filmProfile.halationThreshold, dqsProfile.filmProfile.halationThreshold, 1e-4f)
        assertEquals(dfunsProfile.filmProfile.halationIntensity, dqsProfile.filmProfile.halationIntensity, 1e-4f)

        // 2. Grain Profile (Texture, scale, intensity, weights)
        assertEquals(dfunsProfile.grainProfile.assetPath, dqsProfile.grainProfile.assetPath)
        assertEquals("grains/grain_dfuns.jpg", dqsProfile.grainProfile.assetPath)
        assertEquals(GrainMode.SCANNED, dqsProfile.grainProfile.mode)
        assertEquals(dfunsProfile.grainProfile.intensity, dqsProfile.grainProfile.intensity, 1e-4f)
        assertEquals(0.16f, dqsProfile.grainProfile.intensity, 1e-4f)
        assertEquals(dfunsProfile.grainProfile.scale, dqsProfile.grainProfile.scale, 1e-4f)
        assertEquals(dfunsProfile.grainProfile.shadowStrength, dqsProfile.grainProfile.shadowStrength, 1e-4f)
        assertEquals(dfunsProfile.grainProfile.highlightStrength, dqsProfile.grainProfile.highlightStrength, 1e-4f)
        assertEquals(dfunsProfile.grainProfile.isColorNoise, dqsProfile.grainProfile.isColorNoise)
        assertEquals(dfunsProfile.grainProfile.luminanceWeightingEnabled, dqsProfile.grainProfile.luminanceWeightingEnabled)

        // 3. Optical Profile (Vignette, CA, softness)
        assertEquals(dfunsProfile.opticalProfile.vignetteStrength, dqsProfile.opticalProfile.vignetteStrength, 1e-4f)
        assertEquals(0.24f, dqsProfile.opticalProfile.vignetteStrength, 1e-4f)
        assertEquals(dfunsProfile.opticalProfile.vignetteRadius, dqsProfile.opticalProfile.vignetteRadius, 1e-4f)
        assertEquals(0.90f, dqsProfile.opticalProfile.vignetteRadius, 1e-4f)
        assertEquals(dfunsProfile.opticalProfile.chromaticAberration, dqsProfile.opticalProfile.chromaticAberration, 1e-5f)

        // 4. Color Grading Invariant (Neutral base to prevent double grading)
        assertEquals(dfunsPreset.colorGrading.contrast, dqsPreset.colorGrading.contrast, 1e-4f)
        assertEquals(1.0f, dqsPreset.colorGrading.contrast, 1e-4f)
        assertEquals(dfunsPreset.colorGrading.saturation, dqsPreset.colorGrading.saturation, 1e-4f)
        assertEquals(1.0f, dqsPreset.colorGrading.saturation, 1e-4f)
        assertEquals(dfunsPreset.colorGrading.temperature, dqsPreset.colorGrading.temperature, 1e-4f)
        assertEquals(0.0f, dqsPreset.colorGrading.temperature, 1e-4f)
        assertEquals(dfunsPreset.colorGrading.tint, dqsPreset.colorGrading.tint, 1e-4f)
        assertEquals(dfunsPreset.colorGrading.fade, dqsPreset.colorGrading.fade, 1e-4f)
        assertEquals(dfunsPreset.colorGrading.highlightRollOff, dqsPreset.colorGrading.highlightRollOff, 1e-4f)

        // 5. Preset Grain and Lens configs
        assertEquals(dfunsPreset.grain.intensity, dqsPreset.grain.intensity, 1e-4f)
        assertEquals(dfunsPreset.grain.assetPath, dqsPreset.grain.assetPath)
        assertEquals("grains/grain_dfuns.jpg", dqsPreset.grain.assetPath)
        assertEquals(dfunsPreset.lens.vignetteIntensity, dqsPreset.lens.vignetteIntensity, 1e-4f)
        assertEquals(dfunsPreset.lens.vignetteRadius, dqsPreset.lens.vignetteRadius, 1e-4f)
    }

    @Test
    fun testDqsAdoptsDFunsSkinToneIntelligenceProfile() {
        val dqsSkin = SkinToneProfileCatalog.getProfileForPreset("ccd_retro_compact")
        val dqsAliasSkin = SkinToneProfileCatalog.getProfileForPreset("dqs")
        val dfunsSkin = SkinToneProfileCatalog.getProfileForPreset("ccd_flash")

        assertSame("DQS must use D FUNS skin tone profile", SkinToneProfileCatalog.D_FUNS, dqsSkin)
        assertSame("dqs alias must use D FUNS skin tone profile", SkinToneProfileCatalog.D_FUNS, dqsAliasSkin)
        assertEquals(dfunsSkin.id, dqsSkin.id)
        assertEquals("skin_dfuns", dqsSkin.id)
        assertEquals(dfunsSkin.targetHueBias, dqsSkin.targetHueBias, 1e-4f)
        assertEquals(dfunsSkin.skinHighlightKnee, dqsSkin.skinHighlightKnee, 1e-4f)
        assertArrayEquals(dfunsSkin.skinGains, dqsSkin.skinGains, 1e-4f)
    }

    @Test
    fun testShaderConstantsAssetRoutingParity() {
        assertEquals("lut_dfuns_v1.png", ShaderConstants.getLutAssetForPreset("ccd_retro_compact"))
        assertEquals("lut_dfuns_v1.png", ShaderConstants.getLutAssetForPreset("dqs"))
        assertEquals("lut_dfuns_v1.png", ShaderConstants.getLutAssetForPreset("ccd_flash"))

        assertEquals("grains/grain_dfuns.jpg", ShaderConstants.getGrainAssetForPreset("ccd_retro_compact", false))
        assertEquals("grains/grain_dfuns.jpg", ShaderConstants.getGrainAssetForPreset("dqs", false))
        assertEquals("grains/grain_dfuns.jpg", ShaderConstants.getGrainAssetForPreset("ccd_flash", false))
    }

    @Test
    fun testDefaultProfileC400ZeroSecondaryDistortion() {
        val dqsPolicy = CameraProfileCompatibility.getPolicy("ccd_retro_compact")
        assertEquals("C400", dqsPolicy.defaultProfileId)
        val defaultProfile = DazzColorProfileCatalog.getById("C400")

        val dqsPreset = PresetCatalog.getPresetById("ccd_retro_compact")!!
        val dqsProfile = CameraProfileCatalog.getProfileForPreset(dqsPreset)

        val plan = CameraProfileCompatibility.resolveLutPlan(
            presetId = dqsPreset.id,
            colorProfileIndex = defaultProfile.index,
            cameraLutAsset = dqsProfile.filmProfile.lutAssetPath,
            cameraSecondaryLutAsset = dqsProfile.filmProfile.secondaryLutAssetPath,
            cameraDualLutMix = dqsProfile.filmProfile.dualLutBlendFactor
        )

        assertEquals("lut_dfuns_v1.png", plan.primaryLutAsset)
        assertNull("Default profile must have zero secondary LUT", plan.secondaryLutAsset)
        assertEquals(0.0f, plan.dualLutMix, 1e-4f)
        assertFalse("Default profile must not be marked as custom", plan.isCustomProfile)
        assertEquals(1.0f, plan.effectiveAdjustment.contrastMult, 1e-4f)
        assertEquals(1.0f, plan.effectiveAdjustment.satMult, 1e-4f)
        assertEquals(0.0f, plan.effectiveAdjustment.tempDelta, 1e-4f)
        assertEquals(0.0f, plan.effectiveAdjustment.tintDelta, 1e-4f)
    }

    @Test
    fun testDFunsPresetAndProfileRemainUnaltered() {
        val dfuns = PresetCatalog.getPresetById("ccd_flash")!!
        assertEquals("D Funs", dfuns.name)
        assertEquals("Single-use 35mm", dfuns.subtitle)
        assertEquals("1995", dfuns.year)
        assertEquals(CameraCategory.DISPOSABLE, dfuns.category)
        assertEquals("cam_dfuns", dfuns.iconResName)
        assertEquals(1.0f, dfuns.colorGrading.contrast, 1e-4f)
        assertEquals(1.0f, dfuns.colorGrading.saturation, 1e-4f)
        assertEquals(0.22f, dfuns.grain.intensity, 1e-4f)
        assertEquals("grains/grain_dfuns.jpg", dfuns.grain.assetPath)

        val profile = CameraProfileCatalog.getProfileForPreset(dfuns)
        assertEquals("lut_dfuns_v1.png", profile.filmProfile.lutAssetPath)
        assertEquals("grains/grain_dfuns.jpg", profile.grainProfile.assetPath)
        assertEquals(0.16f, profile.grainProfile.intensity, 1e-4f)
    }
}
