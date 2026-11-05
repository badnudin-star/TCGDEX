package com.pokemontcg.sammlung.ui.main

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryImageDerivationTest {

    private fun userCard(
        cardId: String,
        dexId: Int,
        isPrimary: Boolean = false
    ) = UserCardEntityV2(
        cardId = cardId,
        pokemonId = dexId.toString(),
        pokemonDexId = dexId,
        isPrimary = isPrimary
    )

    private fun card(
        id: String,
        imageUrl: String? = null,
        largeImageUrl: String? = null
    ) = CardEntity(
        id = id,
        name = "Card $id",
        supertype = "Pokémon",
        number = "1",
        rarity = null,
        setId = "set-1",
        setName = "Set 1",
        series = "Series",
        imageUrl = imageUrl,
        largeImageUrl = largeImageUrl,
        marketPrice = null,
        lowPrice = null,
        trendPrice = null,
        nationalPokedexNumber = 1,
        subtypes = null,
        language = "de"
    )

    @Test
    fun primaryCardLargeImageIsUsed() {
        val cards = mapOf(
            "primary" to card("primary", imageUrl = "small.png", largeImageUrl = "large.png"),
            "secondary" to card("secondary", imageUrl = "other.png")
        )

        val result = derivePrimaryImageByDexId(
            userCards = listOf(
                userCard("secondary", 1),
                userCard("primary", 1, isPrimary = true)
            ),
            cardsById = cards
        )

        assertEquals(mapOf(1 to "large.png"), result)
    }

    @Test
    fun cardImageIsFallbackWhenLargeImageIsMissing() {
        val cards = mapOf("primary" to card("primary", imageUrl = "small.png"))

        val result = derivePrimaryImageByDexId(
            userCards = listOf(userCard("primary", 1, isPrimary = true)),
            cardsById = cards
        )

        assertEquals(mapOf(1 to "small.png"), result)
    }

    @Test
    fun nonPrimaryCardsAreIgnored() {
        val cards = mapOf("secondary" to card("secondary", imageUrl = "small.png"))

        val result = derivePrimaryImageByDexId(
            userCards = listOf(userCard("secondary", 1)),
            cardsById = cards
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun missingCardsAndBlankImagesAreOmitted() {
        val cards = mapOf(
            "blank" to card("blank", imageUrl = " ", largeImageUrl = ""),
            "no-image" to card("no-image")
        )

        val result = derivePrimaryImageByDexId(
            userCards = listOf(
                userCard("missing", 1, isPrimary = true),
                userCard("blank", 2, isPrimary = true),
                userCard("no-image", 3, isPrimary = true)
            ),
            cardsById = cards
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun imagesAreMappedIndependentlyPerDexId() {
        val cards = mapOf(
            "one" to card("one", imageUrl = "one.png"),
            "two" to card("two", largeImageUrl = "two-large.png")
        )

        val result = derivePrimaryImageByDexId(
            userCards = listOf(
                userCard("one", 1, isPrimary = true),
                userCard("two", 2, isPrimary = true)
            ),
            cardsById = cards
        )

        assertEquals(mapOf(1 to "one.png", 2 to "two-large.png"), result)
    }

    @Test
    fun firstPrimaryEntryPerDexIdWins() {
        val cards = mapOf(
            "first" to card("first", imageUrl = "first.png"),
            "second" to card("second", imageUrl = "second.png")
        )

        val result = derivePrimaryImageByDexId(
            userCards = listOf(
                userCard("first", 1, isPrimary = true),
                userCard("second", 1, isPrimary = true)
            ),
            cardsById = cards
        )

        assertEquals(mapOf(1 to "first.png"), result)
    }
}
