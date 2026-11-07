package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPriceReportTest {

    private val now = 1_700_000_000_000L
    private val hours = 60L * 60L * 1000L

    private fun point(cardId: String, price: Double, hoursAgo: Long) = PriceHistoryEntity(
        cardId = cardId,
        marketPrice = price,
        timestamp = now - hoursAgo * hours
    )

    @Test
    fun changedCardsAreReportedWithTheirPercentage() {
        val history = listOf(
            point("a", 10.0, 48),
            point("a", 12.0, 1),
            point("b", 5.0, 48),
            // Unchanged heartbeat point: recorded, but the value did not move.
            point("b", 5.0, 1)
        )

        val report = buildDailyPriceReport(history, mapOf("a" to 2, "b" to 1), now = now)

        assertEquals(listOf("a"), report.changedCardIds)
        assertEquals(20.0, report.changePercentByCardId.getValue("a"), 0.001)
        assertEquals(1, report.increasedCount)
        assertEquals(0, report.decreasedCount)
        // 12 * 2 + 5 * 1
        assertEquals(29.0, report.totalValue, 0.001)
        // 10 * 2 + 5 * 1
        assertEquals(25.0, report.previousValue!!, 0.001)
        assertEquals(16.0, report.valueChangePercent!!, 0.001)
        assertTrue(report.hasValueChange)
    }

    @Test
    fun priceDropsAreCountedAsDecrease() {
        val history = listOf(point("a", 20.0, 48), point("a", 15.0, 2))

        val report = buildDailyPriceReport(history, mapOf("a" to 1), now = now)

        assertEquals(listOf("a"), report.changedCardIds)
        assertEquals(-25.0, report.changePercentByCardId.getValue("a"), 0.001)
        assertEquals(0, report.increasedCount)
        assertEquals(1, report.decreasedCount)
        assertTrue(report.hasValueChange)
    }

    @Test
    fun dayWithoutMovementReportsNoChanges() {
        val history = listOf(point("a", 10.0, 48), point("a", 10.0, 1), point("b", 4.0, 3))

        val report = buildDailyPriceReport(history, mapOf("a" to 1, "b" to 3), now = now)

        assertTrue(report.changedCardIds.isEmpty())
        assertFalse(report.hasValueChange)
        assertEquals(0.0, report.valueChangePercent!!, 0.001)
        assertEquals(22.0, report.totalValue, 0.001)
        assertEquals(22.0, report.previousValue!!, 0.001)
        assertTrue(report.hasCards)
    }

    @Test
    fun changesOutsideTheWindowAreIgnored() {
        val history = listOf(point("a", 10.0, 96), point("a", 30.0, 50))

        val report = buildDailyPriceReport(history, mapOf("a" to 1), now = now)

        assertTrue("A change from two days ago belongs to an earlier report", report.changedCardIds.isEmpty())
        assertEquals(30.0, report.totalValue, 0.001)
    }

    @Test
    fun firstEverPricePointIsNotAChange() {
        val history = listOf(point("a", 12.0, 2))

        val report = buildDailyPriceReport(history, mapOf("a" to 1), now = now)

        assertTrue(report.changedCardIds.isEmpty())
        assertEquals(12.0, report.totalValue, 0.001)
        // Without an older point the first known price is the baseline, so nothing looks changed.
        assertEquals(12.0, report.previousValue!!, 0.001)
        assertFalse(report.hasValueChange)
    }

    @Test
    fun cardsWithoutHistoryAndEmptyCollectionsAreHandled() {
        val withoutHistory = buildDailyPriceReport(emptyList(), mapOf("a" to 1), now = now)
        assertTrue(withoutHistory.changedCardIds.isEmpty())
        assertEquals(0.0, withoutHistory.totalValue, 0.001)
        assertNull(withoutHistory.previousValue)
        assertNull(withoutHistory.valueChangePercent)
        assertTrue(withoutHistory.hasCards)

        val emptyCollection = buildDailyPriceReport(emptyList(), emptyMap(), now = now)
        assertFalse("Nothing to report for an empty collection", emptyCollection.hasCards)
    }

    @Test
    fun quantitiesScaleTheCollectionValue() {
        val history = listOf(point("a", 10.0, 48), point("a", 11.0, 1))

        val single = buildDailyPriceReport(history, mapOf("a" to 1), now = now)
        val triple = buildDailyPriceReport(history, mapOf("a" to 3), now = now)

        assertEquals(11.0, single.totalValue, 0.001)
        assertEquals(33.0, triple.totalValue, 0.001)
        assertEquals(30.0, triple.previousValue!!, 0.001)
    }

    @Test
    fun nothingIsReportableWithoutValuesOrMovements() {
        val withoutAnyPrice = buildDailyPriceReport(emptyList(), mapOf("a" to 1), now = now)
        assertFalse(withoutAnyPrice.hasReportableContent)

        val withValueButNoMovement = buildDailyPriceReport(
            listOf(point("a", 10.0, 1)),
            mapOf("a" to 1),
            now = now
        )
        assertTrue(withValueButNoMovement.hasReportableContent)
        assertTrue("An unchanged day is still an answer", withValueButNoMovement.changedCardIds.isEmpty())
    }

    @Test
    fun reportIsSentAtMostOncePerDay() {
        assertTrue(
            shouldSendDailyPriceReport(
                enabled = true,
                lastSentTimestamp = 0L,
                notificationsAllowed = true,
                now = now
            )
        )
        assertFalse(
            shouldSendDailyPriceReport(
                enabled = false,
                lastSentTimestamp = 0L,
                notificationsAllowed = true,
                now = now
            )
        )
        assertFalse(
            shouldSendDailyPriceReport(
                enabled = true,
                lastSentTimestamp = 0L,
                notificationsAllowed = false,
                now = now
            )
        )
        assertFalse(
            "A manual sync two hours later must not repeat the nightly report",
            shouldSendDailyPriceReport(
                enabled = true,
                lastSentTimestamp = now - 2 * hours,
                notificationsAllowed = true,
                now = now
            )
        )
        assertTrue(
            shouldSendDailyPriceReport(
                enabled = true,
                lastSentTimestamp = now - 25 * hours,
                notificationsAllowed = true,
                now = now
            )
        )
    }
}
