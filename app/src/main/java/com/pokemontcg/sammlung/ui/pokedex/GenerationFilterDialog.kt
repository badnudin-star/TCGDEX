package com.pokemontcg.sammlung.ui.pokedex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.model.GenerationModel

@Composable
fun GenerationFilterDialog(
    hiddenGenerations: Set<Int>,
    onHiddenGenerationsChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var localHidden by remember(hiddenGenerations) { mutableStateOf(hiddenGenerations) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generation_hide_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.generation_hide_dialog_description),
                    style = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()

                GenerationModel.GENERATIONS.forEach { gen ->
                    val isHidden = localHidden.contains(gen.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.ui_generation_title_region, gen.name, gen.region), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.generation_dex_range, gen.startDex.toString(), if (gen.endDex == Int.MAX_VALUE) "∞" else gen.endDex.toString()), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = !isHidden,
                            onCheckedChange = { isVisible ->
                                localHidden = if (isVisible) {
                                    localHidden - gen.id
                                } else {
                                    localHidden + gen.id
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onHiddenGenerationsChange(localHidden)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.generation_apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    localHidden = emptySet()
                    onHiddenGenerationsChange(emptySet())
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.generation_show_all))
            }
        }
    )
}
