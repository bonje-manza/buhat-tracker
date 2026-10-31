package com.buhat.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val theme: AppTheme = AppTheme.DARK,
    val unit: WeightUnit = WeightUnit.KG,
    val previousSetSource: PreviousSetSource = PreviousSetSource.ANY_WORKOUT,
    val restWarmupSeconds: Int = 60,
    val restWorkingSeconds: Int = 120,
    val restFailureSeconds: Int = 180,
    val restIncrementSeconds: Int = 15,
    val restEndsAt: Long? = null,
    val seedVersion: Int = 0,
)

/**
 * Defaults when no value is saved: Dark theme, kg, any-workout previous set,
 * rest 60/120/180 s with 15 s increments, no running timer, nothing seeded.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings = dataStore.data.map { prefs ->
        Settings(
            theme = prefs[THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.DARK,
            unit = prefs[UNIT]?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG,
            previousSetSource = prefs[PREVIOUS_SET_SOURCE]?.let { PreviousSetSource.valueOf(it) }
                ?: PreviousSetSource.ANY_WORKOUT,
            restWarmupSeconds = prefs[REST_WARMUP] ?: 60,
            restWorkingSeconds = prefs[REST_WORKING] ?: 120,
            restFailureSeconds = prefs[REST_FAILURE] ?: 180,
            restIncrementSeconds = prefs[REST_INCREMENT] ?: 15,
            restEndsAt = prefs[REST_ENDS_AT],
            seedVersion = prefs[SEED_VERSION] ?: 0,
        )
    }

    suspend fun save(settings: Settings) {
        dataStore.edit { prefs ->
            prefs[THEME] = settings.theme.name
            prefs[UNIT] = settings.unit.name
            prefs[PREVIOUS_SET_SOURCE] = settings.previousSetSource.name
            prefs[REST_WARMUP] = settings.restWarmupSeconds
            prefs[REST_WORKING] = settings.restWorkingSeconds
            prefs[REST_FAILURE] = settings.restFailureSeconds
            prefs[REST_INCREMENT] = settings.restIncrementSeconds
            settings.restEndsAt?.let { prefs[REST_ENDS_AT] = it } ?: prefs.remove(REST_ENDS_AT)
        }
    }

    suspend fun markSeeded(version: Int) {
        dataStore.edit { prefs -> prefs[SEED_VERSION] = version }
    }

    private companion object {
        val THEME = stringPreferencesKey("settings_theme")
        val UNIT = stringPreferencesKey("settings_unit")
        val PREVIOUS_SET_SOURCE = stringPreferencesKey("settings_previous_set_source")
        val REST_WARMUP = intPreferencesKey("settings_rest_warmup_seconds")
        val REST_WORKING = intPreferencesKey("settings_rest_working_seconds")
        val REST_FAILURE = intPreferencesKey("settings_rest_failure_seconds")
        val REST_INCREMENT = intPreferencesKey("settings_rest_increment_seconds")
        val REST_ENDS_AT = longPreferencesKey("settings_rest_ends_at")
        val SEED_VERSION = intPreferencesKey("settings_seed_version")
    }
}
