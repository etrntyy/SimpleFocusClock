package com.focusclock.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/** A SharedPreferences that lives in a map, so settings can be saved and read back without a phone. */
private class FakePrefs(private val store: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = store
    override fun getString(key: String, defValue: String?) = store[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = (store[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String, defValue: Int) = store[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = store[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = store[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = store[key] as Boolean? ?: defValue
    override fun contains(key: String) = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { store.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v } }
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

class ClockSettingsTest {
    private fun context(prefs: SharedPreferences): Context = object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int) = prefs
    }

    @Test
    fun aChosenDrawnFaceIsStillChosenWhenTheAppStartsAgain() {
        val prefs = FakePrefs()
        ClockSettings(context(prefs)).updateStyle(AnalogStyle.Disc.ordinal)
        assertEquals(AnalogStyle.Disc.ordinal, ClockSettings(context(prefs)).style)
    }

    @Test
    fun aChosenFontReplacesADrawnFaceAndIsRemembered() {
        val prefs = FakePrefs()
        val first = ClockSettings(context(prefs))
        first.updateStyle(AnalogStyle.Rings.ordinal)
        first.chooseFont(5)
        val again = ClockSettings(context(prefs))
        assertEquals(-1, again.style)
        assertEquals(5, again.fontIndex)
    }

    @Test
    fun themesAreRemembered() {
        val prefs = FakePrefs()
        val first = ClockSettings(context(prefs))
        first.updateAppTheme(AppTheme.Light)
        first.updateClockTheme(ClockTheme.Dark)
        val again = ClockSettings(context(prefs))
        assertEquals(AppTheme.Light, again.appTheme)
        assertEquals(ClockTheme.Dark, again.clockTheme)
    }
}
