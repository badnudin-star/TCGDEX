package com.pokemontcg.sammlung.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pokemontcg.sammlung.util.PRICE_ALERT_DEFAULT_THRESHOLD
import com.pokemontcg.sammlung.util.PRICE_ALERT_THRESHOLDS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_updates")
        val KEY_SHOW_EN_NAMES = booleanPreferencesKey("show_english_names")
        val KEY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val KEY_CURRENCY_SOURCE = stringPreferencesKey("currency_source")
        val KEY_COLLAPSED_GENERATIONS = stringSetPreferencesKey("collapsed_generations")
        val KEY_PRICE_ALERTS_ENABLED = booleanPreferencesKey("price_alerts_enabled")
        val KEY_PRICE_ALERT_THRESHOLD = intPreferencesKey("price_alert_threshold_percent")
        val KEY_PRICE_ALERT_WINDOW_DAYS = intPreferencesKey("price_alert_window_days")
        val KEY_PRICE_ALERT_LAST_TIMESTAMP = longPreferencesKey("price_alert_last_timestamp")
        val KEY_DAILY_PRICE_REPORT_ENABLED = booleanPreferencesKey("daily_price_report_enabled")
        val KEY_DAILY_PRICE_REPORT_LAST_TIMESTAMP =
            longPreferencesKey("daily_price_report_last_timestamp")
        val KEY_LAST_DATA_CHECK_RESULT = stringPreferencesKey("last_data_check_result")
        val KEY_CATALOG_ETAG = stringPreferencesKey("catalog_etag")
        val KEY_CATALOG_INSTALLED_VERSION = stringPreferencesKey("catalog_installed_version")
        val KEY_CATALOG_INSTALLED_AT = longPreferencesKey("catalog_installed_at")
        val KEY_CATALOG_REMOTE_VERSION = stringPreferencesKey("catalog_remote_version")
        val KEY_CATALOG_REMOTE_SIZE_BYTES = longPreferencesKey("catalog_remote_size_bytes")
        val KEY_CATALOG_MANIFEST_SCHEMA_VERSION = intPreferencesKey("catalog_manifest_schema_version")
        val KEY_CATALOG_REQUIRES_APP_UPDATE = booleanPreferencesKey("catalog_requires_app_update")
        val KEY_CATALOG_LAST_CHECK_AT = longPreferencesKey("catalog_last_check_at")
    }

    val wifiOnlyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY] ?: true
    }

    /** English card names as an additional display line; off by default. */
    val showEnglishNamesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_EN_NAMES] ?: false
    }

    val preferredLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PREFERRED_LANGUAGE] ?: "de"
    }

    val currencySourceFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENCY_SOURCE] ?: "EUR_CARDMARKET"
    }

    val collapsedGenerationsFlow: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_COLLAPSED_GENERATIONS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    val lastDataCheckResultFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_DATA_CHECK_RESULT]
    }

    val catalogEtagFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_ETAG]
    }

    val catalogInstalledVersionFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_INSTALLED_VERSION]
    }

    val catalogInstalledAtFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_INSTALLED_AT] ?: 0L
    }

    val catalogRemoteVersionFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_REMOTE_VERSION]
    }

    val catalogRemoteSizeBytesFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_REMOTE_SIZE_BYTES] ?: 0L
    }

    val catalogManifestSchemaVersionFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_MANIFEST_SCHEMA_VERSION] ?: -1
    }

    val catalogRequiresAppUpdateFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_REQUIRES_APP_UPDATE] ?: false
    }

    val catalogLastCheckAtFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CATALOG_LAST_CHECK_AT] ?: 0L
    }

    val priceAlertsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PRICE_ALERTS_ENABLED] ?: false
    }

    val priceAlertThresholdPercentFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_PRICE_ALERT_THRESHOLD] ?: PRICE_ALERT_DEFAULT_THRESHOLD)
            .takeIf { it in PRICE_ALERT_THRESHOLDS }
            ?: PRICE_ALERT_DEFAULT_THRESHOLD
    }

    val priceAlertWindowDaysFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_PRICE_ALERT_WINDOW_DAYS] ?: 1).let { if (it == 7) 7 else 1 }
    }

    val priceAlertLastTimestampFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_PRICE_ALERT_LAST_TIMESTAMP] ?: 0L
    }

    /**
     * The nightly report is on by default: it answers "did any card value change?" once a day,
     * independently from the threshold based price alerts.
     */
    val dailyPriceReportEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_PRICE_REPORT_ENABLED] ?: true
    }

    val dailyPriceReportLastTimestampFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_DAILY_PRICE_REPORT_LAST_TIMESTAMP] ?: 0L
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setShowEnglishNames(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_EN_NAMES] = enabled
        }
    }

    suspend fun setPreferredLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PREFERRED_LANGUAGE] = language
        }
    }

    suspend fun setCurrencySource(currencySource: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENCY_SOURCE] = currencySource
        }
    }

    suspend fun setCollapsedGenerations(collapsed: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[KEY_COLLAPSED_GENERATIONS] = collapsed.map { it.toString() }.toSet()
        }
    }

    suspend fun setLastDataCheckResult(result: String) {
        dataStore.edit { it[KEY_LAST_DATA_CHECK_RESULT] = result }
    }

    suspend fun setCatalogEtag(etag: String?) {
        dataStore.edit { prefs ->
            if (etag.isNullOrBlank()) prefs.remove(KEY_CATALOG_ETAG) else prefs[KEY_CATALOG_ETAG] = etag
        }
    }

    suspend fun setCatalogInstalledVersion(version: String) {
        if (version.isNotBlank()) {
            dataStore.edit { it[KEY_CATALOG_INSTALLED_VERSION] = version }
        }
    }

    suspend fun setCatalogInstalledAt(timestamp: Long) {
        dataStore.edit { it[KEY_CATALOG_INSTALLED_AT] = timestamp.coerceAtLeast(0L) }
    }

    suspend fun setCatalogRemoteMetadata(version: String, sizeBytes: Long) {
        dataStore.edit { prefs ->
            if (version.isNotBlank()) prefs[KEY_CATALOG_REMOTE_VERSION] = version
            if (sizeBytes > 0L) prefs[KEY_CATALOG_REMOTE_SIZE_BYTES] = sizeBytes
        }
    }

    suspend fun setCatalogManifestSchemaVersion(schemaVersion: Int) {
        dataStore.edit { it[KEY_CATALOG_MANIFEST_SCHEMA_VERSION] = schemaVersion }
    }

    suspend fun setCatalogRequiresAppUpdate(required: Boolean) {
        dataStore.edit { it[KEY_CATALOG_REQUIRES_APP_UPDATE] = required }
    }

    suspend fun setCatalogLastCheckAt(timestamp: Long) {
        dataStore.edit { it[KEY_CATALOG_LAST_CHECK_AT] = timestamp.coerceAtLeast(0L) }
    }

    suspend fun setPriceAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PRICE_ALERTS_ENABLED] = enabled }
    }

    suspend fun setPriceAlertThresholdPercent(percent: Int) {
        val normalized = percent.takeIf { it in PRICE_ALERT_THRESHOLDS }
            ?: PRICE_ALERT_DEFAULT_THRESHOLD
        dataStore.edit { it[KEY_PRICE_ALERT_THRESHOLD] = normalized }
    }

    suspend fun setPriceAlertWindowDays(days: Int) {
        dataStore.edit { it[KEY_PRICE_ALERT_WINDOW_DAYS] = if (days == 7) 7 else 1 }
    }

    suspend fun setPriceAlertLastTimestamp(timestamp: Long) {
        dataStore.edit { it[KEY_PRICE_ALERT_LAST_TIMESTAMP] = timestamp.coerceAtLeast(0L) }
    }

    suspend fun setDailyPriceReportEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DAILY_PRICE_REPORT_ENABLED] = enabled }
    }

    suspend fun setDailyPriceReportLastTimestamp(timestamp: Long) {
        dataStore.edit { it[KEY_DAILY_PRICE_REPORT_LAST_TIMESTAMP] = timestamp.coerceAtLeast(0L) }
    }
}
