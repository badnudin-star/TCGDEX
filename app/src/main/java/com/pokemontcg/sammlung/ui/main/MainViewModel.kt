package com.pokemontcg.sammlung.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.model.GenerationSection
import com.pokemontcg.sammlung.data.model.groupPokemonByGeneration
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.data.repository.PriceRepository
import com.pokemontcg.sammlung.data.model.SyncStage
import com.pokemontcg.sammlung.data.repository.SyncRepository
import com.pokemontcg.sammlung.ui.catalog.CatalogManagerUiState
import com.pokemontcg.sammlung.worker.ResumableCatalogWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val stage: SyncStage? = null,
    val progressMessage: String = "",
    val progressPercent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val errorMessageRes: Int? = null
)

internal fun isPartialSyncFailure(state: WorkInfo.State, hasFailures: Boolean): Boolean =
    state == WorkInfo.State.SUCCEEDED && hasFailures

internal fun normalizeProgressPercent(value: Int): Int? =
    value.takeIf { it in 0..100 }

internal fun exactDownloadPercent(downloadedBytes: Long?, totalBytes: Long?): Int? =
    if (downloadedBytes != null && totalBytes != null && totalBytes > 0L) {
        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    } else {
        null
    }

internal fun isCatalogEmpty(pokemonCount: Int, cardCount: Int): Boolean =
    pokemonCount == 0 && cardCount == 0

data class MainUiState(
    val searchQuery: String = "",
    val selectedFilter: String = "Alle",
    val pokemonList: List<PokemonEntity> = emptyList(),
    val generationSections: List<GenerationSection> = emptyList(),
    val collapsedGenerations: Set<Int> = emptySet(),
    val totalCollectionValue: Double? = null,
    val priceHistory: List<PriceHistoryEntity> = emptyList(),
    val userCards: List<UserCardEntityV2> = emptyList(),
    val cardsWithoutPriceCount: Int = 0,
    val primaryImageByDexId: Map<Int, String> = emptyMap(),
    val sync: SyncUiState = SyncUiState(),
    /**
     * True once a persisted notification feature is known to be enabled (threshold price alerts
     * or the daily 03:00 price report). Defaults to false, so the notification rationale is not
     * shown on every launch to users who turned both off.
     */
    val notificationsEnabled: Boolean = false
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
    val catalogIsEmpty: StateFlow<Boolean> = combine(
        catalogRepository.observePokemonCount(),
        catalogRepository.observeCardCount()
    ) { pokemonCount, cardCount ->
        isCatalogEmpty(pokemonCount, cardCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val primaryImageByDexId: StateFlow<Map<Int, String>> = userCards
        .map { cards ->
            val primaryCards = cards.filter { it.isPrimary }
            if (primaryCards.isEmpty()) {
                emptyMap()
            } else {
                val cardsById = catalogRepository
                    .getCardsByIdsBatched(primaryCards.map { it.cardId })
                    .associateBy { it.id }
                derivePrimaryImageByDexId(primaryCards, cardsById)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val syncWorkInfos: StateFlow<List<WorkInfo>> = syncRepository.observeSyncWork()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val dismissedSyncErrorId = MutableStateFlow<String?>(null)

    private val syncUiState: StateFlow<SyncUiState> = combine(syncWorkInfos, dismissedSyncErrorId) { works, dismissedId ->
        deriveSyncUiState(works, dismissedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiState())

    private data class CatalogCounts(val pokemon: Int, val cards: Int, val sets: Int)

    private val catalogCounts: StateFlow<CatalogCounts> = combine(
        catalogRepository.observePokemonCount(),
        catalogRepository.observeCardCount(),
        catalogRepository.observeSetCount()
    ) { pokemon, cards, sets -> CatalogCounts(pokemon, cards, sets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogCounts(0, 0, 0))

    private data class CatalogVersionMetadata(
        val installedVersion: String?,
        val remoteVersion: String?,
        val installedAt: Long,
        val lastCheckAt: Long
    )

    private data class CatalogMetadata(
        val installedVersion: String?,
        val remoteVersion: String?,
        val installedAt: Long,
        val lastCheckAt: Long,
        val remoteSizeBytes: Long,
        val requiresAppUpdate: Boolean
    )

    private val catalogVersionMetadata: StateFlow<CatalogVersionMetadata> = combine(
        preferencesRepository.catalogInstalledVersionFlow,
        preferencesRepository.catalogRemoteVersionFlow,
        preferencesRepository.catalogInstalledAtFlow,
        preferencesRepository.catalogLastCheckAtFlow
    ) { installedVersion, remoteVersion, installedAt, lastCheckAt ->
        CatalogVersionMetadata(installedVersion, remoteVersion, installedAt, lastCheckAt)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CatalogVersionMetadata(null, null, 0L, 0L)
    )

    private val catalogMetadata: StateFlow<CatalogMetadata> = combine(
        catalogVersionMetadata,
        preferencesRepository.catalogRemoteSizeBytesFlow,
        preferencesRepository.catalogRequiresAppUpdateFlow
    ) { versions, remoteSizeBytes, requiresAppUpdate ->
        CatalogMetadata(
            installedVersion = versions.installedVersion,
            remoteVersion = versions.remoteVersion,
            installedAt = versions.installedAt,
            lastCheckAt = versions.lastCheckAt,
            remoteSizeBytes = remoteSizeBytes,
            requiresAppUpdate = requiresAppUpdate
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CatalogMetadata(null, null, 0L, 0L, 0L, false)
    )

    val catalogManagerState: StateFlow<CatalogManagerUiState> = combine(
        catalogCounts,
        catalogMetadata,
        syncUiState
    ) { counts, metadata, sync ->
        CatalogManagerUiState(
            isLoaded = true,
            catalogIsEmpty = isCatalogEmpty(counts.pokemon, counts.cards),
            pokemonCount = counts.pokemon,
            cardCount = counts.cards,
            setCount = counts.sets,
            installedVersion = metadata.installedVersion,
            remoteVersion = metadata.remoteVersion,
            installedAt = metadata.installedAt,
            lastCheckAt = metadata.lastCheckAt,
            remoteSizeBytes = metadata.remoteSizeBytes.takeIf { it > 0L },
            requiresAppUpdate = metadata.requiresAppUpdate,
            sync = sync
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CatalogManagerUiState()
    )

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
        val stage = activeWork?.progress
            ?.getString(ResumableCatalogWorker.KEY_SYNC_STAGE)
            ?.let(SyncStage::fromKey)
        val downloadedBytes = activeWork?.progress
            ?.getLong(ResumableCatalogWorker.KEY_DOWNLOADED_BYTES, -1L)
            ?.takeIf { it >= 0L }
        val totalBytes = activeWork?.progress
            ?.getLong(ResumableCatalogWorker.KEY_TOTAL_BYTES, -1L)
            ?.takeIf { it > 0L }
        val progressPercent = exactDownloadPercent(downloadedBytes, totalBytes)
            ?: activeWork?.progress
                ?.getInt(ResumableCatalogWorker.KEY_PROGRESS_PERCENT, -1)
                ?.let(::normalizeProgressPercent)
        if (active) {
            return SyncUiState(
                isSyncing = true,
                stage = stage,
                progressMessage = progressMessage,
                progressPercent = progressPercent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
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
        localizedPokemon, catalogRepository.getAllPokemonNames(), searchQuery, selectedFilter, userCards
    ) { allPokemon, allNames, query, filter, cards ->
        val namesByDex = allNames.groupBy { it.dexId }
        val categoryByDex = deriveCategoryByDex(cards)
        var list = allPokemon
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { pokemon ->
                pokemon.name.lowercase().contains(q) || pokemon.searchName.contains(q) || pokemon.number.contains(q) ||
                    (namesByDex[pokemon.dexId]?.any { it.name.lowercase().contains(q) } ?: false)
            }
        }
        if (!filter.equals("Alle", ignoreCase = true)) {
            list = list.filter { pokemon ->
                val category = categoryByDex[pokemon.dexId] ?: CardCategory.NONE
                category.name.equals(filter, ignoreCase = true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val collapsedGenerations = preferencesRepository.collapsedGenerationsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet()
    )

    /** Exposed so the notification rationale is only requested when a report is actually on. */
    private val notificationsEnabled = combine(
        preferencesRepository.priceAlertsEnabledFlow,
        preferencesRepository.dailyPriceReportEnabledFlow
    ) { priceAlerts, dailyReport -> priceAlerts || dailyReport }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private data class MainData(
        val visiblePokemon: List<PokemonEntity>, val sections: List<GenerationSection>,
        val collapsed: Set<Int>, val value: Double?, val history: List<PriceHistoryEntity>,
        val withoutPrice: Int, val cards: List<UserCardEntityV2>, val notificationsEnabled: Boolean
    )

    // The array-based combine overload is used on purpose: the typed overloads stop at five
    // flows, and this needs eight. Keep the index mapping below in sync with the argument order.
    private val mainData = combine(
        localizedPokemon, filteredPokemon, collapsedGenerations, totalCollectionValue,
        priceHistory, cardsWithoutPriceCount, userCards, notificationsEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val catalog = values[0] as List<PokemonEntity>
        @Suppress("UNCHECKED_CAST")
        val filtered = values[1] as List<PokemonEntity>
        @Suppress("UNCHECKED_CAST")
        val collapsed = values[2] as Set<Int>
        val value = values[3] as Double?
        @Suppress("UNCHECKED_CAST")
        val history = values[4] as List<PriceHistoryEntity>
        val withoutPrice = values[5] as Int
        @Suppress("UNCHECKED_CAST")
        val cards = values[6] as List<UserCardEntityV2>
        val notifications = values[7] as Boolean
        val sections = groupPokemonByGeneration(catalog, filtered, cards)
        val visible = sections.flatMap { it.pokemon }
        MainData(visible, sections, collapsed, value, history, withoutPrice, cards, notifications)
    }

    val uiState: StateFlow<MainUiState> = combine(
        mainData, searchQuery, selectedFilter, syncUiState, primaryImageByDexId
    ) { data, query, filter, sync, primaryImages ->
        MainUiState(
            searchQuery = query, selectedFilter = filter, pokemonList = data.visiblePokemon,
            generationSections = data.sections,
            collapsedGenerations = data.collapsed,
            totalCollectionValue = data.value, priceHistory = data.history,
            userCards = data.cards, cardsWithoutPriceCount = data.withoutPrice,
            primaryImageByDexId = primaryImages, sync = sync,
            notificationsEnabled = data.notificationsEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    fun setSearchQuery(query: String) { savedStateHandle["search_query"] = query }
    fun setSelectedFilter(filter: String) { savedStateHandle["selected_filter"] = filter }
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
