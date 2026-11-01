package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardCondition
import com.pokemontcg.sammlung.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSelectionDialog(
    card: CardEntity,
    isPrimary: Boolean,
    category: String,
    initialQuantity: Int = 1,
    initialCondition: String? = null,
    initialLanguage: String = "de",
    initialNotes: String? = null,
    onPrimaryChange: (Boolean) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDetailsChange: (quantity: Int, condition: String?, language: String, notes: String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var primary by remember { mutableStateOf(isPrimary) }
    var selectedCategory by remember { mutableStateOf(category) }
    var quantity by remember { mutableIntStateOf(initialQuantity.coerceAtLeast(1)) }
    var condition by remember { mutableStateOf(initialCondition ?: CardCondition.NEAR_MINT.name) }
    var language by remember { mutableStateOf(initialLanguage.ifBlank { "de" }) }
    var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
    val decreaseQuantityDescription = stringResource(R.string.ui_quantity_decrease)
    val increaseQuantityDescription = stringResource(R.string.ui_quantity_increase)

    var conditionExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    val conditions = listOf(
        CardCondition.MINT.name to stringResource(R.string.ui_condition_mint),
        CardCondition.NEAR_MINT.name to stringResource(R.string.ui_condition_near_mint),
        CardCondition.EXCELLENT.name to stringResource(R.string.ui_condition_excellent),
        CardCondition.GOOD.name to stringResource(R.string.ui_condition_good),
        CardCondition.LIGHT_PLAYED.name to stringResource(R.string.ui_condition_light_played),
        CardCondition.PLAYED.name to stringResource(R.string.ui_condition_played),
        CardCondition.POOR.name to stringResource(R.string.ui_condition_poor)
    )

    val languages = listOf(
        "de" to stringResource(R.string.ui_language_german),
        "en" to stringResource(R.string.ui_language_english),
        "ja" to stringResource(R.string.ui_language_japanese),
        "fr" to stringResource(R.string.ui_language_french),
        "es" to stringResource(R.string.ui_language_spanish),
        "it" to stringResource(R.string.ui_language_italian),
        "ko" to stringResource(R.string.ui_language_korean)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.ui_karte_in_sammlung_bearbeiten_7e5caa),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TcgImage(
                    model = card.largeImageUrl ?: card.imageUrl,
                    contentDescription = stringResource(R.string.ui_card_image_description, card.name, card.number, card.setName),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )

                Text(
                    card.name,
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(stringResource(R.string.ui_card_set_number, card.setName, card.number))
                Text(
                    PriceFormatter.formatPrice(context, card.marketPrice, dateString = card.setReleaseDate),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

                // Quantity selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.ui_anzahl_in_sammlung_ecc4a6), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1,
                            modifier = Modifier.semantics {
                                contentDescription = decreaseQuantityDescription
                            }
                        ) {
                            Text(stringResource(R.string.ui_text_3bc15c), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(quantity.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        OutlinedIconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.semantics {
                                contentDescription = increaseQuantityDescription
                            }
                        ) {
                            Text(stringResource(R.string.ui_text_a979ef), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Category selector
                Text(stringResource(R.string.ui_kategorie_typ_4aa6c2), fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == CardCategory.NONE.name,
                        onClick = {
                            selectedCategory = CardCategory.NONE.name
                            onCategoryChange(selectedCategory)
                        },
                        label = { Text(stringResource(R.string.ui_ohne_f09dc5)) }
                    )
                    FilterChip(
                        selected = selectedCategory == CardCategory.COMMON.name,
                        onClick = {
                            selectedCategory = CardCategory.COMMON.name
                            onCategoryChange(selectedCategory)
                        },
                        label = { Text(stringResource(R.string.ui_common_7de90a)) }
                    )
                    FilterChip(
                        selected = selectedCategory == CardCategory.FULLART.name,
                        onClick = {
                            selectedCategory = CardCategory.FULLART.name
                            onCategoryChange(selectedCategory)
                        },
                        label = { Text(stringResource(R.string.ui_full_art_952d8e)) }
                    )
                }

                // Condition Dropdown
                ExposedDropdownMenuBox(
                    expanded = conditionExpanded,
                    onExpandedChange = { conditionExpanded = !conditionExpanded }
                ) {
                    OutlinedTextField(
                        value = conditions.find { it.first == condition }?.second ?: condition,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ui_zustand_97574a)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = conditionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = conditionExpanded,
                        onDismissRequest = { conditionExpanded = false }
                    ) {
                        conditions.forEach { (condKey, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    condition = condKey
                                    conditionExpanded = false
                                }
                            )
                        }
                    }
                }

                // Language Dropdown
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = !languageExpanded }
                ) {
                    OutlinedTextField(
                        value = languages.find { it.first == language }?.second ?: language,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ui_sprache_der_karte_b92289)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        languages.forEach { (langKey, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    language = langKey
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.ui_notizen_anmerkungen_9551be)) },
                    placeholder = { Text(stringResource(R.string.ui_z_b_first_edition_holo_zustand_detai_44009d)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Primary Chip
                FilterChip(
                    selected = primary,
                    onClick = {
                        primary = !primary
                        onPrimaryChange(primary)
                    },
                    label = { Text(stringResource(R.string.ui_prim_rkarte_845a5e)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDetailsChange(quantity, condition, language, notes.ifBlank { null })
                onConfirm()
            }) {
                Text(stringResource(R.string.ui_speichern_70b73b))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_abbrechen_07af7c)) }
        }
    )
}
