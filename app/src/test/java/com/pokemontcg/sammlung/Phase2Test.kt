package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.data.local.entities.CardPokemonEntity
import com.pokemontcg.sammlung.worker.ResumableCatalogWorker
import com.pokemontcg.sammlung.util.AppLogger
import com.pokemontcg.sammlung.util.PriceFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {

    @Test
    fun testPriceFormatterMissingPriceText() {
        val missingPriceTextNull = PriceFormatter.formatPrice(null)
        val missingPriceTextZero = PriceFormatter.formatPrice(0.0)

        assertEquals("Kein Preis verfügbar", missingPriceTextNull)
        assertEquals("Kein Preis verfügbar", missingPriceTextZero)
    }

    @Test
    fun testPriceFormatterValidPrice() {
        val formatted = PriceFormatter.formatPrice(12.50, sourceName = "Cardmarket", dateString = "25.10.2026")
        assertNotNull(formatted)
        assertTrue(formatted.contains("12,50 €"))
        assertTrue(formatted.contains("Cardmarket"))
        assertTrue(formatted.contains("25.10.2026"))

        val shortFormatted = PriceFormatter.formatPriceShort(12.50)
        assertEquals("12,50 €", shortFormatted)
    }

    @Test
    fun testAppLoggerRingBuffer() {
        AppLogger.clearLogs()
        for (i in 1..600) {
            AppLogger.log("TestTag", "Log message $i")
        }

        val logs = AppLogger.getLogLines()
        assertEquals(500, logs.size)
        assertTrue(logs.first().contains("Log message 101"))
        assertTrue(logs.last().contains("Log message 600"))
    }

    @Test
    fun testCardPokemonEntityJoinMapping() {
        val link1 = CardPokemonEntity(cardId = "xy1-1", dexId = 25)
        val link2 = CardPokemonEntity(cardId = "xy1-1", dexId = 26)

        assertEquals("xy1-1", link1.cardId)
        assertEquals(25, link1.dexId)
        assertEquals(26, link2.dexId)
    }

    @Test
    fun testWorkerCheckpointResumabilityKeys() {
        assertEquals("ResumableCatalogUpdateWork", ResumableCatalogWorker.UNIQUE_WORK_NAME)
        assertEquals("progress_message", ResumableCatalogWorker.KEY_PROGRESS_MESSAGE)
        assertEquals("progress_percent", ResumableCatalogWorker.KEY_PROGRESS_PERCENT)
        assertEquals("has_failures", ResumableCatalogWorker.KEY_HAS_FAILURES)
        assertEquals("catalog_update_continue", ResumableCatalogWorker.CONTINUATION_WORK_NAME)
        assertEquals("catalog_sync", ResumableCatalogWorker.SYNC_TAG)
        assertEquals(8L * 60L * 1000L, ResumableCatalogWorker.MAX_TIME_SLICE_MS)
    }
}
