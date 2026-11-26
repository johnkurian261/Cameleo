package com.craftcam.app.camera.profile

import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.config.filmColorProfileAdjustment
import com.craftcam.app.presets.PresetCatalog
import com.craftcam.app.processor.GrainStage
import org.junit.Assert.*
import org.junit.Test

class CollectionFilmControlsTest {
    @Test fun grainSwitchGatesTheScanAtTheSelectedStrength() {
        val stock = PresetCatalog.getPresetById("analog_fxn_r")
        val profile = CameraProfileCatalog.getProfileForPreset(stock).grainProfile
        val tuning = UserTuningConfig(grainBoost = 1.6f)
        val on = GrainStage.resolve(stock, profile, tuning, 0.7f)
        val off = GrainStage.resolve(stock, profile, tuning.copy(debugStageGrain = false), 0.7f)
        assertTrue(on.useScannedTexture)
        assertEquals(profile.intensity * 1.6f * 0.7f, on.effectiveIntensity, 0.0001f)
        assertFalse(off.useScannedTexture)
        assertEquals(0f, off.proceduralIntensity, 0f)
        assertEquals(0f, off.effectiveIntensity, 0f)
    }

    @Test fun fourSwatchesResolveDistinctGradesWithNeutralC400() {
        val grades = (0..3).map(::filmColorProfileAdjustment)
        assertEquals(4, grades.distinct().size)
        assertEquals(0f, grades[1].tempDelta, 0f)
        assertEquals(1f, grades[1].contrastMult, 0f)
        assertTrue(grades[0].tempDelta > grades[1].tempDelta)
        assertTrue(grades[2].tintDelta < grades[1].tintDelta)
        assertTrue(grades[3].contrastMult > grades[1].contrastMult)
    }
}
