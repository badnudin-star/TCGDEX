package com.pokemontcg.sammlung.ui.catalog

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.ui.main.SyncUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogManagerScreen(
    state: CatalogManagerUiState,
    onUpdateClick: () -> Unit,
    onDismissError: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.catalog_manager_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!state.isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            CatalogManagerContent(
                state = state,
                onUpdateClick = onUpdateClick,
                onDismissError = onDismissError,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun CatalogManagerContent(
    state: CatalogManagerUiState,
    onUpdateClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.catalog_manager_unknown)
    val installedVersion = state.installedVersion?.takeIf { it.isNotBlank() } ?: unknown
    val remoteVersion = state.remoteVersion?.takeIf { it.isNotBlank() } ?: unknown
    val installedAt = formatTimestamp(state.installedAt) ?: unknown
    val lastCheckAt = formatTimestamp(state.lastCheckAt) ?: unknown
    val remoteSize = state.remoteSizeBytes?.let {
        Formatter.formatShortFileSize(context, it)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CatalogStatusCard(state)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.catalog_manager_details_title),
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.catalog_manager_counts,
                        state.setCount,
                        state.cardCount,
                        state.pokemonCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text(stringResource(R.string.catalog_manager_installed_version, installedVersion))
                Text(stringResource(R.string.catalog_manager_remote_version, remoteVersion))
                Text(stringResource(R.string.catalog_manager_installed_at, installedAt))
                Text(stringResource(R.string.catalog_manager_last_check, lastCheckAt))
                Text(
                    if (remoteSize != null) {
                        stringResource(R.string.catalog_manager_size, remoteSize)
                    } else {
                        stringResource(R.string.catalog_manager_size_unknown)
                    }
                )
            }
        }

        if (state.sync.isSyncing) {
            CatalogDownloadProgressCard(state.sync)
        }

        val syncError = state.sync.errorMessageRes?.let { stringResource(it) }
            ?: state.sync.errorMessage
        if (!state.sync.isSyncing && !syncError.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.catalog_manager_error_title),
                        modifier = Modifier.semantics { heading() },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        syncError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            }
        }

        Button(
            onClick = onUpdateClick,
            enabled = !state.sync.isSyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.catalog_manager_update_now))
        }

        Text(
            stringResource(R.string.catalog_manager_wifi_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogStatusCard(state: CatalogManagerUiState) {
    val statusRes = when (state.availability) {
        CatalogAvailability.MISSING -> R.string.catalog_manager_status_missing
        CatalogAvailability.READY -> R.string.catalog_manager_status_ready
        CatalogAvailability.UPDATE_AVAILABLE -> R.string.catalog_manager_status_update_available
        CatalogAvailability.APP_UPDATE_REQUIRED -> R.string.catalog_manager_status_app_update_required
    }
    val containerColor = when (state.availability) {
        CatalogAvailability.MISSING,
        CatalogAvailability.APP_UPDATE_REQUIRED -> MaterialTheme.colorScheme.errorContainer
        CatalogAvailability.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.secondaryContainer
        CatalogAvailability.READY -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (state.availability) {
        CatalogAvailability.MISSING,
        CatalogAvailability.APP_UPDATE_REQUIRED -> MaterialTheme.colorScheme.onErrorContainer
        CatalogAvailability.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.onSecondaryContainer
        CatalogAvailability.READY -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.catalog_manager_status_title),
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                stringResource(statusRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (state.availability == CatalogAvailability.APP_UPDATE_REQUIRED) {
                Text(
                    stringResource(R.string.catalog_manager_app_update_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun CatalogDownloadProgressCard(sync: SyncUiState) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.catalog_manager_progress_title),
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            sync.progressPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                sync.progressMessage.ifBlank { stringResource(R.string.catalog_empty_syncing) },
                style = MaterialTheme.typography.bodySmall
            )
            if (sync.downloadedBytes != null && sync.totalBytes != null) {
                Text(
                    stringResource(
                        R.string.ui_sync_download_bytes,
                        Formatter.formatShortFileSize(context, sync.downloadedBytes),
                        Formatter.formatShortFileSize(context, sync.totalBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            sync.progressPercent?.let { percent ->
                Text(
                    stringResource(R.string.ui_sync_progress_percent, percent),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String? =
    if (timestamp <= 0L) {
        null
    } else {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
