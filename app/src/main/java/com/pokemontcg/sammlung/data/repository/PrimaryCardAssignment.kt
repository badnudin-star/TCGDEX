package com.pokemontcg.sammlung.data.repository

import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

/**
 * Decides how "make this card the favourite" is applied to the local collection.
 *
 * `user_cards` uses an auto-generated row id and has no unique key on
 * `(cardId, language)`, so inserting a second row for a card that is already owned
 * would duplicate the collection entry, reset its quantity to 1 and lose its notes.
 * Therefore an already owned card is updated in place and keeps its details.
 */
internal fun planPrimaryCardAssignment(
    cardId: String,
    language: String,
    pokemonDexId: Int,
    pokemonId: String,
    existingCard: UserCardEntityV2?,
    now: Long
): UserCardEntityV2 = if (existingCard != null) {
    existingCard.copy(
        pokemonDexId = if (pokemonDexId > 0) pokemonDexId else existingCard.pokemonDexId,
        pokemonId = if (pokemonId.isNotBlank()) pokemonId else existingCard.pokemonId,
        isPrimary = true
    )
} else {
    UserCardEntityV2(
        cardId = cardId,
        pokemonId = pokemonId,
        pokemonDexId = pokemonDexId,
        language = language,
        quantity = 1,
        isPrimary = true,
        addedAt = now
    )
}

/** How an assignment must be persisted: a new row or an update of an existing row. */
internal sealed interface PrimaryCardPersistence {
    data class Insert(val card: UserCardEntityV2) : PrimaryCardPersistence
    data class Update(val card: UserCardEntityV2) : PrimaryCardPersistence
}

internal fun primaryCardPersistence(
    existingCard: UserCardEntityV2?,
    resolvedCard: UserCardEntityV2
): PrimaryCardPersistence = when {
    existingCard == null -> PrimaryCardPersistence.Insert(resolvedCard)
    resolvedCard.id > 0L -> PrimaryCardPersistence.Update(resolvedCard)
    else -> PrimaryCardPersistence.Update(resolvedCard.copy(id = existingCard.id))
}
