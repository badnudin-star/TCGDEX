package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import kotlin.math.abs

/** One nightly summary of what moved in the collection's card values. */
data class DailyPriceReport(
    /** Cards whose newest recorded price differs from the price recorded before it. */
    val changedCardIds: List<String>,
    /** Percentage change per changed card, keyed by card id. */
    val changePercentByCardId: Map<String, Double>,
    /** Current collection value using the newest known price of every owned card. */
    val totalValue: Double,
    /** Collection value as it was at the start of the reporting window. */
    val previousValue: Double?,
    val hasCards: Boolean
) {
    val increasedCount: Int
        get() = changePercentByCardId.values.count { it > 0.0 }

    val decreasedCount: Int
        get() = changePercentByCardId.values.count { it < 0.0 }

    val valueChangePercent: Double?
        get() {
            val previous = previousValue ?: return null
            if (previous <= 0.0 || !previous.isFinite()) return null
            val change = ((totalValue - previous) / previous) * 100.0
            return if (change.isFinite()) change else null
        }

    /** Anything below half a percent is displayed as "unchanged" instead of noise. */
    val hasValueChange: Boolean
        get() = abs(valueChangePercent ?: 0.0) >= 0.05

    /**
     * False when neither a movement nor any known value exists - an empty collection or a catalog
     * without prices has nothing to tell the user, so no notification is posted.
     */
    val hasReportableContent: Boolean
        get() = changedCardIds.isNotEmpty() || totalValue > 0.0
}

const val DAILY_REPORT_WINDOW_MS = 24L * 60L * 60L * 1000L

private const val VALUE_CHANGE_EPSILON = 0.0001

/**
 * Builds the nightly report from the recorded price history.
 *
 * A price point is written when a price changed *or* when the last point is older than 20 hours,
 * so "a point exists in the last 24 hours" alone does not mean the value moved. Only cards whose
 * newest point differs from the point before it count as changed.
 */
fun buildDailyPriceReport(
    history: List<PriceHistoryEntity>,
    quantities: Map<String, Int>,
    now: Long = System.currentTimeMillis(),
    windowMs: Long = DAILY_REPORT_WINDOW_MS
): DailyPriceReport {
    if (quantities.isEmpty()) {
        return DailyPriceReport(
            changedCardIds = emptyList(),
            changePercentByCardId = emptyMap(),
            totalValue = 0.0,
            previousValue = null,
            hasCards = false
        )
    }

    val windowStart = now - windowMs
    val historyByCard = history.groupBy { it.cardId }
    val changes = mutableMapOf<String, Double>()

    var totalValue = 0.0
    var previousValue = 0.0
    var hasPreviousReference = false

    for ((cardId, quantity) in quantities) {
        val points = historyByCard[cardId]?.sortedBy { it.timestamp }.orEmpty()
        if (points.isEmpty()) continue
        val effectiveQuantity = quantity.coerceAtLeast(1)

        val latest = points.last()
        totalValue += latest.marketPrice * effectiveQuantity

        // No point before the window: fall back to the oldest known price so a card added
        // yesterday still contributes a comparable baseline instead of dropping out.
        val referencePoint = points.lastOrNull { it.timestamp <= windowStart } ?: points.first()
        previousValue += referencePoint.marketPrice * effectiveQuantity
        hasPreviousReference = true

        // Only report movements that were actually measured inside this window.
        if (latest.timestamp < windowStart) continue
        val pointBefore = points.getOrNull(points.lastIndex - 1) ?: continue
        if (abs(pointBefore.marketPrice - latest.marketPrice) < VALUE_CHANGE_EPSILON) continue
        if (pointBefore.marketPrice <= 0.0 || !pointBefore.marketPrice.isFinite()) continue
        changes[cardId] = ((latest.marketPrice - pointBefore.marketPrice) / pointBefore.marketPrice) * 100.0
    }

    return DailyPriceReport(
        changedCardIds = changes.entries.sortedByDescending { abs(it.value) }.map { it.key },
        changePercentByCardId = changes,
        totalValue = totalValue,
        previousValue = previousValue.takeIf { hasPreviousReference },
        hasCards = true
    )
}

/** Pure gate: the report is sent at most once per day and only when the user left it enabled. */
fun shouldSendDailyPriceReport(
    enabled: Boolean,
    lastSentTimestamp: Long,
    notificationsAllowed: Boolean,
    now: Long = System.currentTimeMillis(),
    windowMs: Long = DAILY_REPORT_WINDOW_MS
): Boolean {
    if (!enabled || !notificationsAllowed) return false
    if (lastSentTimestamp > 0L && now - lastSentTimestamp < windowMs) return false
    return true
}
