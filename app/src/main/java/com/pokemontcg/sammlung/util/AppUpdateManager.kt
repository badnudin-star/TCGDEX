package com.pokemontcg.sammlung.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.pokemontcg.sammlung.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles in-app APK updates via GitHub Releases.
 *
 * The app itself is distributed as sideload APK (not Play Store).
 * Updates are delivered as [app-manifest.json] hosted next to the APK:
 *   https://github.com/badnudin-star/TCGDEX/releases/download/app-latest/app-manifest.json
 *
 * Flow: check -> download via DownloadManager -> trigger installer via FileProvider.
 * Silent install is NOT possible without being a system app or Play Store — the user must
 * confirm the installer dialog + allow "unknown sources" once.
 */
object AppUpdateManager {

    const val DEFAULT_MANIFEST_URL =
        "https://github.com/badnudin-star/TCGDEX/releases/download/app-latest/app-manifest.json"

    val manifestUrl: String
        get() = BuildConfig.APP_UPDATE_MANIFEST_URL.ifBlank { DEFAULT_MANIFEST_URL }

    const val APK_FILE_NAME = "TCG-Sammlung.apk"
    const val APK_MIME = "application/vnd.android.package-archive"

    data class AppUpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String? = null,
        val mandatory: Boolean = false
    )

    sealed class CheckResult {
        data class UpdateAvailable(val info: AppUpdateInfo) : CheckResult()
        data object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    /**
     * Fetches remote manifest and compares to local BuildConfig.
     * Returns [CheckResult] on IO dispatcher.
     */
    suspend fun checkForUpdate(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(manifestUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "TCG-Sammlung/${BuildConfig.VERSION_NAME} Android")

            val code = conn.responseCode
            if (code != 200) {
                return@withContext CheckResult.Error("Update-Check fehlgeschlagen (HTTP $code)")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (body.isBlank()) return@withContext CheckResult.Error("Leere Update-Antwort")

            val json = JSONObject(body)
            val remoteCode = json.optInt("versionCode", -1)
            val remoteName = json.optString("versionName", "")
            val apkUrl = json.optString("apkUrl", json.optString("url", ""))

            if (remoteCode == -1 || remoteName.isBlank() || apkUrl.isBlank()) {
                return@withContext CheckResult.Error("Ungültiges Update-Manifest")
            }

            if (!apkUrl.startsWith("https://")) {
                return@withContext CheckResult.Error("APK-URL muss HTTPS sein")
            }

            val localCode = BuildConfig.VERSION_CODE
            if (remoteCode > localCode) {
                CheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        apkUrl = apkUrl,
                        changelog = json.optString("changelog", null)?.takeIf { it.isNotBlank() },
                        mandatory = json.optBoolean("mandatory", false)
                    )
                )
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Unbekannter Fehler beim Update-Check")
        }
    }

    /**
     * Starts APK download via system DownloadManager.
     * Returns downloadId for tracking.
     */
    fun startDownload(context: Context, info: AppUpdateInfo): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Clean up previous download if exists
        val oldFile = getDownloadedApkFile(context)
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("TCG Sammlung Update ${info.versionName}")
            setDescription("Lade APK herunter…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType(APK_MIME)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        return dm.enqueue(request)
    }

    fun getDownloadStatus(context: Context, downloadId: Long): DownloadStatus? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null
        return try {
            cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                DownloadStatus(status, bytesSoFar, totalBytes, reason, uri)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    data class DownloadStatus(
        val status: Int,
        val bytesSoFar: Long,
        val totalBytes: Long,
        val reason: Int,
        val localUri: String?
    )

    fun getDownloadedApkFile(context: Context): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
    }

    /**
     * Triggers the system installer for the downloaded APK via FileProvider.
     * Caller must have ensured the file exists and permission is granted.
     */
    fun triggerInstall(context: Context, apkFile: File = getDownloadedApkFile(context)): Boolean {
        if (!apkFile.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.log("AppUpdateManager", "Install-Intent fehlgeschlagen: ${e.message}")
            false
        }
    }

    /**
     * Alternative: trigger install directly from DownloadManager URI (after completion).
     */
    fun triggerInstallFromDownloadId(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: return false
        return try {
            // DownloadManager returns content:// URI already grantable, but we normalize via FileProvider if local file exists
            val file = getDownloadedApkFile(context)
            if (file.exists()) {
                triggerInstall(context, file)
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createInstallPermissionIntent(context: Context): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Helper to register a one-shot receiver for DownloadManager completion.
     * Returns the receiver so caller can unregister it.
     */
    fun registerDownloadCompleteReceiver(
        context: Context,
        downloadId: Long,
        onComplete: (Long) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        onComplete(id)
                    }
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        return receiver
    }
}
