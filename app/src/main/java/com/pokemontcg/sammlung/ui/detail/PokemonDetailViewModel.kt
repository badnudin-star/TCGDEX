package com.pokemontcg.sammlung.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.CardCategory
import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.data.repository.PriceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PokemonDetailViewModel(
    private val catalogRepository: CatalogRepository,
    private val collectionRepository: CollectionRepository,
    priceRepository: PriceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val dexId: Int = savedStateHandle["dexId"] ?: 1
    private val pokemonIdStr: String = dexId.toString()

    private val _pokemon = MutableStateFlow<PokemonEntity?>(null)
    val pokemon: StateFlow<PokemonEntity?> = _pokemon.asStateFlow()

    val cards: StateFlow<List<CardEntity>> = catalogRepository.getCardsByPokemon(dexId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userCards: StateFlow<List<UserCardEntityV2>> = collectionRepository.getUserCardsForPokemon(dexId, pokemonIdStr)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val wishlistCardIds: StateFlow<Set<String>> = collectionRepository.getWishlist()
        .map { wishlist -> wishlist.map { it.cardId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val totalPrice: StateFlow<Double?> = priceRepository.getTotalPriceForPokemon(pokemonIdStr)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            catalogRepository.getAllPokemon().collect { all ->
                _pokemon.value = all.firstOrNull { it.dexId == dexId }
            }
        }
    }

    fun setPrimaryCard(card: CardEntity) {
        viewModelScope.launch {
            collectionRepository.setAsPrimaryCard(card, pokemonIdStr)
        }
    }

    fun removePrimaryCard() {
        viewModelScope.launch {
            collectionRepository.removePrimaryCard(pokemonIdStr)
        }
    }

    fun updateCardDetails(
        cardId: String,
        quantity: Int,
        condition: String?,
        language: String,
        notes: String?,
        category: String
    ) {
        viewModelScope.launch {
            val userCard = userCards.value.firstOrNull { it.cardId == cardId } ?: return@launch
            collectionRepository.updateUserCard(
                sanitizeUserCardDetails(
                    userCard = userCard,
                    quantity = quantity,
                    condition = condition,
                    language = language,
                    notes = notes,
                    category = category
                )
            )
        }
    }

    fun updatePrimaryCategory(category: String) {
        val primary = userCards.value.firstOrNull { it.isPrimary } ?: return
        updateCardDetails(
            cardId = primary.cardId,
            quantity = primary.quantity,
            condition = primary.condition,
            language = primary.language,
            notes = primary.notes,
            category = category
        )
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

    fun addSecondaryCard(card: CardEntity, category: CardCategory) {
        viewModelScope.launch {
            collectionRepository.addSecondaryCard(card.id, pokemonIdStr, category)
        }
    }

    fun removeSecondaryCard(cardId: String) {
        viewModelScope.launch {
            collectionRepository.removeUserCard(cardId, dexId, pokemonIdStr)
        }
    }

    class Factory(
        private val catalogRepository: CatalogRepository,
        private val collectionRepository: CollectionRepository,
        private val priceRepository: PriceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return PokemonDetailViewModel(
                catalogRepository,
                collectionRepository,
                priceRepository,
                savedStateHandle
            ) as T
        }
    }
}
