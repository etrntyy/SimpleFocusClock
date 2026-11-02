package com.focusclock.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon follows the app theme: white curve on black for the dark theme, black on white for the light one.
 * Android can't change an icon in place, so the manifest has one launcher entry for each and this switches between them.
 */
object LauncherIcon {
    fun apply(context: Context, light: Boolean) {
        val pm = context.packageManager
        val dark = ComponentName(context, "com.focusclock.app.LauncherDark")
        val bright = ComponentName(context, "com.focusclock.app.LauncherLight")
        // Until the app has switched them, the manifest's own defaults apply: dark on, light off.
        val darkOn = pm.getComponentEnabledSetting(dark).let { it == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || it == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
        val lightOn = pm.getComponentEnabledSetting(bright) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (light && lightOn && !darkOn) return
        if (!light && darkOn && !lightOn) return
        val on = if (light) bright else dark
        val off = if (light) dark else bright
        pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
