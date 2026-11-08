package com.craftcam.app.camera.exposure

import android.graphics.RectF
import com.craftcam.app.portrait.FaceRegion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NaturalExposureControllerTest {

    private lateinit var controller: NaturalExposureController

    @Before
    fun setUp() {
        controller = NaturalExposureController()
    }

    @Test
    fun evaluate_balancedDaylight_producesNeutralExposure() {
        // Balanced scene: midtone-dominated histogram (bins 64..191)
        val histogram = IntArray(256)
        for (i in 64..191) {
            histogram[i] = 100
        }

        // Run multiple evaluations to let smoothing converge
        var decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.5f, -6, 6)
        repeat(15) {
            decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.5f, -6, 6)
        }

        assertEquals("NORMAL_DAYLIGHT_NEUTRAL", decision.sceneTag)
        assertEquals(0.0f, decision.targetEv, 0.05f)
        assertEquals(0, decision.targetIndex)
    }

    @Test
    fun evaluate_severeHighlightClipping_appliesProtectiveNegativeEv() {
        // Scene with severe white blowout (bins 252..255 having 10% of pixels)
        val histogram = IntArray(256)
        for (i in 64..191) histogram[i] = 50
        for (i in 252..255) histogram[i] = 300 // Heavy clipping

        var decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.5f, -6, 6)
        repeat(20) {
            decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.5f, -6, 6)
        }

        assertEquals("EXTREME_HIGHLIGHT_PROTECTION", decision.sceneTag)
        assertTrue("Target EV must be negative to protect highlights", decision.targetEv < -0.4f)
        assertTrue("Target index must be negative", decision.targetIndex <= -1)
    }

    @Test
    fun evaluate_brightSkyHdrScene_preservesSkyHighlights() {
        // HDR scene: bright sky (bins 220..251) and deep shadows (bins 4..63)
        val histogram = IntArray(256)
        for (i in 4..63) histogram[i] = 150
        for (i in 64..191) histogram[i] = 100
        for (i in 220..251) histogram[i] = 180

        var decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.333f, -6, 6)
        repeat(20) {
            decision = controller.evaluate(histogram, 100, 10_000_000L, emptyList(), 0.333f, -6, 6)
        }

        assertEquals("SKY_AND_HDR_PRESERVATION", decision.sceneTag)
        assertTrue("Target EV should protect highlights in HDR sky", decision.targetEv <= -0.25f)
    }

    @Test
    fun evaluate_darkScene_avoidsExcessiveUnderexposure() {
        // Low-light scene: histogram clustered in deep shadows (bins 4..40)
        val histogram = IntArray(256)
        for (i in 4..40) histogram[i] = 200

        var decision = controller.evaluate(histogram, 1600, 40_000_000L, emptyList(), 0.5f, -6, 6)
        repeat(15) {
            decision = controller.evaluate(histogram, 1600, 40_000_000L, emptyList(), 0.5f, -6, 6)
        }

        assertEquals("LOW_LIGHT_CLEAN", decision.sceneTag)
        assertEquals(0.0f, decision.targetEv, 0.05f)
        assertEquals(0, decision.targetIndex)
    }

    @Test
    fun evaluate_backlitPortrait_balancesFaceAndBackground() {
        // Backlit scene with human face detected
        val histogram = IntArray(256)
        for (i in 4..63) histogram[i] = 200
        for (i in 220..251) histogram[i] = 200

        val faces = listOf(FaceRegion(RectF(0.4f, 0.4f, 0.6f, 0.6f), 0.95f))

        var decision = controller.evaluate(histogram, 200, 15_000_000L, faces, 0.5f, -6, 6)
        repeat(20) {
            decision = controller.evaluate(histogram, 200, 15_000_000L, faces, 0.5f, -6, 6)
        }

        assertEquals("BACKLIT_PORTRAIT_BALANCED", decision.sceneTag)
        assertTrue(decision.targetEv < 0.0f)
    }
}
