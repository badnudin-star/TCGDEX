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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_updates")
        val KEY_UPDATE_HOUR = intPreferencesKey("scheduled_update_hour")
        val KEY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val KEY_CURRENCY_SOURCE = stringPreferencesKey("currency_source")
        val KEY_HIDDEN_GENERATIONS = stringSetPreferencesKey("hidden_generations")
        val KEY_COLLAPSED_GENERATIONS = stringSetPreferencesKey("collapsed_generations")
        val KEY_PRICE_ALERTS_ENABLED = booleanPreferencesKey("price_alerts_enabled")
        val KEY_PRICE_ALERT_THRESHOLD = intPreferencesKey("price_alert_threshold_percent")
        val KEY_PRICE_ALERT_WINDOW_DAYS = intPreferencesKey("price_alert_window_days")
        val KEY_PRICE_ALERT_LAST_TIMESTAMP = longPreferencesKey("price_alert_last_timestamp")
        val KEY_LAST_DATA_CHECK_RESULT = stringPreferencesKey("last_data_check_result")
    }

    val wifiOnlyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY] ?: true
    }

    val scheduledUpdateHourFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_HOUR] ?: 3
    }

    val preferredLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PREFERRED_LANGUAGE] ?: "de"
    }

    val currencySourceFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENCY_SOURCE] ?: "EUR_CARDMARKET"
    }

    val hiddenGenerationsFlow: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_GENERATIONS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    val collapsedGenerationsFlow: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[KEY_COLLAPSED_GENERATIONS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    val lastDataCheckResultFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_DATA_CHECK_RESULT]
    }

    val priceAlertsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PRICE_ALERTS_ENABLED] ?: false
    }

    val priceAlertThresholdPercentFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_PRICE_ALERT_THRESHOLD] ?: 10).coerceIn(1, 100)
    }

    val priceAlertWindowDaysFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_PRICE_ALERT_WINDOW_DAYS] ?: 1).let { if (it == 7) 7 else 1 }
    }

    val priceAlertLastTimestampFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_PRICE_ALERT_LAST_TIMESTAMP] ?: 0L
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun setScheduledUpdateHour(hour: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_UPDATE_HOUR] = hour
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

    suspend fun setHiddenGenerations(hidden: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDDEN_GENERATIONS] = hidden.map { it.toString() }.toSet()
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

    suspend fun setPriceAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PRICE_ALERTS_ENABLED] = enabled }
    }

    suspend fun setPriceAlertThresholdPercent(percent: Int) {
        dataStore.edit { it[KEY_PRICE_ALERT_THRESHOLD] = percent.coerceIn(1, 100) }
    }

    suspend fun setPriceAlertWindowDays(days: Int) {
        dataStore.edit { it[KEY_PRICE_ALERT_WINDOW_DAYS] = if (days == 7) 7 else 1 }
    }

    suspend fun setPriceAlertLastTimestamp(timestamp: Long) {
        dataStore.edit { it[KEY_PRICE_ALERT_LAST_TIMESTAMP] = timestamp.coerceAtLeast(0L) }
    }
}
