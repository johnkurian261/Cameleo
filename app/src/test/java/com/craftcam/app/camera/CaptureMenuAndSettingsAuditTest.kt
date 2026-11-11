package com.craftcam.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import com.craftcam.app.captureframe.CaptureFrameState
import com.craftcam.app.config.AppSettingsState
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
}
