package com.craftcam.app.camera.focus

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import com.craftcam.app.camera.AspectRatioMode
import com.craftcam.app.model.CameraHardwareCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AutofocusIntelligenceTest {

    private lateinit var controller: AutofocusController

    @Before
    fun setUp() {
        controller = AutofocusController(context = null, scope = CoroutineScope(Dispatchers.Default))
    }

    @Test
    fun afStatus_mapsCorrectlyFromCamera2Values() {
        assertEquals(AutofocusStatus.INACTIVE, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_INACTIVE, false))
        assertEquals(AutofocusStatus.PASSIVE_SCAN, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN, false))
        assertEquals(AutofocusStatus.PASSIVE_FOCUSED, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED, false))
        assertEquals(AutofocusStatus.ACTIVE_SCAN, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN, false))
        assertEquals(AutofocusStatus.FOCUSED_LOCKED, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED, false))
        assertEquals(AutofocusStatus.NOT_FOCUSED_LOCKED, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED, false))
        assertEquals(AutofocusStatus.PASSIVE_UNFOCUSED, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED, false))
    }

    @Test
    fun fixedFocusLens_overridesToFixedFocusStatus() {
        assertEquals(AutofocusStatus.FIXED_FOCUS, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_INACTIVE, true))
        assertEquals(AutofocusStatus.FIXED_FOCUS, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN, true))
        assertEquals(AutofocusStatus.FIXED_FOCUS, AutofocusStatus.fromCamera2(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED, true))
    }

    @Test
    fun calculateNormalizedPoint_centersAccuratelyInLetterboxedViewfinder() {
        // In a tall 1080x2400 viewport, 4:3 target frame has height = 1080 * (4/3) = 1440px
        // Top letterbox margin = (2400 - 1440) / 2 = 480px
        val viewW = 1080
        val viewH = 2400
        val centerPoint = controller.calculateNormalizedPoint(540f, 1200f, viewW, viewH, AspectRatioMode.RATIO_4_3)

        assertEquals(0.5f, centerPoint.x, 0.01f)
        assertEquals(0.5f, centerPoint.y, 0.01f)

        // Top edge of 4:3 frame at y=480
        val topEdgePoint = controller.calculateNormalizedPoint(540f, 480f, viewW, viewH, AspectRatioMode.RATIO_4_3)
        assertEquals(0.5f, topEdgePoint.x, 0.01f)
        assertEquals(0.0f, topEdgePoint.y, 0.01f)

        // Bottom edge of 4:3 frame at y=1920
        val bottomEdgePoint = controller.calculateNormalizedPoint(540f, 1920f, viewW, viewH, AspectRatioMode.RATIO_4_3)
        assertEquals(0.5f, bottomEdgePoint.x, 0.01f)
        assertEquals(1.0f, bottomEdgePoint.y, 0.01f)
    }

    @Test
    fun calculateNormalizedPoint_clampsOutOfBoundsTaps() {
        val viewW = 1080
        val viewH = 2400
        // Tap far above the 4:3 frame (e.g. in the top letterbox at y=100)
        val outOfBoundsTop = controller.calculateNormalizedPoint(540f, 100f, viewW, viewH, AspectRatioMode.RATIO_4_3)
        assertEquals(0.0f, outOfBoundsTop.y, 0.001f)

        // Tap far below the 4:3 frame (e.g. at y=2300)
        val outOfBoundsBottom = controller.calculateNormalizedPoint(540f, 2300f, viewW, viewH, AspectRatioMode.RATIO_4_3)
        assertEquals(1.0f, outOfBoundsBottom.y, 0.001f)
    }

    @Test
    fun onCameraCapabilitiesChanged_detectsFixedFocusUltrawide() {
        val ultrawideCaps = CameraHardwareCapabilities(
            cameraId = "2",
            minFocusDistance = 0.0f,
            availableAfModes = listOf(CaptureRequest.CONTROL_AF_MODE_OFF),
            isFixedFocus = true
        )
        controller.onCameraCapabilitiesChanged(ultrawideCaps, lensFacing = 1)

        val telemetry = controller.telemetry.value
        assertTrue("Ultrawide must be recognized as fixed-focus", telemetry.isFixedFocus)
        assertEquals(AutofocusStatus.FIXED_FOCUS, telemetry.status)
    }

    @Test
    fun onCameraCapabilitiesChanged_detectsFullAutofocusMainLens() {
        val mainLensCaps = CameraHardwareCapabilities(
            cameraId = "0",
            minFocusDistance = 10.0f,
            availableAfModes = listOf(
                CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_MACRO,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            ),
            isFixedFocus = false
        )
        controller.onCameraCapabilitiesChanged(mainLensCaps, lensFacing = 1)

        val telemetry = controller.telemetry.value
        assertFalse("Main lens must support active autofocus", telemetry.isFixedFocus)
        assertEquals(10.0f, telemetry.minFocusDistanceDiopters, 0.01f)
    }

    @Test
    fun coordinateShutterWithFocus_readyImmediatelyOnFixedFocus() = runBlocking {
        val ultrawideCaps = CameraHardwareCapabilities(
            cameraId = "2",
            minFocusDistance = 0.0f,
            isFixedFocus = true
        )
        controller.onCameraCapabilitiesChanged(ultrawideCaps, lensFacing = 1)

        val ready = controller.coordinateShutterWithFocus(timeoutMs = 500L)
        assertTrue("Fixed-focus lens must be ready immediately for shutter release", ready)
    }

    @Test
    fun diagnosticReport_containsAccurateMetricsAndLogEntries() {
        val mainLensCaps = CameraHardwareCapabilities(
            cameraId = "0",
            minFocusDistance = 10.0f,
            hyperfocalDistance = 0.28f,
            isFixedFocus = false
        )
        controller.onCameraCapabilitiesChanged(mainLensCaps, lensFacing = 1)

        val report = controller.getDiagnosticReport()
        assertTrue(report.contains("CAMELEO AUTOFOCUS DIAGNOSTIC INSPECTOR"))
        assertTrue(report.contains("Camera ID: 0"))
        assertTrue(report.contains("Min Focus Dist: 10.0 D"))
        assertTrue(report.contains("Hyperfocal: 0.28 D"))
    }
}
