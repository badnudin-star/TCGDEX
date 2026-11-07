package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import com.pokemontcg.sammlung.ui.components.ValuePoint
import kotlin.math.ceil

/**
 * Daily collection value for the chart: for every day the last known price of each card is carried
 * forward, so gaps between measurements stay flat instead of dropping to zero.
 *
 * Days before the first measurement of any card are **not** emitted. Back then nothing was
 * measured, and filling those days with the earliest known price used to fake a longer, flatter
 * history than the data supports.
 */
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
        var measured = false

        for ((cardId, qty) in quantities) {
            val cardHistory = historyByCard[cardId] ?: continue
            // Only measurements taken at or before this day count. A card that was first recorded
            // later contributes nothing here; it must not be projected backwards, because that
            // invents a value for a day nobody ever measured (bug review 2026-11-07, finding E).
            val pointBeforeDay = cardHistory.filter { it.timestamp <= dayEndTs }.maxByOrNull { it.timestamp }

            if (pointBeforeDay != null) {
                dayTotal += pointBeforeDay.marketPrice * qty
                measured = true
            }
        }

        // Days before the very first measurement are left out instead of being padded. The chart
        // plots its points by index, so a shorter series simply starts at the first real value.
        if (measured) {
            points.add(ValuePoint(timestamp = dayEndTs, totalValue = dayTotal))
        }
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
