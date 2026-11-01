package com.focusclock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.ZoneId

/** One moment the phone has to wake up for. */
private data class Fire(val key: String, val at: Long, val ring: Ring)

/**
 * Every alarm, reminder, snooze and the timer, kept in SharedPreferences and handed to the system's AlarmManager so they
 * go off with the app closed. One instance per process, so the screens and the receivers see the same lists.
 */
class Schedule private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    var alarms by mutableStateOf(Codec.decodeAlarms(prefs.getString("alarms", "").orEmpty()))
        private set
    var reminders by mutableStateOf(Codec.decodeReminders(prefs.getString("reminders", "").orEmpty()))
        private set
    var timer by mutableStateOf(readTimer())
        private set
    private var snoozes = Codec.decodeSnoozes(prefs.getString("snoozes", "").orEmpty())

    fun saveAlarm(a: Alarm) {
        alarms = (alarms.filterNot { it.id == a.id } + a).sortedWith(compareBy({ it.hour }, { it.minute }))
        persist()
    }

    fun deleteAlarm(id: Long) {
        alarms = alarms.filterNot { it.id == id }
        persist()
    }

    fun saveReminder(r: Reminder) {
        reminders = (reminders.filterNot { it.id == r.id } + r).sortedBy { it.at }
        persist()
    }

    fun deleteReminder(id: Long) {
        reminders = reminders.filterNot { it.id == id }
        persist()
    }

    fun startTimer(ms: Long, now: Long = System.currentTimeMillis()) {
        timer = TimerState.start(ms, now)
        persist()
    }

    fun pauseTimer(now: Long = System.currentTimeMillis()) {
        timer = timer?.takeIf { it.running }?.pause(now) ?: return
        persist()
    }

    fun resumeTimer(now: Long = System.currentTimeMillis()) {
        timer = timer?.takeIf { !it.running && !it.done }?.resume(now) ?: return
        persist()
    }

    /** One more minute on a running or paused timer. */
    fun addToTimer(ms: Long, now: Long = System.currentTimeMillis()) {
        val t = timer ?: return
        timer = if (t.running) t.copy(totalMs = t.totalMs + ms, endAt = t.endAt + ms) else t.copy(totalMs = t.totalMs + ms, pausedLeft = t.pausedLeft + ms)
        persist()
    }

    fun clearTimer() {
        timer = null
        persist()
    }

    /** Rings [ring] again in [minutes]. */
    fun snooze(ring: Ring, minutes: Int, now: Long = System.currentTimeMillis()) {
        snoozes = snoozes + Snooze(now, now + minutes * 60_000L, ring.kind, ring.title, ring.text, ring.tone)
        persist()
    }

    /** Called as [key] goes off: switches off a one-time alarm, moves a repeating reminder on, and sets the next wake-up. */
    fun fired(key: String, now: Long = System.currentTimeMillis()) {
        val id = key.drop(1).toLongOrNull()
        when (key.firstOrNull()) {
            'a' -> alarms.find { it.id == id }?.let { if (it.days == 0) alarms = alarms.map { a -> if (a.id == id) a.copy(enabled = false) else a } }
            'r' -> reminders.find { it.id == id }?.let { r ->
                val next = r.after(maxOf(now, r.at), ZoneId.systemDefault())
                reminders = if (next == null) reminders.filterNot { it.id == id } else reminders.map { if (it.id == id) it.copy(at = next) else it }
            }
            's' -> snoozes = snoozes.filterNot { it.id == id }
            't' -> timer = timer?.finished()
        }
        persist()
    }

    /** Sets (or resets) every wake-up. Called after any change, after boot and when the clock or time zone changes. */
    fun reschedule(now: Long = System.currentTimeMillis()) {
        prefs.getStringSet("keys", emptySet()).orEmpty().forEach { key ->
            PendingIntent.getBroadcast(app, 0, intent(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        val fires = fires(now)
        fires.forEach(::set)
        prefs.edit().putStringSet("keys", fires.map { it.key }.toSet()).apply()
    }

    private fun fires(now: Long): List<Fire> {
        val zone = ZoneId.systemDefault()
        // Anything that came due while the phone was off rings a moment from now.
        val soon = now + 3_000
        return buildList {
            alarms.forEach { a -> a.next(now, zone)?.let { add(Fire("a${a.id}", it, Ring("a${a.id}", Kind.Alarm, "Alarm", a.label, a.tone, a.snooze))) } }
            reminders.forEach { r -> add(Fire("r${r.id}", if (r.at <= now) soon else r.at, Ring("r${r.id}", Kind.Reminder, "Reminder", r.text, "", 10))) }
            snoozes.forEach { s -> add(Fire("s${s.id}", maxOf(s.at, soon), Ring("s${s.id}", s.kind, s.title, s.text, s.tone, 10))) }
            timer?.takeIf { it.running }?.let { add(Fire("t", maxOf(it.endAt, soon), Ring("t", Kind.Timer, "Timer", "Time’s up", "", 1))) }
        }
    }

    private fun intent(key: String) = Intent(app, FireReceiver::class.java).setData(Uri.parse("focusclock://fire/$key"))

    private fun set(f: Fire) {
        val pi = PendingIntent.getBroadcast(app, 0, intent(f.key).also { f.ring.writeTo(it) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (f.ring.kind == Kind.Reminder) {
            runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.at, pi) }
                .onFailure { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.at, pi) }
        } else {
            // An alarm-clock alarm is exact, wakes the phone from Doze and needs no extra permission.
            val show = PendingIntent.getActivity(app, 2, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(f.at, show), pi)
        }
    }

    private fun persist() {
        prefs.edit()
            .putString("alarms", Codec.encodeAlarms(alarms))
            .putString("reminders", Codec.encodeReminders(reminders))
            .putString("snoozes", Codec.encodeSnoozes(snoozes))
            .apply {
                val t = timer
                if (t == null) remove("timer_total") else putLong("timer_total", t.totalMs).putLong("timer_end", t.endAt).putLong("timer_left", t.pausedLeft).putBoolean("timer_done", t.done)
            }
            .apply()
        reschedule()
    }

    private fun readTimer(): TimerState? {
        if (!prefs.contains("timer_total")) return null
        return TimerState(prefs.getLong("timer_total", 0), prefs.getLong("timer_end", 0), prefs.getLong("timer_left", 0), prefs.getBoolean("timer_done", false))
    }

    companion object {
        @Volatile private var instance: Schedule? = null

        fun get(context: Context): Schedule = instance ?: synchronized(this) {
            instance ?: Schedule(context).also { instance = it }
        }
    }
}
