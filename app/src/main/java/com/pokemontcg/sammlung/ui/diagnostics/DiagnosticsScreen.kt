package com.pokemontcg.sammlung.ui.diagnostics

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.local.entities.SourceAuditEntity
import com.pokemontcg.sammlung.data.repository.DataCheckRepository
import com.pokemontcg.sammlung.data.repository.SystemDataMetrics
import com.pokemontcg.sammlung.util.AppLogger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    dataCheckRepository: DataCheckRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf<SystemDataMetrics?>(null) }
    var sourceAudits by remember { mutableStateOf<List<SourceAuditEntity>>(emptyList()) }
    var integrityStatus by remember { mutableStateOf<String?>(null) }
    var lastCheckResult by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        metrics = dataCheckRepository.calculateMetrics()
        sourceAudits = dataCheckRepository.getSourceAudits()
        lastCheckResult = dataCheckRepository.getLastDataCheckResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_diagnose_daten_check_f9cfd5)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ui_system_bersicht_kennzahlen_d2dad8), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()

                    metrics?.let { m ->
                        Text(stringResource(R.string.ui_diag_sets_count, m.setsCount))
                        Text(stringResource(R.string.ui_diag_pokemon_count, m.pokemonCount))
                        Text(stringResource(R.string.ui_diag_catalog_cards, m.totalCatalogCards))
                        Text(stringResource(R.string.ui_diag_card_details, m.totalCards))
                        Text(stringResource(R.string.ui_diag_cards_without_images, m.cardsWithoutImagesCount))
                        Text(stringResource(R.string.ui_diag_cards_without_price, m.cardsWithoutPriceCount))
                        Text(stringResource(R.string.ui_diag_collection_entries, m.uniqueUserCardsCount, m.userCardsCount))
                        Text(stringResource(R.string.ui_diag_unassigned_cards, m.unassignedUserCardsCount))
                        Text(stringResource(R.string.ui_diag_wishlist_count, m.wishlistCount))
                        Text(stringResource(R.string.ui_diag_price_history_count, m.priceHistoryCount))
                        Text(stringResource(R.string.ui_diag_cards_by_language, m.cardsByLanguage.entries.joinToString { (language, count) -> "$language: $count" }.ifBlank { context.getString(R.string.ui_keine_karten) }))
                        Text(stringResource(R.string.ui_diag_db_versions, m.appDbVersion, m.userDbVersion))
                        if (m.catalogRequiresAppUpdate) {
                            Text(
                                stringResource(
                                    R.string.diag_catalog_update_requires_app_update,
                                    m.catalogManifestSchemaVersion,
                                    m.appDbVersion
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            m.catalogInstalledVersion?.takeIf { it.isNotBlank() }?.let { version ->
                                Text(stringResource(R.string.diag_catalog_version, version))
                            }
                        }
                        Text(stringResource(R.string.ui_diag_migration_status, m.migrationStatus))
                        Text(stringResource(R.string.ui_diag_last_update, formatTimestamp(m.lastUpdateAt, context)))
                        Text(stringResource(R.string.ui_diag_last_source_check, formatTimestamp(m.lastSourceCheckAt, context)))
                    } ?: CircularProgressIndicator()
                }
            }

            // Section: Quellenbericht
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ui_quellenbericht_ea5631), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_status_und_vollst_ndigkeit_der_daten_6e2dc9), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()

                    if (sourceAudits.isEmpty()) {
                        Text(stringResource(R.string.ui_noch_kein_quellenbericht_vorhanden_6cccd7), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        sourceAudits.forEach { audit ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(audit.sourceName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.ui_audit_checked_at, dateFormat.format(Date(audit.checkedAt))), style = MaterialTheme.typography.labelSmall)
                                Text(stringResource(R.string.ui_audit_sets, audit.matchedSets, audit.discoveredSets, audit.missingSets), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.ui_audit_cards, audit.discoveredCards, audit.cardsWithImages), style = MaterialTheme.typography.bodySmall)
                                if (audit.note.isNotBlank()) {
                                    Text(stringResource(R.string.ui_audit_note, audit.note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ui_integrit_tspr_fung_e97d46), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_pr_ft_die_physikalische_datenbankstr_64dd72), style = MaterialTheme.typography.bodySmall)

                    integrityStatus?.let { status ->
                        Text(status, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    lastCheckResult?.let { result ->
                        Text(stringResource(R.string.ui_saved_check_result, formatCheckResult(result, context)), style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isChecking = true
                                val (appOk, userOk) = dataCheckRepository.runIntegrityCheck()
                                val newMetrics = dataCheckRepository.calculateMetrics()
                                metrics = newMetrics
                                isChecking = false
                                lastCheckResult = dataCheckRepository.getLastDataCheckResult()
                                integrityStatus = if (appOk && userOk) {
                                    "✓ Beide Datenbanken sind intakt. Unzugeordnet: ${newMetrics.unassignedUserCardsCount}"
                                } else {
                                    "⚠ Integritätsprüfung meldet Fehler!"
                                }
                                AppLogger.log("Diagnostics", "Integritätsprüfung ausgeführt: AppDB=$appOk, UserDB=$userOk")
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isChecking) stringResource(R.string.ui_diagnostic_running) else stringResource(R.string.ui_diagnostic_run_now))
                    }
                }
            }

            Button(
                onClick = { AppLogger.shareLogs(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ui_diagnose_log_teilen_9a68fd))
            }
        }
    }
}


private fun formatTimestamp(timestamp: Long, context: android.content.Context): String {
    if (timestamp <= 0L) return context.getString(R.string.ui_never)
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatCheckResult(result: String, context: android.content.Context): String {
    val values = result.split(';').mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
    }.toMap()
    val checkedAt = values["checkedAt"]?.toLongOrNull() ?: 0L
    val appOk = values["appDbIntegrityOk"] == "true"
    val userOk = values["userDbIntegrityOk"] == "true"
    val unassigned = values["unassignedUserCardsCount"] ?: "?"
    return context.getString(R.string.ui_saved_check_result_details, formatTimestamp(checkedAt, context), context.getString(if (appOk) R.string.ui_status_ok else R.string.ui_status_error), context.getString(if (userOk) R.string.ui_status_ok else R.string.ui_status_error), unassigned)
}
