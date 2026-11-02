package com.focusclock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Version numbers like "v0.4.0" or "0.4": compared part by part, so 0.10.0 is newer than 0.9.0. */
object Version {
    fun parts(text: String): List<Int> = text.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
        .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }.ifEmpty { listOf(0) }

    /** Positive when [a] is newer than [b], negative when older, 0 when the same. */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}

/** Where the update button has got to. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Opened(val version: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Looks up the newest GitHub release and, when it is newer than the installed version, opens its APK in the browser so
 * the phone downloads it. The app never installs anything itself, so it needs no permission to install apps: the user
 * opens the downloaded file, and Android only accepts it as an update if it is signed with the same key.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/etrntyy/SimpleFocusClock/releases/latest"
    private val io = Executors.newSingleThreadExecutor()

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        internal set

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Checks for a newer release and, if there is one, starts downloading it. */
    fun update(context: Context) {
        if (state is UpdateState.Checking) return
        val app = context.applicationContext
        state = UpdateState.Checking
        io.execute {
            try {
                val release = latest()
                val current = installedVersion(app)
                state = when {
                    release == null -> UpdateState.Failed("No release found")
                    Version.compare(release.version, current) <= 0 -> UpdateState.UpToDate(current)
                    else -> {
                        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        UpdateState.Opened(release.version.removePrefix("v"))
                    }
                }
            } catch (e: Exception) {
                state = UpdateState.Failed("Couldn't reach GitHub")
            }
        }
    }

    private class Release(val version: String, val url: String)

    private fun latest(): Release? {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) return Release(json.getString("tag_name"), a.getString("browser_download_url"))
            }
            return null
        } finally {
            c.disconnect()
        }
    }
}
