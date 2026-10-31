package com.pokemontcg.sammlung.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class AppPreferencesRepositoryTest {

    @Test
    fun testPreferencesKeysDefined() {
        assertNotNull(AppPreferencesRepository.KEY_WIFI_ONLY)
        assertNotNull(AppPreferencesRepository.KEY_UPDATE_HOUR)
        assertNotNull(AppPreferencesRepository.KEY_PREFERRED_LANGUAGE)
        assertNotNull(AppPreferencesRepository.KEY_CURRENCY_SOURCE)
        assertNotNull(AppPreferencesRepository.KEY_HIDDEN_GENERATIONS)
        assertNotNull(AppPreferencesRepository.KEY_COLLAPSED_GENERATIONS)

        assertEquals("wifi_only_updates", AppPreferencesRepository.KEY_WIFI_ONLY.name)
        assertEquals("scheduled_update_hour", AppPreferencesRepository.KEY_UPDATE_HOUR.name)
        assertEquals("preferred_language", AppPreferencesRepository.KEY_PREFERRED_LANGUAGE.name)
        assertEquals("currency_source", AppPreferencesRepository.KEY_CURRENCY_SOURCE.name)
        assertEquals("hidden_generations", AppPreferencesRepository.KEY_HIDDEN_GENERATIONS.name)
        assertEquals("collapsed_generations", AppPreferencesRepository.KEY_COLLAPSED_GENERATIONS.name)
    }

    @Test
    fun testPreferencesDefaultsBeforeFirstWrite() = runTest {
        val tempFile = File.createTempFile("tcg-settings-defaults", ".preferences_pb")
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempFile }
        )
        val repository = AppPreferencesRepository(dataStore)

        assertEquals(true, repository.wifiOnlyFlow.first())
        assertEquals(3, repository.scheduledUpdateHourFlow.first())
        assertEquals("de", repository.preferredLanguageFlow.first())
        assertEquals("EUR_CARDMARKET", repository.currencySourceFlow.first())
        assertEquals(emptySet<Int>(), repository.hiddenGenerationsFlow.first())
        assertEquals(emptySet<Int>(), repository.collapsedGenerationsFlow.first())
        assertEquals(false, repository.priceAlertsEnabledFlow.first())
        assertEquals(10, repository.priceAlertThresholdPercentFlow.first())
        assertEquals(1, repository.priceAlertWindowDaysFlow.first())
        assertEquals(0L, repository.priceAlertLastTimestampFlow.first())
    }

    @Test
    fun testPreferencesPersistInTemporaryDataStoreFile() = runTest {
        val tempFile = File.createTempFile("tcg-settings", ".preferences_pb")
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempFile }
        )
        val repository = AppPreferencesRepository(dataStore)

        repository.setWifiOnly(false)
        repository.setScheduledUpdateHour(17)
        repository.setPreferredLanguage("en")
        repository.setHiddenGenerations(setOf(2, 5))
        repository.setCollapsedGenerations(setOf(3))
        repository.setPriceAlertsEnabled(true)
        repository.setPriceAlertThresholdPercent(20)
        repository.setPriceAlertWindowDays(7)
        repository.setPriceAlertLastTimestamp(123L)

        assertEquals(false, repository.wifiOnlyFlow.first())
        assertEquals(17, repository.scheduledUpdateHourFlow.first())
        assertEquals("en", repository.preferredLanguageFlow.first())
        assertEquals(setOf(2, 5), repository.hiddenGenerationsFlow.first())
        assertEquals(setOf(3), repository.collapsedGenerationsFlow.first())
        assertEquals(true, repository.priceAlertsEnabledFlow.first())
        assertEquals(20, repository.priceAlertThresholdPercentFlow.first())
        assertEquals(7, repository.priceAlertWindowDaysFlow.first())
        assertEquals(123L, repository.priceAlertLastTimestampFlow.first())
        assertEquals(true, tempFile.exists())
    }

    @Test
    fun testUnsupportedPriceAlertThresholdFallsBackToDefault() = runTest {
        val tempFile = File.createTempFile("tcg-price-alert", ".preferences_pb")
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempFile }
        )
        val repository = AppPreferencesRepository(dataStore)

        repository.setPriceAlertThresholdPercent(7)

        assertEquals(10, repository.priceAlertThresholdPercentFlow.first())
    }
}
