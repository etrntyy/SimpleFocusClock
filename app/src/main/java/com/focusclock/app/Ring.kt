package com.focusclock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What is going off: an alarm, a reminder or the timer. [snooze] is how many minutes "Snooze" waits. */
data class Ring(val key: String, val kind: Kind, val title: String, val text: String, val tone: String, val snooze: Int) {
    fun writeTo(i: Intent): Intent = i.putExtra("key", key).putExtra("kind", kind.name).putExtra("title", title)
        .putExtra("text", text).putExtra("tone", tone).putExtra("snooze", snooze)

    companion object {
        fun from(i: Intent?): Ring? {
            i ?: return null
            val key = i.getStringExtra("key") ?: return null
            val kind = runCatching { Kind.valueOf(i.getStringExtra("kind").orEmpty()) }.getOrNull() ?: return null
            return Ring(key, kind, i.getStringExtra("title").orEmpty(), i.getStringExtra("text").orEmpty(), i.getStringExtra("tone").orEmpty(), i.getIntExtra("snooze", 10))
        }
    }
}

/** What the screens show right now. [ringing] takes the whole screen; [banner] is a reminder over the clock. */
object RingState {
    var ringing by mutableStateOf<Ring?>(null)
    var banner by mutableStateOf<Ring?>(null)

    /** Whether the app is on screen. If it is, a reminder appears on the clock instead of as a notification. */
    @Volatile var visible = false
}

private const val RING_CHANNEL = "ring"
private const val REMINDER_CHANNEL = "reminders"

private fun channels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    // The ringing sound is played by the service on the alarm stream, so this channel stays silent.
    nm.createNotificationChannel(NotificationChannel(RING_CHANNEL, "Alarms and timer", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
    nm.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
}

private fun openApp(context: Context, request: Int) = PendingIntent.getActivity(
    context, request,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("ring", true),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Receives every wake-up from AlarmManager. */
class FireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = Ring.from(intent) ?: return
        Schedule.get(context).fired(ring.key)
        when (ring.kind) {
            Kind.Alarm, Kind.Timer -> context.startForegroundService(ring.writeTo(Intent(context, RingService::class.java)))
            Kind.Reminder -> if (RingState.visible) {
                RingState.banner = ring
                chime(context)
            } else {
                channels(context)
                context.getSystemService(NotificationManager::class.java).notify(
                    ring.key.hashCode(),
                    Notification.Builder(context, REMINDER_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle(ring.text.ifBlank { "Reminder" })
                        .setCategory(Notification.CATEGORY_REMINDER)
                        .setContentIntent(openApp(context, 3))
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
    }

    /** A short sound for a reminder that lands on the clock; it plays on the alarm stream so focus mode lets it through. */
    private fun chime(context: Context) {
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                play()
            }
        }
    }
}

/** Puts the wake-ups back after a restart, an update, or a change of clock or time zone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Schedule.get(context).reschedule()
}

/** Plays an alarm or the timer's end until it is stopped, whether or not the app is open. */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var ring: Ring? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(snooze = false)
            ACTION_SNOOZE -> finish(snooze = true)
            else -> {
                val next = Ring.from(intent)
                if (next == null) stopSelf() else begin(next)
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(next: Ring) {
        ring = next
        channels(this)
        val action = { label: String, what: String, code: Int ->
            Notification.Action.Builder(null, label, PendingIntent.getService(this, code, Intent(this, RingService::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE)).build()
        }
        val n = Notification.Builder(this, RING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(next.title)
            .setContentText(next.text.ifBlank { null })
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(openApp(this, 4), true)
            .setContentIntent(openApp(this, 5))
            .addAction(action("Stop", ACTION_STOP, 6))
            .addAction(action(if (next.kind == Kind.Timer) "Add 1 min" else "Snooze ${next.snooze} min", ACTION_SNOOZE, 7))
            .build()
        startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        RingState.ringing = next
        play(next)
        vibrate()
        handler.removeCallbacksAndMessages(null)
        // Nobody answered: stop after ten minutes rather than ring for ever.
        handler.postDelayed({ finish(snooze = false) }, 10 * 60_000L)
    }

    private fun play(r: Ring) {
        player?.release()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val chosen = r.tone.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = listOfNotNull(chosen, fallback).firstNotNullOfOrNull { uri ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@RingService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
    }

    private fun vibrate() {
        runCatching { getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 600), 0)) }
    }

    private fun finish(snooze: Boolean) {
        val r = ring
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        runCatching { getSystemService(Vibrator::class.java).cancel() }
        ring = null
        RingState.ringing = null
        val schedule = Schedule.get(this)
        if (r != null) {
            if (r.kind == Kind.Timer) {
                schedule.clearTimer()
                if (snooze) schedule.startTimer(60_000L)
            } else if (snooze) {
                schedule.snooze(r, r.snooze)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        RingState.ringing = null
        super.onDestroy()
    }

    companion object {
        private const val ID = 4200
        const val ACTION_STOP = "com.focusclock.app.STOP"
        const val ACTION_SNOOZE = "com.focusclock.app.SNOOZE"

        /** Called by the ringing screen's buttons. */
        fun stop(context: Context) = context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP))

        fun snooze(context: Context) = context.startService(Intent(context, RingService::class.java).setAction(ACTION_SNOOZE))
    }
}
