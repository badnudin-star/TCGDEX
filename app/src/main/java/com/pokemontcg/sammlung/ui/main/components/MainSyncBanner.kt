package com.pokemontcg.sammlung.ui.main.components

import android.text.format.Formatter
import com.pokemontcg.sammlung.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainSyncBanner(
    isSyncing: Boolean,
    progressMessage: String,
    progressPercent: Int?,
    downloadedBytes: Long?,
    totalBytes: Long?,
    errorMessage: String?,
    errorMessageRes: Int?,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val resolvedErrorMessage = if (errorMessageRes != null) {
        stringResource(errorMessageRes)
    } else {
        errorMessage
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = isSyncing) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = progressMessage.ifBlank { stringResource(R.string.ui_sync_progress_default) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    progressPercent?.let { percent ->
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Text(
                            text = stringResource(R.string.ui_sync_progress_percent, percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (downloadedBytes != null && totalBytes != null) {
                        Text(
                            text = stringResource(
                                R.string.ui_sync_download_bytes,
                                Formatter.formatShortFileSize(context, downloadedBytes),
                                Formatter.formatShortFileSize(context, totalBytes)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = resolvedErrorMessage != null && !isSyncing) {
            resolvedErrorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.ui_ok_9ce3bd), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}
