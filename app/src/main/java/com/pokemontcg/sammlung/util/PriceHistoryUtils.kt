package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.ui.components.ValuePoint
import kotlin.math.ceil

fun buildValueSeries(
    history: List<PriceHistoryEntity>,
    quantities: Map<String, Int>,
    rangeDays: Int,
    now: Long = System.currentTimeMillis()
): List<ValuePoint> {
    if (history.isEmpty() || quantities.isEmpty()) return emptyList()

    val msPerDay = 24L * 60L * 60L * 1000L
    val effectiveDays = if (rangeDays > 0) rangeDays else {
        val minTs = history.minOf { it.timestamp }
        val elapsedDays = ceil((now - minTs).toDouble() / msPerDay.toDouble()).toInt().coerceAtLeast(0)
        elapsedDays + 1
    }

    val historyByCard = history.groupBy { it.cardId }
    val points = mutableListOf<ValuePoint>()

    for (dayIndex in (effectiveDays - 1) downTo 0) {
        val dayEndTs = now - (dayIndex * msPerDay)
        var dayTotal = 0.0

        for ((cardId, qty) in quantities) {
            val cardHistory = historyByCard[cardId] ?: continue
            val pointBeforeDay = cardHistory.filter { it.timestamp <= dayEndTs }.maxByOrNull { it.timestamp }
                ?: cardHistory.minByOrNull { it.timestamp }

            if (pointBeforeDay != null) {
                dayTotal += pointBeforeDay.marketPrice * qty
            }
        }

        points.add(ValuePoint(timestamp = dayEndTs, totalValue = dayTotal))
    }

    return points
}


/** Records a point when the price changes or the last point is at least 20 hours old. */
fun shouldRecordPricePoint(
    latestPrice: Double?,
    latestTimestamp: Long?,
    newPrice: Double,
    now: Long = System.currentTimeMillis()
): Boolean {
    if (!newPrice.isFinite() || newPrice <= 0.0) return false
    if (latestPrice == null || latestTimestamp == null) return true
    return latestPrice != newPrice || now - latestTimestamp >= 20L * 60L * 60L * 1000L
}
