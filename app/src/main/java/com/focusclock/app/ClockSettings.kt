package com.focusclock.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User options, persisted in SharedPreferences and observable from Compose. */
class ClockSettings(context: Context) {
    private val sp = context.getSharedPreferences("clock", Context.MODE_PRIVATE)

    var focusOnStart by mutableStateOf(sp.getBoolean("focus", true))
        private set
    var showDate by mutableStateOf(sp.getBoolean("date", false))
        private set
    var use24h by mutableStateOf(sp.getBoolean("h24", false))
        private set
    var fontIndex by mutableStateOf(sp.getInt("font", 0).coerceIn(ClockFonts.indices))
        private set

    fun updateFocusOnStart(v: Boolean) { focusOnStart = v; sp.edit().putBoolean("focus", v).apply() }
    fun updateShowDate(v: Boolean) { showDate = v; sp.edit().putBoolean("date", v).apply() }
    fun updateUse24h(v: Boolean) { use24h = v; sp.edit().putBoolean("h24", v).apply() }
    fun updateFontIndex(v: Int) { fontIndex = v; sp.edit().putInt("font", v).apply() }
}
