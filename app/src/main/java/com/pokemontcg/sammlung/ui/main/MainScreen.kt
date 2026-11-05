package com.pokemontcg.sammlung.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.export.TradeListExporter
import com.pokemontcg.sammlung.ui.components.NotificationPermissionHandler
import com.pokemontcg.sammlung.ui.main.components.MainFilterChips
import com.pokemontcg.sammlung.ui.main.components.MainSyncBanner
import com.pokemontcg.sammlung.ui.main.components.PriceDevelopmentTab
import com.pokemontcg.sammlung.ui.pokedex.PokedexScreen
import com.pokemontcg.sammlung.ui.pokedex.GenerationFilterDialog
import com.pokemontcg.sammlung.data.model.GenerationModel

enum class MainTab {
    POKEMONS,
    PRICE_DEVELOPMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToPokemonDetail: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWishlist: () -> Unit,
    onNavigateToCatalogManager: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToSets: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val catalogIsEmpty by viewModel.catalogIsEmpty.collectAsState()
    val searchQuery = uiState.searchQuery
    val selectedFilter = uiState.selectedFilter
    val pokemonList = uiState.pokemonList
    val totalValue = uiState.totalCollectionValue
    val priceHistory = uiState.priceHistory
    val userCards = uiState.userCards
    val cardsWithoutPriceCount = uiState.cardsWithoutPriceCount

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.POKEMONS) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showGenerationDialog by rememberSaveable { mutableStateOf(false) }

    val quantitiesMap = remember(userCards) {
        userCards.groupBy { it.cardId }
            .mapValues { (_, cards) -> cards.sumOf { it.quantity } }
    }

    NotificationPermissionHandler()

    if (showGenerationDialog) {
        GenerationFilterDialog(
            hiddenGenerations = uiState.hiddenGenerations,
            onHiddenGenerationsChange = viewModel::setHiddenGenerations,
            onDismiss = { showGenerationDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.ui_tcg_sammlung_c2e767),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.ui_icon_search))
                    }
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(painterResource(R.drawable.ic_tcg_camera), contentDescription = stringResource(R.string.ui_icon_scanner))
                    }
                    IconButton(onClick = onNavigateToWishlist) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.ui_icon_wishlist))
                    }
                    IconButton(onClick = onNavigateToSets) {
                        Icon(painterResource(R.drawable.ic_tcg_sets), contentDescription = stringResource(R.string.ui_icon_sets))
                    }
                    IconButton(onClick = { showGenerationDialog = true }) {
                        Icon(painterResource(R.drawable.ic_tcg_filter), contentDescription = stringResource(R.string.generation_show_hide_content_description))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ui_icon_settings))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.POKEMONS,
                    onClick = { selectedTab = MainTab.POKEMONS },
                    icon = { Icon(painterResource(R.drawable.ic_tcg_pokeball), contentDescription = stringResource(R.string.ui_icon_pokedex)) },
                    label = { Text(stringResource(R.string.ui_pok_mons_57f3d1)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PRICE_DEVELOPMENT,
                    onClick = { selectedTab = MainTab.PRICE_DEVELOPMENT },
                    icon = { Icon(painterResource(R.drawable.ic_tcg_chart), contentDescription = stringResource(R.string.ui_icon_price_development)) },
                    label = { Text(stringResource(R.string.ui_preisentwicklung_552ac6)) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MainSyncBanner(
                isSyncing = uiState.sync.isSyncing,
                progressMessage = uiState.sync.progressMessage,
                progressPercent = uiState.sync.progressPercent,
                downloadedBytes = uiState.sync.downloadedBytes,
                totalBytes = uiState.sync.totalBytes,
                errorMessage = uiState.sync.errorMessage,
                errorMessageRes = uiState.sync.errorMessageRes,
                onDismissError = viewModel::dismissSyncError
            )

            when (selectedTab) {
                MainTab.POKEMONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.ui_pok_mon_suchen_z_b_glurak_charizard__3db2d4)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.ui_icon_search))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.ui_icon_clear))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.hiddenGenerations.isNotEmpty()) {
                            AssistChip(
                                onClick = { showGenerationDialog = true },
                                label = { Text(stringResource(R.string.generation_hidden_chip, uiState.hiddenGenerations.size)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_tcg_filter), contentDescription = stringResource(R.string.ui_icon_filter)) }
                            )
                        }
                        if (uiState.hiddenSearchMatches > 0) {
                            TextButton(onClick = { showGenerationDialog = true }) {
                                Text(stringResource(R.string.generation_hidden_search_matches, uiState.hiddenSearchMatches),
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        MainFilterChips(
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setSelectedFilter(it) }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            if (!catalogIsEmpty && uiState.generationSections.isEmpty() && uiState.hiddenGenerations.containsAll(GenerationModel.GENERATIONS.map { it.id }.toSet()) && uiState.hiddenSearchMatches == 0 && searchQuery.isBlank()) {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.generation_empty_all_hidden))
                                    TextButton(onClick = { viewModel.setHiddenGenerations(emptySet()) }) { Text(stringResource(R.string.generation_show_all)) }
                                }
                            } else PokedexScreen(
                                generationSections = uiState.generationSections,
                                collapsedGenerations = uiState.collapsedGenerations,
                                onToggleGeneration = { genId ->
                                    val next = if (genId in uiState.collapsedGenerations) uiState.collapsedGenerations - genId else uiState.collapsedGenerations + genId
                                    viewModel.setCollapsedGenerations(next)
                                },
                                isLoading = false,
                                isCatalogEmpty = catalogIsEmpty,
                                isSyncing = uiState.sync.isSyncing,
                                syncProgressMessage = uiState.sync.progressMessage,
                                syncProgressPercent = uiState.sync.progressPercent,
                                syncDownloadedBytes = uiState.sync.downloadedBytes,
                                syncTotalBytes = uiState.sync.totalBytes,
                                onLoadCatalog = viewModel::triggerSync,
                                onOpenCatalogManager = onNavigateToCatalogManager,
                                primaryImageByDexId = uiState.primaryImageByDexId,
                                onPokemonClick = { pokemon ->
                                    onNavigateToPokemonDetail(pokemon.dexId)
                                }
                            )
                        }
                    }
                }
                MainTab.PRICE_DEVELOPMENT -> {
                    if (uiState.hiddenGenerations.isNotEmpty()) {
                        Text(stringResource(R.string.generation_total_value_hidden_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    PriceDevelopmentTab(
                        totalValue = totalValue,
                        priceHistory = priceHistory,
                        userCardQuantities = quantitiesMap,
                        totalCardsCount = userCards.sumOf { it.quantity },
                        cardsWithoutPriceCount = cardsWithoutPriceCount,
                        onExportTradeList = {
                            val duplicates = userCards.filter { it.quantity > 1 }
                            TradeListExporter.shareTradeList(context, duplicates, emptyMap())
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
