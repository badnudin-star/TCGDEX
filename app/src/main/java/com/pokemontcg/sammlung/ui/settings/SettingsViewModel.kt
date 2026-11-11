package com.pokemontcg.sammlung.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.data.repository.CatalogRepository
import com.pokemontcg.sammlung.worker.UpdateScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wifiOnly: Boolean = true,
    val showEnglishNames: Boolean = false,
    val preferredLanguage: String = "de",
    val currencySource: String = "EUR_CARDMARKET",
    val priceAlertsEnabled: Boolean = false,
    val dailyPriceReportEnabled: Boolean = true,
    val priceAlertThresholdPercent: Int = 10,
    val priceAlertWindowDays: Int = 1,
    val availableLanguages: List<String> = emptyList(),
    val isLoaded: Boolean = false
)

private data class SettingsCore(
    val wifiOnly: Boolean,
    val preferredLanguage: String,
    val currencySource: String
)

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    catalogRepository: CatalogRepository,
    private val context: Context
) : ViewModel() {

    private val corePreferences = combine(
        preferencesRepository.wifiOnlyFlow,
        preferencesRepository.preferredLanguageFlow,
        preferencesRepository.currencySourceFlow
    ) { wifi, lang, currency ->
        SettingsCore(wifi, lang, currency)
    }

    private data class AlertPreferences(
        val enabled: Boolean,
        val thresholdPercent: Int,
        val windowDays: Int,
        val dailyReportEnabled: Boolean
    )

    private val alertPreferences = combine(
        preferencesRepository.priceAlertsEnabledFlow,
        preferencesRepository.priceAlertThresholdPercentFlow,
        preferencesRepository.priceAlertWindowDaysFlow,
        preferencesRepository.dailyPriceReportEnabledFlow
    ) { enabled, threshold, window, dailyReport ->
        AlertPreferences(enabled, threshold, window, dailyReport)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        corePreferences,
        alertPreferences,
        catalogRepository.getAvailablePokemonNameLanguages(),
        preferencesRepository.showEnglishNamesFlow
    ) { core, alerts, languages, showEnglishNames ->
        SettingsUiState(
            wifiOnly = core.wifiOnly,
            showEnglishNames = showEnglishNames,
            preferredLanguage = core.preferredLanguage,
            currencySource = core.currencySource,
            priceAlertsEnabled = alerts.enabled,
            priceAlertThresholdPercent = alerts.thresholdPercent,
            priceAlertWindowDays = alerts.windowDays,
            dailyPriceReportEnabled = alerts.dailyReportEnabled,
            availableLanguages = languages,
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setShowEnglishNames(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowEnglishNames(enabled)
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWifiOnly(wifiOnly)
            // The nightly run is pinned to 03:00; only the network constraint is configurable.
            UpdateScheduler.schedulePeriodicSync(context, wifiOnly)
        }
    }

    fun setDailyPriceReportEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDailyPriceReportEnabled(enabled) }
    }

    fun setPreferredLanguage(language: String) {
        viewModelScope.launch {
            preferencesRepository.setPreferredLanguage(language)
        }
    }

    fun setPriceAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPriceAlertsEnabled(enabled) }
    }

    fun setPriceAlertThresholdPercent(percent: Int) {
        viewModelScope.launch { preferencesRepository.setPriceAlertThresholdPercent(percent) }
    }

    fun setPriceAlertWindowDays(days: Int) {
        viewModelScope.launch { preferencesRepository.setPriceAlertWindowDays(days) }
    }

    class Factory(
        private val preferencesRepository: AppPreferencesRepository,
        private val catalogRepository: CatalogRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesRepository, catalogRepository, context) as T
        }
    }
}
