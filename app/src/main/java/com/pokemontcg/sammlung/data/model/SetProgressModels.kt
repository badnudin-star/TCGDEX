package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.SetEntity

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

fun computeSetProgress(
    ownedCardIdsBySet: Map<String, Set<String>>,
    catalogCardCountBySet: Map<String, Int>,
    sets: List<SetEntity>
): List<SetProgress> {
    return sets.map { set ->
        val ownedSet = ownedCardIdsBySet[set.id] ?: emptySet()
        val collectedCount = ownedSet.size
        val totalOfficial = set.printedTotal.coerceAtLeast(1)
        val catalogCount = catalogCardCountBySet[set.id] ?: totalOfficial
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
