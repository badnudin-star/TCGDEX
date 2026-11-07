package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.util.PriceFormatter

@Composable
fun SecondaryCardDialog(
    cards: List<CardEntity>,
    userCards: List<UserCardEntityV2>,
    onAddCard: (CardEntity, CardCategory) -> Unit,
    onRemoveCard: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCards = remember(cards, searchQuery) {
        val queryTokens = searchQuery.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) cards else cards.filter { card ->
            val searchable = listOf(
                card.name, card.setName, card.setId, card.series, card.number,
                card.rarity.orEmpty(), card.language, card.subtypes.orEmpty(),
                card.setReleaseDate.orEmpty(), card.setCardCountOfficial?.toString().orEmpty(),
                card.setCardCountTotal?.toString().orEmpty()
            ).joinToString(" ").lowercase(Locale.ROOT)
            queryTokens.all(searchable::contains)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.ui_weitere_karten_verwalten_9c849a),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.ui_karte_oder_set_suchen_dc3b5f)) },
                    placeholder = { Text(stringResource(R.string.ui_name_set_nummer_sprache_058f1e)) }
                )
                if (cards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_keine_karten_f_r_dieses_pok_mon_vorh_53cb93))
                    }
                } else if (filteredCards.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_keine_passenden_karten_gefunden_e6f6bf))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredCards, key = { it.id }) { card ->
                            val userCard = userCards.firstOrNull { it.cardId == card.id && !it.isPrimary }
                            val isAdded = userCard != null
                            val category = userCard?.category

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = if (isAdded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TcgImage(
                                        model = card.imageUrl,
                                        contentDescription = stringResource(R.string.ui_card_image_description, card.name, card.number, card.setName),
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(card.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            val setImage = card.setSymbolUrl ?: card.setLogoUrl
                                            if (!setImage.isNullOrBlank()) {
                                                TcgImage(
                                                    model = setImage,
                                                    contentDescription = stringResource(R.string.ui_set_symbol_description, card.setName),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(card.setName, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(stringResource(R.string.ui_card_number_rarity, card.number, card.rarity ?: stringResource(R.string.ui_keine_oder_noch_ohne_)) , style = MaterialTheme.typography.labelSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (category == CardCategory.COMMON.name) Text(stringResource(R.string.ui_common_7de90a), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                            if (category == CardCategory.FULLART.name) Text(stringResource(R.string.ui_full_art_952d8e), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            PriceFormatter.formatPriceShort(
                                                card.marketPrice,
                                                stringResource(R.string.price_no_price_short)
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isAdded) {
                                            IconButton(onClick = { onRemoveCard(card.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_remove_card_named, card.name), tint = MaterialTheme.colorScheme.error)
                                            }
                                        } else {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                FilterChip(
                                                    selected = false,
                                                    onClick = { onAddCard(card, CardCategory.COMMON) },
                                                    label = { Text(stringResource(R.string.ui_common_7de90a)) }
                                                )
                                                FilterChip(
                                                    selected = false,
                                                    onClick = { onAddCard(card, CardCategory.FULLART) },
                                                    label = { Text(stringResource(R.string.ui_full_art_952d8e)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_schlie_en_44424b)) } }
    )
}
