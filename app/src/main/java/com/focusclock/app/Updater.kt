package com.focusclock.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
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
    data class Downloading(val version: String, val percent: Int) : UpdateState
    data class Installing(val version: String) : UpdateState
    data object NeedsPermission : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Updates the app from the newest GitHub release: finds its APK, streams it straight into Android's package installer and
 * commits it. The installer only accepts an update signed with the same key as the installed app, so a wrong file can't
 * replace it. Android still asks for the final tap unless it lets the app that installed it update it silently.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/etrntyy/SimpleFocusClock/releases/latest"
    private val io = Executors.newSingleThreadExecutor()

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        internal set

    private fun busy() = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Checks for a newer release and, if there is one, downloads and installs it. */
    fun update(context: Context) {
        if (busy()) return
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission
            return
        }
        state = UpdateState.Checking
        io.execute {
            try {
                val release = latest()
                val current = installedVersion(app)
                if (release == null) {
                    state = UpdateState.Failed("No release found")
                } else if (Version.compare(release.version, current) <= 0) {
                    state = UpdateState.UpToDate(current)
                } else {
                    install(app, release)
                }
            } catch (e: Exception) {
                state = UpdateState.Failed("Couldn't reach GitHub")
            }
        }
    }

    /** Opens Android's page where an app is allowed to install other apps. */
    fun openPermissionPage(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        state = UpdateState.Idle
    }

    private class Release(val version: String, val url: String, val size: Long)

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
                if (a.getString("name").endsWith(".apk")) {
                    return Release(json.getString("tag_name"), a.getString("browser_download_url"), a.optLong("size", -1L))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun install(app: Context, release: Release) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (release.size > 0) params.setSize(release.size)
        // Lets Android skip its confirmation when this app is allowed to update itself.
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            val c = URL(release.url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                if (c.responseCode != 200) {
                    state = UpdateState.Failed("Download failed")
                    session.abandon()
                    return
                }
                val total = if (release.size > 0) release.size else c.contentLengthLong
                state = UpdateState.Downloading(release.version, 0)
                session.openWrite("update.apk", 0, total).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    c.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else 0
                            if (percent != shown) {
                                shown = percent
                                state = UpdateState.Downloading(release.version, percent)
                            }
                        }
                    }
                    session.fsync(out)
                }
            } finally {
                c.disconnect()
            }
            state = UpdateState.Installing(release.version)
            val callback = PendingIntent.getBroadcast(
                app, id, Intent(app, UpdateReceiver::class.java).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        } catch (e: Exception) {
            runCatching { session.abandon() }
            state = UpdateState.Failed("Download failed")
        } finally {
            session.close()
        }
    }
}

/** Hears back from Android's installer: asks for the user's tap when it needs one, and reports a failure. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.state = UpdateState.Failed("Update cancelled")
            else -> Updater.state = UpdateState.Failed("Couldn't install the update")
        }
    }
}
