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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wifiOnly: Boolean = true,
    val scheduledUpdateHour: Int = 3,
    val preferredLanguage: String = "de",
    val currencySource: String = "EUR_CARDMARKET",
    val priceAlertsEnabled: Boolean = false,
    val priceAlertThresholdPercent: Int = 10,
    val priceAlertWindowDays: Int = 1,
    val availableLanguages: List<String> = emptyList(),
    val isLoaded: Boolean = false
)

private data class SettingsCore(
    val wifiOnly: Boolean,
    val scheduledUpdateHour: Int,
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
        preferencesRepository.scheduledUpdateHourFlow,
        preferencesRepository.preferredLanguageFlow,
        preferencesRepository.currencySourceFlow
    ) { wifi, hour, lang, currency ->
        SettingsCore(wifi, hour, lang, currency)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        corePreferences,
        preferencesRepository.priceAlertsEnabledFlow,
        preferencesRepository.priceAlertThresholdPercentFlow,
        preferencesRepository.priceAlertWindowDaysFlow,
        catalogRepository.getAvailablePokemonNameLanguages()
    ) { core, alertsEnabled, alertThreshold, alertWindow, languages ->
        SettingsUiState(
            wifiOnly = core.wifiOnly,
            scheduledUpdateHour = core.scheduledUpdateHour,
            preferredLanguage = core.preferredLanguage,
            currencySource = core.currencySource,
            priceAlertsEnabled = alertsEnabled,
            priceAlertThresholdPercent = alertThreshold,
            priceAlertWindowDays = alertWindow,
            availableLanguages = languages,
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWifiOnly(wifiOnly)
            val currentHour = preferencesRepository.scheduledUpdateHourFlow.first()
            UpdateScheduler.schedulePeriodicSync(context, wifiOnly, currentHour)
        }
    }

    fun setScheduledUpdateHour(hour: Int) {
        viewModelScope.launch {
            preferencesRepository.setScheduledUpdateHour(hour)
            val currentWifi = preferencesRepository.wifiOnlyFlow.first()
            UpdateScheduler.schedulePeriodicSync(context, currentWifi, hour)
        }
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
