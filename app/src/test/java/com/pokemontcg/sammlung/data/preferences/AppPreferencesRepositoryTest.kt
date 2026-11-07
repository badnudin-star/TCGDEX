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
        assertNotNull(AppPreferencesRepository.KEY_PREFERRED_LANGUAGE)
        assertNotNull(AppPreferencesRepository.KEY_CURRENCY_SOURCE)
        assertNotNull(AppPreferencesRepository.KEY_DAILY_PRICE_REPORT_ENABLED)
        assertNotNull(AppPreferencesRepository.KEY_DAILY_PRICE_REPORT_LAST_TIMESTAMP)
        assertNotNull(AppPreferencesRepository.KEY_COLLAPSED_GENERATIONS)
        assertNotNull(AppPreferencesRepository.KEY_CATALOG_INSTALLED_AT)
        assertNotNull(AppPreferencesRepository.KEY_CATALOG_REMOTE_VERSION)
        assertNotNull(AppPreferencesRepository.KEY_CATALOG_REMOTE_SIZE_BYTES)

        assertEquals("wifi_only_updates", AppPreferencesRepository.KEY_WIFI_ONLY.name)
        assertEquals("preferred_language", AppPreferencesRepository.KEY_PREFERRED_LANGUAGE.name)
        assertEquals("currency_source", AppPreferencesRepository.KEY_CURRENCY_SOURCE.name)
        assertEquals("daily_price_report_enabled", AppPreferencesRepository.KEY_DAILY_PRICE_REPORT_ENABLED.name)
        assertEquals(
            "daily_price_report_last_timestamp",
            AppPreferencesRepository.KEY_DAILY_PRICE_REPORT_LAST_TIMESTAMP.name
        )
        assertEquals("collapsed_generations", AppPreferencesRepository.KEY_COLLAPSED_GENERATIONS.name)
        assertEquals("catalog_installed_at", AppPreferencesRepository.KEY_CATALOG_INSTALLED_AT.name)
        assertEquals("catalog_remote_version", AppPreferencesRepository.KEY_CATALOG_REMOTE_VERSION.name)
        assertEquals("catalog_remote_size_bytes", AppPreferencesRepository.KEY_CATALOG_REMOTE_SIZE_BYTES.name)
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
        assertEquals("de", repository.preferredLanguageFlow.first())
        assertEquals("EUR_CARDMARKET", repository.currencySourceFlow.first())
        assertEquals(emptySet<Int>(), repository.collapsedGenerationsFlow.first())
        assertEquals(false, repository.priceAlertsEnabledFlow.first())
        // The nightly report ships enabled: the user wants to know daily whether values moved.
        assertEquals(true, repository.dailyPriceReportEnabledFlow.first())
        assertEquals(0L, repository.dailyPriceReportLastTimestampFlow.first())
        assertEquals(10, repository.priceAlertThresholdPercentFlow.first())
        assertEquals(1, repository.priceAlertWindowDaysFlow.first())
        assertEquals(0L, repository.priceAlertLastTimestampFlow.first())
        assertEquals(null, repository.catalogEtagFlow.first())
        assertEquals(null, repository.catalogInstalledVersionFlow.first())
        assertEquals(0L, repository.catalogInstalledAtFlow.first())
        assertEquals(null, repository.catalogRemoteVersionFlow.first())
        assertEquals(0L, repository.catalogRemoteSizeBytesFlow.first())
        assertEquals(-1, repository.catalogManifestSchemaVersionFlow.first())
        assertEquals(false, repository.catalogRequiresAppUpdateFlow.first())
        assertEquals(0L, repository.catalogLastCheckAtFlow.first())
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
        repository.setPreferredLanguage("en")
        repository.setCollapsedGenerations(setOf(3))
        repository.setPriceAlertsEnabled(true)
        repository.setPriceAlertThresholdPercent(20)
        repository.setPriceAlertWindowDays(7)
        repository.setPriceAlertLastTimestamp(123L)
        repository.setDailyPriceReportEnabled(false)
        repository.setDailyPriceReportLastTimestamp(456L)

        assertEquals(false, repository.wifiOnlyFlow.first())
        assertEquals("en", repository.preferredLanguageFlow.first())
        assertEquals(setOf(3), repository.collapsedGenerationsFlow.first())
        assertEquals(true, repository.priceAlertsEnabledFlow.first())
        assertEquals(20, repository.priceAlertThresholdPercentFlow.first())
        assertEquals(7, repository.priceAlertWindowDaysFlow.first())
        assertEquals(123L, repository.priceAlertLastTimestampFlow.first())
        assertEquals(false, repository.dailyPriceReportEnabledFlow.first())
        assertEquals(456L, repository.dailyPriceReportLastTimestampFlow.first())
        assertEquals(true, tempFile.exists())
    }

    @Test
    fun testCatalogUpdateStatePersistsInDataStore() = runTest {
        val tempFile = File.createTempFile("tcg-catalog-state", ".preferences_pb")
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempFile }
        )
        val repository = AppPreferencesRepository(dataStore)

        repository.setCatalogEtag("\"etag-1\"")
        repository.setCatalogInstalledVersion("2026.10.31")
        repository.setCatalogInstalledAt(654321L)
        repository.setCatalogRemoteMetadata("2026.11.01", 987654L)
        repository.setCatalogManifestSchemaVersion(17)
        repository.setCatalogRequiresAppUpdate(true)
        repository.setCatalogLastCheckAt(123456L)

        assertEquals("\"etag-1\"", repository.catalogEtagFlow.first())
        assertEquals("2026.10.31", repository.catalogInstalledVersionFlow.first())
        assertEquals(654321L, repository.catalogInstalledAtFlow.first())
        assertEquals("2026.11.01", repository.catalogRemoteVersionFlow.first())
        assertEquals(987654L, repository.catalogRemoteSizeBytesFlow.first())
        assertEquals(17, repository.catalogManifestSchemaVersionFlow.first())
        assertEquals(true, repository.catalogRequiresAppUpdateFlow.first())
        assertEquals(123456L, repository.catalogLastCheckAtFlow.first())

        repository.setCatalogEtag(null)
        assertEquals(null, repository.catalogEtagFlow.first())
    }

    @Test
    fun testNegativeDailyReportTimestampIsClampedToZero() = runTest {
        val tempFile = File.createTempFile("tcg-daily-report", ".preferences_pb")
        tempFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempFile }
        )
        val repository = AppPreferencesRepository(dataStore)

        repository.setDailyPriceReportLastTimestamp(-5L)

        assertEquals(0L, repository.dailyPriceReportLastTimestampFlow.first())
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
