package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.dao.CardSearchRow
import com.pokemontcg.sammlung.data.local.entities.CardEntity

/** One Pokémon with the catalog cards found for it. */
data class PokemonCardGroup(
    /** Null for cards without a Pokédex link, e.g. trainer and energy cards. */
    val dexId: Int?,
    val pokemonName: String,
    val cards: List<CardEntity>
)

/** A generation block of the search result, mirroring the main screen's structure. */
data class CatalogSearchSection(
    /** Null for the trailing block of cards without a Pokédex link. */
    val generationId: Int?,
    val groups: List<PokemonCardGroup>
)

/**
 * Turns flat catalog search hits into the Pokémon grouping used by the main screen:
 * generation blocks, inside them Pokémon ordered by Pokédex id, cards without a Pokédex link last.
 *
 * `excludedDexIds` drops every Pokémon the user already declared a full art favourite for, so the
 * wishlist search only offers Pokémon that are still missing one.
 */
fun groupCatalogSearchByPokemon(
    rows: List<CardSearchRow>,
    cardsById: Map<String, CardEntity>,
    pokemonNameByDexId: Map<Int, String>,
    excludedDexIds: Set<Int> = emptySet()
): List<CatalogSearchSection> {
    val groupsByDexId = LinkedHashMap<Int?, MutableList<CardEntity>>()
    for (row in rows) {
        if (row.dexId != null && row.dexId in excludedDexIds) continue
        val card = cardsById[row.cardId] ?: continue
        groupsByDexId.getOrPut(row.dexId) { mutableListOf() }.add(card)
    }

    val groups = groupsByDexId.map { (dexId, cards) ->
        PokemonCardGroup(
            dexId = dexId,
            pokemonName = dexId?.let { pokemonNameByDexId[it] }.orEmpty(),
            cards = cards
        )
    }

    return groups
        .groupBy { group -> group.dexId?.let { GenerationModel.generationOf(it) } }
        .toSortedMap(compareBy(nullsLast<Int>()) { it })
        .map { (generationId, sectionGroups) ->
            CatalogSearchSection(
                generationId = generationId,
                groups = sectionGroups.sortedWith(
                    compareBy(nullsLast<Int>()) { it.dexId }
                )
            )
        }
        .filter { it.groups.isNotEmpty() }
}

/** Counts how many Pokémon of the result were dropped because they already are full art. */
fun countExcludedFullartPokemon(rows: List<CardSearchRow>, excludedDexIds: Set<Int>): Int =
    rows.mapNotNull { it.dexId }
        .filter { it in excludedDexIds }
        .distinct()
        .size
