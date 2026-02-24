package com.videobgremover.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.videobgremover.app.data.preferences.Settings
import com.videobgremover.app.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing app settings.
 */
class SettingsViewModel(private val dataStore: SettingsDataStore) : ViewModel() {

    /**
     * Current settings as StateFlow.
     */
    val settings: StateFlow<Settings> = dataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Settings()
        )

    // Processing settings
    fun setTargetFps(fps: Int) {
        viewModelScope.launch { dataStore.setTargetFps(fps) }
    }

    fun setMaxVideoDuration(seconds: Int) {
        viewModelScope.launch { dataStore.setMaxVideoDuration(seconds) }
    }

    fun setProcessingResolution(resolution: Int) {
        viewModelScope.launch { dataStore.setProcessingResolution(resolution) }
    }

    // Segmentation settings
    fun setConfidenceThreshold(threshold: Float) {
        viewModelScope.launch { dataStore.setConfidenceThreshold(threshold) }
    }

    fun setTemporalSmoothing(enabled: Boolean) {
        viewModelScope.launch { dataStore.setTemporalSmoothing(enabled) }
    }

    fun setTemporalAlpha(alpha: Float) {
        viewModelScope.launch { dataStore.setTemporalAlpha(alpha) }
    }

    fun setApplyMorphology(enabled: Boolean) {
        viewModelScope.launch { dataStore.setApplyMorphology(enabled) }
    }

    fun setApplyFeather(enabled: Boolean) {
        viewModelScope.launch { dataStore.setApplyFeather(enabled) }
    }

    fun setFeatherRadius(radius: Float) {
        viewModelScope.launch { dataStore.setFeatherRadius(radius) }
    }

    // Export settings
    fun setDefaultExportFormat(format: Int) {
        viewModelScope.launch { dataStore.setDefaultExportFormat(format) }
    }

    fun setAutoCleanup(enabled: Boolean) {
        viewModelScope.launch { dataStore.setAutoCleanup(enabled) }
    }

    // UI settings
    fun setDarkMode(mode: Int) {
        viewModelScope.launch { dataStore.setDarkMode(mode) }
    }

    fun setShowOnboarding(show: Boolean) {
        viewModelScope.launch { dataStore.setShowOnboarding(show) }
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetToDefaults() {
        viewModelScope.launch { dataStore.resetToDefaults() }
    }

    companion object {
        /**
         * Factory for creating [SettingsViewModel].
         */
        fun createFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dataStore = SettingsDataStore(context.applicationContext)
                    return SettingsViewModel(dataStore) as T
                }
            }
        }
    }
}
