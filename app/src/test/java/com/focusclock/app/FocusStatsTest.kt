package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FocusStatsTest {
    private val zone = ZoneId.of("UTC")

    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private fun session(d: LocalDate, h1: Int, m1: Int, h2: Int, m2: Int) = Session(at(d, h1, m1), at(d, h2, m2))
    private fun report(p: Period, s: List<Session>, now: Long) = FocusStats.report(p, s, now, zone, Locale.ENGLISH)

    private val wed = LocalDate.of(2026, 10, 28)

    @Test fun dayHasOneBarPerHourAndSplitsASessionAcrossHours() {
        val r = report(Period.Day, listOf(session(wed, 10, 15, 10, 45), session(wed, 10, 50, 11, 20)), at(wed, 15, 30))
        assertEquals(24, r.bars.size)
        assertEquals(40.0, r.bars[10].minutes, 1e-9)
        assertEquals(20.0, r.bars[11].minutes, 1e-9)
        assertEquals(60.0, r.totalMinutes, 1e-9)
        assertEquals(2, r.sessions)
        assertEquals("10am", r.thirdValue)
        assertEquals(15, r.current)
        assertEquals("10–11am", r.bars[10].label)
    }

    @Test fun aSessionOverMidnightCountsOnBothDays() {
        val s = listOf(Session(at(wed, 23, 30), at(wed.plusDays(1), 0, 30)))
        assertEquals(30.0, report(Period.Day, s, at(wed, 23, 59)).totalMinutes, 1e-9)
        assertEquals(30.0, report(Period.Day, s, at(wed.plusDays(1), 12)).totalMinutes, 1e-9)
        assertEquals(60.0, report(Period.Week, s, at(wed, 12)).totalMinutes, 1e-9)
    }

    @Test fun weekRunsMondayToSundayAndAveragesOverTheDaysSoFar() {
        assertEquals(java.time.DayOfWeek.WEDNESDAY, wed.dayOfWeek)
        val s = listOf(session(wed.minusDays(2), 9, 0, 11, 0), session(wed, 9, 0, 10, 0))
        val r = report(Period.Week, s, at(wed, 12))
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), r.bars.map { it.label })
        assertEquals(120.0, r.bars[0].minutes, 1e-9)
        assertEquals(60.0, r.bars[2].minutes, 1e-9)
        assertEquals(2, r.current)
        assertEquals(180.0, r.totalMinutes, 1e-9)
        assertEquals("1h 00m", r.thirdValue)
        assertEquals("Daily avg", r.thirdLabel)
        assertEquals(120.0, r.longestMinutes, 1e-9)
    }

    @Test fun monthHasOneBarPerDayOfThatMonth() {
        val r = report(Period.Month, listOf(session(wed, 8, 0, 9, 30)), at(wed, 12))
        assertEquals(31, r.bars.size)
        assertEquals(27, r.current)
        assertEquals(90.0, r.bars[27].minutes, 1e-9)
        assertEquals("28", r.bars[27].label)
    }

    @Test fun yearHasTwelveMonthsAndAllHasOneBarPerYearFromTheFirstSession() {
        val old = LocalDate.of(2024, 3, 5)
        val s = listOf(session(old, 8, 0, 9, 0), session(wed, 8, 0, 10, 0))
        val year = report(Period.Year, s, at(wed, 12))
        assertEquals(12, year.bars.size)
        assertEquals(120.0, year.bars[9].minutes, 1e-9)
        assertEquals(120.0, year.totalMinutes, 1e-9)
        val all = report(Period.All, s, at(wed, 12))
        assertEquals(listOf("2024", "2025", "2026"), all.bars.map { it.label })
        assertEquals(listOf(60.0, 0.0, 120.0), all.bars.map { it.minutes })
        assertEquals(180.0, all.totalMinutes, 1e-9)
        assertEquals(2, all.current)
    }

    @Test fun anEmptyHistoryIsAllZeros() {
        for (p in Period.entries) {
            val r = report(p, emptyList(), at(wed, 12))
            assertEquals(0.0, r.totalMinutes, 0.0)
            assertEquals(0, r.sessions)
            assertTrue(r.bars.isNotEmpty())
        }
        assertEquals("–", report(Period.Day, emptyList(), at(wed, 12)).thirdValue)
    }

    @Test fun hourRangesNameBothEnds() {
        assertEquals("12–1am", FocusStats.hourRange(0))
        assertEquals("10–11am", FocusStats.hourRange(10))
        assertEquals("11am–12pm", FocusStats.hourRange(11))
        assertEquals("12–1pm", FocusStats.hourRange(12))
        assertEquals("1–2pm", FocusStats.hourRange(13))
        assertEquals("11pm–12am", FocusStats.hourRange(23))
        assertEquals("12am", FocusStats.hourLabel(0))
        assertEquals("12pm", FocusStats.hourLabel(12))
        assertEquals("3pm", FocusStats.hourLabel(15))
    }

    @Test fun durationsAndFiguresReadTheWayTheScreensShowThem() {
        assertEquals("12h 40m", FocusStats.duration(760.0))
        assertEquals("2h 05m", FocusStats.duration(125.0))
        assertEquals("55m", FocusStats.duration(55.0))
        assertEquals("0m", FocusStats.duration(0.0))
        assertEquals("30m", FocusStats.barValue(30.0, perHour = true))
        assertEquals("", FocusStats.barValue(0.0, perHour = true))
        assertEquals("1.7h", FocusStats.barValue(100.0, perHour = false))
        assertEquals("1h", FocusStats.barValue(60.0, perHour = false))
        assertEquals("45m", FocusStats.barValue(45.0, perHour = false))
        assertEquals("Off", FocusStats.target(0))
        assertEquals("2h", FocusStats.target(120))
        assertEquals("1h 15m", FocusStats.target(75))
        assertEquals("45m", FocusStats.target(45))
    }

    @Test fun theStartRingFillsLapByLapAndGoesSlightlyGreyer() {
        assertEquals(emptyList<Float>(), FocusStats.laps(0.0))
        assertEquals(listOf(0.4f), FocusStats.laps(0.4))
        assertEquals(listOf(1f), FocusStats.laps(1.0))
        assertEquals(listOf(1f, 0.3f), FocusStats.laps(1.3).map { Math.round(it * 10) / 10f })
        assertEquals(listOf(1f, 1f, 0.5f), FocusStats.laps(2.5))
        assertEquals(12, FocusStats.laps(500.0).size)
        assertEquals(1f, FocusStats.lapGrey(0), 0f)
        assertTrue(FocusStats.lapGrey(1) < 1f && FocusStats.lapGrey(1) > 0.85f)
        for (k in 1..30) assertTrue(FocusStats.lapGrey(k) <= FocusStats.lapGrey(k - 1))
        assertTrue(FocusStats.lapGrey(30) >= 0.4f)
    }

    @Test fun targetsStepAndStayWithinTheirLimits() {
        assertEquals(15, targetStep(Period.Day))
        assertEquals(3_000, targetStep(Period.Year))
        assertTrue(maxTarget(Period.Day) >= 24 * 60)
    }

    @Test fun theCsvKeepsEverySessionAndReadsBackUnchanged() {
        val s = listOf(session(wed, 9, 0, 9, 45), Session(at(wed, 23, 30), at(wed.plusDays(1), 0, 30)))
        val text = FocusLog.format(s, zone)
        assertTrue(text.startsWith("start,end,minutes\n"))
        assertEquals(s, FocusLog.parse(text))
        assertTrue(text.contains("45.0"))
    }

    @Test fun theCsvReaderSkipsTheHeaderAndAnyDamagedLine() {
        val text = "start,end,minutes\nnot,a,line\n2026-10-28T09:00:00Z,2026-10-28T09:30:00Z,30.0\n\n"
        val read = FocusLog.parse(text)
        assertEquals(1, read.size)
        assertEquals(30.0, read[0].minutes, 1e-9)
    }
}
