package com.focusclock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TimeToolsTest {
    private val zone = ZoneId.of("Europe/London")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // 3 November 2025 is a Monday.
    private val monday7am = at(2025, 11, 3, 7, 0)

    @Test
    fun aOneTimeAlarmRingsTodayIfStillAhead() {
        val alarm = Alarm(1, 8, 30)
        assertEquals(at(2025, 11, 3, 8, 30), alarm.next(monday7am, zone))
    }

    @Test
    fun aOneTimeAlarmThatHasPassedRingsTomorrow() {
        val alarm = Alarm(1, 6, 30)
        assertEquals(at(2025, 11, 4, 6, 30), alarm.next(monday7am, zone))
    }

    @Test
    fun anAlarmRightNowWaitsUntilTomorrow() {
        assertEquals(at(2025, 11, 4, 7, 0), Alarm(1, 7, 0).next(monday7am, zone))
    }

    @Test
    fun aWeekdayAlarmSkipsTheWeekend() {
        val friday9pm = at(2025, 11, 7, 21, 0)
        val alarm = Alarm(1, 6, 30, days = 31)
        assertEquals(at(2025, 11, 10, 6, 30), alarm.next(friday9pm, zone))
    }

    @Test
    fun aWeeklyAlarmOnTheSameWeekdayWaitsAWeek() {
        val alarm = Alarm(1, 6, 30, days = 1) // Mondays
        assertEquals(at(2025, 11, 10, 6, 30), alarm.next(monday7am, zone))
    }

    @Test
    fun aSwitchedOffAlarmNeverRings() {
        assertNull(Alarm(1, 8, 0, enabled = false).next(monday7am, zone))
    }

    @Test
    fun anAlarmKeepsItsClockTimeAcrossTheClocksChanging() {
        // The clocks go back on 26 October 2025: 6:30 is still 6:30 the next morning, 25 hours on.
        val saturday = at(2025, 10, 25, 7, 0)
        assertEquals(at(2025, 10, 26, 6, 30), Alarm(1, 6, 30).next(saturday, zone))
    }

    @Test
    fun aReminderThatIsStillAheadKeepsItsTime() {
        val r = Reminder(1, "Call", at(2025, 11, 3, 15, 0))
        assertEquals(r.at, r.after(monday7am, zone))
    }

    @Test
    fun aReminderThatHasPassedAndDoesNotRepeatIsOver() {
        assertNull(Reminder(1, "Call", at(2025, 11, 3, 6, 0)).after(monday7am, zone))
    }

    @Test
    fun repeatingRemindersMoveOnByTheirPeriod() {
        val base = at(2025, 11, 3, 6, 0)
        assertEquals(at(2025, 11, 4, 6, 0), Reminder(1, "", base, Repeat.Daily).after(monday7am, zone))
        assertEquals(at(2025, 11, 10, 6, 0), Reminder(1, "", base, Repeat.Weekly).after(monday7am, zone))
        assertEquals(at(2025, 12, 3, 6, 0), Reminder(1, "", base, Repeat.Monthly).after(monday7am, zone))
    }

    @Test
    fun aMonthlyReminderOnThe31stDoesNotDriftShort() {
        val jan31 = at(2025, 1, 31, 9, 0)
        val r = Reminder(1, "Rent", jan31, Repeat.Monthly)
        assertEquals(at(2025, 2, 28, 9, 0), r.after(at(2025, 2, 1, 0, 0), zone))
        assertEquals(at(2025, 3, 31, 9, 0), r.after(at(2025, 3, 1, 0, 0), zone))
    }

    @Test
    fun theTimerCountsDownPausesAndResumes() {
        val t = TimerState.start(60_000, 1_000)
        assertEquals(60_000, t.left(1_000))
        assertEquals(35_000, t.left(26_000))
        val paused = t.pause(26_000)
        assertEquals(35_000, paused.left(500_000))
        val resumed = paused.resume(600_000)
        assertEquals(15_000, resumed.left(620_000))
        assertEquals(0, resumed.left(700_000))
    }

    @Test
    fun theStopwatchKeepsTimeAcrossPauses() {
        val s = Stopwatch().start(1_000).pause(4_000)
        assertEquals(3_000, s.elapsed(99_000))
        val again = s.start(10_000)
        assertEquals(5_000, again.elapsed(12_000))
    }

    @Test
    fun lapsAreWorkedOutFromRunningTotals() {
        val s = Stopwatch().start(1_000).lap(31_000).lap(76_000).lap(81_000)
        assertEquals(listOf(30_000L, 45_000L, 5_000L), s.splits())
    }

    @Test
    fun countdownRoundsUpAndAddsHours() {
        assertEquals("00:01", TimeFormat.countdown(1))
        assertEquals("24:08", TimeFormat.countdown(24 * 60_000L + 8_000))
        assertEquals("1:05:09", TimeFormat.countdown(3_909_000))
        assertEquals("00:00", TimeFormat.countdown(0))
    }

    @Test
    fun stopwatchReadsSecondsAndHundredths() {
        assertEquals("02:41" to "37", TimeFormat.stopwatch(161_370))
        assertEquals("1:02:03" to "05", TimeFormat.stopwatch(3_723_050))
    }

    @Test
    fun alarmTimesReadInBothClocks() {
        assertEquals("6:30" to "am", TimeFormat.alarmTime(6, 30, false))
        assertEquals("12:05" to "am", TimeFormat.alarmTime(0, 5, false))
        assertEquals("12:00" to "pm", TimeFormat.alarmTime(12, 0, false))
        assertEquals("9:45" to "pm", TimeFormat.alarmTime(21, 45, false))
        assertEquals("21:45" to "", TimeFormat.alarmTime(21, 45, true))
    }

    @Test
    fun repeatDaysReadNaturally() {
        assertEquals("Once", TimeFormat.days(0))
        assertEquals("Every day", TimeFormat.days(127))
        assertEquals("Mon–Fri", TimeFormat.days(31))
        assertEquals("Sat, Sun", TimeFormat.days(96))
        assertEquals("Mon, Wed", TimeFormat.days(5))
    }

    @Test
    fun whenTextNamesTodayAndTomorrow() {
        val now = at(2025, 11, 3, 7, 0)
        assertEquals("Today, 3:00 pm", TimeFormat.whenText(at(2025, 11, 3, 15, 0), now, zone, false, Locale.UK))
        assertEquals("Tomorrow, 09:00", TimeFormat.whenText(at(2025, 11, 4, 9, 0), now, zone, true, Locale.UK))
        assertEquals("Mon 1 Dec, 9:00 am", TimeFormat.whenText(at(2025, 12, 1, 9, 0), now, zone, false, Locale.UK))
    }

    @Test
    fun savedItemsComeBackExactlyIncludingAwkwardText() {
        val alarms = listOf(Alarm(1, 6, 30, 31, "Gym\tand\nrun \\ now", true, 15, "content://media/x"), Alarm(2, 23, 59, 0, "", false))
        assertEquals(alarms, Codec.decodeAlarms(Codec.encodeAlarms(alarms)))
        val reminders = listOf(Reminder(3, "Call the dentist", 1_700_000_000_000, Repeat.Weekly), Reminder(4, "", 5, Repeat.Never))
        assertEquals(reminders, Codec.decodeReminders(Codec.encodeReminders(reminders)))
        val snoozes = listOf(Snooze(5, 99, Kind.Alarm, "Alarm", "Gym", "tone"))
        assertEquals(snoozes, Codec.decodeSnoozes(Codec.encodeSnoozes(snoozes)))
    }

    @Test
    fun damagedLinesAreSkipped() {
        assertEquals(listOf(Alarm(1, 6, 30)), Codec.decodeAlarms("garbage\n" + Codec.encodeAlarms(listOf(Alarm(1, 6, 30))) + "\n1\t2"))
    }
}
