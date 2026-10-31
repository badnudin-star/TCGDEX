package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import kotlin.math.abs

data class PriceChange(
    val cardId: String,
    val oldPrice: Double,
    val newPrice: Double,
    val changePercent: Double
)

val PRICE_ALERT_THRESHOLDS = listOf(5, 10, 20)
const val PRICE_ALERT_DEFAULT_THRESHOLD = 10
const val PRICE_ALERT_COOLDOWN_MS = 24L * 60L * 60L * 1000L

/**
 * Compares the latest point with the last point at or before the start of the
 * requested window. Cards without an older point are skipped because there is
 * no reliable reference price for the selected window.
 */
fun findPriceChanges(
    history: List<PriceHistoryEntity>,
    thresholdPercent: Double,
    windowDays: Int,
    now: Long = System.currentTimeMillis()
): List<PriceChange> {
    if (thresholdPercent <= 0.0 || !thresholdPercent.isFinite()) return emptyList()
    val days = if (windowDays == 7) 7 else 1
    val targetWindowTs = now - days * 24L * 60L * 60L * 1000L

    return history.groupBy { it.cardId }.mapNotNull { (cardId, points) ->
        val sorted = points.sortedBy { it.timestamp }
        val oldPoint = sorted.lastOrNull { it.timestamp <= targetWindowTs }
            ?: return@mapNotNull null
        val latestPoint = sorted.lastOrNull { it.timestamp > targetWindowTs }
            ?: return@mapNotNull null
        val oldPrice = oldPoint.marketPrice
        val newPrice = latestPoint.marketPrice
        if (!oldPrice.isFinite() || oldPrice <= 0.0 ||
            !newPrice.isFinite() || newPrice <= 0.0
        ) {
            return@mapNotNull null
        }
        val change = ((newPrice - oldPrice) / oldPrice) * 100.0
        if (!change.isFinite() || abs(change) < thresholdPercent) return@mapNotNull null
        PriceChange(cardId, oldPrice, newPrice, change)
    }.sortedByDescending { abs(it.changePercent) }
}

/** Pure 24-hour cooldown and permission gate for the notification side effect. */
fun canSendPriceAlert(
    settings: PriceAlertSettings,
    now: Long,
    notificationsAllowed: Boolean
): Boolean {
    if (!settings.enabled || !notificationsAllowed) return false
    if (settings.lastAlertTimestamp > 0L &&
        now - settings.lastAlertTimestamp < PRICE_ALERT_COOLDOWN_MS
    ) {
        return false
    }
    return true
}
