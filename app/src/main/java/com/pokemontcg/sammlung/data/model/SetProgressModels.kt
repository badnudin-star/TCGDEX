package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.entities.SetEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

data class SetProgress(
    val set: SetEntity,
    val collectedCount: Int,
    val totalCount: Int,
    val catalogCount: Int,
    val progressPercent: Float,
    val isCatalogIncomplete: Boolean
)

enum class SetSortOption {
    PROGRESS_DESC,
    RELEASE_DATE_DESC,
    NAME_ASC
}

/** Groups distinct owned card IDs by catalog set, optionally filtered by collection language. */
fun ownedCardIdsBySet(
    userCards: List<UserCardEntityV2>,
    cardsMap: Map<String, CardEntity>,
    language: String?
): Map<String, Set<String>> =
    userCards.asSequence()
        .filter { language == null || it.language == language }
        .mapNotNull { userCard ->
            cardsMap[userCard.cardId]?.let { card -> card.setId to userCard.cardId }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, cardIds) -> cardIds.toSet() }

/** Keeps Room's IN(...) argument count below the SQLite limit while removing duplicates. */
fun batchCardIds(cardIds: List<String>, batchSize: Int = 500): List<List<String>> {
    require(batchSize in 1..500) { "batchSize must be between 1 and 500" }
    return cardIds.distinct().chunked(batchSize)
}

fun computeSetProgress(
    ownedCardIdsBySet: Map<String, Set<String>>,
    catalogCardCountBySet: Map<String, Int>,
    sets: List<SetEntity>
): List<SetProgress> {
    return sets.map { set ->
        val ownedSet = ownedCardIdsBySet[set.id] ?: emptySet()
        val collectedCount = ownedSet.size
        val totalOfficial = set.printedTotal.coerceAtLeast(1)
        val catalogCount = catalogCardCountBySet[set.id] ?: 0
        val isIncomplete = catalogCount < totalOfficial
        val percent = (collectedCount.toFloat() / totalOfficial.toFloat()).coerceIn(0f, 1f)

        SetProgress(
            set = set,
            collectedCount = collectedCount,
            totalCount = totalOfficial,
            catalogCount = catalogCount,
            progressPercent = percent,
            isCatalogIncomplete = isIncomplete
        )
    }
}

fun sortSetProgress(
    list: List<SetProgress>,
    option: SetSortOption
): List<SetProgress> {
    return when (option) {
        SetSortOption.PROGRESS_DESC -> list.sortedWith(compareByDescending<SetProgress> { it.progressPercent }.thenBy { it.set.name })
        SetSortOption.RELEASE_DATE_DESC -> list.sortedWith(compareByDescending<SetProgress> { it.set.releaseDate }.thenBy { it.set.name })
        SetSortOption.NAME_ASC -> list.sortedBy { it.set.name }
    }
}
