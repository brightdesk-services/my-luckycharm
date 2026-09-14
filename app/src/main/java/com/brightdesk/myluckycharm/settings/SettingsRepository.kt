package com.brightdesk.myluckycharm.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dreamcatcher_settings")

private object Keys {
    val placementMode = stringPreferencesKey("placement_mode")
    val tiltSource = stringPreferencesKey("tilt_source")
    val pullAction = stringPreferencesKey("pull_action")
    val charmType = stringPreferencesKey("charm_type")
    val builtInCharmId = stringPreferencesKey("built_in_charm_id")
    val customImagePath = stringPreferencesKey("custom_image_path")
    val emojiChar = stringPreferencesKey("emoji_char")
    val soundEnabled = booleanPreferencesKey("sound_enabled")
    val anchorXFraction = floatPreferencesKey("anchor_x_fraction")
    val anchorYFraction = floatPreferencesKey("anchor_y_fraction")
    val ropeLengthDp = floatPreferencesKey("rope_length_dp")
    val charmSizeDp = floatPreferencesKey("charm_size_dp")
    val stretchFraction = floatPreferencesKey("stretch_fraction")
    val opacityFraction = floatPreferencesKey("opacity_fraction")
    val singleTapAction = stringPreferencesKey("single_tap_action")
    val doubleTapAction = stringPreferencesKey("double_tap_action")
    val tripleTapAction = stringPreferencesKey("triple_tap_action")
}

/**
 * Settings are exposed as a [Flow] so the settings UI and the physics surface
 * both observe the same source and react immediately — picking a new charm
 * updates the render without an app restart (spec §11).
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { cause ->
            // A corrupt or unreadable store should fall back to defaults rather
            // than take down the frame loop that collects this.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences[Keys.placementMode] = updated.placementMode.name
            preferences[Keys.tiltSource] = updated.tiltSource.name
            preferences[Keys.pullAction] = updated.pullAction.name
            preferences[Keys.charmType] = updated.charmType.name
            preferences[Keys.builtInCharmId] = updated.builtInCharmId
            preferences[Keys.emojiChar] = updated.emojiChar
            preferences[Keys.soundEnabled] = updated.soundEnabled
            preferences[Keys.anchorXFraction] = updated.anchorXFraction
            preferences[Keys.anchorYFraction] = updated.anchorYFraction
            preferences[Keys.ropeLengthDp] = updated.ropeLengthDp
            preferences[Keys.charmSizeDp] = updated.charmSizeDp
            preferences[Keys.stretchFraction] = updated.stretchFraction
            preferences[Keys.opacityFraction] = updated.opacityFraction
            preferences[Keys.singleTapAction] = updated.singleTapAction.name
            preferences[Keys.doubleTapAction] = updated.doubleTapAction.name
            preferences[Keys.tripleTapAction] = updated.tripleTapAction.name
            val path = updated.customImagePath
            if (path == null) {
                preferences.remove(Keys.customImagePath)
            } else {
                preferences[Keys.customImagePath] = path
            }
        }
    }
}

private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        placementMode = this[Keys.placementMode].toEnumOr(defaults.placementMode),
        tiltSource = this[Keys.tiltSource].toEnumOr(defaults.tiltSource),
        pullAction = this[Keys.pullAction].toEnumOr(defaults.pullAction),
        charmType = this[Keys.charmType].toEnumOr(defaults.charmType),
        builtInCharmId = this[Keys.builtInCharmId] ?: defaults.builtInCharmId,
        customImagePath = this[Keys.customImagePath],
        emojiChar = this[Keys.emojiChar] ?: defaults.emojiChar,
        soundEnabled = this[Keys.soundEnabled] ?: defaults.soundEnabled,
        anchorXFraction = this[Keys.anchorXFraction] ?: defaults.anchorXFraction,
        anchorYFraction = this[Keys.anchorYFraction] ?: defaults.anchorYFraction,
        ropeLengthDp = this[Keys.ropeLengthDp] ?: defaults.ropeLengthDp,
        charmSizeDp = this[Keys.charmSizeDp] ?: defaults.charmSizeDp,
        stretchFraction = this[Keys.stretchFraction] ?: defaults.stretchFraction,
        opacityFraction = this[Keys.opacityFraction] ?: defaults.opacityFraction,
        singleTapAction = this[Keys.singleTapAction].toEnumOr(defaults.singleTapAction),
        doubleTapAction = this[Keys.doubleTapAction].toEnumOr(defaults.doubleTapAction),
        tripleTapAction = this[Keys.tripleTapAction].toEnumOr(defaults.tripleTapAction),
    )
}

/** Tolerates values written by an older build that no longer name a valid constant. */
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T {
    if (this == null) return fallback
    return enumValues<T>().firstOrNull { it.name == this } ?: fallback
}
