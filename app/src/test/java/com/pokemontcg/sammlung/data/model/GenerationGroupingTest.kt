package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.entities.PokemonEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.data.local.user.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val groups = groupPokemonByGeneration(catalog, displayed, cards, emptySet())
        assertEquals(listOf(1, 2, 3), groups.map { it.generationId })
        assertEquals(4, groups.sumOf { it.totalCount })
        assertEquals(listOf("a"), groups.first().pokemon.map { it.id })
        assertEquals(1, groups.first().collectedCount)
        assertEquals(2, groups.last().totalCount)
        assertEquals(1, groups.last().pokemon.size)
    }

    @Test
    fun hiddenGenerationsAreExcludedAndMatchesCounted() {
        val list = listOf(pokemon("a", 1), pokemon("b", 252), pokemon("c", 253))
        val groups = groupPokemonByGeneration(list, list, emptyList(), setOf(3))
        assertEquals(listOf(1), groups.map { it.generationId })
        assertEquals(2, countHiddenGenerationMatches(list, setOf(3)))
        assertFalse(groups.flatMap { it.pokemon }.any { it.id == "b" || it.id == "c" })
    }
}
