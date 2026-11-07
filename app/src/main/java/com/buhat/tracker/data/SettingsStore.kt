package com.buhat.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
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

    val notificationPermissionRequested = dataStore.data.map {
        it[NOTIFICATION_PERMISSION_REQUESTED] ?: false
    }

    val settings = dataStore.data.map { it.toSettings() }

    val lastExportAt = dataStore.data.map { it[LAST_EXPORT_AT] }
    val backupSnoozedUntil = dataStore.data.map { it[BACKUP_SNOOZED_UNTIL] }
    val lastRunVersionCode = dataStore.data.map { it[LAST_RUN_VERSION_CODE] }

    suspend fun markExported(at: Long = System.currentTimeMillis()) {
        dataStore.edit { it[LAST_EXPORT_AT] = at }
    }

    suspend fun snoozeBackupReminder(until: Long) {
        dataStore.edit { it[BACKUP_SNOOZED_UNTIL] = until }
    }

    suspend fun clearBackupSnooze() {
        dataStore.edit { it.remove(BACKUP_SNOOZED_UNTIL) }
    }

    suspend fun acknowledgeVersion(code: Int) {
        dataStore.edit { it[LAST_RUN_VERSION_CODE] = code }
    }

    /** Recovery only: clears profile, settings, and release-hardening metadata. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    suspend fun save(settings: Settings) {
        dataStore.edit { it.write(settings) }
    }

    /** Read-modify-write inside the DataStore edit so rapid changes cannot drop each other. */
    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings() = Settings(
        theme = this[THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.DARK,
        unit = this[UNIT]?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG,
        previousSetSource = this[PREVIOUS_SET_SOURCE]?.let { PreviousSetSource.valueOf(it) }
            ?: PreviousSetSource.ANY_WORKOUT,
        restWarmupSeconds = this[REST_WARMUP] ?: 60,
        restWorkingSeconds = this[REST_WORKING] ?: 120,
        restFailureSeconds = this[REST_FAILURE] ?: 180,
        restIncrementSeconds = this[REST_INCREMENT] ?: 15,
        restEndsAt = this[REST_ENDS_AT],
        seedVersion = this[SEED_VERSION] ?: 0,
    )

    private fun MutablePreferences.write(settings: Settings) {
        this[THEME] = settings.theme.name
        this[UNIT] = settings.unit.name
        this[PREVIOUS_SET_SOURCE] = settings.previousSetSource.name
        this[REST_WARMUP] = settings.restWarmupSeconds
        this[REST_WORKING] = settings.restWorkingSeconds
        this[REST_FAILURE] = settings.restFailureSeconds
        this[REST_INCREMENT] = settings.restIncrementSeconds
    }

    /** Timer state is written on its own so a settings save cannot undo a running rest. */
    suspend fun setRestEndsAt(endsAt: Long?) {
        dataStore.edit { prefs ->
            endsAt?.let { prefs[REST_ENDS_AT] = it } ?: prefs.remove(REST_ENDS_AT)
        }
    }

    suspend fun clearRestIfMatches(endsAt: Long): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            if (prefs[REST_ENDS_AT] == endsAt) {
                prefs.remove(REST_ENDS_AT)
                cleared = true
            }
        }
        return cleared
    }

    suspend fun markNotificationPermissionRequested(): Boolean {
        var firstRequest = false
        dataStore.edit { prefs ->
            if (prefs[NOTIFICATION_PERMISSION_REQUESTED] != true) {
                prefs[NOTIFICATION_PERMISSION_REQUESTED] = true
                firstRequest = true
            }
        }
        return firstRequest
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
        val NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("settings_notification_permission_requested")
        val SEED_VERSION = intPreferencesKey("settings_seed_version")
        val LAST_EXPORT_AT = longPreferencesKey("settings_last_export_at")
        val BACKUP_SNOOZED_UNTIL = longPreferencesKey("settings_backup_snoozed_until")
        val LAST_RUN_VERSION_CODE = intPreferencesKey("settings_last_run_version_code")
    }
}
