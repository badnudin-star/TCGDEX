package com.pokemontcg.sammlung.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokemontcg.sammlung.data.local.dao.CardSearchRow
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.data.model.CatalogSearchSection
import com.pokemontcg.sammlung.data.model.RarityGrouper
import com.pokemontcg.sammlung.data.model.countExcludedFullartPokemon
import com.pokemontcg.sammlung.data.model.groupCatalogSearchByPokemon
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.ui.detail.applyWishlistToggle
import com.pokemontcg.sammlung.ui.main.deriveCategoryByDex
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filters of the catalog search inside the wishlist. */
data class WishlistSearchParams(
    val query: String = "",
    val rarities: Set<String> = emptySet(),
    val maxPriceText: String = ""
) {
    /** German and English separators are both accepted; anything unparsable disables the ceiling. */
    val maxPrice: Double?
        get() = parseMaxPrice(maxPriceText)

    val hasActiveFilter: Boolean
        get() = query.isNotBlank() || rarities.isNotEmpty() || maxPriceText.isNotBlank()
}

/**
 * Parses a typed price ceiling. "12,50" and "12.50" both mean twelve fifty, and mixed input such
 * as "1.234,56" or "1,234.56" keeps the last separator as the decimal one. A lone dot followed by
 * exactly three digits counts as a thousand separator ("1.234" = 1234), everything else stays a
 * decimal point. Values that are not a positive number return null, which disables the filter
 * instead of hiding every card.
 */
fun parseMaxPrice(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val normalized = when {
        trimmed.contains(',') && trimmed.contains('.') ->
            if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                trimmed.replace(".", "").replace(',', '.')
            } else {
                trimmed.replace(",", "")
            }
        trimmed.contains(',') -> trimmed.replace(',', '.')
        THOUSAND_SEPARATOR_PATTERN.matches(trimmed) -> trimmed.replace(".", "")
        else -> trimmed
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
}

private val THOUSAND_SEPARATOR_PATTERN = Regex("^\\d{1,3}(?:\\.\\d{3})+$")

data class WishlistUiState(
    val wishlist: List<WishlistEntity> = emptyList(),
    val cardMap: Map<String, CardEntity> = emptyMap(),
    val wishlistCardIds: Set<String> = emptySet(),
    val searchParams: WishlistSearchParams = WishlistSearchParams(),
    /** Canonical rarity groups (English terms) present in the catalog, for the filter chips. */
    val availableRarityGroups: List<String> = emptyList(),
    /** Search hits grouped per Pokémon, ordered like the main screen. */
    val searchSections: List<CatalogSearchSection> = emptyList(),
    /** Number of merged search entries (language prints of one card count as one entry). */
    val searchResultCount: Int = 0,
    /** Pokémon hidden from the result because a full art favourite already exists. */
    val excludedFullartPokemonCount: Int = 0,
    val isCatalogEmpty: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WishlistViewModel(
    private val catalogRepository: CatalogRepository,
    private val collectionRepository: CollectionRepository,
    preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val searchParams = MutableStateFlow(WishlistSearchParams())

    private data class WishlistData(
        val wishlist: List<WishlistEntity>,
        val cardMap: Map<String, CardEntity>
    )

    private data class SearchData(
        val sections: List<CatalogSearchSection>,
        val resultCount: Int,
        val excludedFullartPokemon: Int
    )

    private data class SearchInputs(
        val rows: List<CardSearchRow>,
        val excludedDexIds: Set<Int>,
        val names: Map<Int, String>,
        val preferredLanguage: String
    )

    private val wishlistData: StateFlow<WishlistData> = collectionRepository.getWishlist()
        .map { wishes ->
            // Always reassign so an emptied wishlist cannot leave stale cards behind;
            // batched lookups stay below SQLite's parameter limit.
            val cardsById = catalogRepository
                .getCardsByIdsBatched(wishes.map { it.cardId })
                .associateBy { it.id }
            WishlistData(wishes, cardsById)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistData(emptyList(), emptyMap()))

    private val availableRarities: StateFlow<List<String>> = catalogRepository.getAllRarities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Canonical English rarity groups for the filter chips, known groups first. */
    private val availableRarityGroups: StateFlow<List<String>> = availableRarities
        .map { raw -> RarityGrouper.chipGroups(raw) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Language of the wishlist search representative print and Pokémon names. */
    private val preferredLanguage: StateFlow<String> = preferencesRepository.preferredLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "de")

    private val pokemonNameByDexId: StateFlow<Map<Int, String>> = combine(
        catalogRepository.getAllPokemon(),
        catalogRepository.getAllPokemonNames(),
        preferencesRepository.preferredLanguageFlow
    ) { pokemon, names, preferredLanguage ->
        val namesByDex = names.groupBy { it.dexId }
        pokemon.associate { entry ->
            entry.dexId to (namesByDex[entry.dexId]
                ?.firstOrNull { it.language.equals(preferredLanguage, ignoreCase = true) }
                ?.name ?: entry.name)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Pokémon the user already declared a full art favourite for; they leave the search result. */
    private val fullartDexIds: StateFlow<Set<Int>> = collectionRepository.getAllUserCards()
        .map { userCards ->
            deriveCategoryByDex(userCards)
                .filterValues { it == CardCategory.FULLART }
                .keys
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val isCatalogEmpty: StateFlow<Boolean> = combine(
        catalogRepository.observePokemonCount(),
        catalogRepository.observeCardCount()
    ) { pokemonCount, cardCount -> pokemonCount == 0 && cardCount == 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val searchData: StateFlow<SearchData> = combine(
        searchParams.debounce(SEARCH_DEBOUNCE_MS),
        availableRarities
    ) { params, rawRarities ->
        // The chips hold canonical English groups; the SQL filter needs the raw catalog values.
        params to RarityGrouper.rawValuesFor(params.rarities, rawRarities)
    }
        .flatMapLatest { (params, rawRarities) ->
            catalogRepository.searchCatalogCards(
                query = params.query,
                rarities = rawRarities,
                maxPrice = params.maxPrice
            )
        }
        .combine(fullartDexIds) { rows, excludedDexIds -> rows to excludedDexIds }
        // Names are combined instead of read as a snapshot: localized Pokémon names arrive from
        // the catalog after the first search hits and must still reach the group headers.
        .combine(pokemonNameByDexId) { (rows, excludedDexIds), names ->
            Triple(rows, excludedDexIds, names)
        }
        .combine(preferredLanguage) { (rows, excludedDexIds, names), language ->
            SearchInputs(rows, excludedDexIds, names, language)
        }
        .map { inputs ->
            val cardsById = catalogRepository
                .getCardsByIdsBatched(inputs.rows.map { it.cardId })
                .associateBy { it.id }
            val sections = groupCatalogSearchByPokemon(
                rows = inputs.rows,
                cardsById = cardsById,
                pokemonNameByDexId = inputs.names,
                excludedDexIds = inputs.excludedDexIds,
                preferredLanguage = inputs.preferredLanguage
            )
            SearchData(
                sections = sections,
                resultCount = sections.sumOf { section -> section.groups.sumOf { it.cards.size } },
                excludedFullartPokemon = countExcludedFullartPokemon(inputs.rows, inputs.excludedDexIds)
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchData(emptyList(), 0, 0)
        )

    val uiState: StateFlow<WishlistUiState> = combine(
        wishlistData,
        searchParams,
        searchData,
        availableRarityGroups,
        isCatalogEmpty
    ) { wishes, params, search, rarityGroups, catalogEmpty ->
        WishlistUiState(
            wishlist = wishes.wishlist,
            cardMap = wishes.cardMap,
            wishlistCardIds = wishes.wishlist.map { it.cardId }.toSet(),
            searchParams = params,
            availableRarityGroups = rarityGroups,
            searchSections = search.sections,
            searchResultCount = search.resultCount,
            excludedFullartPokemonCount = search.excludedFullartPokemon,
            isCatalogEmpty = catalogEmpty
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUiState())

    fun setSearchQuery(query: String) {
        searchParams.value = searchParams.value.copy(query = query)
    }

    /** Rarity is a multi-select filter: tapping a chip again removes it. */
    fun toggleRarity(rarity: String) {
        val current = searchParams.value.rarities
        searchParams.value = searchParams.value.copy(
            rarities = if (rarity in current) current - rarity else current + rarity
        )
    }

    fun clearRarities() {
        searchParams.value = searchParams.value.copy(rarities = emptySet())
    }

    fun setMaxPriceText(maxPriceText: String) {
        searchParams.value = searchParams.value.copy(
            maxPriceText = maxPriceText.filter { it.isDigit() || it == ',' || it == '.' }
        )
    }

    fun clearSearchFilters() {
        searchParams.value = WishlistSearchParams()
    }

    fun toggleWishlist(card: CardEntity) {
        viewModelScope.launch {
            applyWishlistToggle(
                cardId = card.id,
                loadWishlist = collectionRepository::getWishlistOnce,
                add = collectionRepository::addToWishlist,
                remove = collectionRepository::removeFromWishlist
            )
        }
    }

    fun removeFromWishlist(cardId: String) {
        viewModelScope.launch { collectionRepository.removeFromWishlist(cardId) }
    }

    /** Re-inserts with REPLACE semantics, which is how target price and notes are edited. */
    fun updateWish(wish: WishlistEntity) {
        viewModelScope.launch { collectionRepository.addToWishlist(wish) }
    }

    class Factory(
        private val catalogRepository: CatalogRepository,
        private val collectionRepository: CollectionRepository,
        private val preferencesRepository: AppPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WishlistViewModel(
                catalogRepository,
                collectionRepository,
                preferencesRepository
            ) as T
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
