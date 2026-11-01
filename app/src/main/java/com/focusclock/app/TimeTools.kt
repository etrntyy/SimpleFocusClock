package com.focusclock.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

enum class Kind { Alarm, Reminder, Timer }

/**
 * An alarm that rings at [hour]:[minute]. [days] has one bit per weekday, Monday as bit 0; none set means it rings once
 * and then switches itself off.
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val snooze: Int = 10,
    val tone: String = "",
) {
    /** The next moment after [after] it rings, or null when it is off. */
    fun next(after: Long, zone: ZoneId): Long? {
        if (!enabled) return null
        val first = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (i in 0L..7L) {
            val day = first.plusDays(i)
            if (days != 0 && days and (1 shl (day.dayOfWeek.value - 1)) == 0) continue
            val t = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            if (t > after) return t
        }
        return null
    }
}

enum class Repeat(val label: String) { Never("Never"), Daily("Every day"), Weekly("Every week"), Monthly("Every month") }

/** A note that pops up on the clock at [at] (epoch milliseconds) and then repeats, or goes away. */
data class Reminder(val id: Long, val text: String, val at: Long, val repeat: Repeat = Repeat.Never) {
    /** [at] while it is still ahead; otherwise the next repeat after [now], or null if it does not repeat. */
    fun after(now: Long, zone: ZoneId): Long? {
        if (at > now) return at
        if (repeat == Repeat.Never) return null
        val base = Instant.ofEpochMilli(at).atZone(zone)
        for (k in 1L..20_000L) {
            val t = when (repeat) {
                Repeat.Daily -> base.plusDays(k)
                Repeat.Weekly -> base.plusWeeks(k)
                else -> base.plusMonths(k)
            }.toInstant().toEpochMilli()
            if (t > now) return t
        }
        return null
    }
}

/** Something that rings again later: a snoozed alarm or reminder. */
data class Snooze(val id: Long, val at: Long, val kind: Kind, val title: String, val text: String, val tone: String)

/** A countdown. Running when [endAt] is set, paused with [pausedLeft] on the clock, and [done] once it has rung. */
data class TimerState(val totalMs: Long, val endAt: Long = 0, val pausedLeft: Long = 0, val done: Boolean = false) {
    val running get() = endAt > 0
    fun left(now: Long) = if (running) max(0L, endAt - now) else pausedLeft
    fun pause(now: Long) = TimerState(totalMs, 0, left(now))
    fun resume(now: Long) = TimerState(totalMs, now + pausedLeft)
    fun finished() = TimerState(totalMs, 0, 0, done = true)

    companion object {
        fun start(ms: Long, now: Long) = TimerState(ms, now + ms)
    }
}

/** A stopwatch driven by a monotonic clock: [base] is time already counted, [startedAt] when it last started running. */
data class Stopwatch(val base: Long = 0, val startedAt: Long = 0, val laps: List<Long> = emptyList()) {
    val running get() = startedAt > 0
    fun elapsed(now: Long) = base + if (running) now - startedAt else 0L
    fun start(now: Long) = copy(startedAt = max(1L, now))
    fun pause(now: Long) = copy(base = elapsed(now), startedAt = 0)
    fun lap(now: Long) = copy(laps = laps + elapsed(now))

    /** Each lap's own length, oldest first. */
    fun splits() = laps.mapIndexed { i, t -> t - (laps.getOrNull(i - 1) ?: 0L) }
}

/** The text each of the time tools shows. */
object TimeFormat {
    /** "24:08", or "1:05:09" from an hour up. Rounds up, so the last second still reads 00:01. */
    fun countdown(ms: Long): String {
        val s = ceil(ms / 1000.0).toLong()
        val h = s / 3600
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, s % 3600 / 60, s % 60) else "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
    }

    /** The stopwatch as whole seconds and hundredths: ("02:41", "37"). */
    fun stopwatch(ms: Long): Pair<String, String> {
        val s = ms / 1000
        val h = s / 3600
        val main = if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, s % 3600 / 60, s % 60) else "%02d:%02d".format(Locale.ROOT, s / 60, s % 60)
        return main to "%02d".format(Locale.ROOT, ms % 1000 / 10)
    }

    /** ("6:30", "am") or, in 24-hour time, ("06:30", ""). */
    fun alarmTime(hour: Int, minute: Int, use24h: Boolean): Pair<String, String> {
        if (use24h) return "%02d:%02d".format(Locale.ROOT, hour, minute) to ""
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d".format(Locale.ROOT, h, minute) to if (hour < 12) "am" else "pm"
    }

    fun time(hour: Int, minute: Int, use24h: Boolean) = alarmTime(hour, minute, use24h).let { (t, ap) -> if (ap.isEmpty()) t else "$t $ap" }

    private val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Once", "Every day", "Mon–Fri", "Sat, Sun", "Mon, Wed". */
    fun days(mask: Int): String = when {
        mask and 127 == 0 -> "Once"
        mask == 127 -> "Every day"
        mask == 31 -> "Mon–Fri"
        else -> names.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
    }

    /** "Today, 3:00 pm", "Tomorrow, 9:00 am", "Mon 3 Nov, 9:00 am". */
    fun whenText(at: Long, now: Long, zone: ZoneId, use24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (t.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> t.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        }
        return "$day, ${time(t.hour, t.minute, use24h)}"
    }

    fun date(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }
}

/** Saves the alarms, reminders and snoozes as plain text, one tab-separated line each. */
object Codec {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun lines(text: String) = text.lineSequence().filter { it.isNotBlank() }

    fun encodeAlarms(all: List<Alarm>) = all.joinToString("\n") {
        listOf(it.id, it.hour, it.minute, it.days, if (it.enabled) 1 else 0, it.snooze, esc(it.tone), esc(it.label)).joinToString("\t")
    }

    fun decodeAlarms(text: String): List<Alarm> = lines(text).mapNotNull { line ->
        val p = line.split('\t')
        runCatching { Alarm(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), unesc(p[7]), p[4] == "1", p[5].toInt(), unesc(p[6])) }.getOrNull()
    }.toList()

    fun encodeReminders(all: List<Reminder>) = all.joinToString("\n") { listOf(it.id, it.at, it.repeat.name, esc(it.text)).joinToString("\t") }

    fun decodeReminders(text: String): List<Reminder> = lines(text).mapNotNull { line ->
        val p = line.split('\t')
        runCatching { Reminder(p[0].toLong(), unesc(p[3]), p[1].toLong(), Repeat.valueOf(p[2])) }.getOrNull()
    }.toList()

    fun encodeSnoozes(all: List<Snooze>) = all.joinToString("\n") { listOf(it.id, it.at, it.kind.name, esc(it.title), esc(it.text), esc(it.tone)).joinToString("\t") }

    fun decodeSnoozes(text: String): List<Snooze> = lines(text).mapNotNull { line ->
        val p = line.split('\t')
        runCatching { Snooze(p[0].toLong(), p[1].toLong(), Kind.valueOf(p[2]), unesc(p[3]), unesc(p[4]), unesc(p[5])) }.getOrNull()
    }.toList()
}
