package com.focusclock.app

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One stretch of time the clock was open, in epoch milliseconds. */
data class Session(val start: Long, val end: Long) {
    val minutes get() = (end - start) / 60_000.0
}

enum class Period(val tab: String) { Day("Day"), Week("Week"), Month("Month"), Year("Year"), All("All") }

/** One bar of a chart: what it is called and how many minutes of focus it holds. */
data class Bar(val label: String, val minutes: Double)

/** Everything the statistics screen shows for one period. */
data class Report(
    val period: Period,
    val bars: List<Bar>,
    /** Index of the bar that holds the present moment. */
    val current: Int,
    val totalMinutes: Double,
    val sessions: Int,
    val longestMinutes: Double,
    val thirdValue: String,
    val thirdLabel: String,
)

object FocusStats {
    /** Sessions shorter than this are taps, not focus. */
    const val MIN_SESSION_MS = 10_000L

    fun report(period: Period, sessions: List<Session>, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): Report {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstYear = sessions.minOfOrNull { Instant.ofEpochMilli(it.start).atZone(zone).year } ?: today.year
        val starts: List<LocalDate>
        val ends: List<LocalDate>
        val labels: List<String>
        val current: Int
        when (period) {
            Period.Day -> {
                starts = emptyList(); ends = emptyList()
                labels = (0..23).map(::hourRange)
                current = Instant.ofEpochMilli(now).atZone(zone).hour
            }
            Period.Week -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                starts = (0..6).map { monday.plusDays(it.toLong()) }
                ends = starts.map { it.plusDays(1) }
                labels = starts.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
                current = today.dayOfWeek.value - 1
            }
            Period.Month -> {
                val first = today.withDayOfMonth(1)
                starts = (0 until today.lengthOfMonth()).map { first.plusDays(it.toLong()) }
                ends = starts.map { it.plusDays(1) }
                labels = starts.map { it.dayOfMonth.toString() }
                current = today.dayOfMonth - 1
            }
            Period.Year -> {
                starts = (1..12).map { LocalDate.of(today.year, it, 1) }
                ends = starts.map { it.plusMonths(1) }
                labels = starts.map { it.month.getDisplayName(TextStyle.SHORT, locale) }
                current = today.monthValue - 1
            }
            Period.All -> {
                starts = (firstYear..today.year).map { LocalDate.of(it, 1, 1) }
                ends = starts.map { it.plusYears(1) }
                labels = starts.map { it.year.toString() }
                current = starts.lastIndex
            }
        }
        val edges = if (period == Period.Day) {
            val midnight = today.atStartOfDay()
            (0..24).map { midnight.plusHours(it.toLong()).atZone(zone).toInstant().toEpochMilli() }
        } else {
            starts.map { it.atStartOfDay(zone).toInstant().toEpochMilli() } + ends.last().atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val minutes = buckets(sessions, edges)
        val total = minutes.sum()
        val from = edges.first()
        val to = edges.last()
        val inside = sessions.filter { overlap(it, from, to) > 0 }
        val longest = inside.maxOfOrNull { overlap(it, from, to) / 60_000.0 } ?: 0.0
        val elapsed = (current + 1).toDouble()
        val third = when (period) {
            Period.Day -> (if (total > 0) hourLabel(minutes.indices.maxBy { minutes[it] }) else "–") to "Peak hour"
            Period.Week, Period.Month -> duration(total / elapsed) to "Daily avg"
            Period.Year -> duration(total / elapsed) to "Monthly avg"
            Period.All -> duration(total / minutes.size) to "Yearly avg"
        }
        return Report(period, labels.mapIndexed { i, l -> Bar(l, minutes[i]) }, current, total, inside.size, longest, third.first, third.second)
    }

    /** Minutes of [sessions] inside each bucket; [edges] has one more entry than there are buckets. */
    fun buckets(sessions: List<Session>, edges: List<Long>): DoubleArray {
        val out = DoubleArray(edges.size - 1)
        for (s in sessions) {
            for (i in out.indices) {
                if (edges[i] >= s.end) break
                out[i] += overlap(s, edges[i], edges[i + 1]) / 60_000.0
            }
        }
        return out
    }

    private fun overlap(s: Session, from: Long, to: Long) = max(0L, min(s.end, to) - max(s.start, from))

    /** "10–11am", "11am–12pm", "12–1pm": the hour that starts at [h]. */
    fun hourRange(h: Int): String {
        val e = (h + 1) % 24
        fun n(x: Int) = if (x % 12 == 0) 12 else x % 12
        fun m(x: Int) = if (x < 12) "am" else "pm"
        return if (m(h) == m(e)) "${n(h)}–${n(e)}${m(e)}" else "${n(h)}${m(h)}–${n(e)}${m(e)}"
    }

    fun hourLabel(h: Int): String = (if (h % 12 == 0) 12 else h % 12).toString() + if (h < 12) "am" else "pm"

    /** How much of each lap of the Start ring is filled for [fraction] of the daily target; 1.0 is one full lap. */
    fun laps(fraction: Double): List<Float> {
        if (!(fraction > 0)) return emptyList()
        val n = minOf(kotlin.math.ceil(fraction).toInt(), 12)
        return (0 until n).map { k -> (fraction - k).coerceIn(0.0, 1.0).toFloat() }
    }

    /** The shade of lap [k]: white for the first, then a very slightly greyer white each time round. */
    fun lapGrey(k: Int): Float = max(120, 255 - 25 * k) / 255f

    /** "12h 40m", "2h 05m", "55m". */
    fun duration(minutes: Double): String {
        val m = minutes.roundToInt()
        return if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
    }

    /** The small figure above a bar: minutes under an hour, otherwise hours to one decimal. */
    fun barValue(minutes: Double, perHour: Boolean): String {
        val m = minutes.roundToInt()
        if (m <= 0) return ""
        if (perHour || m < 60) return "${m}m"
        val h = (minutes / 60 * 10).roundToInt() / 10.0
        return if (h == h.toInt().toDouble()) "${h.toInt()}h" else "$h" + "h"
    }

    /** A target as the settings show it: "Off", "45m", "2h", "1h 15m". */
    fun target(minutes: Int): String = when {
        minutes <= 0 -> "Off"
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** How far a target's + button steps, in minutes. */
fun targetStep(period: Period) = when (period) {
    Period.Day -> 15
    Period.Week -> 60
    Period.Month -> 300
    else -> 3_000
}

fun maxTarget(period: Period) = when (period) {
    Period.Day -> 24 * 60
    Period.Week -> 7 * 24 * 60
    Period.Month -> 31 * 24 * 60
    else -> 366 * 24 * 60
}
