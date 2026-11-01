package com.focusclock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Keeps every focus session. The history lives in a private file and, once the user has picked a folder, also in
 * `SimpleFocusClock/sessions.csv` inside it, so it needs no account and can be read back after a reinstall.
 */
class FocusLog(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("focus_log", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "sessions.csv")
    private val io = Executors.newSingleThreadExecutor()

    var sessions by mutableStateOf(readPrivate())
        private set
    var hasFolder by mutableStateOf(folderUri() != null)
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

    /** Called with the folder the user picked; the app asks Android to keep the grant. */
    fun chooseFolder(uri: Uri) {
        val kept = runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (!kept) return
        prefs.edit().putString("tree", uri.toString()).apply()
        hasFolder = true
        io.execute {
            mergeFolder()
            writeFolder(sessions)
        }
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

    private fun folderUri(): Uri? {
        val tree = prefs.getString("tree", null)?.let(Uri::parse) ?: return null
        val granted = app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return if (granted) tree else null
    }

    /** Pulls in sessions already saved in the folder (a reinstall, or another phone) and keeps the larger history. */
    private fun mergeFolder() {
        val tree = folderUri() ?: return
        val saved = runCatching { find(tree)?.let { app.contentResolver.openInputStream(it)?.use { s -> parse(s.reader().readText()) } } }
            .getOrNull() ?: return
        val merged = (sessions + saved).distinctBy { it.start }.sortedBy { it.start }
        if (merged.size != sessions.size) {
            file.writeText(format(merged))
            sessions = merged
        }
        if (merged.size != saved.size) writeFolder(merged)
    }

    private fun writeFolder(all: List<Session>) {
        val tree = folderUri() ?: return
        runCatching {
            val uri = find(tree, create = true) ?: return
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(format(all).toByteArray()) }
        }
    }

    /** The `sessions.csv` inside the `SimpleFocusClock` folder of [tree]; with [create] both are made when missing. */
    private fun find(tree: Uri, create: Boolean = false): Uri? {
        val resolver = app.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val rootName = child(tree, root, null)
        val folder = if (rootName == "SimpleFocusClock") root else
            child(tree, root, "SimpleFocusClock")?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                ?: if (create) DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, "SimpleFocusClock") else null
        folder ?: return null
        return child(tree, folder, "sessions.csv")?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
            ?: if (create) DocumentsContract.createDocument(resolver, folder, "text/csv", "sessions.csv") else null
    }

    /** The document id of the child of [parent] called [name]; with a null [name], the display name of [parent] itself. */
    private fun child(tree: Uri, parent: Uri, name: String?): String? {
        val resolver = app.contentResolver
        if (name == null) {
            return resolver.query(parent, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            while (it.moveToNext()) if (it.getString(1) == name) return it.getString(0)
        }
        return null
    }

    companion object {
        private const val HEADER = "start,end,minutes"

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
