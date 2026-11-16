package com.pokemontcg.sammlung.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.BuildConfig
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.util.AppLogger
import com.pokemontcg.sammlung.util.AppUpdateManager
import kotlinx.coroutines.launch

@Composable
fun AppUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdateManager.AppUpdateInfo?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returns from system settings — re-check permission and proceed if granted
        if (AppUpdateManager.canRequestPackageInstalls(context)) {
            val file = AppUpdateManager.getDownloadedApkFile(context)
            if (file.exists()) {
                AppUpdateManager.triggerInstall(context, file)
            } else if (pendingUpdate != null) {
                // No file yet, start download
                try {
                    val id = AppUpdateManager.startDownload(context, pendingUpdate!!)
                    downloadId = id
                    isDownloading = true
                    statusText = context.getString(R.string.settings_app_update_downloading)
                    // Auto-install after download
                    AppUpdateManager.registerDownloadCompleteReceiver(context, id) { completedId ->
                        scope.launch {
                            isDownloading = false
                            statusText = context.getString(R.string.settings_app_update_download_complete)
                            AppUpdateManager.triggerInstallFromDownloadId(context, completedId)
                        }
                    }
                } catch (e: Exception) {
                    statusText = context.getString(R.string.settings_app_update_error, e.message ?: "")
                }
            }
        }
    }

    // Observe download completion for install trigger (when permission already granted)
    DisposableEffect(downloadId) {
        val id = downloadId
        var receiver: android.content.BroadcastReceiver? = null
        if (id != null) {
            receiver = AppUpdateManager.registerDownloadCompleteReceiver(context, id) { completedId ->
                isDownloading = false
                statusText = context.getString(R.string.settings_app_update_download_complete)
                val ok = AppUpdateManager.triggerInstallFromDownloadId(context, completedId)
                if (!ok) {
                    Toast.makeText(context, context.getString(R.string.settings_app_update_installer_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
        onDispose {
            receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.settings_section_app_update),
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_app_update_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(
                    R.string.settings_app_version_current,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            if (statusText != null) {
                Text(
                    statusText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isChecking || isDownloading) return@Button
                        isChecking = true
                        statusText = context.getString(R.string.settings_app_update_checking)
                        pendingUpdate = null
                        scope.launch {
                            when (val result = AppUpdateManager.checkForUpdate(context)) {
                                is AppUpdateManager.CheckResult.UpdateAvailable -> {
                                    pendingUpdate = result.info
                                    statusText = context.getString(
                                        R.string.settings_app_update_available,
                                        result.info.versionName,
                                        result.info.versionCode
                                    )
                                    if (!result.info.changelog.isNullOrBlank()) {
                                        statusText = statusText + "\n" + result.info.changelog
                                    }
                                }
                                is AppUpdateManager.CheckResult.UpToDate -> {
                                    statusText = context.getString(
                                        R.string.settings_app_update_up_to_date,
                                        BuildConfig.VERSION_NAME
                                    )
                                }
                                is AppUpdateManager.CheckResult.Error -> {
                                    statusText = context.getString(
                                        R.string.settings_app_update_error,
                                        result.message
                                    )
                                }
                            }
                            isChecking = false
                        }
                    },
                    enabled = !isChecking && !isDownloading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_app_update_check))
                }
            }

            if (pendingUpdate != null) {
                Button(
                    onClick = {
                        val info = pendingUpdate ?: return@Button
                        if (isDownloading) return@Button

                        // Android 8+ needs INSTALL permission
                        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                            statusText = context.getString(R.string.settings_app_update_install_permission_required)
                            try {
                                installPermissionLauncher.launch(
                                    AppUpdateManager.createInstallPermissionIntent(context)
                                )
                            } catch (e: Exception) {
                                AppLogger.log("AppUpdateSection", "Kein Settings-Intent: ${e.message}")
                            }
                            return@Button
                        }

                        // Start download
                        try {
                            val id = AppUpdateManager.startDownload(context, info)
                            downloadId = id
                            isDownloading = true
                            statusText = context.getString(R.string.settings_app_update_downloading)
                        } catch (e: Exception) {
                            statusText = context.getString(R.string.settings_app_update_error, e.message ?: "")
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_app_update_download))
                }

                if (isDownloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.settings_app_update_downloading_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If already downloaded file exists, offer direct install
                val apkFile = AppUpdateManager.getDownloadedApkFile(context)
                if (apkFile.exists() && !isDownloading) {
                    OutlinedButton(
                        onClick = {
                            if (!AppUpdateManager.canRequestPackageInstalls(context)) {
                                installPermissionLauncher.launch(
                                    AppUpdateManager.createInstallPermissionIntent(context)
                                )
                                return@OutlinedButton
                            }
                            val ok = AppUpdateManager.triggerInstall(context, apkFile)
                            if (!ok) {
                                Toast.makeText(context, context.getString(R.string.settings_app_update_apk_not_found), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_app_update_install))
                    }
                }
            }

            // Debug info: manifest URL
            Text(
                stringResource(R.string.settings_app_update_source, AppUpdateManager.manifestUrl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
