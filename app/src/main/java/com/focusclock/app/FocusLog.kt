package com.focusclock.app

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Keeps every focus session. The history lives in a private file and also in `Documents/SimpleFocusClock/sessions.csv`,
 * which the app makes and names itself, so it needs no account and can be read back after a reinstall.
 */
class FocusLog(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("focus_log", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "sessions.csv")
    private val io = Executors.newSingleThreadExecutor()

    var sessions by mutableStateOf(readPrivate())
        private set

    private val openStart get() = prefs.getLong("open_start", 0L).takeIf { it > 0 }

    init {
        // The app was killed mid-session: keep what was recorded up to the last heartbeat.
        openStart?.let { start ->
            val seen = prefs.getLong("open_seen", start)
            prefs.edit().remove("open_start").remove("open_seen").apply()
            add(Session(start, seen))
        }
        io.execute { mergeFolder() }
    }

    fun begin(now: Long = System.currentTimeMillis()) {
        if (openStart != null) return
        prefs.edit().putLong("open_start", now).putLong("open_seen", now).apply()
    }

    fun beat(now: Long = System.currentTimeMillis()) {
        if (openStart != null) prefs.edit().putLong("open_seen", now).apply()
    }

    fun end(now: Long = System.currentTimeMillis()) {
        val start = openStart ?: return
        prefs.edit().remove("open_start").remove("open_seen").apply()
        add(Session(start, now))
    }

    private fun add(s: Session) {
        if (s.end - s.start < FocusStats.MIN_SESSION_MS) return
        sessions = (sessions + s).distinctBy { it.start }.sortedBy { it.start }
        val all = sessions
        io.execute {
            writePrivate(all)
            writeFolder(all)
        }
    }

    private fun readPrivate(): List<Session> = if (file.exists()) parse(file.readText()) else emptyList()

    private fun writePrivate(all: List<Session>) = file.writeText(format(all))

    /**
     * The history file in the app's own `Documents/SimpleFocusClock` folder. Android creates the folder when the file is
     * first written, and an app can always read back what it wrote there, even after a reinstall, so no permission or
     * folder picker is needed. With [create] false it returns null when there is no file yet.
     */
    private fun documentUri(create: Boolean): Uri? {
        val resolver = app.contentResolver
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            files, arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
            arrayOf("$FOLDER/", FILE), null,
        )?.use { if (it.moveToFirst()) return ContentUris.withAppendedId(files, it.getLong(0)) }
        if (!create) return null
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "text/csv")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, FOLDER)
        }
        return resolver.insert(files, values)
    }

    /** Pulls in what is already saved in the folder (a reinstall) and keeps the larger history. */
    private fun mergeFolder() {
        val saved = runCatching {
            documentUri(create = false)?.let { app.contentResolver.openInputStream(it)?.use { s -> parse(s.reader().readText()) } }
        }.getOrNull() ?: return
        val merged = (sessions + saved).distinctBy { it.start }.sortedBy { it.start }
        if (merged.size != sessions.size) {
            file.writeText(format(merged))
            sessions = merged
        }
        if (merged.size != saved.size) writeFolder(merged)
    }

    private fun writeFolder(all: List<Session>) {
        runCatching {
            val uri = documentUri(create = true) ?: return
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(format(all).toByteArray()) }
        }
    }

    companion object {
        private const val HEADER = "start,end,minutes"
        private const val FOLDER = "Documents/SimpleFocusClock"
        private const val FILE = "sessions.csv"

        fun format(all: List<Session>, zone: ZoneId = ZoneId.systemDefault()): String =
            (listOf(HEADER) + all.map {
                val a = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.start), zone)
                val b = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.end), zone)
                "$a,$b,${"%.1f".format(java.util.Locale.ROOT, it.minutes)}"
            }).joinToString("\n", postfix = "\n")

        fun parse(text: String): List<Session> = text.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size < 2) return@mapNotNull null
            runCatching {
                Session(OffsetDateTime.parse(p[0].trim()).toInstant().toEpochMilli(), OffsetDateTime.parse(p[1].trim()).toInstant().toEpochMilli())
            }.getOrNull()
        }.sortedBy { it.start }.toList()
    }
}
