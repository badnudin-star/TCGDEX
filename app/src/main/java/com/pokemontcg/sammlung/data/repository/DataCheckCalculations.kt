package com.pokemontcg.sammlung.data.repository

/** Pure calculations kept separate so diagnostic counts can be verified without a live database. */
object DataCheckCalculations {
    fun countUnassigned(userCardIds: Iterable<String>, catalogCardIds: Set<String>): Int =
        userCardIds.count { it !in catalogCardIds }

    fun countCardsByLanguage(
        languages: Iterable<String>,
        unknownLabel: String
    ): Map<String, Int> =
        languages.groupingBy { it.ifBlank { unknownLabel }.lowercase() }
            .eachCount()
            .toSortedMap()
}
