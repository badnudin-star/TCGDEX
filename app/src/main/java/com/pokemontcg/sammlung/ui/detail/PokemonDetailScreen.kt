package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.ui.cards.CardSelectionDialog
import com.pokemontcg.sammlung.ui.cards.PrimaryCardSelectionDialog
import com.pokemontcg.sammlung.ui.cards.SecondaryCardDialog
import com.pokemontcg.sammlung.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    pokemon: PokemonEntity,
    cards: List<CardEntity>,
    userCards: List<UserCardEntityV2>,
    totalPrice: Double?,
    onBackClick: () -> Unit,
    onSelectPrimary: (CardEntity) -> Unit,
    onRemovePrimary: () -> Unit,
    onAddSecondary: (CardEntity, CardCategory) -> Unit,
    onRemoveSecondary: (String) -> Unit,
    onClassificationChange: (String) -> Unit
) {
    var showPrimaryDialog by remember { mutableStateOf(false) }
    var showSecondaryDialog by remember { mutableStateOf(false) }
    var selectedCardForEdit by remember { mutableStateOf<CardEntity?>(null) }

    val primaryUserCard = userCards.firstOrNull { it.isPrimary }
    val primaryCard = cards.firstOrNull { it.id == primaryUserCard?.cardId }
    val secondaryUserCards = userCards.filter { !it.isPrimary }
    val secondaryCards = secondaryUserCards.mapNotNull { userCard ->
        cards.firstOrNull { it.id == userCard.cardId }?.let { it to userCard }
    }

    if (showPrimaryDialog) {
        PrimaryCardSelectionDialog(
            cards = cards,
            currentPrimaryId = primaryCard?.id,
            onSelect = { card ->
                onSelectPrimary(card)
                showPrimaryDialog = false
            },
            onDismiss = { showPrimaryDialog = false }
        )
    }

    if (showSecondaryDialog) {
        SecondaryCardDialog(
            cards = cards,
            userCards = userCards,
            onAddCard = { card, category -> onAddSecondary(card, category) },
            onRemoveCard = onRemoveSecondary,
            onDismiss = { showSecondaryDialog = false }
        )
    }

    selectedCardForEdit?.let { card ->
        val uc = userCards.firstOrNull { it.cardId == card.id }
        CardSelectionDialog(
            card = card,
            isPrimary = uc?.isPrimary ?: false,
            category = uc?.category ?: "NONE",
            initialQuantity = uc?.quantity ?: 1,
            initialCondition = uc?.condition,
            initialLanguage = uc?.language ?: "de",
            initialNotes = uc?.notes,
            onPrimaryChange = { isPri -> if (isPri) onSelectPrimary(card) },
            onCategoryChange = { _ -> },
            onDetailsChange = { _, _, _, _ -> },
            onConfirm = { selectedCardForEdit = null },
            onDismiss = { selectedCardForEdit = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pokemon.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ui_wert_der_ausgew_hlten_karten_6abd48), style = MaterialTheme.typography.titleMedium)
                    Text(PriceFormatter.formatPrice(totalPrice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.ui_prim_rkarte_und_alle_weiteren_karten_80d843), style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_kategorie_f_r_die_bersicht_c2c26d), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Common" to "Common", "Fullart" to "Full Art", "NONE" to "Ohne Kategorie").forEach { (typeKey, label) ->
                            FilterChip(
                                selected = pokemon.cardType.name.equals(typeKey, ignoreCase = true),
                                onClick = { onClassificationChange(typeKey) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Primary Card Section
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_prim_rkarte_845a5e), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showPrimaryDialog = true }) {
                            Text(stringResource(R.string.ui_ausw_hlen_2ac692))
                        }
                    }

                    if (primaryCard != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedCardForEdit = primaryCard },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TcgImage(
                                model = primaryCard.largeImageUrl ?: primaryCard.imageUrl,
                                contentDescription = stringResource(R.string.ui_primary_card_description, primaryCard.name, primaryCard.number, primaryCard.setName),
                                modifier = Modifier.size(80.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(primaryCard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.ui_card_set_number_hash, primaryCard.setName, primaryCard.number), style = MaterialTheme.typography.bodySmall)
                                Text(PriceFormatter.formatPriceShort(primaryCard.marketPrice), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onRemovePrimary) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_remove_primary_card_named, primaryCard.name), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.ui_noch_keine_prim_rkarte_ausgew_hlt_785232), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Secondary Cards Section
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_weitere_karten_c7ec5c), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showSecondaryDialog = true }) {
                            Text(stringResource(R.string.ui_verwalten_d0d41d))
                        }
                    }

                    if (secondaryCards.isEmpty()) {
                        Text(stringResource(R.string.ui_noch_keine_weiteren_karten_ausgew_hl_c3316b), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        secondaryCards.forEach { (card, _) ->
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { selectedCardForEdit = card },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TcgImage(
                                    model = card.imageUrl,
                                    contentDescription = stringResource(R.string.ui_card_image_description, card.name, card.number, card.setName),
                                    modifier = Modifier.size(60.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(card.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.ui_card_set_number_compact, card.setName, card.number), style = MaterialTheme.typography.bodySmall)
                                    Text(PriceFormatter.formatPriceShort(card.marketPrice), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onRemoveSecondary(card.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_remove_secondary_card_named, card.name), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
