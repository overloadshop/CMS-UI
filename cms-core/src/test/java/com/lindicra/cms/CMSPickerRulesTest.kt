package com.lindicra.cms

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CMSPickerRulesTest {
    @Test fun wheelCenterIncludesContentPadding() {
        // 五行滚筒，前后各两行 padding。选中行在布局中的 offset 为 0。
        assertEquals(0f, pickerWheelDistance(0, 36, -72, 108), 0f)
        assertEquals(-36f, pickerWheelDistance(-36, 36, -72, 108), 0f)
        assertEquals(36f, pickerWheelDistance(36, 36, -72, 108), 0f)
        // 七行滚筒依然共享同一视觉与吸附中心。
        assertEquals(0f, pickerWheelDistance(0, 36, -108, 144), 0f)
    }

    @Test fun twelveHourSelectionCanReachEveryHourIncludingMidnightAndNoon() {
        for (hour in 0..23) {
            assertEquals(hour, picker24Hour(picker12HourIndex(hour), hour >= 12))
        }
        assertEquals(0, picker24Hour(11, false))
        assertEquals(12, picker24Hour(11, true))
        assertEquals(23, picker24Hour(10, true))
    }

    @Test fun changingMonthAndYearKeepsDayWhenPossibleAndHandlesLeapDays() {
        assertEquals(LocalDate.of(2024, 2, 29),
            pickerDateWithMonth(LocalDate.of(2024, 1, 31), 2024, 2))
        assertEquals(LocalDate.of(2025, 2, 28),
            pickerDateWithMonth(LocalDate.of(2024, 2, 29), 2025, 2))
        assertEquals(LocalDate.of(2026, 4, 30),
            pickerDateWithMonth(LocalDate.of(2026, 3, 31), 2026, 4))
    }

    @Test fun dateRangeHonorsPartialMonthsAndSingleDayRanges() {
        val lower = LocalDate.of(2026, 11, 11)
        val upper = LocalDate.of(2026, 12, 3)
        assertEquals(lower, pickerDateInRange(lower.minusDays(1), lower, upper))
        assertEquals(upper, pickerDateInRange(upper.plusDays(1), lower, upper))
        assertEquals(lower.plusDays(1), pickerDateInRange(lower.plusDays(1), lower, upper))
        assertEquals(lower, pickerDateInRange(upper, lower, lower))
    }

    @Test fun hexInputDistinguishesRgbFromArgbAndRejectsIncompleteValues() {
        assertEquals(0xFF3B82F6.toInt(), pickerColorFromHex("#3b82f6", true)!!.toArgb())
        assertEquals(0x803B82F6.toInt(), pickerColorFromHex("803B82F6", true)!!.toArgb())
        assertNull(pickerColorFromHex("803B82F6", false))
        assertNull(pickerColorFromHex("3B82", true))
        assertNull(pickerColorFromHex("GG82F6", true))
        assertNull(pickerColorFromHex("", true))
    }
}
