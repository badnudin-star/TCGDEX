package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.data.local.dao.CardSearchRow
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchGroupingTest {

    private fun card(
        id: String,
        name: String = id,
        price: Double? = 1.0,
        rarity: String? = "Common",
        language: String = "de"
    ) = CardEntity(
        id = id,
        name = name,
        supertype = "Pokémon",
        number = "1",
        rarity = rarity,
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
        language = language
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
        assertEquals(listOf("card-25b", "card-25a"), sections.single().groups[1].cards.map { it.representative.id })
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
        assertEquals(
            listOf("trainer-1", "trainer-2"),
            sections.last().groups.single().cards.map { it.representative.id }
        )
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

    @Test
    fun languagePrintsOfTheSameCardAreMergedIntoOneEntry() {
        val rows = listOf(
            CardSearchRow("xy1-1", 25),
            CardSearchRow("en:xy1-1", 25),
            CardSearchRow("fr:xy1-1", 25)
        )
        val cardsById = mapOf(
            "xy1-1" to card("xy1-1", language = "de"),
            "en:xy1-1" to card("en:xy1-1", language = "en"),
            "fr:xy1-1" to card("fr:xy1-1", language = "fr")
        )

        val group = groupCatalogSearchByPokemon(rows, cardsById, emptyMap())
            .single().groups.single()

        assertEquals(1, group.cards.size)
        val merged = group.cards.single()
        assertEquals(listOf("DE", "EN", "FR"), merged.languages)
        assertEquals(3, merged.variants.map { it.id }.toSet().size)
    }

    @Test
    fun representativePrefersThePreferredLanguageThenGermanThenEnglish() {
        val rows = listOf(
            CardSearchRow("fr:xy1-1", 25),
            CardSearchRow("en:xy1-1", 25),
            CardSearchRow("es:xy1-1", 25)
        )
        val cardsById = mapOf(
            "fr:xy1-1" to card("fr:xy1-1", language = "fr"),
            "en:xy1-1" to card("en:xy1-1", language = "en"),
            "es:xy1-1" to card("es:xy1-1", language = "es")
        )

        val frPreferred = groupCatalogSearchByPokemon(
            rows, cardsById, emptyMap(), preferredLanguage = "fr"
        ).single().groups.single().cards.single()
        assertEquals("fr:xy1-1", frPreferred.representative.id)

        // Without a preferred match German wins; it is the app default.
        val germanFallback = groupCatalogSearchByPokemon(
            rows, cardsById, emptyMap(), preferredLanguage = "de"
        ).single().groups.single().cards.single()
        assertEquals("en:xy1-1", germanFallback.representative.id)
    }

    @Test
    fun cardsFromDifferentSetsOrNumbersAreNotMerged() {
        val rows = listOf(
            CardSearchRow("xy1-1", 25),
            CardSearchRow("en:xy2-1", 25),
            CardSearchRow("en:xy1-2", 25)
        )
        val cardsById = mapOf(
            "xy1-1" to card("xy1-1", language = "de"),
            "en:xy2-1" to card("en:xy2-1", language = "en"),
            "en:xy1-2" to card("en:xy1-2", language = "en")
        )

        val group = groupCatalogSearchByPokemon(rows, cardsById, emptyMap())
            .single().groups.single()

        assertEquals(3, group.cards.size)
    }

    @Test
    fun rawCardIdStripsTheLanguagePrefix() {
        assertEquals("xy1-1", rawCardId("en:xy1-1"))
        assertEquals("xy1-1", rawCardId("es-mx:xy1-1"))
        // German ids have no prefix.
        assertEquals("xy1-1", rawCardId("xy1-1"))
    }
}
