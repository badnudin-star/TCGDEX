package com.pokemontcg.sammlung.data.export

import android.content.Context
import android.content.Intent
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.util.PriceFormatter

object TradeListExporter {

    fun generateTradeListText(
        duplicates: List<UserCardEntityV2>,
        cardDetailsMap: Map<String, CardEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("🎴 Meine Doppelte- & Tauschliste (TCG Sammlung)\n")
        sb.append("===========================================\n\n")

        if (duplicates.isEmpty()) {
            sb.append("Derzeit keine doppelten Karten in der Sammlung.\n")
            return sb.toString()
        }

        duplicates.forEachIndexed { index, userCard ->
            val detail = cardDetailsMap[userCard.cardId]
            val cardName = detail?.name ?: userCard.cardId
            val setName = detail?.setName ?: "Unbekanntes Set"
            val number = detail?.number ?: ""
            val priceText = PriceFormatter.formatPriceShort(detail?.marketPrice)
            val tradeQuantity = (userCard.quantity - 1).coerceAtLeast(1)

            sb.append("${index + 1}. $cardName")
            if (number.isNotBlank()) sb.append(" (#$number)")
            sb.append("\n")
            sb.append("   Set: $setName\n")
            sb.append("   Verfügbar zum Tauschen: $tradeQuantity x\n")
            sb.append("   Zustand: ${userCard.condition ?: "Near Mint"}\n")
            sb.append("   Richtpreis: $priceText\n\n")
        }

        sb.append("Erstellt mit TCG Sammlung App.")
        return sb.toString()
    }

    fun shareTradeList(
        context: Context,
        duplicates: List<UserCardEntityV2>,
        cardDetailsMap: Map<String, CardEntity>
    ) {
        val textContent = generateTradeListText(duplicates, cardDetailsMap)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, textContent)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Tauschliste teilen")
        context.startActivity(shareIntent)
    }
}
