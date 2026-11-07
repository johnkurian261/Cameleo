package com.craftcam.app.captureframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CaptureFrameMapperTest {

    @Test
    fun testDefaultCenteredFramingWindow() {
        val state = CaptureFrameState(
            normalizedCenterX = 0.5f,
            normalizedCenterY = 0.5f,
            sizeFraction = 0.70f,
            aspectRatio = 3f / 4f,
            baseFocalLengthMm = 26.0f
        )

        val previewAspect = 3f / 4f
        val rect = state.getNormalizedRect(previewAspect)

        assertEquals(0.15f, rect.left, 0.001f)
        assertEquals(0.15f, rect.top, 0.001f)
        assertEquals(0.85f, rect.right, 0.001f)
        assertEquals(0.85f, rect.bottom, 0.001f)
        assertEquals(0.70f, rect.width, 0.001f)
        assertEquals(0.70f, rect.height, 0.001f)

        // Effective focal length for 0.70x crop on 26mm lens is ~37.14mm
        assertTrue(abs(state.effectiveFocalLengthMm - 37.14f) < 0.2f)
        assertEquals("37.1mm", state.formattedFocalLength)
    }

    @Test
    fun testFocalLengthSteps() {
        val baseMm = 26.0f

        // Full sensor (1.0) -> 26mm
        val full = CaptureFrameState(sizeFraction = 1.0f, baseFocalLengthMm = baseMm)
        assertEquals("26mm", full.formattedFocalLength)

        // 0.52 crop -> 50mm
        val fifty = CaptureFrameState(sizeFraction = 26f / 50f, baseFocalLengthMm = baseMm)
        assertEquals("50mm", fifty.formattedFocalLength)

        // 0.371 crop -> 70mm
        val seventy = CaptureFrameState(sizeFraction = 26f / 70f, baseFocalLengthMm = baseMm)
        assertEquals("70mm", seventy.formattedFocalLength)

        // 0.26 crop -> 100mm
        val hundred = CaptureFrameState(sizeFraction = 26f / 100f, baseFocalLengthMm = baseMm)
        assertEquals("100mm", hundred.formattedFocalLength)
    }

    @Test
    fun testStayCenteredLocked() {
        var state = CaptureFrameState(
            normalizedCenterX = 0.5f,
            normalizedCenterY = 0.5f,
            sizeFraction = 0.50f,
            aspectRatio = 3f / 4f
        )
        val aspect = 3f / 4f

        // Moving is disabled; rectangle remains strictly centered at (0.5, 0.5)
        state = state.moveBy(-1.0f, 0f, aspect)
        val rect = state.getNormalizedRect(aspect)
        assertEquals(0.25f, rect.left, 0.001f)
        assertEquals(0.75f, rect.right, 0.001f)
        assertEquals(0.25f, rect.top, 0.001f)
        assertEquals(0.75f, rect.bottom, 0.001f)
        assertEquals(0.5f, rect.centerX, 0.001f)
        assertEquals(0.5f, rect.centerY, 0.001f)

        state = state.moveBy(2.0f, -2.0f, aspect)
        val rect2 = state.getNormalizedRect(aspect)
        assertEquals(0.25f, rect2.left, 0.001f)
        assertEquals(0.75f, rect2.right, 0.001f)
        assertEquals(0.5f, rect2.centerX, 0.001f)
        assertEquals(0.5f, rect2.centerY, 0.001f)
    }

    @Test
    fun testResizeBounds() {
        val aspect = 3f / 4f
        var state = CaptureFrameState(
            normalizedCenterX = 0.5f,
            normalizedCenterY = 0.5f,
            sizeFraction = 0.50f,
            minSizeFraction = 0.25f,
            maxSizeFraction = 1.0f
        )

        // Shrink below minimum
        state = state.resizeBy(0.1f, aspect)
        assertEquals(0.25f, state.sizeFraction, 0.001f)

        // Enlarge beyond maximum
        state = state.resizeBy(10.0f, aspect)
        assertEquals(1.0f, state.sizeFraction, 0.001f)
    }

    @Test
    fun testMappingToFullResolutionImage() {
        val snapshot = CaptureFrameSnapshot(
            normalizedRect = NormalizedRect(0.10f, 0.20f, 0.80f, 0.90f),
            previewAspect = 3f / 4f,
            sensorOrientation = 90,
            isFrontCamera = false,
            baseFocalLengthMm = 26.0f,
            effectiveFocalLengthMm = 37.0f
        )

        val orientedW = 3024
        val orientedH = 4032

        val cropRect = CaptureFrameMapper.mapToCropRect(snapshot, orientedW, orientedH)

        assertEquals(302, cropRect.left)
        assertEquals(806, cropRect.top)
        assertEquals(2419, cropRect.right)
        assertEquals(3629, cropRect.bottom)

        assertTrue(cropRect.left >= 0)
        assertTrue(cropRect.top >= 0)
        assertTrue(cropRect.right <= orientedW)
        assertTrue(cropRect.bottom <= orientedH)
        assertTrue(cropRect.width > 0)
        assertTrue(cropRect.height > 0)
    }

    @Test
    fun testFallbackOnNullSnapshot() {
        val orientedW = 3024
        val orientedH = 4032

        val fallbackRect = CaptureFrameMapper.mapToCropRect(null, orientedW, orientedH)

        assertEquals(0, fallbackRect.left)
        assertEquals(0, fallbackRect.top)
        assertEquals(orientedW, fallbackRect.right)
        assertEquals(orientedH, fallbackRect.bottom)
    }
}

