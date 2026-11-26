package com.craftcam.app.camera.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmResponseTest {
    @Test
    fun fxnrCandidateRampsAreMonotonicAndLeaveHighlightsAlone() {
        for (strength in listOf(0.035f, 0.065f, 0.10f)) {
            var previous = -1f
            for (i in 0..4096) {
                val input = i / 4096f
                val output = input + lowerMidtoneLift(input, strength)
                assertTrue(output >= previous)
                if (input >= 0.685f) assertEquals(input, output, 0f)
                previous = output
            }
        }
    }

    @Test
    fun brightRampRetainsTheStockResponse() {
        val stocks = listOf(CameraProfileCatalog.INSTANT_CAMERA, CameraProfileCatalog.FXN_R, CameraProfileCatalog.CCD_DIGITAL)
        val white = stocks.map { profile ->
            val shoulder = profile.filmProfile.shoulderAtIntensity(1f)
            var previous = -1f
            for (level in 0..1024) {
                val input = level / 1024f
                val output = compressFilmHighlight(input, shoulder)
                assertTrue(output >= previous && output <= input + 0.000001f)
                if (input <= shoulder) assertEquals(input, output, 0f)
                previous = output
            }
            previous
        }
        assertEquals(0.86f, white[0], 0.00001f)
        assertEquals(0.89f, white[1], 0.00001f)
        assertEquals(0.97f, white[2], 0.00001f)
    }

    @Test
    fun intensityAndStockFadeRespectPresetDefaults() {
        val fxnr = CameraProfileCatalog.FXN_R.filmProfile
        val paf = CameraProfileCatalog.PAF_INSTANT.filmProfile
        assertEquals(1f, compressFilmHighlight(1f, fxnr.shoulderAtIntensity(0f)), 0f)
        assertEquals(0.89f, fxnr.shoulderAtIntensity(0.5f), 0.00001f)
        assertEquals(0.032f, fxnr.fadeAtIntensity(0.032f, 0f, 1f), 0f)
        assertEquals(0.04f, paf.fadeAtIntensity(0.01f, 0f, 0.5f), 0.00001f)
        assertEquals(0f, paf.fadeAtIntensity(0.01f, 0.02f, 0f), 0f)
    }
}
