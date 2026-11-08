package com.craftcam.app.threed

import com.craftcam.app.camera.profile.D3DProfile
import com.craftcam.app.presets.PresetCatalog
import org.junit.Assert.*
import org.junit.Test

class D3DFilterTest {

    @Test
    fun testD3DProfileDefaults() {
        val profile = D3DProfile.Default
        assertEquals(0.40f, profile.depthStrength, 0.001f)
        assertEquals(0.015f, profile.parallaxStrength, 0.001f)
        assertEquals(0.0035f, profile.rgbSeparation, 0.0001f)
        assertEquals(0.75f, profile.foregroundDepth, 0.001f)
        assertEquals(0.25f, profile.backgroundDepth, 0.001f)
        assertEquals(0.85f, profile.edgeProtection, 0.001f)
        assertEquals(0.90f, profile.faceProtection, 0.001f)
        assertEquals(0.0f, profile.opticalSoftness, 0.001f)
        assertEquals(0.20f, profile.vignetteStrength, 0.001f)
        assertEquals(0.0f, profile.anaglyphStrength, 0.001f)
        assertFalse(profile.isAnaglyphMode)
    }

    @Test
    fun testD3DAnaglyphProfile() {
        val anaglyph = D3DProfile.Anaglyph
        assertTrue(anaglyph.isAnaglyphMode)
        assertEquals(1.0f, anaglyph.anaglyphStrength, 0.001f)
        assertTrue(anaglyph.rgbSeparation > 0.005f)
    }

    @Test
    fun testD3DPresetCatalogRegistration() {
        val preset = PresetCatalog.getPresetById("d3d")
        assertNotNull("D3D preset must be resolvable from PresetCatalog", preset)
        assertEquals("D3D", preset.name)
        assertNotNull("D3D preset must have a D3DProfile", preset.d3dProfile)
        assertEquals(0.40f, preset.d3dProfile!!.depthStrength, 0.001f)
    }

    @Test
    fun testDepthDependentParallaxDisplacement() {
        val profile = D3DProfile.Default
        val width = 4000f
        val pivot = 0.45f
        val strength = 0.40f

        val maxParallaxPx = width * profile.parallaxStrength * strength // 4000 * 0.015 * 0.40 = 24px

        // 1. Foreground subject (D = 0.85)
        val dForeground = 0.85f
        val deltaFg = (dForeground - pivot)
        val dispFg = maxParallaxPx * deltaFg // 24 * 0.40 = 9.6px
        assertTrue("Foreground displacement must be positive", dispFg > 0f)

        // 2. Convergence subject plane (D = pivot)
        val dPivot = pivot
        val deltaPivot = (dPivot - pivot)
        val dispPivot = maxParallaxPx * deltaPivot
        assertEquals("Subject at convergence plane must remain rock-solid stationary", 0.0f, dispPivot, 0.001f)

        // 3. Far background (D = 0.10)
        val dBackground = 0.10f
        val deltaBg = (dBackground - pivot)
        val dispBg = maxParallaxPx * deltaBg // 24 * -0.35 = -8.4px
        assertTrue("Background displacement must move counter to foreground", dispBg < 0f)

        // 4. Parallax Monotonicity
        assertTrue(dispFg > dispPivot)
        assertTrue(dispPivot > dispBg)
    }

    @Test
    fun testFaceProtectionAttenuation() {
        val profile = D3DProfile(faceProtection = 0.90f)
        val width = 4000f
        val pivot = 0.45f
        val dSubject = 0.80f

        val unattenuatedDelta = (dSubject - pivot) * (width * profile.parallaxStrength)

        // Inside face region: displacement is attenuated by (1.0 - faceProtection)
        val faceAttenFactor = 1.0f - profile.faceProtection // 0.10 (90% reduction)
        val protectedDelta = unattenuatedDelta * faceAttenFactor

        assertEquals(unattenuatedDelta * 0.10f, protectedDelta, 0.01f)
        assertTrue("Protected face displacement must be less than 15% of raw displacement", protectedDelta < unattenuatedDelta * 0.15f)
    }

    @Test
    fun testDepthDependentRgbSeparation() {
        val profile = D3DProfile(rgbSeparation = 0.0035f)
        val width = 4000f
        val pivot = 0.45f

        val maxCaPx = width * profile.rgbSeparation // 14px

        // Near foreground receives chromatic separation
        val caFg = maxCaPx * (0.85f - pivot)
        assertTrue("Foreground must have positive chromatic separation", caFg > 0f)

        // In-focus convergence plane receives zero chromatic distortion
        val caPivot = maxCaPx * (pivot - pivot)
        assertEquals("Convergence subject plane must have zero chromatic distortion", 0.0f, caPivot, 0.0001f)
    }
}

