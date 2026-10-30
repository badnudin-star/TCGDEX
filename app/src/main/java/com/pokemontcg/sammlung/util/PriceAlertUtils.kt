package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import kotlin.math.abs

data class PriceChange(
    val cardId: String,
    val oldPrice: Double,
    val newPrice: Double,
    val changePercent: Double
)

/** Pure comparison logic so alert thresholds can be verified without Android notification APIs. */
fun findPriceChanges(
    history: List<PriceHistoryEntity>,
    thresholdPercent: Double,
    windowDays: Int,
    now: Long = System.currentTimeMillis()
): List<PriceChange> {
    if (thresholdPercent <= 0.0 || !thresholdPercent.isFinite()) return emptyList()
    val windowMs = (if (windowDays == 7) 7L else 1L) * 24L * 60L * 60L * 1000L
    val targetWindowTs = now - windowMs

    return history.groupBy { it.cardId }.mapNotNull { (cardId, points) ->
        if (points.size < 2) return@mapNotNull null
        val latestPoint = points.maxByOrNull { it.timestamp } ?: return@mapNotNull null
        val oldPoint = points.filter { it.timestamp <= targetWindowTs }.maxByOrNull { it.timestamp }
            ?: points.minByOrNull { it.timestamp }
            ?: return@mapNotNull null
        val oldPrice = oldPoint.marketPrice
        val newPrice = latestPoint.marketPrice
        if (oldPrice <= 0.0 || !newPrice.isFinite() || newPrice <= 0.0) return@mapNotNull null
        val change = ((newPrice - oldPrice) / oldPrice) * 100.0
        if (abs(change) < thresholdPercent) return@mapNotNull null
        PriceChange(cardId, oldPrice, newPrice, change)
    }.sortedByDescending { abs(it.changePercent) }
}
