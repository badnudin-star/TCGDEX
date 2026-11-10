package com.pokemontcg.sammlung.ui.detail

import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.ui.sets.setLabelWithAbbreviation
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.model.MergedCatalogCard
import com.pokemontcg.sammlung.data.model.rawCardId
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.CardGrading
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.ui.cards.CardCategoryDialog
import com.pokemontcg.sammlung.ui.cards.CardSelectionDialog
import com.pokemontcg.sammlung.ui.cards.PrimaryCardSelectionDialog
import com.pokemontcg.sammlung.ui.cards.SecondaryCardDialog
import com.pokemontcg.sammlung.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    pokemon: PokemonEntity,
    cards: List<CardEntity>,
    /** Language prints merged per artwork, for the favourite and secondary card dialogs. */
    mergedCards: List<MergedCatalogCard>,
    /** English card names as an additional display line; off by default. */
    showEnglishNames: Boolean = false,
    userCards: List<UserCardEntityV2>,
    wishlistCardIds: Set<String>,
    totalPrice: Double?,
    onBackClick: () -> Unit,
    onSelectPrimary: (CardEntity, CardCategory) -> Unit,
    onRemovePrimary: () -> Unit,
    onAddSecondary: (CardEntity, CardCategory) -> Unit,
    onRemoveSecondary: (Long) -> Unit,
    onToggleWishlist: (CardEntity) -> Unit,
    onUpdateCardDetails: (rowId: Long, quantity: Int, condition: String?, language: String, notes: String?, category: String, grading: CardGrading) -> Unit,
    onClassificationChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showPrimaryDialog by remember { mutableStateOf(false) }
    var showSecondaryDialog by remember { mutableStateOf(false) }
    var selectedCardForEdit by remember { mutableStateOf<Pair<CardEntity, UserCardEntityV2>?>(null) }
    var pendingCategory by remember { mutableStateOf(CardCategory.NONE.name) }
    // Card picked as favourite but not classified yet; the category popup follows immediately.
    var pendingPrimaryCard by remember { mutableStateOf<CardEntity?>(null) }

    fun openCardEditor(card: CardEntity, userCard: UserCardEntityV2) {
        pendingCategory = userCard.category.ifBlank { CardCategory.NONE.name }
        selectedCardForEdit = card to userCard
    }

    val primaryUserCard = userCards.firstOrNull { it.isPrimary }
    val primaryCard = cards.firstOrNull { it.id == primaryUserCard?.cardId }
    val primaryCategory = primaryUserCard?.let { userCard ->
        CardCategory.entries.firstOrNull { it.name.equals(userCard.category, ignoreCase = true) }
            ?: CardCategory.NONE
    }
    val secondaryUserCards = userCards.filter { !it.isPrimary }
    val secondaryCards = secondaryUserCards.mapNotNull { userCard ->
        cards.firstOrNull { it.id == userCard.cardId }?.let { it to userCard }
    }

    if (showPrimaryDialog) {
        PrimaryCardSelectionDialog(
            cards = mergedCards,
            currentPrimaryId = primaryCard?.id,
            onSelect = { card ->
                showPrimaryDialog = false
                pendingPrimaryCard = card
            },
            onDismiss = { showPrimaryDialog = false }
        )
    }

    pendingPrimaryCard?.let { card ->
        // Re-selecting the same favourite keeps its stored category instead of resetting it.
        val storedCategory = userCards
            .firstOrNull { it.cardId == card.id }
            ?.let { userCard ->
                CardCategory.entries.firstOrNull { it.name.equals(userCard.category, ignoreCase = true) }
            } ?: CardCategory.NONE
        CardCategoryDialog(
            card = card,
            initialCategory = storedCategory,
            onConfirm = { category ->
                onSelectPrimary(card, category)
                pendingPrimaryCard = null
            },
            onDismiss = { pendingPrimaryCard = null }
        )
    }

    if (showSecondaryDialog) {
        SecondaryCardDialog(
            cards = mergedCards,
            userCards = userCards,
            onAddCard = { card, category -> onAddSecondary(card, category) },
            onRemoveCard = onRemoveSecondary,
            onDismiss = { showSecondaryDialog = false }
        )
    }

    selectedCardForEdit?.let { (card, uc) ->
        CardSelectionDialog(
            card = card,
            isPrimary = uc.isPrimary,
            category = uc.category,
            initialQuantity = uc.quantity,
            initialCondition = uc.condition,
            initialLanguage = uc.language,
            initialNotes = uc.notes,
            initialGrading = CardGrading(uc.gradingCompany, uc.grade, uc.certNumber),
            onPrimaryChange = { isPrimary ->
                if (isPrimary) {
                    val category = CardCategory.entries.firstOrNull { it.name == pendingCategory }
                        ?: CardCategory.NONE
                    onSelectPrimary(card, category)
                } else {
                    onRemovePrimary()
                }
            },
            onCategoryChange = { selectedCategory -> pendingCategory = selectedCategory },
            onDetailsChange = { quantity, condition, language, notes, grading ->
                onUpdateCardDetails(
                    uc.id,
                    quantity,
                    condition,
                    language,
                    notes,
                    pendingCategory,
                    grading
                )
            },
            onConfirm = { selectedCardForEdit = null },
            onDismiss = { selectedCardForEdit = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pokemon.name,
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.ui_wert_der_ausgew_hlten_karten_6abd48),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(PriceFormatter.formatPrice(context, totalPrice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.ui_prim_rkarte_und_alle_weiteren_karten_80d843), style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.ui_kategorie_f_r_die_bersicht_c2c26d),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            CardCategory.COMMON.name to stringResource(R.string.main_filter_common),
                            CardCategory.FULLART.name to stringResource(R.string.main_filter_fullart),
                            CardCategory.NONE.name to stringResource(R.string.main_filter_none)
                        ).forEach { (typeKey, label) ->
                            FilterChip(
                                selected = primaryCategory?.name.equals(typeKey, ignoreCase = true),
                                onClick = { onClassificationChange(typeKey) },
                                enabled = primaryUserCard != null,
                                label = { Text(label) }
                            )
                        }
                    }
                    if (primaryUserCard == null) {
                        Text(
                            stringResource(R.string.detail_category_requires_primary),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Primary Card Section
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ui_prim_rkarte_845a5e),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showPrimaryDialog = true }) {
                            Text(stringResource(R.string.ui_ausw_hlen_2ac692))
                        }
                    }

                    if (primaryCard != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    if (primaryUserCard != null) {
                                        openCardEditor(primaryCard, primaryUserCard)
                                    }
                                },
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
                                Text(
                                    stringResource(
                                        R.string.ui_card_set_number_hash,
                                        setLabelWithAbbreviation(primaryCard.setName, primaryCard.setAbbreviation),
                                        primaryCard.number
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (showEnglishNames) {
                                    englishNameFor(primaryCard, cards)?.let { englishName ->
                                        Text(
                                            stringResource(R.string.ui_english_name_hint, englishName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                primaryUserCard?.takeIf { !it.gradingCompany.isNullOrBlank() }?.let { graded ->
                                    Text(
                                        stringResource(
                                            R.string.ui_grading_summary,
                                            graded.gradingCompany.orEmpty(),
                                            graded.grade.orEmpty().ifBlank { "—" }
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(PriceFormatter.formatPriceShort(context, primaryCard.marketPrice), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            val isWishlisted = primaryCard.id in wishlistCardIds
                            IconButton(onClick = { onToggleWishlist(primaryCard) }) {
                                Icon(
                                    imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(
                                        if (isWishlisted) R.string.detail_wishlist_remove else R.string.detail_wishlist_add,
                                        primaryCard.name
                                    ),
                                    tint = if (isWishlisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        Text(
                            stringResource(R.string.ui_weitere_karten_c7ec5c),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showSecondaryDialog = true }) {
                            Text(stringResource(R.string.ui_verwalten_d0d41d))
                        }
                    }

                    if (secondaryCards.isEmpty()) {
                        Text(stringResource(R.string.ui_noch_keine_weiteren_karten_ausgew_hl_c3316b), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        secondaryCards.forEach { (card, userCard) ->
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { openCardEditor(card, userCard) },
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
                                    Text(
                                        stringResource(
                                            R.string.ui_card_set_number_compact,
                                            setLabelWithAbbreviation(card.setName, card.setAbbreviation),
                                            card.number
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(PriceFormatter.formatPriceShort(context, card.marketPrice), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                val isWishlisted = card.id in wishlistCardIds
                                IconButton(onClick = { onToggleWishlist(card) }) {
                                    Icon(
                                        imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(
                                            if (isWishlisted) R.string.detail_wishlist_remove else R.string.detail_wishlist_add,
                                            card.name
                                        ),
                                        tint = if (isWishlisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemoveSecondary(userCard.id) }) {
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

/**
 * The English print of the same artwork (same raw id), or null when the catalog has no
 * English variant - typically the case for Asian-only prints.
 */
private fun englishNameFor(card: CardEntity, allCards: List<CardEntity>): String? =
    allCards.firstOrNull { it.language == "en" && rawCardId(it.id) == rawCardId(card.id) }?.name
