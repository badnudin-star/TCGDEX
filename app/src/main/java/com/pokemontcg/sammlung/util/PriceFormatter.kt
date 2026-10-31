package com.pokemontcg.sammlung.util

import android.content.Context
import com.pokemontcg.sammlung.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class PriceLabels(
    val noPrice: String,
    val sourceName: String,
    val priceType: String,
    val dateLabel: String
)

object PriceFormatter {

    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.GERMANY))

    fun labels(context: Context): PriceLabels = PriceLabels(
        noPrice = context.getString(R.string.price_no_price),
        sourceName = context.getString(R.string.price_source_cardmarket),
        priceType = context.getString(R.string.price_type_trend),
        dateLabel = context.getString(R.string.price_date_label)
    )

    fun formatPrice(
        price: Double?,
        labels: PriceLabels,
        dateString: String? = null
    ): String {
        if (price == null || price <= 0.0) {
            return labels.noPrice
        }
        val formattedPrice = "${decimalFormat.format(price)} €"
        return when {
            !dateString.isNullOrBlank() ->
                "$formattedPrice · ${labels.sourceName} · ${labels.priceType} · ${labels.dateLabel} $dateString"
            else -> "$formattedPrice · ${labels.sourceName}"
        }
    }

    fun formatPrice(
        context: Context,
        price: Double?,
        sourceName: String? = null,
        priceType: String? = null,
        dateString: String? = null
    ): String {
        val base = labels(context)
        return formatPrice(
            price = price,
            labels = base.copy(
                sourceName = sourceName ?: base.sourceName,
                priceType = priceType ?: base.priceType
            ),
            dateString = dateString
        )
    }

    fun formatPriceShort(price: Double?, noPriceText: String): String {
        if (price == null || price <= 0.0) {
            return noPriceText
        }
        return "${decimalFormat.format(price)} €"
    }

    fun formatPriceShort(context: Context, price: Double?): String =
        formatPriceShort(price, context.getString(R.string.price_no_price))
}
