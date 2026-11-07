package com.craftcam.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ZoomStateTest {

    @Test
    fun testStandardFocalLengthsFormatting() {
        val standardCases = listOf(
            24.0f to "24mm",
            26.0f to "26mm",
            28.0f to "28mm",
            30.0f to "30mm",
            35.0f to "35mm",
            37.0f to "37mm",
            50.0f to "50mm",
            70.0f to "70mm",
            100.0f to "100mm"
        )

        for ((mm, expected) in standardCases) {
            val state = ZoomState(
                zoomRatio = mm / 26.0f,
                displayFocalLengthMm = mm,
                baseFocalLength35mm = 26.0f
            )
            assertEquals("Focal length $mm format mismatch", expected, state.formattedFocalLength)
        }
    }

    @Test
    fun testSmoothContinuousFocalLengthsFormatting() {
        // Continuous zoom between 35mm and 37mm
        val continuousCases = listOf(
            35.0f to "35mm",
            35.4f to "35.4mm",
            35.8f to "35.8mm",
            36.2f to "36.2mm",
            36.7f to "36.7mm",
            37.0f to "37mm"
        )

        for ((mm, expected) in continuousCases) {
            val state = ZoomState(
                zoomRatio = mm / 26.0f,
                displayFocalLengthMm = mm,
                baseFocalLength35mm = 26.0f
            )
            assertEquals("Smooth focal length $mm format mismatch", expected, state.formattedFocalLength)
        }
    }

    @Test
    fun testEffectiveFocalLengthCalculation() {
        val baseFocalMm = 26.0f // Rear wide
        val zoomFactors = listOf(1.0f, 1.423f, 1.923f, 2.692f, 3.846f)
        val expectedMm = listOf(26.0f, 37.0f, 50.0f, 70.0f, 100.0f)

        for (i in zoomFactors.indices) {
            val calculated = baseFocalMm * zoomFactors[i]
            assertTrue(
                "Calculated focal length ${calculated}mm deviated from expected ${expectedMm[i]}mm",
                abs(calculated - expectedMm[i]) < 0.2f
            )
        }
    }

    @Test
    fun testUltraWideAndFrontCameraFocalLengths() {
        // Ultra-wide base 13mm
        val ultrawideState = ZoomState(
            zoomRatio = 1.0f,
            displayFocalLengthMm = 13.0f,
            baseFocalLength35mm = 13.0f,
            activeCameraId = "0"
        )
        assertEquals("13mm", ultrawideState.formattedFocalLength)

        // Ultra-wide zoomed to 1.5x -> 19.5mm
        val uwZoomed = ultrawideState.copy(
            zoomRatio = 1.5f,
            displayFocalLengthMm = 13.0f * 1.5f
        )
        assertEquals("19.5mm", uwZoomed.formattedFocalLength)

        // Front camera base 26mm
        val frontState = ZoomState(
            zoomRatio = 1.0f,
            displayFocalLengthMm = 26.0f,
            baseFocalLength35mm = 26.0f,
            activeCameraId = "1"
        )
        assertEquals("26mm", frontState.formattedFocalLength)
    }

    @Test
    fun testClampingLimits() {
        val minZ = 1.0f
        val maxZ = 10.0f

        val ratioUnder = 0.5f
        val clampedUnder = ratioUnder.coerceIn(minZ, maxZ)
        assertEquals(minZ, clampedUnder)

        val ratioOver = 15.0f
        val clampedOver = ratioOver.coerceIn(minZ, maxZ)
        assertEquals(maxZ, clampedOver)
    }
}
