package com.pokemontcg.sammlung.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceFormatter {

    const val NO_PRICE_TEXT = "Kein Preis verfügbar"

    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.GERMANY))

    fun formatPrice(
        price: Double?,
        sourceName: String = "Cardmarket",
        priceType: String = "Trend",
        dateString: String? = null
    ): String {
        if (price == null || price <= 0.0) {
            return NO_PRICE_TEXT
        }
        val formattedPrice = "${decimalFormat.format(price)} €"
        return when {
            !dateString.isNullOrBlank() -> "$formattedPrice · $sourceName · $priceType · Stand $dateString"
            else -> "$formattedPrice · $sourceName"
        }
    }

    fun formatPriceShort(price: Double?): String {
        if (price == null || price <= 0.0) {
            return NO_PRICE_TEXT
        }
        return "${decimalFormat.format(price)} €"
    }
}
