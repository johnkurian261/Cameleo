package com.craftcam.app.color.profile

import com.craftcam.app.config.filmColorProfileAdjustment
import org.junit.Assert.*
import org.junit.Test

class DazzColorProfileTest {

    @Test
    fun catalogContainsAllTenAuthenticProfiles() {
        val profiles = DazzColorProfileCatalog.PROFILES
        assertEquals(10, profiles.size)

        val expectedIds = listOf("03G", "C400", "07FF", "19P", "19P2", "19M", "22M", "EG200", "FX33", "FXN2")
        val expectedNames = listOf("03 G", "C400", "07 FF", "19 P", "19 P2", "19 M", "22 M", "EG200", "FX33", "FXN2")

        assertEquals(expectedIds, profiles.map { it.id })
        assertEquals(expectedNames, profiles.map { it.displayName })
    }

    @Test
    fun c400IsNeutralDaylightBaseline() {
        val c400 = DazzColorProfileCatalog.getByIndex(1)
        assertEquals("C400", c400.displayName)
        assertTrue(c400.hasRainbow)
        assertFalse(c400.isMonochrome)
        assertEquals(0f, c400.adjustment.tempDelta, 0.001f)
        assertEquals(0f, c400.adjustment.tintDelta, 0.001f)
        assertEquals(1.0f, c400.adjustment.contrastMult, 0.001f)
        assertEquals(1.0f, c400.adjustment.satMult, 0.001f)
    }

    @Test
    fun twentyTwoMIsMonochromeCinemaFilm() {
        val bw = DazzColorProfileCatalog.getById("22M")
        assertEquals("22 M", bw.displayName)
        assertTrue(bw.isMonochrome)
        assertEquals(0.0f, bw.adjustment.satMult, 0.0001f)
        assertTrue(bw.adjustment.contrastMult > 1.0f)
    }

    @Test
    fun nineteenSeriesTungstenDifferentiatesPrintFromMonitor() {
        val p = DazzColorProfileCatalog.getById("19P")
        val p2 = DazzColorProfileCatalog.getById("19P2")
        val m = DazzColorProfileCatalog.getById("19M")

        // 19 P has magenta print tones (positive tintDelta) and tungsten coolness (negative tempDelta)
        assertTrue("19 P must lean tungsten", p.adjustment.tempDelta < 0f)
        assertTrue("19 P must lean magenta print", p.adjustment.tintDelta > 0f)

        // 19 M is monitor-based, warm-leaning
        assertTrue("19 M must be warm-leaning", m.adjustment.tempDelta > 0f)

        // All three have distinct adjustments
        assertNotEquals(p.adjustment, p2.adjustment)
        assertNotEquals(p.adjustment, m.adjustment)
    }

    @Test
    fun eg200HasDistinctGreenShadowsAndDaylightSaturation() {
        val eg = DazzColorProfileCatalog.getById("EG200")
        assertEquals("EG200", eg.displayName)
        assertTrue("EG200 has green tint in shadows", eg.adjustment.tintDelta < 0f)
        assertTrue("EG200 has daylight warmth", eg.adjustment.tempDelta > 0f)
        assertTrue("EG200 has vibrant colors", eg.adjustment.satMult > 1.0f)
    }

    @Test
    fun catalogClampingAndLookupByIdAreRobust() {
        // Out of bounds indices clamp safely
        assertEquals("03 G", DazzColorProfileCatalog.getByIndex(-1).displayName)
        assertEquals("FXN2", DazzColorProfileCatalog.getByIndex(999).displayName)

        // Case-insensitive ID lookup
        assertEquals("07 FF", DazzColorProfileCatalog.getById("07ff").displayName)
        assertEquals("C400", DazzColorProfileCatalog.getById("non_existent").displayName)
    }

    @Test
    fun filmColorProfileAdjustmentDelegatesSeamlessly() {
        for (i in 0 until 10) {
            val adj = filmColorProfileAdjustment(i)
            assertEquals(DazzColorProfileCatalog.getByIndex(i).adjustment, adj)
        }
    }
}
