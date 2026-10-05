package com.psvita.lockscreen

import com.psvita.lockscreen.data.ClockPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockPreferencesTest {

    @Test
    fun testClockPositionMapping() {
        assertEquals(ClockPosition.BOTTOM_LEFT, ClockPosition.fromId(0))
        assertEquals(ClockPosition.TOP_LEFT, ClockPosition.fromId(1))
        assertEquals(ClockPosition.BOTTOM_RIGHT, ClockPosition.fromId(2))
        // Invalid id fallback to BOTTOM_LEFT
        assertEquals(ClockPosition.BOTTOM_LEFT, ClockPosition.fromId(999))
    }

    @Test
    fun testDateTimeFormattingFormatsProperly() {
        val now = Date()
        val format24 = SimpleDateFormat("HH:mm", Locale.US).format(now)
        val format12 = SimpleDateFormat("hh:mm", Locale.US).format(now)

        assertEquals(5, format24.length)
        assertEquals(5, format12.length)
        assertEquals(':', format24[2])
        assertEquals(':', format12[2])
    }

    @Test
    fun testCardSizeClampingFormula() {
        fun clamp(size: Int) = size.coerceIn(70, 100)
        assertEquals(70, clamp(50))
        assertEquals(70, clamp(70))
        assertEquals(85, clamp(85))
        assertEquals(88, clamp(88))
        assertEquals(92, clamp(92))
        assertEquals(100, clamp(100))
        assertEquals(100, clamp(120))
    }
}
