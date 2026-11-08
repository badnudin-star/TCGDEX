package com.pokemontcg.sammlung.data.export

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.util.CardmarketLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistTextExporterTest {

    private fun card(
        id: String,
        name: String = "Pikachu",
        setName: String = "Base Set",
        number: String = "25/102",
        price: Double? = 12.5,
        language: String = "de"
    ) = CardEntity(
        id = id,
        name = name,
        supertype = "Pokémon",
        number = number,
        rarity = "Holo Rare",
        setId = "base1",
        setName = setName,
        series = "Serie",
        imageUrl = null,
        largeImageUrl = null,
        marketPrice = price,
        lowPrice = null,
        trendPrice = null,
        nationalPokedexNumber = 25,
        subtypes = null,
        language = language
    )

    private fun wish(
        cardId: String,
        targetPrice: Double? = null,
        notes: String? = null,
        addedAt: Long = 1L
    ) = WishlistEntity(
        cardId = cardId,
        targetPrice = targetPrice,
        notes = notes,
        addedAt = addedAt
    )

    @Test
    fun listsEveryCardWithSetLanguagePriceTargetNoteAndLink() {
        val cards = mapOf(
            "xy1-1" to card("xy1-1"),
            "en:xy2-5" to card("en:xy2-5", name = "Bibor V", setName = "XY", number = "5/100", price = null, language = "en")
        )

        val text = WishlistTextExporter.buildText(
            wishlist = listOf(
                wish("xy1-1", targetPrice = 10.0, notes = " Geburtstag "),
                wish("en:xy2-5")
            ),
            cardsById = cards,
            headerDateText = "08.11.2026",
            noPriceText = "/"
        )

        assertTrue(text.startsWith("TCG-Sammlung – Wunschliste"))
        assertTrue(text.contains("Stand: 08.11.2026 · 2 Karten"))
        assertTrue(text.contains("1. Pikachu"))
        assertTrue(text.contains("Set: Base Set · Nr. 25/102"))
        assertTrue(text.contains("Sprache: DE"))
        assertTrue(text.contains("Preis: 12,50 €"))
        assertTrue(text.contains("Zielpreis: 10,00 €"))
        assertTrue(text.contains("Notiz: Geburtstag"))
        assertTrue(text.contains(CardmarketLinks.forCard("Pikachu", "Base Set")))
        assertTrue(text.contains("2. Bibor V"))
        assertTrue(text.contains("Sprache: EN"))
        assertTrue(text.contains("Preis: /"))
        // No target/notes lines for the second card.
        val secondBlock = text.substringAfter("2. Bibor V")
        assertFalse(secondBlock.contains("Zielpreis"))
        assertFalse(secondBlock.contains("Notiz"))
    }

    @Test
    fun missingCatalogEntriesStillListTheCardId() {
        val text = WishlistTextExporter.buildText(
            wishlist = listOf(wish("gone-card")),
            cardsById = emptyMap(),
            headerDateText = "08.11.2026",
            noPriceText = "/"
        )

        assertTrue(text.contains("1. gone-card"))
        assertTrue(text.contains("Kartendetails sind im Katalog nicht mehr vorhanden."))
    }

    @Test
    fun emptyWishlistExportsHeaderOnly() {
        val text = WishlistTextExporter.buildText(
            wishlist = emptyList(),
            cardsById = emptyMap(),
            headerDateText = "08.11.2026",
            noPriceText = "/"
        )

        assertEquals("TCG-Sammlung – Wunschliste\nStand: 08.11.2026 · 0 Karten\n", text)
    }
}
