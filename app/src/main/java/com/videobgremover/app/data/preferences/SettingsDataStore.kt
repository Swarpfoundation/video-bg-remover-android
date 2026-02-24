package com.videobgremover.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings keys for DataStore.
 */
object SettingsKeys {
    // Processing settings
    val TARGET_FPS = intPreferencesKey("target_fps")
    val MAX_VIDEO_DURATION = intPreferencesKey("max_video_duration")
    val PROCESSING_RESOLUTION = intPreferencesKey("processing_resolution")

    // Segmentation settings
    val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    val TEMPORAL_SMOOTHING = booleanPreferencesKey("temporal_smoothing")
    val TEMPORAL_ALPHA = floatPreferencesKey("temporal_alpha")
    val APPLY_MORPHOLOGY = booleanPreferencesKey("apply_morphology")
    val APPLY_FEATHER = booleanPreferencesKey("apply_feather")
    val FEATHER_RADIUS = floatPreferencesKey("feather_radius")

    // Export settings
    val DEFAULT_EXPORT_FORMAT = intPreferencesKey("default_export_format")
    val AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup")

    // UI settings
    val DARK_MODE = intPreferencesKey("dark_mode") // 0=system, 1=light, 2=dark
    val SHOW_ONBOARDING = booleanPreferencesKey("show_onboarding")
}

/**
 * Default values for settings.
 */
object SettingsDefaults {
    const val TARGET_FPS = 15
    const val MAX_VIDEO_DURATION = 60 // seconds
    const val PROCESSING_RESOLUTION = 512
    const val CONFIDENCE_THRESHOLD = 0.5f
    const val TEMPORAL_SMOOTHING = true
    const val TEMPORAL_ALPHA = 0.3f
    const val APPLY_MORPHOLOGY = true
    const val APPLY_FEATHER = true
    const val FEATHER_RADIUS = 3f
    const val DEFAULT_EXPORT_FORMAT = 0 // 0=PNG_ZIP, 1=MASK_MP4
    const val AUTO_CLEANUP = true
    const val DARK_MODE = 0
    const val SHOW_ONBOARDING = true
}

/**
 * Settings data class representing all user preferences.
 */
data class Settings(
    val targetFps: Int = SettingsDefaults.TARGET_FPS,
    val maxVideoDuration: Int = SettingsDefaults.MAX_VIDEO_DURATION,
    val processingResolution: Int = SettingsDefaults.PROCESSING_RESOLUTION,
    val confidenceThreshold: Float = SettingsDefaults.CONFIDENCE_THRESHOLD,
    val temporalSmoothing: Boolean = SettingsDefaults.TEMPORAL_SMOOTHING,
    val temporalAlpha: Float = SettingsDefaults.TEMPORAL_ALPHA,
    val applyMorphology: Boolean = SettingsDefaults.APPLY_MORPHOLOGY,
    val applyFeather: Boolean = SettingsDefaults.APPLY_FEATHER,
    val featherRadius: Float = SettingsDefaults.FEATHER_RADIUS,
    val defaultExportFormat: Int = SettingsDefaults.DEFAULT_EXPORT_FORMAT,
    val autoCleanup: Boolean = SettingsDefaults.AUTO_CLEANUP,
    val darkMode: Int = SettingsDefaults.DARK_MODE,
    val showOnboarding: Boolean = SettingsDefaults.SHOW_ONBOARDING
)

/**
 * Extension property for DataStore.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manager for app settings using DataStore.
 */
class SettingsDataStore(private val context: Context) {

    /**
     * Flow of all settings.
     */
    val settings: Flow<Settings> = context.dataStore.data.map { preferences ->
        Settings(
            targetFps = preferences[SettingsKeys.TARGET_FPS] ?: SettingsDefaults.TARGET_FPS,
            maxVideoDuration = preferences[SettingsKeys.MAX_VIDEO_DURATION]
                ?: SettingsDefaults.MAX_VIDEO_DURATION,
            processingResolution = preferences[SettingsKeys.PROCESSING_RESOLUTION]
                ?: SettingsDefaults.PROCESSING_RESOLUTION,
            confidenceThreshold = preferences[SettingsKeys.CONFIDENCE_THRESHOLD]
                ?: SettingsDefaults.CONFIDENCE_THRESHOLD,
            temporalSmoothing = preferences[SettingsKeys.TEMPORAL_SMOOTHING]
                ?: SettingsDefaults.TEMPORAL_SMOOTHING,
            temporalAlpha = preferences[SettingsKeys.TEMPORAL_ALPHA]
                ?: SettingsDefaults.TEMPORAL_ALPHA,
            applyMorphology = preferences[SettingsKeys.APPLY_MORPHOLOGY]
                ?: SettingsDefaults.APPLY_MORPHOLOGY,
            applyFeather = preferences[SettingsKeys.APPLY_FEATHER]
                ?: SettingsDefaults.APPLY_FEATHER,
            featherRadius = preferences[SettingsKeys.FEATHER_RADIUS]
                ?: SettingsDefaults.FEATHER_RADIUS,
            defaultExportFormat = preferences[SettingsKeys.DEFAULT_EXPORT_FORMAT]
                ?: SettingsDefaults.DEFAULT_EXPORT_FORMAT,
            autoCleanup = preferences[SettingsKeys.AUTO_CLEANUP]
                ?: SettingsDefaults.AUTO_CLEANUP,
            darkMode = preferences[SettingsKeys.DARK_MODE] ?: SettingsDefaults.DARK_MODE,
            showOnboarding = preferences[SettingsKeys.SHOW_ONBOARDING]
                ?: SettingsDefaults.SHOW_ONBOARDING
        )
    }

    // Processing settings
    suspend fun setTargetFps(fps: Int) {
        context.dataStore.edit { it[SettingsKeys.TARGET_FPS] = fps }
    }

    suspend fun setMaxVideoDuration(seconds: Int) {
        context.dataStore.edit { it[SettingsKeys.MAX_VIDEO_DURATION] = seconds }
    }

    suspend fun setProcessingResolution(resolution: Int) {
        context.dataStore.edit { it[SettingsKeys.PROCESSING_RESOLUTION] = resolution }
    }

    // Segmentation settings
    suspend fun setConfidenceThreshold(threshold: Float) {
        context.dataStore.edit { it[SettingsKeys.CONFIDENCE_THRESHOLD] = threshold }
    }

    suspend fun setTemporalSmoothing(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.TEMPORAL_SMOOTHING] = enabled }
    }

    suspend fun setTemporalAlpha(alpha: Float) {
        context.dataStore.edit { it[SettingsKeys.TEMPORAL_ALPHA] = alpha }
    }

    suspend fun setApplyMorphology(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.APPLY_MORPHOLOGY] = enabled }
    }

    suspend fun setApplyFeather(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.APPLY_FEATHER] = enabled }
    }

    suspend fun setFeatherRadius(radius: Float) {
        context.dataStore.edit { it[SettingsKeys.FEATHER_RADIUS] = radius }
    }

    // Export settings
    suspend fun setDefaultExportFormat(format: Int) {
        context.dataStore.edit { it[SettingsKeys.DEFAULT_EXPORT_FORMAT] = format }
    }

    suspend fun setAutoCleanup(enabled: Boolean) {
        context.dataStore.edit { it[SettingsKeys.AUTO_CLEANUP] = enabled }
    }

    // UI settings
    suspend fun setDarkMode(mode: Int) {
        context.dataStore.edit { it[SettingsKeys.DARK_MODE] = mode }
    }

    suspend fun setShowOnboarding(show: Boolean) {
        context.dataStore.edit { it[SettingsKeys.SHOW_ONBOARDING] = show }
    }

    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
