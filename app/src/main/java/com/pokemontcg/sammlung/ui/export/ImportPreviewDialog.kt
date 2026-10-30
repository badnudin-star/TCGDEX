package com.pokemontcg.sammlung.ui.export

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.export.ExportMergeStrategy
import com.pokemontcg.sammlung.data.export.ImportPreview

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirmStrategy: (ExportMergeStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ExportMergeStrategy.SKIP_EXISTING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_sammlung_importieren_vorschau_29d15d)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.ui_read_records, preview.totalRead))
                Text(
                    stringResource(
                        R.string.import_dialog_summary,
                        preview.newCardsCount,
                        preview.existingCardsCount,
                        preview.unknownCardIds.size
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

                Text(stringResource(R.string.ui_import_strategie_w_hlen_f98493), fontWeight = FontWeight.Bold)

                val strategies = listOf(
                    ExportMergeStrategy.SKIP_EXISTING to stringResource(R.string.ui_import_strategy_skip),
                    ExportMergeStrategy.OVERWRITE_EXISTING to stringResource(R.string.ui_import_strategy_overwrite),
                    ExportMergeStrategy.MERGE_ADD to stringResource(R.string.ui_import_strategy_merge)
                )

                strategies.forEach { (strategy, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStrategy == strategy,
                            onClick = { selectedStrategy = strategy }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmStrategy(selectedStrategy)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ui_import_starten_16dd74))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_abbrechen_07af7c))
            }
        }
    )
}
