package com.lambrk.compass.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the app decides between light and dark appearance. */
enum class ThemeMode {
    /** Follow the system dark-mode setting. */
    SYSTEM,
    LIGHT,
    DARK,

    /** Use the ambient light sensor: dark surroundings → dark theme. */
    AMBIENT,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.AMBIENT,
    /** Below this many lux the ambient theme switches to dark. */
    val darkLuxThreshold: Float = DEFAULT_LUX_THRESHOLD,
    val dynamicColor: Boolean = false,
    val trueNorth: Boolean = false,
    val showLevel: Boolean = true,
    val hapticFeedback: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** 0 = raw sensor values, 1 = heavily smoothed. */
    val smoothing: Float = 0.6f,
) {
    companion object {
        const val DEFAULT_LUX_THRESHOLD = 20f
        const val MIN_LUX_THRESHOLD = 1f
        const val MAX_LUX_THRESHOLD = 1000f
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val luxThreshold = floatPreferencesKey("lux_threshold")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val trueNorth = booleanPreferencesKey("true_north")
        val showLevel = booleanPreferencesKey("show_level")
        val haptics = booleanPreferencesKey("haptics")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val smoothing = floatPreferencesKey("smoothing")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.themeMode] = updated.themeMode.name
            prefs[Keys.luxThreshold] = updated.darkLuxThreshold
            prefs[Keys.dynamicColor] = updated.dynamicColor
            prefs[Keys.trueNorth] = updated.trueNorth
            prefs[Keys.showLevel] = updated.showLevel
            prefs[Keys.haptics] = updated.hapticFeedback
            prefs[Keys.keepScreenOn] = updated.keepScreenOn
            prefs[Keys.smoothing] = updated.smoothing
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = this[Keys.themeMode]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            darkLuxThreshold = (this[Keys.luxThreshold] ?: defaults.darkLuxThreshold)
                .coerceIn(AppSettings.MIN_LUX_THRESHOLD, AppSettings.MAX_LUX_THRESHOLD),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            trueNorth = this[Keys.trueNorth] ?: defaults.trueNorth,
            showLevel = this[Keys.showLevel] ?: defaults.showLevel,
            hapticFeedback = this[Keys.haptics] ?: defaults.hapticFeedback,
            keepScreenOn = this[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            smoothing = (this[Keys.smoothing] ?: defaults.smoothing).coerceIn(0f, 1f),
        )
    }
}
