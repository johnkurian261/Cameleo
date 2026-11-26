package com.craftcam.app.color.profile

import com.craftcam.app.camera.CameraUIState
import com.craftcam.app.camera.UserTuningConfig
import com.craftcam.app.model.CameraPreset
import com.craftcam.app.model.WhiteBalanceConfig
import com.craftcam.app.model.WhiteBalancePolicy
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test

class ColorProfilePhase2Test {

    @Test
    fun testAllProfilesHaveDedicated3DLuts() {
        val profiles = DazzColorProfileCatalog.PROFILES
        assertEquals(10, profiles.size)

        val lutPaths = mutableSetOf<String>()
        for (profile in profiles) {
            assertTrue("LUT asset path must not be empty for ${profile.id}", profile.lutAssetPath.isNotEmpty())
            assertTrue("LUT asset path must end with .png for ${profile.id}", profile.lutAssetPath.endsWith(".png"))
            assertTrue("LUT asset path must start with lut_profile_ for ${profile.id}", profile.lutAssetPath.startsWith("lut_profile_"))
            lutPaths.add(profile.lutAssetPath)
        }
        assertEquals("Each profile must have a unique dedicated 3D LUT", 10, lutPaths.size)
    }

    @Test
    fun testAllProfilesHavePhotographicThumbnails() {
        val profiles = DazzColorProfileCatalog.PROFILES
        for (profile in profiles) {
            assertTrue(profile.thumbnailDrawableName.startsWith("thumb_profile_"))
            assertEquals("thumb_profile_${profile.id.lowercase()}", profile.thumbnailDrawableName)
        }
    }

    @Test
    fun testCameraProfileCompatibilityDecoupling() {
        // Disposables support interchangeable color profiles with default C400
        assertTrue(CameraProfileCompatibility.isProfileSupported("dfuns"))
        assertTrue(CameraProfileCompatibility.isProfileSupported("dqs"))
        assertTrue(CameraProfileCompatibility.isProfileSupported("cpm35"))
        assertEquals("C400", CameraProfileCompatibility.getDefaultProfile("dfuns").id)

        // D3D defaults to 19P tungsten cinema stock
        assertTrue(CameraProfileCompatibility.isProfileSupported("d3d"))
        assertEquals("19P", CameraProfileCompatibility.getDefaultProfile("d3d").id)

        // Classic Digicams default to FX33 CCD look
        assertTrue(CameraProfileCompatibility.isProfileSupported("ccd_r"))
        assertTrue(CameraProfileCompatibility.isProfileSupported("ccd_grd"))
        assertTrue(CameraProfileCompatibility.isProfileSupported("ccd_y2k_digital"))
        assertEquals("FX33", CameraProfileCompatibility.getDefaultProfile("ccd_r").id)
        assertEquals("FX33", CameraProfileCompatibility.getDefaultProfile("ccd_grd").id)

        // FXN-R defaults to FXN2
        assertTrue(CameraProfileCompatibility.isProfileSupported("analog_fxn_r"))
        assertEquals("FXN2", CameraProfileCompatibility.getDefaultProfile("analog_fxn_r").id)

        // Modern Smartphone Zero is fixed Natural ISP pipeline (not film emulsion)
        assertFalse(CameraProfileCompatibility.isProfileSupported("zero"))

        // Camcorders are fixed CRT/VHS analog video pipelines
        assertFalse(CameraProfileCompatibility.isProfileSupported("cam_retro_vhs"))
        assertFalse(CameraProfileCompatibility.isProfileSupported("cam_analog_video"))
        assertFalse(CameraProfileCompatibility.isProfileSupported("cam_90s_camcorder"))
    }

    @Test
    fun testMonochromeAchromaticSuppression() {
        val bw = DazzColorProfileCatalog.getById("22M")
        assertTrue(bw.isMonochrome)
        assertEquals(0.0f, bw.adjustment.satMult, 0.0001f)
        assertEquals("lut_profile_22m.png", bw.lutAssetPath)

        val color = DazzColorProfileCatalog.getById("C400")
        assertFalse(color.isMonochrome)
        assertEquals(1.0f, color.adjustment.satMult, 0.0001f)
    }

    @Test
    fun testColorProfileInspectorDiagnosticOutput() {
        val preset = requireNotNull(PresetCatalog.getPresetById("disp_warm"))
        val uiState = CameraUIState(
            tuning = UserTuningConfig(
                filmColorProfileIndex = 3, // 19P
                isHardwareWbActive = true,
                temperatureOffset = 0.0f
            ),
            isHardwareWbActive = true
        )

        val report = ColorProfileInspector.inspect(preset, uiState)
        assertEquals("disp_warm", report.cameraPresetId)
        assertTrue(report.isProfileSupported)
        assertEquals("19P", report.activeProfileId)
        assertEquals("lut_profile_19p.png", report.activeLutAsset)
        assertTrue(report.isHardwareWbActive)
        assertEquals(0.0f, report.userTempOffset, 0.0001f)
        assertTrue(report.singleApplicationInvariantValid)

        val md = report.toMarkdown()
        assertTrue(md.contains("19P"))
        assertTrue(md.contains("lut_profile_19p.png"))
        assertTrue(md.contains("DECOUPLED"))
        assertTrue(md.contains("PASS"))
    }

    @Test
    fun testSingleApplicationInvariantGuardsAgainstDoubleWhiteBalance() {
        val preset = requireNotNull(PresetCatalog.getPresetById("ccd_flash"))
        
        // When hardware WB is active, software temp offset must be 0
        val validState = CameraUIState(
            tuning = UserTuningConfig(temperatureOffset = 0.0f, isHardwareWbActive = true),
            isHardwareWbActive = true
        )
        assertTrue(ColorProfileInspector.inspect(preset, validState).singleApplicationInvariantValid)

        // If software temp offset was non-zero while hardware WB was active, inspector flags violation
        val violatingState = CameraUIState(
            tuning = UserTuningConfig(temperatureOffset = 0.15f, isHardwareWbActive = true),
            isHardwareWbActive = true
        )
        assertFalse(ColorProfileInspector.inspect(preset, violatingState).singleApplicationInvariantValid)
    }

    @Test
    fun testNineteenSeriesTungstenDifferentiatesPrintFromMonitor() {
        val p = DazzColorProfileCatalog.getById("19P")
        val p2 = DazzColorProfileCatalog.getById("19P2")
        val m = DazzColorProfileCatalog.getById("19M")

        assertNotEquals(p.lutAssetPath, p2.lutAssetPath)
        assertNotEquals(p.lutAssetPath, m.lutAssetPath)
        assertNotEquals(p2.lutAssetPath, m.lutAssetPath)

        assertTrue(p.lutAssetPath == "lut_profile_19p.png")
        assertTrue(p2.lutAssetPath == "lut_profile_19p2.png")
        assertTrue(m.lutAssetPath == "lut_profile_19m.png")
    }
}
