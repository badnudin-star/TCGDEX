package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory

/**
 * Asks how a freshly picked favourite card should be classified. Shown right after the card
 * selection so the Common/Full Art filter in the main screen is set in the same step instead of
 * through a separate chip row the user has to find first.
 */
@Composable
fun CardCategoryDialog(
    card: CardEntity,
    initialCategory: CardCategory,
    onConfirm: (CardCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(card.id, initialCategory) { mutableStateOf(initialCategory) }

    val options = listOf(
        CardCategory.FULLART to stringResource(R.string.category_dialog_option_fullart),
        CardCategory.COMMON to stringResource(R.string.category_dialog_option_common),
        CardCategory.NONE to stringResource(R.string.category_dialog_option_none)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.category_dialog_title, card.name),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.category_dialog_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(
                        R.string.category_dialog_card,
                        card.setName,
                        card.number,
                        card.rarity?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.ui_keine_oder_noch_ohne_)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                options.forEach { (category, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { selected = category },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RadioButton(
                            selected = selected == category,
                            onClick = { selected = category }
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.ui_speichern_70b73b))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_abbrechen_07af7c))
            }
        }
    )
}
