package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceHistoryAndAlertTest {

    private val now = 1_700_000_000_000L
    private val oneDayMs = 24L * 60L * 60L * 1000L

    private fun point(id: Long, cardId: String, price: Double, daysAgo: Long): PriceHistoryEntity =
        PriceHistoryEntity(
            id = id,
            cardId = cardId,
            marketPrice = price,
            timestamp = now - daysAgo * oneDayMs
        )

    @Test
    fun testBuildValueSeriesMultipleCardsAndQuantities() {
        val history = listOf(
            point(1, "card1", 10.0, 3),
            point(2, "card1", 12.0, 1),
            point(3, "card2", 5.0, 2)
        )
        val quantities = mapOf("card1" to 2, "card2" to 3)

        val series7 = buildValueSeries(history, quantities, rangeDays = 7, now = now)

        // card2 is only measured from day 2 on, so days 6..3 hold no data at all and day 3
        // contains card1 alone: 10.0 * 2 = 20.0.
        assertEquals(4, series7.size)
        assertEquals(20.0, series7.first { it.timestamp == now - 3 * oneDayMs }.totalValue, 0.001)
        assertEquals(35.0, series7.first { it.timestamp == now - 2 * oneDayMs }.totalValue, 0.001)
        assertEquals(39.0, series7.first { it.timestamp == now - oneDayMs }.totalValue, 0.001)
    }

    @Test
    fun testBuildValueSeriesAllTimeIncludesFirstDay() {
        val history = listOf(point(1, "card1", 10.0, 3))

        val series = buildValueSeries(history, mapOf("card1" to 1), rangeDays = 0, now = now)

        assertEquals(4, series.size)
        assertEquals(now - 3 * oneDayMs, series.first().timestamp)
        assertEquals(10.0, series.first().totalValue, 0.001)
    }

    @Test
    fun testBuildValueSeriesUsesLastKnownPriceAcrossGaps() {
        val history = listOf(
            point(1, "card1", 10.0, 5),
            point(2, "card1", 20.0, 1)
        )

        val series = buildValueSeries(history, mapOf("card1" to 1), rangeDays = 7, now = now)

        assertEquals(10.0, series.first { it.timestamp == now - 4 * oneDayMs }.totalValue, 0.001)
        assertEquals(10.0, series.first { it.timestamp == now - 2 * oneDayMs }.totalValue, 0.001)
        assertEquals(20.0, series.first { it.timestamp == now - oneDayMs }.totalValue, 0.001)
        assertEquals(20.0, series.first { it.timestamp == now }.totalValue, 0.001)
    }

    @Test
    fun testBuildValueSeriesDoesNotProjectTheFirstPriceBackwards() {
        val history = listOf(point(1, "new-card", 7.0, 2))

        val series = buildValueSeries(history, mapOf("new-card" to 2), rangeDays = 7, now = now)

        // Three measured days (2, 1 and today) instead of seven invented ones.
        assertEquals(3, series.size)
        assertEquals(now - 2 * oneDayMs, series.first().timestamp)
        assertTrue(series.all { it.totalValue == 14.0 })
    }

    @Test
    fun testBuildValueSeriesDropsDaysBeforeTheFirstMeasurement() {
        val history = listOf(
            point(1, "card1", 10.0, 4),
            point(2, "card2", 5.0, 1)
        )

        val series = buildValueSeries(history, mapOf("card1" to 1, "card2" to 1), rangeDays = 30, now = now)

        assertEquals(now - 4 * oneDayMs, series.first().timestamp)
        assertEquals(5, series.size)
        // card1 carries forward, card2 joins from its own first measurement on.
        assertEquals(10.0, series.first { it.timestamp == now - 2 * oneDayMs }.totalValue, 0.001)
        assertEquals(15.0, series.first { it.timestamp == now - oneDayMs }.totalValue, 0.001)
    }

    @Test
    fun testBuildValueSeriesSingleMeasurementIsOnePointNotAFakeLine() {
        val history = listOf(point(1, "card1", 12.0, 0))

        val series = buildValueSeries(history, mapOf("card1" to 1), rangeDays = 90, now = now)

        // One point means the chart reports "not enough data" instead of drawing a flat line
        // across 90 days that were never measured.
        assertEquals(1, series.size)
        assertEquals(12.0, series.first().totalValue, 0.001)
    }

    @Test
    fun testBuildValueSeriesReturnsEmptyWithoutHistoryOrQuantities() {
        assertTrue(buildValueSeries(emptyList(), mapOf("card1" to 1), 7, now).isEmpty())
        assertTrue(buildValueSeries(listOf(point(1, "card1", 10.0, 1)), emptyMap(), 7, now).isEmpty())
    }

    @Test
    fun testPricePointRecordedOnlyOnChangeOrAfterTwentyHours() {
        val twentyHours = 20L * 60L * 60L * 1000L

        assertTrue(shouldRecordPricePoint(null, null, 10.0, now))
        assertTrue(shouldRecordPricePoint(10.0, now - twentyHours, 10.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now - 2L * 60L * 60L * 1000L, 10.0, now))
        assertTrue(shouldRecordPricePoint(10.0, now - 1_000L, 11.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now, 0.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now, -1.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now, Double.NaN, now))
    }

    @Test
    fun testFindPriceChangesUsesThresholdAndSortsLargestFirst() {
        val history = listOf(
            point(1, "small", 10.0, 2),
            point(2, "small", 11.0, 0),
            point(3, "large", 10.0, 2),
            point(4, "large", 15.0, 0)
        )

        val changes = findPriceChanges(history, thresholdPercent = 10.0, windowDays = 1, now = now)

        assertEquals(2, changes.size)
        assertEquals("large", changes.first().cardId)
        assertEquals(50.0, changes.first().changePercent, 0.001)
        assertEquals("small", changes.last().cardId)
        assertTrue(findPriceChanges(history, thresholdPercent = 60.0, windowDays = 1, now = now).isEmpty())
    }

    @Test
    fun testFindPriceChangesSkipsCardsWithoutOlderReferencePoint() {
        val history = listOf(
            PriceHistoryEntity(
                id = 1,
                cardId = "recent-only",
                marketPrice = 10.0,
                timestamp = now - 12L * 60L * 60L * 1000L
            ),
            point(2, "recent-only", 12.0, 0)
        )

        val changes = findPriceChanges(history, thresholdPercent = 5.0, windowDays = 1, now = now)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun testFindPriceChangesUsesSelectedWindow() {
        val history = listOf(
            point(1, "card1", 10.0, 2),
            point(2, "card1", 12.0, 0)
        )

        assertEquals(1, findPriceChanges(history, thresholdPercent = 5.0, windowDays = 1, now = now).size)
        assertTrue(findPriceChanges(history, thresholdPercent = 5.0, windowDays = 7, now = now).isEmpty())
    }

    @Test
    fun testFindPriceChangesIncludesExactThreshold() {
        val history = listOf(
            point(1, "card1", 10.0, 2),
            point(2, "card1", 11.0, 0)
        )

        val changes = findPriceChanges(history, thresholdPercent = 10.0, windowDays = 1, now = now)

        assertEquals(1, changes.size)
        assertEquals(10.0, changes.single().changePercent, 0.001)
    }

    @Test
    fun testFindPriceChangesUsesAbsoluteChange() {
        val history = listOf(
            point(1, "card1", 10.0, 2),
            point(2, "card1", 8.0, 0)
        )

        val changes = findPriceChanges(history, thresholdPercent = 10.0, windowDays = 1, now = now)

        assertEquals(1, changes.size)
        assertEquals(-20.0, changes.single().changePercent, 0.001)
    }

    @Test
    fun testCanSendPriceAlertHonorsCooldownPermissionAndSwitch() {
        val enabled = PriceAlertSettings(
            enabled = true,
            thresholdPercent = 10.0,
            windowDays = 1,
            lastAlertTimestamp = 0L
        )

        assertTrue(canSendPriceAlert(enabled, now, notificationsAllowed = true))
        assertFalse(canSendPriceAlert(enabled.copy(enabled = false), now, notificationsAllowed = true))
        assertFalse(canSendPriceAlert(enabled, now, notificationsAllowed = false))
        assertFalse(
            canSendPriceAlert(
                enabled.copy(lastAlertTimestamp = now - 60L * 60L * 1000L),
                now,
                notificationsAllowed = true
            )
        )
        assertTrue(
            canSendPriceAlert(
                enabled.copy(lastAlertTimestamp = now - 25L * 60L * 60L * 1000L),
                now,
                notificationsAllowed = true
            )
        )
        assertTrue(
            canSendPriceAlert(
                enabled.copy(lastAlertTimestamp = now - 24L * 60L * 60L * 1000L),
                now,
                notificationsAllowed = true
            )
        )
    }
}
