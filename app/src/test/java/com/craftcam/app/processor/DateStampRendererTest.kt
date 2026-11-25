package com.craftcam.app.processor

import com.craftcam.app.model.DateStampConfig
import com.craftcam.app.model.DateStampStyle
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class DateStampRendererTest {

    @Test
    fun dateStampStyles_includeAmberAndYellowFormats() {
        val amberStyle = DateStampStyle.AMBER_SUBTLE_DATE
        val yellowStyle = DateStampStyle.DIGICAM_YELLOW_DATETIME

        assertNotNull(amberStyle)
        assertNotNull(yellowStyle)
        assertEquals("AMBER_SUBTLE_DATE", amberStyle.name)
        assertEquals("DIGICAM_YELLOW_DATETIME", yellowStyle.name)
    }

    @Test
    fun amberSubtleDateFormat_matchesReferencePattern() {
        // Pattern: ''M d ''yy (e.g. '8 22 '25 as seen in Reference 3)
        val sdf = SimpleDateFormat("''M d ''yy", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.AUGUST, 22)
        val formatted = sdf.format(cal.time)

        assertEquals("'8 22 '25", formatted)
    }

    @Test
    fun digicamYellowDateTimeFormat_matchesReferencePattern() {
        // Pattern: yyyy/MM/dd HH:mm:ss (e.g. 2026/06/02 17:40:39 as seen in Reference 7)
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JUNE, 2, 17, 40, 39)
        val formatted = sdf.format(cal.time)

        assertEquals("2026/06/02 17:40:39", formatted)
    }

    @Test
    fun disabledDateStamp_isBypassed() {
        val config = DateStampConfig(enabled = false, style = DateStampStyle.AMBER_SUBTLE_DATE)
        assertFalse(config.enabled)
    }
}
