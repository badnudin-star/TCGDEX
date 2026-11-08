package com.pokemontcg.sammlung.ui.sets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.model.MergedCatalogCard
import com.pokemontcg.sammlung.data.model.mergeCardsByRawId
import com.pokemontcg.sammlung.data.model.rawCardId
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * All cards of one set for the set detail screen: language prints merged per artwork, ordered
 * by card number, with the owned quantity per card so the collection status is visible.
 */
class SetDetailViewModel(
    catalogRepository: CatalogRepository,
    collectionRepository: CollectionRepository,
    preferencesRepository: AppPreferencesRepository,
    setId: String
) : ViewModel() {

    data class UiState(
        val set: SetEntity? = null,
        /** Merged cards ordered by card number; trainers and energies included. */
        val cards: List<MergedCatalogCard> = emptyList(),
        /** Owned quantity per artwork (raw card id), summed over language prints. */
        val ownedQuantitiesByRawId: Map<String, Int> = emptyMap(),
        /** All languages the set exists in. */
        val setLanguages: List<String> = emptyList(),
        val isLoading: Boolean = true
    ) {
        val ownedCardCount: Int
            get() = cards.count { (ownedQuantitiesByRawId[rawCardId(it.representative.id)] ?: 0) > 0 }
    }

    private val set: StateFlow<SetEntity?> = catalogRepository.getAllSets()
        .map { sets -> sets.firstOrNull { it.id == setId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<UiState> = combine(
        set,
        catalogRepository.getCardsForSet(setId),
        collectionRepository.getAllUserCards(),
        preferencesRepository.preferredLanguageFlow
    ) { set, cards, userCards, preferredLanguage ->
        val mergedCards = mergeCardsByRawId(cards, preferredLanguage)
        val quantities = userCards
            .groupBy { rawCardId(it.cardId) }
            .mapValues { (_, owned) -> owned.sumOf { it.quantity } }
        UiState(
            set = set,
            cards = mergedCards,
            ownedQuantitiesByRawId = quantities,
            setLanguages = cards.map { it.language }.distinct().sorted(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    class Factory(
        private val catalogRepository: CatalogRepository,
        private val collectionRepository: CollectionRepository,
        private val preferencesRepository: AppPreferencesRepository,
        private val setId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T = SetDetailViewModel(catalogRepository, collectionRepository, preferencesRepository, setId) as T
    }
}
