package com.craftcam.app.color

import org.junit.Assert.*
import org.junit.Test

class NaturalColorProfileTest {

    @Test
    fun s21CalibratedProfile_hasCleanNaturalFlagshipParameters() {
        val profile = NaturalColorProfile.S21_CALIBRATED

        assertEquals(1.00f, profile.saturation, 0.001f)
        assertEquals(1.02f, profile.contrast, 0.001f)
        assertEquals(0.12f, profile.vibrance, 0.001f)
        assertEquals(0.68f, profile.highlightRollOffKnee, 0.001f)
        assertEquals(0.32f, profile.highlightRollOffFactor, 0.001f)
        assertEquals(0.14f, profile.shadowLift, 0.001f)
        assertEquals(0.90f, profile.greenTamingFactor, 0.001f)
        assertEquals(1.02f, profile.skyCobaltFactor, 0.001f)
        assertEquals(0.88f, profile.skinProtectionStrength, 0.001f)
        assertEquals(26.0f, profile.skinHueCenter, 0.001f)
        assertEquals(0.18f, profile.sharpeningStrength, 0.001f)
        assertEquals(0.35f, profile.sharpeningSkinDampener, 0.001f)
        assertTrue(profile.enableDithering)
    }

    @Test
    fun s21SensorMatrix_neutralizesYellowGreenBias() {
        val matrix = NaturalColorProfile.S21_SENSOR_MATRIX

        // Red channel slightly boosted to counter green/yellow bias
        assertTrue("Red gain should be > 1.0", matrix.m00 > 1.0f)
        // Green channel gently checked
        assertEquals(1.016f, matrix.m11, 0.001f)
        // Row sums close to 1.0 for photometric conservation
        val row0Sum = matrix.m00 + matrix.m01 + matrix.m02
        val row1Sum = matrix.m10 + matrix.m11 + matrix.m12
        val row2Sum = matrix.m20 + matrix.m21 + matrix.m22

        assertEquals(1.0f, row0Sum, 0.01f)
        assertEquals(1.0f, row1Sum, 0.01f)
        assertEquals(1.0f, row2Sum, 0.01f)
    }

    @Test
    fun highlightRollOff_preservesLinearBelowKneeAndCompressesAbove() {
        val knee = 0.68f
        val factor = 0.32f

        // Below knee: linear identity
        val below = HighlightRollOff.apply(0.50f, knee, factor)
        assertEquals(0.50f, below, 0.001f)

        // At knee: identity
        val atKnee = HighlightRollOff.apply(knee, knee, factor)
        assertEquals(knee, atKnee, 0.001f)

        // Above knee: continuous asymptotic compression < 1.0
        val above1 = HighlightRollOff.apply(0.85f, knee, factor)
        assertTrue("Rolled off value must be < input", above1 < 0.85f)
        assertTrue("Rolled off value must be > knee", above1 > knee)

        // Extreme highlight: asymptotically bounds to 1.0
        val extreme = HighlightRollOff.apply(2.0f, knee, factor)
        assertTrue("Extreme highlight must not exceed 1.0", extreme <= 1.0f)
    }
}

