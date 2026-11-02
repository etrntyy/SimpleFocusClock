package com.focusclock.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme(val label: String) { Dark("Dark"), Light("Light"), System("System") }

enum class ClockTheme(val label: String) { Dark("Dark"), Light("Light"), SameAsApp("Same as app") }

/** Whether a screen is drawn light: the clock, timer and stopwatch follow the clock theme, everything else the app theme. */
fun isLight(app: AppTheme, clock: ClockTheme, systemDark: Boolean, clockScreen: Boolean): Boolean {
    val appLight = when (app) { AppTheme.Dark -> false; AppTheme.Light -> true; AppTheme.System -> !systemDark }
    if (!clockScreen) return appLight
    return when (clock) { ClockTheme.Dark -> false; ClockTheme.Light -> true; ClockTheme.SameAsApp -> appLight }
}

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
    /** Whether the noise also plays on the phone's speaker while headphones are connected. */
    var noiseSpeaker by mutableStateOf(sp.getBoolean("noise_speaker", false))
        private set
    var appTheme by mutableStateOf(AppTheme.entries.getOrElse(sp.getInt("app_theme", 0)) { AppTheme.Dark })
        private set
    var clockTheme by mutableStateOf(ClockTheme.entries.getOrElse(sp.getInt("clock_theme", 2)) { ClockTheme.SameAsApp })
        private set
    /** The drawn clock face in use, or -1 for the digital clock in the chosen font. */
    var style by mutableStateOf(sp.getInt("style", -1).takeIf { it in AnalogStyle.entries.indices } ?: -1)
        private set
    /** The fractal behind the clock, as a saved key, or empty for the plain clock. */
    var fractalBackground by mutableStateOf(sp.getString("fractal_bg", "").orEmpty())
        private set
    /** Whether the Fractals section shows the monochrome versions. */
    var fractalMono by mutableStateOf(sp.getBoolean("fractal_mono", false))
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
    fun updateNoiseSpeaker(v: Boolean) { noiseSpeaker = v; sp.edit().putBoolean("noise_speaker", v).apply() }
    fun updateAppTheme(v: AppTheme) { appTheme = v; sp.edit().putInt("app_theme", v.ordinal).apply() }
    fun updateClockTheme(v: ClockTheme) { clockTheme = v; sp.edit().putInt("clock_theme", v.ordinal).apply() }
    fun updateFractalBackground(v: String) { fractalBackground = v; sp.edit().putString("fractal_bg", v).apply() }
    fun updateFractalMono(v: Boolean) { fractalMono = v; sp.edit().putBoolean("fractal_mono", v).apply() }
    fun updateFontIndex(v: Int) { fontIndex = v; sp.edit().putInt("font", v).apply() }
    fun updateStyle(v: Int) { style = v; sp.edit().putInt("style", v).apply() }
    /** Picks a digital font, which also switches back from a drawn face. */
    fun chooseFont(v: Int) { updateFontIndex(v); updateStyle(-1) }
}
