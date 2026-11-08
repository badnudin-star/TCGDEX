package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.data.local.entities.CardEntity

/**
 * One accumulated scan result in batch mode: the best catalog match of a scan plus how
 * often it was scanned (or manually corrected with the +/- controls).
 */
data class BatchScanItem(
    val card: CardEntity,
    val count: Int
)

/**
 * Adds a scanned card to a batch list: re-scanning the same card increments its count,
 * a new card is appended. The list order stays stable and ids stay unique.
 */
fun mergeBatchScanItem(items: List<BatchScanItem>, card: CardEntity): List<BatchScanItem> {
    var found = false
    val updated = items.map { item ->
        if (item.card.id == card.id) {
            found = true
            item.copy(count = item.count + 1)
        } else {
            item
        }
    }
    return if (found) updated else updated + BatchScanItem(card, 1)
}

/** Changes the count of one batch item; items dropping to zero are removed. */
fun adjustBatchItemCount(items: List<BatchScanItem>, cardId: String, delta: Int): List<BatchScanItem> =
    items.mapNotNull { item ->
        if (item.card.id != cardId) {
            item
        } else {
            val newCount = item.count + delta
            if (newCount > 0) item.copy(count = newCount) else null
        }
    }

/** Total number of scanned cards across all batch items. */
fun totalBatchCards(items: List<BatchScanItem>): Int = items.sumOf { it.count }
