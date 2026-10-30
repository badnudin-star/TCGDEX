package com.pokemontcg.sammlung.util

import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceHistoryAndAlertTest {

    @Test
    fun testBuildValueSeriesMultipleCardsAndQuantities() {
        val now = 1700000000000L
        val oneDayMs = 24L * 60L * 60L * 1000L

        val history = listOf(
            PriceHistoryEntity(id = 1, cardId = "card1", marketPrice = 10.0, timestamp = now - 3 * oneDayMs),
            PriceHistoryEntity(id = 2, cardId = "card1", marketPrice = 12.0, timestamp = now - 1 * oneDayMs),
            PriceHistoryEntity(id = 3, cardId = "card2", marketPrice = 5.0, timestamp = now - 2 * oneDayMs)
        )

        val quantities = mapOf("card1" to 2, "card2" to 3)

        val series7 = buildValueSeries(history, quantities, rangeDays = 7, now = now)
        assertEquals(7, series7.size)

        // On day 3 ago: card1 = 10.0 * 2 = 20.0, card2 (fallback min) = 5.0 * 3 = 15.0 -> Total = 35.0
        val day3Ago = series7.find { it.timestamp == now - 3 * oneDayMs }
        assertEquals(35.0, day3Ago?.totalValue ?: 0.0, 0.001)

        // On day 1 ago: card1 = 12.0 * 2 = 24.0, card2 = 5.0 * 3 = 15.0 -> Total = 39.0
        val day1Ago = series7.find { it.timestamp == now - 1 * oneDayMs }
        assertEquals(39.0, day1Ago?.totalValue ?: 0.0, 0.001)
    }

    @Test
    fun testPriceAlertSettingsThreshold() {
        val now = 1700000000000L
        val oneDayMs = 24L * 60L * 60L * 1000L

        val history = listOf(
            PriceHistoryEntity(id = 1, cardId = "card1", marketPrice = 10.0, timestamp = now - 2 * oneDayMs),
            PriceHistoryEntity(id = 2, cardId = "card1", marketPrice = 12.0, timestamp = now) // +20% change
        )

        val cardNames = mapOf("card1" to "Glurak")

        val settingsEnabled = PriceAlertSettings(
            enabled = true,
            thresholdPercent = 15.0, // 20% >= 15% -> should alert
            windowDays = 1,
            lastAlertTimestamp = 0L
        )

        // Simulated check logic without Context notification
        val windowMs = settingsEnabled.windowDays * oneDayMs
        val targetTs = now - windowMs

        val points = history.filter { it.cardId == "card1" }
        val latest = points.maxByOrNull { it.timestamp }!!
        val old = points.filter { it.timestamp <= targetTs }.maxByOrNull { it.timestamp } ?: points.minByOrNull { it.timestamp }!!

        val changePercent = ((latest.marketPrice - old.marketPrice) / old.marketPrice) * 100.0
        assertEquals(20.0, changePercent, 0.001)
        assertTrue(Math.abs(changePercent) >= settingsEnabled.thresholdPercent)
    }

    @Test
    fun pricePointRecordedOnlyOnChangeOrAfterTwentyHours() {
        val now = 1_700_000_000_000L
        val twentyHours = 20L * 60L * 60L * 1000L
        assertTrue(shouldRecordPricePoint(null, null, 10.0, now))
        assertTrue(shouldRecordPricePoint(10.0, now - twentyHours, 10.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now - 2L * 60L * 60L * 1000L, 10.0, now))
        assertTrue(shouldRecordPricePoint(10.0, now - 1_000L, 11.0, now))
        assertFalse(shouldRecordPricePoint(10.0, now, 0.0, now))
    }

    @Test
    fun findPriceChangesUsesThresholdAndSortsLargestFirst() {
        val now = 1_700_000_000_000L
        val day = 24L * 60L * 60L * 1000L
        val history = listOf(
            PriceHistoryEntity(id = 1, cardId = "small", marketPrice = 10.0, timestamp = now - 2 * day),
            PriceHistoryEntity(id = 2, cardId = "small", marketPrice = 11.0, timestamp = now),
            PriceHistoryEntity(id = 3, cardId = "large", marketPrice = 10.0, timestamp = now - 2 * day),
            PriceHistoryEntity(id = 4, cardId = "large", marketPrice = 15.0, timestamp = now)
        )
        val changes = findPriceChanges(history, thresholdPercent = 10.0, windowDays = 1, now = now)
        assertEquals(2, changes.size)
        assertEquals("large", changes.first().cardId)
        assertEquals(50.0, changes.first().changePercent, 0.001)
        assertEquals("small", changes.last().cardId)
        assertTrue(findPriceChanges(history, thresholdPercent = 60.0, windowDays = 1, now = now).isEmpty())
    }

    @Test
    fun testPriceAlertCooldownBlock() {
        val now = 1700000000000L
        val oneHourAgo = now - (60L * 60L * 1000L)

        val settingsInCooldown = PriceAlertSettings(
            enabled = true,
            thresholdPercent = 10.0,
            windowDays = 1,
            lastAlertTimestamp = oneHourAgo // less than 24h ago!
        )

        val canAlert = (now - settingsInCooldown.lastAlertTimestamp) >= (24L * 60L * 60L * 1000L)
        assertFalse(canAlert)
    }
}
