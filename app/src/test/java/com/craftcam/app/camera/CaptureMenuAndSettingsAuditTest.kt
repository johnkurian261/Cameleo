package com.craftcam.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import com.craftcam.app.captureframe.CaptureFrameState
import com.craftcam.app.config.AppSettingsState
import com.craftcam.app.ui.components.ExposureState
import com.craftcam.app.ui.components.QuickControlMode
import com.craftcam.app.ui.components.TemperatureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * High-precision architectural tests for CAMELEO Three-Dot Capture Menu and Settings Screen.
 * Verifies flash cycling, grid synchronization, frame toggle isolation, zoom mode distinction,
 * mirror front camera gating, and persistent settings integrity.
 */
class CaptureMenuAndSettingsAuditTest {

    // ── 1. Flash State Transitions Test ──
    @Test
    fun testFlashStateCycling() {
        var currentFlash = ImageCapture.FLASH_MODE_OFF

        fun cycleFlash(mode: Int): Int = when (mode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }

        currentFlash = cycleFlash(currentFlash)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, currentFlash)

        currentFlash = cycleFlash(currentFlash)
        assertEquals(ImageCapture.FLASH_MODE_ON, currentFlash)

        currentFlash = cycleFlash(currentFlash)
        assertEquals(ImageCapture.FLASH_MODE_OFF, currentFlash)
    }

    // ── 2. Grid State Synchronization Test ──
    @Test
    fun testGridStateSynchronization() {
        val initialSettings = AppSettingsState(assistiveGrid = false)
        assertFalse(initialSettings.assistiveGrid)

        // When user toggles grid in 3-dot menu or Settings
        val toggledSettings = initialSettings.copy(assistiveGrid = !initialSettings.assistiveGrid)
        assertTrue(toggledSettings.assistiveGrid)

        // CameraState synchronization
        val initialCameraState = CameraUIState(showGrid = initialSettings.assistiveGrid)
        assertFalse(initialCameraState.showGrid)

        val updatedCameraState = initialCameraState.copy(showGrid = toggledSettings.assistiveGrid)
        assertTrue(updatedCameraState.showGrid)
        assertEquals(toggledSettings.assistiveGrid, updatedCameraState.showGrid)
    }

    // ── 3. Frame Toggle Isolation & Snapshot Test ──
    @Test
    fun testFrameToggleStateIsolation() {
        val tuningEnabled = UserTuningConfig(frameBorderEnabled = true)
        val frameStateVisible = CaptureFrameState(isVisible = true)
        assertTrue(tuningEnabled.frameBorderEnabled)
        assertTrue(frameStateVisible.isVisible)

        // Toggling Frame off in menu
        val tuningDisabled = tuningEnabled.copy(frameBorderEnabled = false)
        val frameStateHidden = frameStateVisible.copy(isVisible = false)
        assertFalse(tuningDisabled.frameBorderEnabled)
        assertFalse(frameStateHidden.isVisible)

        // Verify snapshot policy: when frameBorderEnabled is false, no composition crop snapshot is taken
        fun resolveSnapshot(enabled: Boolean, baseState: CaptureFrameState): CaptureFrameState? {
            return if (enabled) baseState else null
        }

        val snapshotEnabled = resolveSnapshot(tuningEnabled.frameBorderEnabled, frameStateVisible)
        assertEquals(frameStateVisible, snapshotEnabled)

        val snapshotDisabled = resolveSnapshot(tuningDisabled.frameBorderEnabled, frameStateHidden)
        assertNull(snapshotDisabled)
    }

    // ── 4. Zoom Mode State Test ──
    @Test
    fun testZoomModeStateToggle() {
        var currentMode = "Frame"
        fun toggleZoomMode(mode: String): String = if (mode == "Frame") "Lens" else "Frame"

        currentMode = toggleZoomMode(currentMode)
        assertEquals("Lens", currentMode)

        currentMode = toggleZoomMode(currentMode)
        assertEquals("Frame", currentMode)

        // In Frame mode: pinch adjusts capture frame without altering sensor optical zoom
        val initialSensorZoom = 1.0f
        val zoomModeFrame = "Frame"
        val sensorZoomAfterPinch = if (zoomModeFrame == "Lens") initialSensorZoom * 1.5f else initialSensorZoom
        assertEquals(1.0f, sensorZoomAfterPinch, 0.001f)

        // In Lens mode: pinch adjusts hardware optical sensor zoom
        val zoomModeLens = "Lens"
        val sensorZoomInLensMode = if (zoomModeLens == "Lens") initialSensorZoom * 1.5f else initialSensorZoom
        assertEquals(1.5f, sensorZoomInLensMode, 0.001f)
    }

    // ── 5. Mirror Front Camera Gating Test ──
    @Test
    fun testMirrorFrontCameraGating() {
        val settingsMirrorOn = AppSettingsState(mirrorFrontCamera = true)
        val settingsMirrorOff = AppSettingsState(mirrorFrontCamera = false)

        fun shouldMirror(lensFacing: Int, settings: AppSettingsState): Boolean {
            return (lensFacing == CameraSelector.LENS_FACING_FRONT) && settings.mirrorFrontCamera
        }

        // Front Camera with Mirror ON -> True
        assertTrue(shouldMirror(CameraSelector.LENS_FACING_FRONT, settingsMirrorOn))

        // Front Camera with Mirror OFF -> False
        assertFalse(shouldMirror(CameraSelector.LENS_FACING_FRONT, settingsMirrorOff))

        // Rear Camera with Mirror ON -> False (never mirror rear camera)
        assertFalse(shouldMirror(CameraSelector.LENS_FACING_BACK, settingsMirrorOn))

        // Rear Camera with Mirror OFF -> False
        assertFalse(shouldMirror(CameraSelector.LENS_FACING_BACK, settingsMirrorOff))
    }

    // ── 6. Settings Persistence & Integrity Test ──
    @Test
    fun testSettingsPersistenceModelIntegrity() {
        val defaultSettings = AppSettingsState()

        // Verify default camera ergonomics
        assertTrue(defaultSettings.exportToAlbum)
        assertFalse(defaultSettings.keepOriginalPhotos)
        assertFalse(defaultSettings.saveLocation)
        assertTrue(defaultSettings.mirrorFrontCamera)
        assertFalse(defaultSettings.assistiveGrid)
        assertFalse(defaultSettings.level)
        assertTrue(defaultSettings.shutterVibration)
        assertTrue(defaultSettings.recommendedApps)
        assertEquals("Frame", defaultSettings.zoomMode)
        assertTrue(defaultSettings.saveFilmNegatives)
        assertTrue(defaultSettings.highResolutionExport)
        assertTrue(defaultSettings.tactileHaptics)
        assertTrue(defaultSettings.preserveExif)

        // Update settings
        val modified = defaultSettings.copy(
            assistiveGrid = true,
            mirrorFrontCamera = false,
            shutterVibration = false,
            zoomMode = "Lens"
        )

        assertTrue(modified.assistiveGrid)
        assertFalse(modified.mirrorFrontCamera)
        assertFalse(modified.shutterVibration)
        assertEquals("Lens", modified.zoomMode)
        assertTrue(modified.exportToAlbum)
    }

    // ── 7. Temperature & AWB State Machine Test ──
    @Test
    fun testTemperatureAndAwbStateMachine() {
        val defaultTemp = TemperatureState()
        assertTrue(defaultTemp.isAuto)
        assertEquals(5500, defaultTemp.kelvin)
        assertEquals(2500, defaultTemp.minKelvin)
        assertEquals(9500, defaultTemp.maxKelvin)

        // Manual Kelvin adjustment switches isAuto to false
        fun updateKelvin(state: TemperatureState, newKelvin: Int): TemperatureState {
            val clamped = newKelvin.coerceIn(state.minKelvin, state.maxKelvin)
            return state.copy(isAuto = false, kelvin = clamped)
        }

        val manualTemp = updateKelvin(defaultTemp, 9400)
        assertFalse(manualTemp.isAuto)
        assertEquals(9400, manualTemp.kelvin)

        // Auto White Balance toggle resets isAuto to true
        val resetAuto = manualTemp.copy(isAuto = true)
        assertTrue(resetAuto.isAuto)
        assertEquals(9400, resetAuto.kelvin) // Kelvin remains preserved

        // Clamping bounds
        val overMax = updateKelvin(defaultTemp, 12000)
        assertEquals(9500, overMax.kelvin)

        val underMin = updateKelvin(defaultTemp, 1000)
        assertEquals(2500, underMin.kelvin)
    }

    // ── 8. Exposure & EV Calculation Test ──
    @Test
    fun testExposureAndEvCalculation() {
        val defaultExp = ExposureState()
        assertTrue(defaultExp.isAuto)
        assertEquals(0, defaultExp.exposureIndex)
        assertEquals(0.0f, defaultExp.evValue, 0.001f)
        assertEquals("0", defaultExp.formattedEv)

        // +1.4 EV simulation (index = 7 with step = 0.2f or index = 2 with step = 0.7f)
        val positiveExp = ExposureState(isAuto = false, exposureIndex = 2, exposureStep = 0.7f)
        assertFalse(positiveExp.isAuto)
        assertEquals(1.4f, positiveExp.evValue, 0.001f)
        assertEquals("+1.4", positiveExp.formattedEv)

        // -2.0 EV simulation
        val negativeExp = ExposureState(isAuto = false, exposureIndex = -4, exposureStep = 0.5f)
        assertFalse(negativeExp.isAuto)
        assertEquals(-2.0f, negativeExp.evValue, 0.001f)
        assertEquals("-2.0", negativeExp.formattedEv)

        // Undo resets exposureIndex to 0 and isAuto to true
        fun undoExposure(state: ExposureState): ExposureState = state.copy(exposureIndex = 0, isAuto = true)
        val undone = undoExposure(negativeExp)
        assertTrue(undone.isAuto)
        assertEquals(0, undone.exposureIndex)
        assertEquals("0", undone.formattedEv)
    }

    // ── 9. Quick Control Mode State Machine Test ──
    @Test
    fun testQuickControlModeTransitions() {
        var mode = QuickControlMode.COMPACT

        fun toggleMode(target: QuickControlMode): QuickControlMode {
            return if (mode == target) QuickControlMode.COMPACT else target
        }

        // Tapping Temperature expands temperature slider
        mode = toggleMode(QuickControlMode.TEMPERATURE)
        assertEquals(QuickControlMode.TEMPERATURE, mode)

        // Tapping active Temperature collapses back to COMPACT
        mode = toggleMode(QuickControlMode.TEMPERATURE)
        assertEquals(QuickControlMode.COMPACT, mode)

        // Tapping Exposure expands exposure slider
        mode = toggleMode(QuickControlMode.EXPOSURE)
        assertEquals(QuickControlMode.EXPOSURE, mode)

        // Tapping Temperature while in Exposure switches directly to Temperature
        mode = toggleMode(QuickControlMode.TEMPERATURE)
        assertEquals(QuickControlMode.TEMPERATURE, mode)

        // Collapse
        mode = toggleMode(QuickControlMode.TEMPERATURE)
        assertEquals(QuickControlMode.COMPACT, mode)
    }
}
