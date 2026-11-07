package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationGroupingTest {
    private fun pokemon(id: String, dex: Int) = PokemonEntity(
        id = id, dexId = dex, number = dex.toString(), name = id, searchName = id,
        imageUrl = null, primaryCardImageUrl = null, cardType = CardCategory.NONE
    )

    @Test
    fun groupingPreservesCatalogTotalsAndDexOrder() {
        val catalog = listOf(pokemon("c", 252), pokemon("b", 152), pokemon("a", 1), pokemon("d", 253))
        val displayed = listOf(pokemon("c", 252), pokemon("b", 152), pokemon("a", 1))
        val cards = listOf(UserCardEntityV2(cardId = "card-a", pokemonId = "a", pokemonDexId = 1))
        val groups = groupPokemonByGeneration(catalog, displayed, cards)
        assertEquals(listOf(1, 2, 3), groups.map { it.generationId })
        assertEquals(4, groups.sumOf { it.totalCount })
        assertEquals(listOf("a"), groups.first().pokemon.map { it.id })
        assertEquals(1, groups.first().collectedCount)
        assertEquals(2, groups.last().totalCount)
        assertEquals(1, groups.last().pokemon.size)
    }

    @Test
    fun importedCardsWithoutPokemonIdStillCountAsCollected() {
        val catalog = listOf(pokemon("25", 25), pokemon("26", 26))
        // Rows imported from a backup before the fix carry an empty pokemonId.
        val cards = listOf(
            UserCardEntityV2(cardId = "card-25", pokemonId = "", pokemonDexId = 25),
            UserCardEntityV2(cardId = "card-26", pokemonId = "", pokemonDexId = 26)
        )

        val groups = groupPokemonByGeneration(catalog, catalog, cards)

        assertEquals(2, groups.single().collectedCount)
    }

    @Test
    fun everyGenerationStaysVisibleAndEmptySectionsAreDropped() {
        val list = listOf(pokemon("a", 1), pokemon("b", 252), pokemon("c", 253))

        val groups = groupPokemonByGeneration(list, list, emptyList())

        // No generation can be hidden any more, and only generations with a search hit appear.
        assertEquals(listOf(1, 3), groups.map { it.generationId })
        assertEquals(listOf("a", "c", "b").sorted(), groups.flatMap { it.pokemon }.map { it.id }.sorted())

        val onlyFirst = groupPokemonByGeneration(list, listOf(pokemon("a", 1)), emptyList())
        assertEquals(listOf(1), onlyFirst.map { it.generationId })
        // Totals keep counting the whole catalog even when the search shows less.
        assertEquals(1, onlyFirst.single().totalCount)
    }
}
