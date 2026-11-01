package com.pokemontcg.sammlung.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.export.CollectionExportData
import com.pokemontcg.sammlung.data.export.CollectionExportException
import com.pokemontcg.sammlung.data.export.CollectionExporter
import com.pokemontcg.sammlung.data.export.ImportPreview
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.ui.export.ImportPreviewDialog
import com.pokemontcg.sammlung.ui.pokedex.GenerationFilterDialog
import com.pokemontcg.sammlung.util.AppLogger
import com.pokemontcg.sammlung.util.PRICE_ALERT_THRESHOLDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    collectionRepo: CollectionRepository,
    catalogRepo: CatalogRepository,
    onNavigateToDiagnostics: () -> Unit,
    onTriggerSync: () -> Unit,
    onShowAbout: () -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGenDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingImportData by remember { mutableStateOf<CollectionExportData?>(null) }
    var pendingImportPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var statusDialogMessage by remember { mutableStateOf<String?>(null) }

    val msgNothingToExport = stringResource(R.string.export_msg_nothing)
    val msgExported = stringResource(R.string.export_msg_exported)
    val msgImportCompleted = stringResource(R.string.import_msg_completed)
    val msgReadError = stringResource(R.string.import_msg_read_error)
    val msgExportFailed = stringResource(R.string.export_msg_failed)
    val msgImportFailed = stringResource(R.string.import_msg_failed)

    fun exportFailureMessage(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.ui_export_failed, it) }
            ?: msgExportFailed

    fun importFailureMessage(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.import_msg_failed_detail, it) }
            ?: msgImportFailed

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val cards = collectionRepo.getAllUserCardsOnce()
                    val wishlist = collectionRepo.getWishlistOnce()
                    if (cards.isEmpty() && wishlist.isEmpty()) {
                        withContext(Dispatchers.Main) { statusDialogMessage = msgNothingToExport }
                        return@launch
                    }
                    val json = CollectionExporter.exportToJsonString(cards, wishlist)
                    requireNotNull(context.contentResolver.openOutputStream(it)) { context.getString(R.string.ui_file_open_error) }.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        statusDialogMessage = msgExported.format(cards.size + wishlist.size)
                    }
                } catch (e: Exception) {
                    AppLogger.log("SettingsScreen", context.getString(R.string.ui_settings_export_error_json, e.message.orEmpty()))
                    withContext(Dispatchers.Main) { statusDialogMessage = exportFailureMessage(e) }
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val cards = collectionRepo.getAllUserCardsOnce()
                    val wishlist = collectionRepo.getWishlistOnce()
                    if (cards.isEmpty() && wishlist.isEmpty()) {
                        withContext(Dispatchers.Main) { statusDialogMessage = msgNothingToExport }
                        return@launch
                    }
                    val csv = CollectionExporter.exportToCsvString(cards, wishlist)
                    val bom = "\uFEFF"
                    requireNotNull(context.contentResolver.openOutputStream(it)) { context.getString(R.string.ui_file_open_error) }.use { stream ->
                        stream.write((bom + csv).toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        statusDialogMessage = msgExported.format(cards.size)
                    }
                } catch (e: Exception) {
                    AppLogger.log("SettingsScreen", context.getString(R.string.ui_settings_export_error_csv, e.message.orEmpty()))
                    withContext(Dispatchers.Main) { statusDialogMessage = exportFailureMessage(e) }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                        ?: throw IllegalArgumentException(msgReadError)
                    val exportData = CollectionExporter.parseExportJson(content)
                    val existing = collectionRepo.getAllUserCardsOnce()
                    val validIds = catalogRepo.getAllCardIds().toSet()
                    val preview = CollectionExporter.generatePreview(exportData, existing, validIds)
                    withContext(Dispatchers.Main) {
                        pendingImportData = exportData
                        pendingImportPreview = preview
                    }
                } catch (e: CollectionExportException) {
                    withContext(Dispatchers.Main) {
                        statusDialogMessage = context.getString(e.messageRes, *e.formatArgs)
                    }
                } catch (e: Exception) {
                    AppLogger.log("SettingsScreen", context.getString(R.string.ui_settings_import_error, e.message.orEmpty()))
                    withContext(Dispatchers.Main) { statusDialogMessage = importFailureMessage(e) }
                }
            }
        }
    }

    if (pendingImportPreview != null && pendingImportData != null) {
        ImportPreviewDialog(
            preview = pendingImportPreview!!,
            onConfirmStrategy = { strategy ->
                val dataToImport = pendingImportData!!
                pendingImportData = null
                pendingImportPreview = null
                scope.launch(Dispatchers.IO) {
                    try {
                        val validIds = catalogRepo.getAllCardIds().toSet()
                        val result = collectionRepo.executeCollectionImport(
                            exportData = dataToImport,
                            validCatalogCardIds = validIds,
                            strategy = strategy
                        )
                        withContext(Dispatchers.Main) {
                            statusDialogMessage = msgImportCompleted.format(
                                result.insertedCount,
                                result.updatedCount,
                                result.skippedCount,
                                result.unknownCardsPreservedCount
                            )
                        }
                    } catch (e: Exception) {
                        AppLogger.log("SettingsScreen", context.getString(R.string.ui_settings_import_execution_error, e.message.orEmpty()))
                        withContext(Dispatchers.Main) {
                            statusDialogMessage = importFailureMessage(e)
                        }
                    }
                }
            },
            onDismiss = {
                pendingImportData = null
                pendingImportPreview = null
            }
        )
    }

    if (statusDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { statusDialogMessage = null },
            title = {
                Text(
                    stringResource(R.string.common_dialog_notice),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = { Text(statusDialogMessage!!) },
            confirmButton = {
                TextButton(onClick = { statusDialogMessage = null }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

    if (showGenDialog) {
        GenerationFilterDialog(
            hiddenGenerations = uiState.hiddenGenerations,
            onHiddenGenerationsChange = { viewModel.setHiddenGenerations(it) },
            onDismiss = { showGenDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        if (!uiState.isLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Aktualisierung
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.settings_section_update),
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_wifi_only), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.settings_wifi_only_desc), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = uiState.wifiOnly,
                                onCheckedChange = { viewModel.setWifiOnly(it) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics {
                                        stateDescription = context.getString(
                                            if (uiState.wifiOnly) R.string.a11y_state_on else R.string.a11y_state_off
                                        )
                                    }
                            )
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.settings_update_time_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_update_time_value, uiState.scheduledUpdateHour), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.scheduledUpdateHour.toFloat(),
                            onValueChange = { viewModel.setScheduledUpdateHour(it.toInt()) },
                            valueRange = 0f..23f,
                            steps = 22
                        )
                        Button(
                            onClick = onTriggerSync,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_btn_update_now))
                        }
                    }
                }

                // Section: Anzeige
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.settings_section_display),
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.availableLanguages.forEach { language ->
                                FilterChip(
                                    selected = uiState.preferredLanguage == language,
                                    onClick = { viewModel.setPreferredLanguage(language) },
                                    label = { Text(language.uppercase()) }
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.settings_currency_source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Section: Preisalarme
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.settings_price_alerts_title),
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(stringResource(R.string.settings_price_alerts_desc), style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_price_alerts_enabled), modifier = Modifier.weight(1f))
                            Switch(
                                checked = uiState.priceAlertsEnabled,
                                onCheckedChange = viewModel::setPriceAlertsEnabled,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics {
                                        stateDescription = context.getString(
                                            if (uiState.priceAlertsEnabled) R.string.a11y_state_on else R.string.a11y_state_off
                                        )
                                    }
                            )
                        }
                        Text(stringResource(R.string.settings_price_alert_threshold, uiState.priceAlertThresholdPercent))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PRICE_ALERT_THRESHOLDS.forEach { threshold ->
                                FilterChip(
                                    selected = uiState.priceAlertThresholdPercent == threshold,
                                    onClick = { viewModel.setPriceAlertThresholdPercent(threshold) },
                                    enabled = uiState.priceAlertsEnabled,
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.settings_price_alert_threshold_option,
                                                threshold
                                            )
                                        )
                                    }
                                )
                            }
                        }
                        Text(stringResource(R.string.settings_price_alert_window), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.priceAlertWindowDays == 1,
                                onClick = { viewModel.setPriceAlertWindowDays(1) },
                                enabled = uiState.priceAlertsEnabled,
                                label = { Text(stringResource(R.string.settings_price_alert_24h)) }
                            )
                            FilterChip(
                                selected = uiState.priceAlertWindowDays == 7,
                                onClick = { viewModel.setPriceAlertWindowDays(7) },
                                enabled = uiState.priceAlertsEnabled,
                                label = { Text(stringResource(R.string.settings_price_alert_7d)) }
                            )
                        }
                        Text(stringResource(R.string.settings_price_alert_permission_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Section: Sammlung sichern
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.settings_section_backup),
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val hasContent = collectionRepo.getAllUserCardsOnce().isNotEmpty() || collectionRepo.getWishlistOnce().isNotEmpty()
                                    withContext(Dispatchers.Main) {
                                        if (hasContent) exportJsonLauncher.launch("tcg-sammlung-${LocalDate.now()}.json")
                                        else statusDialogMessage = msgNothingToExport
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_export_json))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val hasContent = collectionRepo.getAllUserCardsOnce().isNotEmpty() || collectionRepo.getWishlistOnce().isNotEmpty()
                                    withContext(Dispatchers.Main) {
                                        if (hasContent) exportCsvLauncher.launch("tcg-sammlung-${LocalDate.now()}.csv")
                                        else statusDialogMessage = msgNothingToExport
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_export_csv))
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/csv"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_import))
                        }
                    }
                }

                // Section: Navigation & Diagnose
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.settings_section_navigation),
                            modifier = Modifier.semantics { heading() },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = { showGenDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_filter_generations))
                        }
                        Button(
                            onClick = onNavigateToDiagnostics,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_diagnostics))
                        }
                        OutlinedButton(
                            onClick = onShowAbout,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_about))
                        }
                    }
                }
            }
        }
    }
}
