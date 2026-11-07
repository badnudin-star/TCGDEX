package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2

data class GenerationSection(
    val generationId: Int,
    val pokemon: List<PokemonEntity>,
    val collectedCount: Int,
    val totalCount: Int
)

/** Pure grouping function shared by the ViewModel and unit tests. */
fun groupPokemonByGeneration(
    catalogPokemon: List<PokemonEntity>,
    displayedPokemon: List<PokemonEntity>,
    userCards: List<UserCardEntityV2>
): List<GenerationSection> {
    // Rows imported from a backup used to carry an empty pokemonId; falling back to the
    // Pokédex id keeps those collections counted instead of showing "0 gesammelt".
    val collectedPokemonIds = userCards.asSequence()
        .flatMap { card ->
            sequenceOf(card.pokemonId, card.pokemonDexId.takeIf { it > 0 }?.toString().orEmpty())
        }
        .filter { it.isNotBlank() }
        .toSet()
    val catalogByGeneration = catalogPokemon.asSequence()
        .groupBy { GenerationModel.generationOf(it.dexId) }
    val displayedByGeneration = displayedPokemon.asSequence()
        .groupBy { GenerationModel.generationOf(it.dexId) }
    return catalogByGeneration.toSortedMap()
        .filter { (generationId, _) -> displayedByGeneration[generationId].orEmpty().isNotEmpty() }
        .map { (generationId, allMembers) ->
            val displayed = displayedByGeneration[generationId].orEmpty()
                .sortedWith(compareBy<PokemonEntity> { it.dexId }.thenBy { it.id })
            GenerationSection(
                generationId = generationId,
                pokemon = displayed,
                collectedCount = allMembers.count { it.id in collectedPokemonIds },
                totalCount = allMembers.size
            )
        }
}
