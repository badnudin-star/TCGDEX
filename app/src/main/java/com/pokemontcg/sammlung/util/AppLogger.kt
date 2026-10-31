package com.pokemontcg.sammlung.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.pokemontcg.sammlung.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object AppLogger {

    private const val MAX_LOG_LINES = 500
    private val buffer = LinkedList<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "$timestamp [$tag] $message"
        try { Log.d(tag, message) } catch (_: Throwable) {}

        buffer.addLast(logEntry)
        while (buffer.size > MAX_LOG_LINES) {
            buffer.removeFirst()
        }
    }

    @Synchronized
    fun getLogLines(): List<String> {
        return buffer.toList()
    }

    @Synchronized
    fun clearLogs() {
        buffer.clear()
    }

    fun shareLogs(context: Context) {
        val logContent = getLogLines().joinToString("\n")
        val cacheDir = context.cacheDir
        val logFile = File(cacheDir, "tcg_sammlung_log.txt")

        logFile.writeText(logContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_subject))
            val logLineCount = getLogLines().size
            putExtra(
                Intent.EXTRA_TEXT,
                context.resources.getQuantityString(
                    R.plurals.log_share_text,
                    logLineCount,
                    logLineCount
                )
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.log_share_chooser))
        )
    }
}
