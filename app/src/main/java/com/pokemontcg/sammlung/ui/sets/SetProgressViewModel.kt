package com.pokemontcg.sammlung.ui.sets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SetProgressViewModel(
    private val catalogRepository: CatalogRepository,
    private val collectionRepository: CollectionRepository
) : ViewModel() {

    data class UiState(
        val sets: List<SetEntity> = emptyList(),
        val userCards: List<UserCardEntityV2> = emptyList(),
        val cardsMap: Map<String, CardEntity> = emptyMap(),
        val catalogCardCountBySet: Map<String, Int> = emptyMap(),
        val isLoading: Boolean = true
    )

    val uiState: StateFlow<UiState> = combine(
        catalogRepository.getAllSets(),
        collectionRepository.getAllUserCards()
    ) { sets, userCards ->
        sets to userCards
    }.map { (sets, userCards) ->
        val cardIds = userCards.map { it.cardId }.distinct()
        val cards = catalogRepository.getCardsByIdsBatched(cardIds)
        UiState(
            sets = sets,
            userCards = userCards,
            cardsMap = cards.associateBy { it.id },
            catalogCardCountBySet = catalogRepository.getCardCountsBySet(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    class Factory(
        private val catalogRepository: CatalogRepository,
        private val collectionRepository: CollectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T = SetProgressViewModel(catalogRepository, collectionRepository) as T
    }
}
