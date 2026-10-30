package com.pokemontcg.sammlung.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.model.GenerationSection
import com.pokemontcg.sammlung.data.model.countHiddenGenerationMatches
import com.pokemontcg.sammlung.data.model.groupPokemonByGeneration
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.data.repository.PriceRepository
import com.pokemontcg.sammlung.data.repository.SyncRepository
import com.pokemontcg.sammlung.worker.ResumableCatalogWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val progressMessage: String = "",
    val progressPercent: Int? = null,
    val errorMessage: String? = null,
    val errorMessageRes: Int? = null
)

internal fun isPartialSyncFailure(state: WorkInfo.State, hasFailures: Boolean): Boolean =
    state == WorkInfo.State.SUCCEEDED && hasFailures

internal fun normalizeProgressPercent(value: Int): Int? =
    value.takeIf { it in 0..100 }

data class MainUiState(
    val searchQuery: String = "",
    val selectedFilter: String = "Alle",
    val pokemonList: List<PokemonEntity> = emptyList(),
    val generationSections: List<GenerationSection> = emptyList(),
    val hiddenGenerations: Set<Int> = emptySet(),
    val collapsedGenerations: Set<Int> = emptySet(),
    val hiddenSearchMatches: Int = 0,
    val totalCollectionValue: Double? = null,
    val priceHistory: List<PriceHistoryEntity> = emptyList(),
    val userCards: List<UserCardEntityV2> = emptyList(),
    val cardsWithoutPriceCount: Int = 0,
    val sync: SyncUiState = SyncUiState()
)

class MainViewModel(
    catalogRepository: CatalogRepository,
    collectionRepository: CollectionRepository,
    priceRepository: PriceRepository,
    private val syncRepository: SyncRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow("search_query", "")
    val selectedFilter: StateFlow<String> = savedStateHandle.getStateFlow("selected_filter", "Alle")

    val totalCollectionValue: StateFlow<Double?> = priceRepository.getTotalCollectionPrice().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val priceHistory: StateFlow<List<PriceHistoryEntity>> = priceRepository.getAllHistory().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val cardsWithoutPriceCount: StateFlow<Int> = priceRepository.getCardsWithoutPriceCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )
    val userCards: StateFlow<List<UserCardEntityV2>> = collectionRepository.getAllUserCards().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val syncWorkInfos: StateFlow<List<WorkInfo>> = syncRepository.observeSyncWork()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val dismissedSyncErrorId = MutableStateFlow<String?>(null)

    private val syncUiState: StateFlow<SyncUiState> = combine(syncWorkInfos, dismissedSyncErrorId) { works, dismissedId ->
        deriveSyncUiState(works, dismissedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiState())

    init {
        viewModelScope.launch {
            syncWorkInfos.collect { works ->
                val active = works.any {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                }
                if (active) dismissedSyncErrorId.value = null
            }
        }
    }

    private fun deriveSyncUiState(works: List<WorkInfo>, dismissedId: String?): SyncUiState {
        val active = works.any {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        val activeWork = works.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: works.firstOrNull {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
        val progressMessage = activeWork?.progress
            ?.getString(ResumableCatalogWorker.KEY_PROGRESS_MESSAGE)
            .orEmpty()
        val progressPercent = activeWork?.progress
            ?.getInt(ResumableCatalogWorker.KEY_PROGRESS_PERCENT, -1)
            ?.let(::normalizeProgressPercent)
        if (active) {
            return SyncUiState(
                isSyncing = true,
                progressMessage = progressMessage,
                progressPercent = progressPercent
            )
        }

        val latestFinished = latestFinishedWork(works)
        val failed = latestFinished?.takeIf { it.state == WorkInfo.State.FAILED }
        val partial = latestFinished?.takeIf { info ->
            isPartialSyncFailure(
                info.state,
                info.progress.getBoolean(ResumableCatalogWorker.KEY_HAS_FAILURES, false)
            )
        }
        val errorWork = failed ?: partial
        val dismissed = errorWork != null && errorWork.id.toString() == dismissedId
        val errorMessage = when {
            errorWork == null || dismissed -> null
            failed != null -> null
            else -> partial?.progress?.getString(ResumableCatalogWorker.KEY_PROGRESS_MESSAGE)
        }
        val errorMessageRes = when {
            errorWork == null || dismissed -> null
            failed != null -> R.string.ui_main_sync_error
            else -> null
        }
        return SyncUiState(
            isSyncing = false,
            progressMessage = "",
            progressPercent = null,
            errorMessage = errorMessage,
            errorMessageRes = errorMessageRes
        )
    }

    private fun latestFinishedWork(works: List<WorkInfo>): WorkInfo? =
        works
            .filter { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }
            .maxByOrNull { info ->
                maxOf(
                    info.progress.getLong(ResumableCatalogWorker.KEY_FINISHED_AT, 0L),
                    info.progress.getLong(ResumableCatalogWorker.KEY_STARTED_AT, 0L)
                )
            }

    private val localizedPokemon: StateFlow<List<PokemonEntity>> = combine(
        catalogRepository.getAllPokemon(), catalogRepository.getAllPokemonNames(),
        preferencesRepository.preferredLanguageFlow
    ) { allPokemon, allNames, prefLang ->
        val namesByDex = allNames.groupBy { it.dexId }
        allPokemon.map { pokemon ->
            val localizedName = namesByDex[pokemon.dexId]
                ?.firstOrNull { it.language.equals(prefLang, ignoreCase = true) }?.name ?: pokemon.name
            pokemon.copy(name = localizedName)
        }.sortedWith(compareBy<PokemonEntity> { it.dexId }.thenBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filteredPokemon: StateFlow<List<PokemonEntity>> = combine(
        localizedPokemon, catalogRepository.getAllPokemonNames(), searchQuery, selectedFilter
    ) { allPokemon, allNames, query, filter ->
        val namesByDex = allNames.groupBy { it.dexId }
        var list = allPokemon
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { pokemon ->
                pokemon.name.lowercase().contains(q) || pokemon.searchName.contains(q) || pokemon.number.contains(q) ||
                    (namesByDex[pokemon.dexId]?.any { it.name.lowercase().contains(q) } ?: false)
            }
        }
        if (!filter.equals("Alle", ignoreCase = true)) {
            list = list.filter { it.cardType.name.equals(filter, ignoreCase = true) }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val hiddenGenerations = preferencesRepository.hiddenGenerationsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet()
    )
    private val collapsedGenerations = preferencesRepository.collapsedGenerationsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet()
    )

    private data class MainData(
        val visiblePokemon: List<PokemonEntity>, val sections: List<GenerationSection>,
        val hidden: Set<Int>, val collapsed: Set<Int>, val hiddenMatches: Int,
        val value: Double?, val history: List<PriceHistoryEntity>, val withoutPrice: Int,
        val cards: List<UserCardEntityV2>
    )

    private val mainData = combine(
        localizedPokemon, filteredPokemon, hiddenGenerations, collapsedGenerations, totalCollectionValue,
        priceHistory, cardsWithoutPriceCount, userCards
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val catalog = values[0] as List<PokemonEntity>
        @Suppress("UNCHECKED_CAST")
        val filtered = values[1] as List<PokemonEntity>
        @Suppress("UNCHECKED_CAST")
        val hidden = values[2] as Set<Int>
        @Suppress("UNCHECKED_CAST")
        val collapsed = values[3] as Set<Int>
        val value = values[4] as Double?
        @Suppress("UNCHECKED_CAST")
        val history = values[5] as List<PriceHistoryEntity>
        val withoutPrice = values[6] as Int
        @Suppress("UNCHECKED_CAST")
        val cards = values[7] as List<UserCardEntityV2>
        val sections = groupPokemonByGeneration(catalog, filtered, cards, hidden)
        val visible = sections.flatMap { it.pokemon }
        MainData(visible, sections, hidden, collapsed, countHiddenGenerationMatches(filtered, hidden), value, history, withoutPrice, cards)
    }

    val uiState: StateFlow<MainUiState> = combine(mainData, searchQuery, selectedFilter, syncUiState) { data, query, filter, sync ->
        MainUiState(
            searchQuery = query, selectedFilter = filter, pokemonList = data.visiblePokemon,
            generationSections = data.sections, hiddenGenerations = data.hidden,
            collapsedGenerations = data.collapsed, hiddenSearchMatches = data.hiddenMatches,
            totalCollectionValue = data.value, priceHistory = data.history,
            userCards = data.cards, cardsWithoutPriceCount = data.withoutPrice,
            sync = sync
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    fun setSearchQuery(query: String) { savedStateHandle["search_query"] = query }
    fun setSelectedFilter(filter: String) { savedStateHandle["selected_filter"] = filter }
    fun setHiddenGenerations(hidden: Set<Int>) {
        viewModelScope.launch { preferencesRepository.setHiddenGenerations(hidden) }
    }
    fun setCollapsedGenerations(collapsed: Set<Int>) {
        viewModelScope.launch { preferencesRepository.setCollapsedGenerations(collapsed) }
    }

    fun dismissSyncError() {
        val works = syncWorkInfos.value
        val errorWork = latestFinishedWork(works)
        dismissedSyncErrorId.value = errorWork?.id?.toString()
    }

    fun triggerSync() { syncRepository.startResumableCatalogSync() }

    class Factory(
        private val catalogRepository: CatalogRepository,
        private val collectionRepository: CollectionRepository,
        private val priceRepository: PriceRepository,
        private val syncRepository: SyncRepository,
        private val preferencesRepository: AppPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
            MainViewModel(catalogRepository, collectionRepository, priceRepository, syncRepository, preferencesRepository, extras.createSavedStateHandle()) as T
    }
}
