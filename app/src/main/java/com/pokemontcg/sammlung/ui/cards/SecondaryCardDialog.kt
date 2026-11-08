package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.ui.sets.SetBadge
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.model.MergedCatalogCard
import com.pokemontcg.sammlung.data.model.RarityGrouper
import com.pokemontcg.sammlung.data.model.filterMerged
import com.pokemontcg.sammlung.util.PriceFormatter

/**
 * Manages the secondary cards of a Pokémon. Language prints of the same artwork are merged
 * into one entry (like the wishlist catalog search); the rarity filter uses the canonical
 * English groups and applies to every language print.
 */
@Composable
fun SecondaryCardDialog(
    cards: List<MergedCatalogCard>,
    userCards: List<UserCardEntityV2>,
    onAddCard: (CardEntity, CardCategory) -> Unit,
    onRemoveCard: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRarityGroups by remember { mutableStateOf(setOf<String>()) }

    val rarityGroups = remember(cards) {
        RarityGrouper.chipGroups(cards.mapNotNull { it.representative.rarity })
    }

    val filteredCards = remember(cards, searchQuery, selectedRarityGroups) {
        cards.filterMerged(searchQuery, selectedRarityGroups)
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
                if (rarityGroups.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rarityGroups.forEach { rarityGroup ->
                            FilterChip(
                                selected = rarityGroup in selectedRarityGroups,
                                onClick = {
                                    selectedRarityGroups =
                                        if (rarityGroup in selectedRarityGroups) selectedRarityGroups - rarityGroup
                                        else selectedRarityGroups + rarityGroup
                                },
                                label = { Text(rarityGroup) }
                            )
                        }
                        if (selectedRarityGroups.isNotEmpty()) {
                            AssistChip(
                                onClick = { selectedRarityGroups = emptySet() },
                                label = { Text(stringResource(R.string.wishlist_search_rarity_clear)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.ui_icon_clear),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
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
                        items(filteredCards, key = { it.representative.id }) { card ->
                            val representative = card.representative
                            // The favourite print of this artwork — shown with a star, without
                            // add/delete chips so it cannot be mutated as a secondary card.
                            val isFavourite = userCards.any { it.isPrimary && it.cardId in card.variantIds }
                            // Ownership counts regardless of which language print is stored.
                            val userCard = userCards.firstOrNull { !it.isPrimary && it.cardId in card.variantIds }
                            val isAdded = userCard != null
                            val category = userCard?.category
                            val rarityGroup = RarityGrouper.canonical(representative.rarity)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isFavourite -> MaterialTheme.colorScheme.tertiaryContainer
                                        isAdded -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TcgImage(
                                        model = representative.imageUrl,
                                        contentDescription = stringResource(R.string.ui_card_image_description, representative.name, representative.number, representative.setName),
                                        modifier = Modifier.size(60.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isFavourite) {
                                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.ui_icon_primary_card), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(representative.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            val setImage = representative.setSymbolUrl ?: representative.setLogoUrl
                                            if (!setImage.isNullOrBlank()) {
                                                TcgImage(
                                                    model = setImage,
                                                    contentDescription = stringResource(R.string.ui_set_symbol_description, representative.setName),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else if (representative.setAbbreviation != null || representative.setName.isNotBlank()) {
                                                SetBadge(
                                                    setAbbreviation = representative.setAbbreviation,
                                                    setName = representative.setName,
                                                    setId = representative.setId,
                                                    size = 18.dp,
                                                    contentDescription = stringResource(R.string.ui_set_symbol_description, representative.setName)
                                                )
                                            }
                                            Text(representative.setName, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            stringResource(
                                                R.string.ui_card_number_rarity,
                                                representative.number,
                                                if (rarityGroup == "None") stringResource(R.string.ui_keine_oder_noch_ohne_) else rarityGroup
                                            ),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        if (isFavourite) {
                                            Text(
                                                stringResource(R.string.ui_prim_rkarte_845a5e),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        } else if (card.languages.size > 1) {
                                            Text(
                                                stringResource(
                                                    R.string.wishlist_available_languages,
                                                    card.languages.joinToString(" · ")
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (category == CardCategory.COMMON.name) Text(stringResource(R.string.ui_common_7de90a), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                            if (category == CardCategory.FULLART.name) Text(stringResource(R.string.ui_full_art_952d8e), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            PriceFormatter.formatPriceShort(
                                                representative.marketPrice,
                                                stringResource(R.string.price_no_price_short)
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        // An owned row gets the delete button; new cards
                                        // get the chips — except favourites, which are
                                        // informational only and must not be mutated here.
                                        when {
                                            userCard != null -> {
                                                // Removes exactly the stored row, not every
                                                // language print that shares the card id.
                                                IconButton(onClick = { onRemoveCard(userCard.id) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_remove_card_named, representative.name), tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            !isFavourite -> {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    FilterChip(
                                                        selected = false,
                                                        onClick = { onAddCard(representative, CardCategory.COMMON) },
                                                        label = { Text(stringResource(R.string.ui_common_7de90a)) }
                                                    )
                                                    FilterChip(
                                                        selected = false,
                                                        onClick = { onAddCard(representative, CardCategory.FULLART) },
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_schlie_en_44424b)) } }
    )
}
