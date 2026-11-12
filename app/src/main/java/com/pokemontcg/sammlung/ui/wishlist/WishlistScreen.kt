package com.pokemontcg.sammlung.ui.wishlist

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pokemontcg.sammlung.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.data.model.CatalogSearchSection
import com.pokemontcg.sammlung.data.model.MergedCatalogCard
import com.pokemontcg.sammlung.data.model.PokemonCardGroup
import com.pokemontcg.sammlung.data.model.GenerationModel
import com.pokemontcg.sammlung.data.model.RarityGrouper
import com.pokemontcg.sammlung.data.model.RarityGroupLocalization
import com.pokemontcg.sammlung.data.export.WishlistTextExporter
import com.pokemontcg.sammlung.ui.components.CardImageDialog
import com.pokemontcg.sammlung.ui.components.TcgImage
import com.pokemontcg.sammlung.ui.sets.setLabelWithAbbreviation
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.util.CardmarketLinks
import com.pokemontcg.sammlung.util.PriceFormatter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WishlistSortOption {
    ADDED_DESC,
    PRICE_ASC,
    TARGET_REACHED_FIRST
}

enum class WishlistTab {
    LIST,
    DATABASE
}

private const val FILE_PROVIDER_AUTHORITY = "com.pokemontcg.sammlung.fileprovider"
private const val SHARE_FILE_NAME = "wunschliste-tcg.txt"

fun isTargetReached(price: Double?, targetPrice: Double?): Boolean {
    return price != null && targetPrice != null && price > 0.0 && targetPrice > 0.0 && price <= targetPrice
}

fun sortWishlist(
    wishlist: List<WishlistEntity>,
    cardMap: Map<String, CardEntity>,
    option: WishlistSortOption
): List<WishlistEntity> {
    return when (option) {
        WishlistSortOption.ADDED_DESC -> wishlist.sortedByDescending { it.addedAt }
        WishlistSortOption.PRICE_ASC -> wishlist.sortedBy { cardMap[it.cardId]?.marketPrice ?: Double.MAX_VALUE }
        WishlistSortOption.TARGET_REACHED_FIRST -> wishlist.sortedByDescending { isTargetReached(cardMap[it.cardId]?.marketPrice, it.targetPrice) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableStateOf(WishlistTab.LIST) }
    var currentSort by rememberSaveable { mutableStateOf(WishlistSortOption.ADDED_DESC) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showShareMenu by rememberSaveable { mutableStateOf(false) }
    var enlargedCard by remember { mutableStateOf<CardEntity?>(null) }

    var editingWishTarget by remember { mutableStateOf<WishlistEntity?>(null) }
    var editingWishNotes by remember { mutableStateOf<WishlistEntity?>(null) }

    val sortedList = remember(uiState.wishlist, uiState.cardMap, currentSort) {
        sortWishlist(uiState.wishlist, uiState.cardMap, currentSort)
    }

    /** The plain-text export always reflects the list exactly as the user currently sees it. */
    fun buildExportText(): String {
        val dateText = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
        return WishlistTextExporter.buildText(
            wishlist = sortedList,
            cardsById = uiState.cardMap,
            headerDateText = dateText,
            noPriceText = context.getString(R.string.price_no_price_short)
        )
    }

    val saveTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(buildExportText().toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            snackbarHostState.showSnackbar(
                context.getString(
                    if (saved) R.string.wishlist_export_saved else R.string.wishlist_export_failed
                )
            )
        }
    }

    /** Writes the export into the cache and hands it to the system share sheet (WhatsApp etc.). */
    fun shareWishlistText() {
        if (sortedList.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.wishlist_export_empty))
            }
            return
        }
        val shareFile = File(context.cacheDir, SHARE_FILE_NAME)
        val intent = runCatching {
            shareFile.writeText(buildExportText(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, shareFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.wishlist_share_subject))
                putExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(R.string.wishlist_share_message, sortedList.size)
                )
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(
                    context.getString(R.string.wishlist_share_subject),
                    uri
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
        if (intent == null) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.wishlist_export_failed))
            }
            return
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.wishlist_share_chooser))
        )
    }

    if (editingWishTarget != null) {
        val wish = editingWishTarget!!
        var targetText by remember { mutableStateOf(wish.targetPrice?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editingWishTarget = null },
            title = {
                Text(
                    stringResource(R.string.ui_zielpreis_festlegen_99510b),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(stringResource(R.string.ui_zielpreis_in_leer_kein_ziel_fe22b8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newPrice = targetText.replace(",", ".").toDoubleOrNull()
                        viewModel.updateWish(wish.copy(targetPrice = newPrice))
                        editingWishTarget = null
                    }
                ) {
                    Text(stringResource(R.string.ui_speichern_70b73b))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWishTarget = null }) {
                    Text(stringResource(R.string.ui_abbrechen_07af7c))
                }
            }
        )
    }

    if (editingWishNotes != null) {
        val wish = editingWishNotes!!
        var notesText by remember { mutableStateOf(wish.notes ?: "") }
        AlertDialog(
            onDismissRequest = { editingWishNotes = null },
            title = {
                Text(
                    stringResource(R.string.ui_notiz_bearbeiten_a47793),
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(stringResource(R.string.ui_notiz_b6c441)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateWish(wish.copy(notes = notesText))
                        editingWishNotes = null
                    }
                ) {
                    Text(stringResource(R.string.ui_speichern_70b73b))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWishNotes = null }) {
                    Text(stringResource(R.string.ui_abbrechen_07af7c))
                }
            }
        )
    }

    enlargedCard?.let { card ->
        CardImageDialog(
            imageUrl = card.largeImageUrl ?: card.imageUrl,
            contentDescription = stringResource(
                R.string.ui_card_image_description,
                card.name,
                card.number,
                card.setName
            ),
            onDismiss = { enlargedCard = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ui_wishlist_title_count, uiState.wishlist.size),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_icon_back))
                    }
                },
                actions = {
                    when (selectedTab) {
                        WishlistTab.LIST -> {
                            Box {
                                IconButton(onClick = { showShareMenu = true }) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(R.string.wishlist_icon_share)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wishlist_export_save_txt)) },
                                        onClick = {
                                            showShareMenu = false
                                            if (sortedList.isEmpty()) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.wishlist_export_empty)
                                                    )
                                                }
                                            } else {
                                                val datePart = SimpleDateFormat("yyyyMMdd", Locale.GERMANY).format(Date())
                                                saveTxtLauncher.launch("wunschliste-$datePart.txt")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.wishlist_export_share)) },
                                        onClick = {
                                            showShareMenu = false
                                            shareWishlistText()
                                        }
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ui_icon_sort))
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_zuletzt_hinzugef_gt_040f67)) },
                                        onClick = {
                                            currentSort = WishlistSortOption.ADDED_DESC
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_preis_aufsteigend_30e797)) },
                                        onClick = {
                                            currentSort = WishlistSortOption.PRICE_ASC
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ui_ziel_erreicht_zuerst_a3e976)) },
                                        onClick = {
                                            currentSort = WishlistSortOption.TARGET_REACHED_FIRST
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        WishlistTab.DATABASE -> if (uiState.searchParams.hasActiveFilter) {
                            TextButton(onClick = viewModel::clearSearchFilters) {
                                Text(stringResource(R.string.wishlist_search_clear_filters))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = if (selectedTab == WishlistTab.LIST) 0 else 1) {
                Tab(
                    selected = selectedTab == WishlistTab.LIST,
                    onClick = { selectedTab = WishlistTab.LIST },
                    text = { Text(stringResource(R.string.wishlist_tab_list)) }
                )
                Tab(
                    selected = selectedTab == WishlistTab.DATABASE,
                    onClick = { selectedTab = WishlistTab.DATABASE },
                    text = { Text(stringResource(R.string.wishlist_tab_database)) }
                )
            }

            // The tab content shares the remaining height instead of filling the whole column,
            // otherwise the list would be as tall as the screen and lose its last entry behind it.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    WishlistTab.LIST -> WishlistListTab(
                        wishlist = uiState.wishlist,
                        sortedList = sortedList,
                        cardMap = uiState.cardMap,
                        onRemoveWish = { cardId ->
                            val removedWish = uiState.wishlist.firstOrNull { it.cardId == cardId }
                            viewModel.removeFromWishlist(cardId)
                            if (removedWish != null) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.ui_wishlist_removed),
                                        actionLabel = context.getString(R.string.ui_undo)
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.updateWish(removedWish)
                                    }
                                }
                            }
                        },
                        onEditTarget = { editingWishTarget = it },
                        onEditNotes = { editingWishNotes = it },
                        onEnlargeImage = { enlargedCard = it }
                    )
                    WishlistTab.DATABASE -> WishlistDatabaseTab(
                        state = uiState,
                        onQueryChange = viewModel::setSearchQuery,
                        onToggleRarity = viewModel::toggleRarity,
                        onClearRarities = viewModel::clearRarities,
                        onMaxPriceChange = viewModel::setMaxPriceText,
                        onToggleWishlist = viewModel::toggleWishlist,
                        onEnlargeImage = { enlargedCard = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistListTab(
    wishlist: List<WishlistEntity>,
    sortedList: List<WishlistEntity>,
    cardMap: Map<String, CardEntity>,
    onRemoveWish: (String) -> Unit,
    onEditTarget: (WishlistEntity) -> Unit,
    onEditNotes: (WishlistEntity) -> Unit,
    onEnlargeImage: (CardEntity) -> Unit
) {
    val context = LocalContext.current
    val enlargeLabel = stringResource(R.string.wishlist_card_image_enlarge)

    if (wishlist.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ui_deine_wunschliste_ist_leer_ef1608),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.wishlist_empty_hint_database),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sortedList, key = { it.cardId }) { wish ->
            val card = cardMap[wish.cardId]
            val marketPrice = card?.marketPrice
            val targetReached = isTargetReached(marketPrice, wish.targetPrice)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TcgImage(
                            model = card?.imageUrl,
                            contentDescription = card?.let {
                                stringResource(R.string.ui_card_image_description, it.name, it.number, it.setName)
                            } ?: stringResource(R.string.ui_card_id_description, wish.cardId),
                            modifier = Modifier
                                .size(60.dp)
                                .clickable(onClickLabel = card?.let { enlargeLabel }) {
                                    card?.let(onEnlargeImage)
                                }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                card?.name ?: wish.cardId,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (card != null) {
                                Text(
                                    stringResource(
                                        R.string.ui_card_set_number_compact,
                                        setLabelWithAbbreviation(card.setName, card.setAbbreviation),
                                        card.number
                                    ) + " · " + card.language.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                PriceFormatter.formatPriceShort(context, marketPrice),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (wish.targetPrice != null) {
                                Text(
                                    stringResource(R.string.ui_wishlist_target_price, PriceFormatter.formatPriceShort(context, wish.targetPrice)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!wish.notes.isNullOrBlank()) {
                                Text(stringResource(R.string.ui_wishlist_note, wish.notes), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            IconButton(onClick = { onRemoveWish(wish.cardId) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.ui_icon_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (card != null) {
                            AssistChip(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(CardmarketLinks.forCard(card.name, card.setName))
                                        )
                                    )
                                },
                                label = { Text(stringResource(R.string.wishlist_cardmarket_open)) },
                                leadingIcon = {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        AssistChip(
                            onClick = { onEditTarget(wish) },
                            label = { Text(stringResource(R.string.ui_zielpreis_848291)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = { onEditNotes(wish) },
                            label = { Text(stringResource(R.string.ui_notiz_b6c441)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )

                        if (targetReached) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    stringResource(R.string.ui_ziel_erreicht_d2e169),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Catalog browser inside the wishlist: free text, rarity multi-select and a price ceiling, with
 * the hits grouped per Pokémon exactly like the main screen. Language prints of the same card
 * (same set, same artwork) are merged into one entry that lists its available languages.
 * Pokémon that already have a full art favourite are filtered out before grouping.
 */
@Composable
private fun WishlistDatabaseTab(
    state: WishlistUiState,
    onQueryChange: (String) -> Unit,
    onToggleRarity: (String) -> Unit,
    onClearRarities: () -> Unit,
    onMaxPriceChange: (String) -> Unit,
    onToggleWishlist: (CardEntity) -> Unit,
    onEnlargeImage: (CardEntity) -> Unit
) {
    val params = state.searchParams

    if (state.isCatalogEmpty) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.wishlist_search_catalog_empty),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "wishlist_search_field") {
            OutlinedTextField(
                value = params.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.wishlist_search_field_label)) },
                placeholder = { Text(stringResource(R.string.ui_karte_oder_set_suchen_dc3b5f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.ui_icon_search))
                },
                trailingIcon = {
                    if (params.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.ui_icon_clear))
                        }
                    }
                }
            )
        }

        item(key = "wishlist_search_price") {
            OutlinedTextField(
                value = params.maxPriceText,
                onValueChange = onMaxPriceChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text(stringResource(R.string.wishlist_search_max_price_label)) },
                supportingText = { Text(stringResource(R.string.wishlist_search_max_price_hint)) },
                trailingIcon = {
                    if (params.maxPriceText.isNotEmpty()) {
                        IconButton(onClick = { onMaxPriceChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.ui_icon_clear))
                        }
                    }
                }
            )
        }

        if (state.availableRarityGroups.isNotEmpty()) {
            item(key = "wishlist_search_rarity_title") {
                Text(
                    stringResource(R.string.wishlist_search_rarity_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
            }
            item(key = "wishlist_search_rarity_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.availableRarityGroups.forEach { rarityGroup ->
                        FilterChip(
                            selected = rarityGroup in params.rarities,
                            onClick = { onToggleRarity(rarityGroup) },
                            label = { Text(RarityGroupLocalization.localizedLabel(rarityGroup)) }
                        )
                    }
                    if (params.rarities.isNotEmpty()) {
                        AssistChip(
                            onClick = onClearRarities,
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
        }

        item(key = "wishlist_search_summary") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.wishlist_search_result_count, state.searchResultCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.excludedFullartPokemonCount > 0) {
                    Text(
                        stringResource(
                            R.string.wishlist_search_excluded_fullart,
                            state.excludedFullartPokemonCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.searchSections.isEmpty()) {
            item(key = "wishlist_search_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.wishlist_search_no_results))
                }
            }
        } else {
            state.searchSections.forEach { section ->
                item(key = "wishlist_search_section_${section.generationId ?: "none"}") {
                    WishlistSearchSectionHeader(section = section)
                }
                section.groups.forEach { group ->
                    item(key = "wishlist_search_group_${group.dexId ?: "none"}") {
                        WishlistSearchPokemonHeader(group = group)
                    }
                    items(group.cards, key = { "wishlist_search_card_${it.representative.id}" }) { card ->
                        WishlistSearchCardRow(
                            card = card,
                            isInWishlist = card.variants.any { it.id in state.wishlistCardIds },
                            onToggleWishlist = onToggleWishlist,
                            onEnlargeImage = onEnlargeImage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistSearchSectionHeader(section: CatalogSearchSection) {
    val generationId = section.generationId
    val title = if (generationId == null) {
        stringResource(R.string.wishlist_search_group_without_pokedex)
    } else {
        val info = GenerationModel.getGenerationInfo(generationId)
        stringResource(
            R.string.ui_generation_title_region,
            stringResource(info.nameRes),
            stringResource(info.regionRes)
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { heading() },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun WishlistSearchPokemonHeader(group: PokemonCardGroup) {
    val label = if (group.dexId == null) {
        group.pokemonName.ifBlank { stringResource(R.string.wishlist_search_group_without_pokedex) }
    } else {
        stringResource(R.string.wishlist_search_pokemon_header, group.dexId, group.pokemonName)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            pluralStringResource(R.plurals.ui_card_count, group.cards.size, group.cards.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WishlistSearchCardRow(
    card: MergedCatalogCard,
    isInWishlist: Boolean,
    onToggleWishlist: (CardEntity) -> Unit,
    onEnlargeImage: (CardEntity) -> Unit
) {
    val representative = card.representative
    val rarityGroup = RarityGrouper.canonical(representative.rarity)
    val enlargeLabel = stringResource(R.string.wishlist_card_image_enlarge)
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
                    .clickable(onClickLabel = enlargeLabel) { onEnlargeImage(representative) }
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(representative.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.ui_card_set_number_compact,
                        setLabelWithAbbreviation(representative.setName, representative.setAbbreviation),
                        representative.number
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (rarityGroup != "None") {
                    Text(RarityGroupLocalization.localizedLabel(rarityGroup), style = MaterialTheme.typography.labelSmall)
                }
                if (card.languages.isNotEmpty()) {
                    Text(
                        stringResource(R.string.wishlist_available_languages, card.languages.joinToString(" · ")),
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
            IconButton(onClick = { onToggleWishlist(representative) }) {
                Icon(
                    imageVector = if (isInWishlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isInWishlist) R.string.wishlist_search_remove_card else R.string.wishlist_search_add_card,
                        representative.name
                    ),
                    tint = if (isInWishlist) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
