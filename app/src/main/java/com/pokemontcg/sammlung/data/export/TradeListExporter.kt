package com.pokemontcg.sammlung.data.export

import android.content.Context
import android.content.Intent
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.entities.CardEntity
import com.pokemontcg.sammlung.data.local.user.UserCardEntityV2
import com.pokemontcg.sammlung.util.PriceFormatter

data class TradeListLabels(
    val title: String,
    val empty: String,
    val unknownSet: String,
    val setLabel: String,
    val availableLabel: String,
    val conditionLabel: String,
    val defaultCondition: String,
    val priceLabel: String,
    val footer: String,
    val noPriceText: String
)

object TradeListExporter {

    fun labels(context: Context): TradeListLabels = TradeListLabels(
        title = context.getString(R.string.trade_list_title),
        empty = context.getString(R.string.trade_list_empty),
        unknownSet = context.getString(R.string.trade_list_unknown_set),
        setLabel = context.getString(R.string.trade_list_set_label),
        availableLabel = context.getString(R.string.trade_list_available_label),
        conditionLabel = context.getString(R.string.trade_list_condition_label),
        defaultCondition = context.getString(R.string.trade_list_default_condition),
        priceLabel = context.getString(R.string.trade_list_price_label),
        footer = context.getString(R.string.trade_list_footer),
        noPriceText = context.getString(R.string.price_no_price)
    )

    fun generateTradeListText(
        duplicates: List<UserCardEntityV2>,
        cardDetailsMap: Map<String, CardEntity>,
        labels: TradeListLabels
    ): String {
        val sb = StringBuilder()
        sb.append(labels.title).append("\n")
        sb.append("===========================================\n\n")

        if (duplicates.isEmpty()) {
            sb.append(labels.empty).append("\n")
            return sb.toString()
        }

        duplicates.forEachIndexed { index, userCard ->
            val detail = cardDetailsMap[userCard.cardId]
            val cardName = detail?.name ?: userCard.cardId
            val setName = detail?.setName ?: labels.unknownSet
            val number = detail?.number ?: ""
            val priceText = PriceFormatter.formatPriceShort(detail?.marketPrice, labels.noPriceText)
            val tradeQuantity = (userCard.quantity - 1).coerceAtLeast(1)

            sb.append("${index + 1}. $cardName")
            if (number.isNotBlank()) sb.append(" (#$number)")
            sb.append("\n")
            sb.append("   ${labels.setLabel}: $setName\n")
            sb.append("   ${labels.availableLabel}: $tradeQuantity x\n")
            sb.append("   ${labels.conditionLabel}: ${userCard.condition ?: labels.defaultCondition}\n")
            sb.append("   ${labels.priceLabel}: $priceText\n\n")
        }

        sb.append(labels.footer)
        return sb.toString()
    }

    fun shareTradeList(
        context: Context,
        duplicates: List<UserCardEntityV2>,
        cardDetailsMap: Map<String, CardEntity>
    ) {
        val textContent = generateTradeListText(duplicates, cardDetailsMap, labels(context))
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, textContent)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(
            sendIntent,
            context.getString(R.string.trade_list_share_chooser)
        )
        context.startActivity(shareIntent)
    }
}
