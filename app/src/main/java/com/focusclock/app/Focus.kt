package com.focusclock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context

/** Focus mode: pins the app (no home, recents or notification shade) and silences notifications. */
class Focus(private val activity: Activity) {
    private val nm = activity.getSystemService(NotificationManager::class.java)
    private val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var savedFilter: Int? = null

    val canSilence get() = nm.isNotificationPolicyAccessGranted
    val pinned get() = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun enter() {
        if (canSilence && savedFilter == null) {
            savedFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        }
        activity.startLockTask()
    }

    fun exit() {
        if (pinned) activity.stopLockTask()
        savedFilter?.let { if (canSilence) nm.setInterruptionFilter(it) }
        savedFilter = null
    }
}
