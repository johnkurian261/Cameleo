package com.craftcam.app.color

import com.craftcam.app.model.WhiteBalanceConfig
import com.craftcam.app.model.WhiteBalancePolicy
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test

/**
 * High-precision verification test suite for CAMELEO White Balance Architecture,
 * Camera-Specific Calibration, and Single-Application Invariant.
 */
class WhiteBalanceCalibrationTest {

    @Test
    fun perDeviceCalibrationPreservesOffAxisColorAfterManualBalance() {
        val identity = ColorMatrix3x3.IDENTITY.toFloatArray()
        val calibration = floatArrayOf(.88f,.04f,0f, .02f,1f,.03f, 0f,.02f,.98f)
        val device = ColorMatrix3x3.fromArray(calibration)
        val referenceWhite = floatArrayOf(.95046f,1f,1.08906f)
        val referenceColor = floatArrayOf(.4f,.3f,.2f)
        val white = device.apply(referenceWhite[0],referenceWhite[1],referenceWhite[2])
        val color = device.apply(referenceColor[0],referenceColor[1],referenceColor[2])
        val expected = ColorMatrix3x3.fromArray(HardwareWhiteBalanceCalibrator.deviceCcm(
            identity, identity, identity, .3127f, .329f)).apply(
                referenceColor[0]/referenceWhite[0], referenceColor[1], referenceColor[2]/referenceWhite[2])
        val actual = ColorMatrix3x3.fromArray(HardwareWhiteBalanceCalibrator.deviceCcm(
            identity, identity, calibration, .3127f, .329f)).apply(color[0]/white[0],color[1]/white[1],color[2]/white[2])
        assertArrayEquals(expected, actual, .0001f)
    }

    @Test
    fun testAllPresetsHaveValidWhiteBalancePolicy() {
        val presets = PresetCatalog.allPresets
        assertTrue("Preset catalog must not be empty", presets.isNotEmpty())
        assertEquals("Expected 35 authentic camera presets", 35, presets.size)

        for (preset in presets) {
            val wb = preset.whiteBalance
            assertNotNull("Preset ${preset.id} must have a non-null WhiteBalanceConfig", wb)
            assertTrue("defaultKelvin must be in [2500..9500]", wb.defaultKelvin in 2500..9500)
            assertTrue("minKelvin <= maxKelvin", wb.minKelvin <= wb.maxKelvin)

            // Disposable cameras must strictly enforce FIXED_DAYLIGHT
            if (preset.id in listOf("ccd_flash", "ccd_retro_compact", "disp_flash", "disp_classic")) {
                assertEquals(
                    "Disposable camera ${preset.id} must use FIXED_DAYLIGHT policy",
                    WhiteBalancePolicy.FIXED_DAYLIGHT,
                    wb.policy
                )
                assertFalse("Disposable camera must not recommend auto WB", wb.autoRecommended)
                assertEquals(5500, wb.defaultKelvin)
            }

            // Zero must use AUTO
            if (preset.id == "zero") {
                assertEquals(WhiteBalancePolicy.AUTO, wb.policy)
                assertTrue("Zero must recommend auto WB", wb.autoRecommended)
            }

            // CT2F must use FIXED_TUNGSTEN
            if (preset.id == "analog_soft_film") {
                assertEquals(WhiteBalancePolicy.FIXED_TUNGSTEN, wb.policy)
                assertEquals(3200, wb.defaultKelvin)
                assertFalse(wb.autoRecommended)
            }

            // CCD R and GR D must use HYBRID_ILLUMINANT
            if (preset.id in listOf("ccd_r", "ccd_y2k_digital", "analog_warm_film", "ccd_grd")) {
                assertEquals(WhiteBalancePolicy.HYBRID_ILLUMINANT, wb.policy)
                assertTrue(wb.autoRecommended)
            }
        }
    }

    @Test
    fun testKelvinToCieXyMonotonicity() {
        // As Kelvin increases (cooler), x chromaticity must strictly decrease
        var prevX = 1.0f
        for (k in 2500..9500 step 500) {
            val (x, y) = HardwareWhiteBalanceCalibrator.kelvinToCieXy(k, 0.0f)
            assertTrue("x chromaticity must be in valid CIE range", x in 0.25f..0.60f)
            assertTrue("y chromaticity must be in valid CIE range", y in 0.25f..0.50f)
            assertTrue("x must monotonically decrease with increasing Kelvin ($k: $x < $prevX)", x < prevX)
            prevX = x
        }

        // Test daylight baseline (5500K)
        val (x55, y55) = HardwareWhiteBalanceCalibrator.kelvinToCieXy(5500, 0.0f)
        assertEquals("D55 x chromaticity parity", 0.332f, x55, 0.015f)
        assertEquals("D55 y chromaticity parity", 0.347f, y55, 0.015f)
    }

    @Test
    fun testSamsungS21SensorCalibrationGains() {
        // At 2856K (Tungsten): blue gain must be significantly higher than red gain
        val tungstenGains = HardwareWhiteBalanceCalibrator.computeCalibratedGains(
            kelvin = 2856,
            tintOffset = 0.0f,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "0"
        )
        assertTrue(
            "Under tungsten, blue gain must exceed red gain (r=${tungstenGains.red}, b=${tungstenGains.blue})",
            tungstenGains.blue > tungstenGains.red
        )
        assertEquals("Green gains must remain normalized to 1.0", 1.0f, tungstenGains.greenEven, 0.001f)
        assertEquals("Green gains must remain normalized to 1.0", 1.0f, tungstenGains.greenOdd, 0.001f)

        // At 6504K (Daylight D65): red gain must exceed or balance blue gain
        val daylightGains = HardwareWhiteBalanceCalibrator.computeCalibratedGains(
            kelvin = 6504,
            tintOffset = 0.0f,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "0"
        )
        assertTrue(
            "Under daylight, red gain should balance or exceed blue gain (r=${daylightGains.red}, b=${daylightGains.blue})",
            daylightGains.red >= daylightGains.blue * 0.95f
        )

        // Front camera calibration should produce valid non-clipped gains
        val frontGains = HardwareWhiteBalanceCalibrator.computeCalibratedGains(
            kelvin = 5500,
            tintOffset = 0.0f,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "1"
        )
        assertTrue(frontGains.red in 1.2f..3.5f)
        assertTrue(frontGains.blue in 1.2f..3.5f)
    }

    @Test
    fun testSingleApplicationInvariant() {
        val dfuns = PresetCatalog.getPresetById("ccd_flash")

        // 1. When hardware WB is supported, execution must mark isHardwareApplied = true
        val hwExec = HardwareWhiteBalanceCalibrator.resolveExecution(
            policy = dfuns.whiteBalance.policy,
            requestedKelvin = dfuns.whiteBalance.defaultKelvin,
            supportsManualWb = true,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "0"
        )
        assertTrue("Hardware WB must be active when supported", hwExec.isHardwareApplied)
        assertNotNull("Hardware gains must be provided", hwExec.colorCorrectionGains)
        assertNotNull("ColorSpaceTransform must be provided", hwExec.colorSpaceTransform)

        // Inspector check: verifies no duplicate software shift
        val snapshot = WhiteBalanceInspector.inspect(
            preset = dfuns,
            kelvin = 5500,
            supportsManualWb = true,
            brand = DeviceBrand.SAMSUNG,
            cameraId = "0"
        )
        assertTrue("Single application invariant must pass", snapshot.singleApplicationVerified)
        assertTrue("Hardware must be applied", snapshot.isHardwareApplied)

        // 2. Fallback when hardware WB is unsupported
        val fallbackExec = HardwareWhiteBalanceCalibrator.resolveExecution(
            policy = dfuns.whiteBalance.policy,
            requestedKelvin = dfuns.whiteBalance.defaultKelvin,
            supportsManualWb = false,
            brand = DeviceBrand.GENERIC,
            cameraId = "0"
        )
        assertFalse("Hardware WB must not be reported active when unsupported", fallbackExec.isHardwareApplied)
        assertNull("Gains must be null in fallback", fallbackExec.colorCorrectionGains)
    }

    @Test
    fun testResetToBaselineKelvin() {
        val dfuns = PresetCatalog.getPresetById("ccd_flash")
        assertEquals(5500, dfuns.whiteBalance.defaultKelvin)

        val ct2f = PresetCatalog.getPresetById("analog_soft_film")
        assertEquals(3200, ct2f.whiteBalance.defaultKelvin)

        val zero = PresetCatalog.getPresetById("zero")
        assertEquals(5500, zero.whiteBalance.defaultKelvin)
    }
}
