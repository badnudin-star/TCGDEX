package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.dao.CardSearchRow
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchGroupingTest {

    private fun card(id: String, name: String = id, price: Double? = 1.0) = CardEntity(
        id = id,
        name = name,
        supertype = "Pokémon",
        number = "1",
        rarity = "Common",
        setId = "set1",
        setName = "Set 1",
        series = "Series",
        imageUrl = null,
        largeImageUrl = null,
        marketPrice = price,
        lowPrice = null,
        trendPrice = null,
        nationalPokedexNumber = null,
        subtypes = null,
        language = "de"
    )

    @Test
    fun cardsAreGroupedPerPokemonAndOrderedLikeTheMainScreen() {
        val rows = listOf(
            CardSearchRow("card-25b", 25),
            CardSearchRow("card-25a", 25),
            CardSearchRow("card-150", 150),
            CardSearchRow("card-6", 6)
        )
        val cardsById = listOf("card-25a", "card-25b", "card-150", "card-6").associateWith { card(it) }

        val sections = groupCatalogSearchByPokemon(rows, cardsById, emptyMap())

        // All three Pokémon belong to generation 1 and stay in Pokédex order.
        assertEquals(listOf(1), sections.map { it.generationId })
        assertEquals(listOf(6, 25, 150), sections.single().groups.map { it.dexId })
        assertEquals(listOf("card-25b", "card-25a"), sections.single().groups[1].cards.map { it.id })
    }

    @Test
    fun generationSectionsFollowTheDexOrder() {
        val rows = listOf(
            CardSearchRow("card-906", 906),
            CardSearchRow("card-1", 1),
            CardSearchRow("card-252", 252)
        )
        val cardsById = listOf("card-1", "card-252", "card-906").associateWith { card(it) }

        val sections = groupCatalogSearchByPokemon(rows, cardsById, emptyMap())

        assertEquals(listOf(1, 3, 9), sections.map { it.generationId })
    }

    @Test
    fun pokemonWithFullartFavouriteAreFilteredOut() {
        val rows = listOf(
            CardSearchRow("card-6", 6),
            CardSearchRow("card-25", 25),
            CardSearchRow("card-25b", 25)
        )
        val cardsById = listOf("card-6", "card-25", "card-25b").associateWith { card(it) }

        val sections = groupCatalogSearchByPokemon(rows, cardsById, emptyMap(), excludedDexIds = setOf(25))

        assertEquals(listOf(6), sections.flatMap { it.groups }.map { it.dexId })
        assertEquals(1, countExcludedFullartPokemon(rows, setOf(25)))
    }

    @Test
    fun cardsWithoutPokedexLinkLandInTheTrailingSection() {
        val rows = listOf(
            CardSearchRow("trainer-1", null),
            CardSearchRow("card-25", 25),
            CardSearchRow("trainer-2", null)
        )
        val cardsById = listOf("trainer-1", "card-25", "trainer-2").associateWith { card(it) }

        val sections = groupCatalogSearchByPokemon(rows, cardsById, emptyMap())

        assertEquals(listOf(1, null), sections.map { it.generationId })
        assertEquals(listOf("trainer-1", "trainer-2"), sections.last().groups.single().cards.map { it.id })
        assertNull(sections.last().groups.single().dexId)
    }

    @Test
    fun unknownCardsAndEmptyResultsProduceNoSections() {
        val rows = listOf(CardSearchRow("missing-card", 25))

        assertTrue(groupCatalogSearchByPokemon(rows, emptyMap(), emptyMap()).isEmpty())
        assertTrue(groupCatalogSearchByPokemon(emptyList(), emptyMap(), emptyMap()).isEmpty())
        assertEquals(0, countExcludedFullartPokemon(emptyList(), setOf(25)))
    }

    @Test
    fun localizedPokemonNamesReachTheGroupHeaders() {
        val rows = listOf(CardSearchRow("card-25", 25))
        val cardsById = mapOf("card-25" to card("card-25"))

        val sections = groupCatalogSearchByPokemon(rows, cardsById, mapOf(25 to "Pikachu"))

        assertEquals("Pikachu", sections.single().groups.single().pokemonName)
    }
}
