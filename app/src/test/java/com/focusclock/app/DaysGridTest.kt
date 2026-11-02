package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DaysGridTest {
    private val zone = ZoneId.of("Europe/London")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2025, 11, 5, 12, 0) // a Wednesday

    @Test
    fun noFocusIsTheFaintestGreyAndSixHoursIsWhite() {
        assertEquals(22f / 255f, FocusStats.heat(0.0), 1e-6f)
        assertEquals(1f, FocusStats.heat(360.0), 1e-6f)
        assertEquals(1f, FocusStats.heat(1_000.0), 1e-6f)
    }

    @Test
    fun theGreyRisesEvenlyWithNoStepNearSixHours() {
        var last = -1f
        for (m in 0..360 step 15) {
            val h = FocusStats.heat(m.toDouble())
            assertTrue(h > last)
            last = h
        }
        // A quarter of an hour short of six hours is within four per cent of white.
        assertTrue(1f - FocusStats.heat(345.0) < 0.04f)
    }

    @Test
    fun theGridStartsOnAMondayAndEndsToday() {
        val grid = FocusStats.dayGrid(emptyList(), now, zone)
        assertEquals(DayOfWeek.MONDAY, grid.first.dayOfWeek)
        assertEquals(LocalDate.of(2025, 11, 5), grid.last)
        assertEquals(26, grid.columns)
    }

    @Test
    fun aLongHistoryWidensTheGrid() {
        val session = Session(at(2024, 1, 10, 9, 0), at(2024, 1, 10, 10, 0))
        val grid = FocusStats.dayGrid(listOf(session), now, zone)
        assertEquals(LocalDate.of(2024, 1, 8), grid.first)
        assertTrue(grid.columns > 80)
    }

    @Test
    fun eachSessionLandsOnItsOwnDay() {
        val sessions = listOf(
            Session(at(2025, 11, 3, 9, 0), at(2025, 11, 3, 10, 30)),
            Session(at(2025, 11, 5, 8, 0), at(2025, 11, 5, 8, 45)),
            Session(at(2025, 11, 5, 11, 0), at(2025, 11, 5, 11, 15)),
        )
        val grid = FocusStats.dayGrid(sessions, now, zone)
        val monday = grid.minutes.size - 3
        assertEquals(90.0, grid.minutes[monday], 1e-6)
        assertEquals(0.0, grid.minutes[monday + 1], 1e-6)
        assertEquals(60.0, grid.minutes[monday + 2], 1e-6)
    }

    @Test
    fun aSessionAcrossMidnightIsSplitBetweenTheTwoDays() {
        val grid = FocusStats.dayGrid(listOf(Session(at(2025, 11, 4, 23, 30), at(2025, 11, 5, 0, 45))), now, zone)
        val n = grid.minutes.size
        assertEquals(30.0, grid.minutes[n - 2], 1e-6)
        assertEquals(45.0, grid.minutes[n - 1], 1e-6)
    }

    @Test
    fun theLightThemeFlipsOnlyWhatItShould() {
        // Dark app theme: everything dark unless the clock alone is set to light.
        assertFalse(isLight(AppTheme.Dark, ClockTheme.SameAsApp, systemDark = false, clockScreen = false))
        assertTrue(isLight(AppTheme.Dark, ClockTheme.Light, systemDark = false, clockScreen = true))
        assertFalse(isLight(AppTheme.Dark, ClockTheme.Light, systemDark = false, clockScreen = false))
        // Light app theme: the clock follows it, unless set to dark.
        assertTrue(isLight(AppTheme.Light, ClockTheme.SameAsApp, systemDark = true, clockScreen = true))
        assertFalse(isLight(AppTheme.Light, ClockTheme.Dark, systemDark = true, clockScreen = true))
        assertTrue(isLight(AppTheme.Light, ClockTheme.Dark, systemDark = true, clockScreen = false))
        // System theme follows the phone.
        assertTrue(isLight(AppTheme.System, ClockTheme.SameAsApp, systemDark = false, clockScreen = false))
        assertFalse(isLight(AppTheme.System, ClockTheme.SameAsApp, systemDark = true, clockScreen = true))
    }
}
