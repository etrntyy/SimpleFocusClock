package com.focusclock.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User options, persisted in SharedPreferences and observable from Compose. */
class ClockSettings(context: Context) {
    private val sp = context.getSharedPreferences("clock", Context.MODE_PRIVATE)

    var focus by mutableStateOf(sp.getBoolean("focus", true))
        private set
    var showDate by mutableStateOf(sp.getBoolean("date", false))
        private set
    var use24h by mutableStateOf(sp.getBoolean("h24", false))
        private set
    var showSeconds by mutableStateOf(sp.getBoolean("seconds", false))
        private set
    var fontIndex by mutableStateOf(sp.getInt("font", 0).coerceIn(ClockFonts.indices))
        private set

    /** Focus targets in minutes; 0 means off. */
    var targetDaily by mutableStateOf(sp.getInt("target_daily", 0))
        private set
    var targetWeekly by mutableStateOf(sp.getInt("target_weekly", 0))
        private set
    var targetMonthly by mutableStateOf(sp.getInt("target_monthly", 0))
        private set
    var targetYearly by mutableStateOf(sp.getInt("target_yearly", 0))
        private set

    /** Whether the one-time system prompts (data folder, Do Not Disturb) have been shown. */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    fun updateTarget(period: Period, minutes: Int) {
        val v = minutes.coerceIn(0, maxTarget(period))
        when (period) {
            Period.Day -> { targetDaily = v; sp.edit().putInt("target_daily", v).apply() }
            Period.Week -> { targetWeekly = v; sp.edit().putInt("target_weekly", v).apply() }
            Period.Month -> { targetMonthly = v; sp.edit().putInt("target_monthly", v).apply() }
            Period.Year -> { targetYearly = v; sp.edit().putInt("target_yearly", v).apply() }
            Period.All -> Unit
        }
    }

    fun target(period: Period) = when (period) {
        Period.Day -> targetDaily
        Period.Week -> targetWeekly
        Period.Month -> targetMonthly
        Period.Year -> targetYearly
        Period.All -> 0
    }

    fun updateFocus(v: Boolean) { focus = v; sp.edit().putBoolean("focus", v).apply() }
    fun updateShowDate(v: Boolean) { showDate = v; sp.edit().putBoolean("date", v).apply() }
    fun updateUse24h(v: Boolean) { use24h = v; sp.edit().putBoolean("h24", v).apply() }
    fun updateShowSeconds(v: Boolean) { showSeconds = v; sp.edit().putBoolean("seconds", v).apply() }
    fun updateFontIndex(v: Int) { fontIndex = v; sp.edit().putInt("font", v).apply() }
}
