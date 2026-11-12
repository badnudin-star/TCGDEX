package com.pokemontcg.sammlung.ui.sets

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.model.MergedCatalogCard
import com.pokemontcg.sammlung.data.model.RarityGrouper
import com.pokemontcg.sammlung.data.model.RarityGroupLocalization
import com.pokemontcg.sammlung.data.model.rawCardId
import com.pokemontcg.sammlung.ui.components.CardImageDialog
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.ui.components.TcgLogoImage
import com.pokemontcg.sammlung.util.PriceFormatter

/**
 * Full card list of one set: every card of the set ordered by card number, trainers and
 * energies included, language prints merged into one entry each. Owned cards carry a badge
 * with the quantity, so it is immediately visible what is still missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    viewModel: SetDetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var enlargedCard by remember { mutableStateOf<MergedCatalogCard?>(null) }
    val enlargeLabel = stringResource(R.string.wishlist_card_image_enlarge)

    enlargedCard?.let { card ->
        CardImageDialog(
            imageUrl = card.representative.largeImageUrl ?: card.representative.imageUrl,
            contentDescription = stringResource(
                R.string.ui_card_image_description,
                card.representative.name,
                card.representative.number,
                card.representative.setName
            ),
            onDismiss = { enlargedCard = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.set?.name.orEmpty(),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            uiState.set == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.set_detail_not_found), textAlign = TextAlign.Center)
            }
            else -> SetDetailContent(
                state = uiState,
                onEnlargeImage = { enlargedCard = it },
                enlargeLabel = enlargeLabel,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun SetDetailContent(
    state: SetDetailViewModel.UiState,
    onEnlargeImage: (MergedCatalogCard) -> Unit,
    enlargeLabel: String,
    modifier: Modifier = Modifier
) {
    val set = state.set ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "set_detail_header") {
            SetDetailHeader(set = set, state = state)
        }
        if (state.cards.isEmpty()) {
            item(key = "set_detail_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.set_detail_no_cards))
                }
            }
        } else {
            item(key = "set_detail_all_cards_title") {
                Text(
                    stringResource(R.string.set_detail_all_cards, state.cards.size),
                    modifier = Modifier.semantics { heading() },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.cards, key = { it.representative.id }) { card ->
                SetCardRow(
                    card = card,
                    ownedQuantity = state.ownedQuantitiesByRawId[rawCardId(card.representative.id)] ?: 0,
                    showEnglishName = state.showEnglishNames,
                    onEnlargeImage = onEnlargeImage,
                    enlargeLabel = enlargeLabel
                )
            }
        }
    }
}

@Composable
private fun SetDetailHeader(
    set: SetEntity,
    state: SetDetailViewModel.UiState
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!set.symbolUrl.isNullOrBlank()) {
                    TcgLogoImage(
                        model = set.symbolUrl,
                        contentDescription = stringResource(R.string.ui_set_symbol_description, set.name),
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    // Sets without a symbol image get a generated abbreviation badge.
                    SetBadge(
                        setAbbreviation = set.setAbbreviation,
                        setName = set.name,
                        setId = set.id,
                        size = 32.dp,
                        contentDescription = stringResource(R.string.ui_set_symbol_description, set.name)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                if (!set.logoUrl.isNullOrBlank()) {
                    TcgLogoImage(
                        model = set.logoUrl,
                        contentDescription = stringResource(R.string.ui_set_logo_description, set.name),
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(max = 110.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        set.name,
                        modifier = Modifier.semantics { heading() },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(
                            R.string.ui_set_series_release,
                            set.series,
                            set.releaseDate.ifBlank { stringResource(R.string.ui_unbekannt) }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (state.setLanguages.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.wishlist_available_languages,
                        state.setLanguages.joinToString(" · ") { it.uppercase() }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.cards.isNotEmpty()) {
                Text(
                    stringResource(R.string.set_detail_owned_summary, state.ownedCardCount, state.cards.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = {
                        if (state.cards.isEmpty()) 0f
                        else state.ownedCardCount.toFloat() / state.cards.size.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }
    }
}

@Composable
private fun SetCardRow(
    card: MergedCatalogCard,
    ownedQuantity: Int,
    showEnglishName: Boolean,
    onEnlargeImage: (MergedCatalogCard) -> Unit,
    enlargeLabel: String
) {
    val representative = card.representative
    val rarityGroup = RarityGrouper.canonical(representative.rarity)
    // Local capture: englishName has a custom getter and cannot be smart-cast.
    val englishName = card.englishName
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TcgImage(
                model = representative.imageUrl,
                contentDescription = stringResource(
                    R.string.ui_card_image_description,
                    representative.name,
                    representative.number,
                    representative.setName
                ),
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClickLabel = enlargeLabel) { onEnlargeImage(card) }
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.set_detail_card_number, representative.number),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    representative.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (showEnglishName && englishName != null && englishName != representative.name) {
                    Text(
                        stringResource(R.string.ui_english_name_hint, englishName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (rarityGroup != "None") {
                    Text(RarityGroupLocalization.localizedLabel(rarityGroup), style = MaterialTheme.typography.labelSmall)
                }
                if (card.languages.size > 1) {
                    Text(
                        stringResource(
                            R.string.wishlist_available_languages,
                            card.languages.joinToString(" · ")
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    PriceFormatter.formatPriceShort(
                        representative.marketPrice,
                        stringResource(R.string.price_no_price_short)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (ownedQuantity > 0) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (ownedQuantity > 1) {
                                stringResource(R.string.set_detail_owned_quantity, ownedQuantity)
                            } else {
                                stringResource(R.string.set_detail_owned)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
