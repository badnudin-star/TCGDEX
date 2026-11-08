package com.pokemontcg.sammlung.data.export

import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.WishlistEntity
import com.pokemontcg.sammlung.util.CardmarketLinks
import com.pokemontcg.sammlung.util.PriceFormatter

/**
 * Builds the shareable plain-text export of the wishlist: every card with set, number, language,
 * price, target price, note and its Cardmarket link. The result is written to a .txt file that
 * can be saved via the system file picker or shared through WhatsApp & co.
 *
 * Pure function on purpose (no Android imports) so the format is unit-testable.
 */
object WishlistTextExporter {

    fun buildText(
        wishlist: List<WishlistEntity>,
        cardsById: Map<String, CardEntity>,
        headerDateText: String,
        noPriceText: String
    ): String {
        val builder = StringBuilder()
        builder.appendLine("TCG-Sammlung – Wunschliste")
        builder.appendLine("Stand: $headerDateText · ${wishlist.size} Karten")
        if (wishlist.isEmpty()) {
            return builder.toString().trimEnd() + "\n"
        }
        builder.appendLine()
        wishlist.forEachIndexed { index, wish ->
            val card = cardsById[wish.cardId]
            val number = index + 1
            if (card == null) {
                builder.appendLine("$number. ${wish.cardId}")
                builder.appendLine("   ${noCardText()}")
            } else {
                builder.appendLine("$number. ${card.name}")
                builder.appendLine("   Set: ${card.setName} · Nr. ${card.number}")
                builder.appendLine("   Sprache: ${card.language.uppercase()}")
                builder.appendLine("   Preis: ${PriceFormatter.formatPriceShort(card.marketPrice, noPriceText)}")
                wish.targetPrice?.let {
                    builder.appendLine("   Zielpreis: ${PriceFormatter.formatPriceShort(it, noPriceText)}")
                }
                if (!wish.notes.isNullOrBlank()) {
                    builder.appendLine("   Notiz: ${wish.notes.trim()}")
                }
                builder.appendLine("   Cardmarket: ${CardmarketLinks.forCard(card.name, card.setName)}")
            }
            builder.appendLine()
        }
        return builder.toString().trimEnd() + "\n"
    }

    private fun noCardText(): String = "Kartendetails sind im Katalog nicht mehr vorhanden."
}
