package com.buhat.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Profile(
    val name: String?,
    val gender: String?,
    val age: Int?,
    val weightKg: Double?,
    val heightCm: Int?,
)

/**
 * Defaults when no value is saved: name, gender, age, weight, height all empty.
 */
class ProfileStore(private val dataStore: DataStore<Preferences>) {

    val profile = dataStore.data.map { prefs ->
        Profile(
            name = prefs[NAME],
            gender = prefs[GENDER],
            age = prefs[AGE],
            weightKg = prefs[WEIGHT_KG],
            heightCm = prefs[HEIGHT_CM],
        )
    }

    suspend fun save(profile: Profile) {
        dataStore.edit { prefs ->
            profile.name?.let { prefs[NAME] = it } ?: prefs.remove(NAME)
            profile.gender?.let { prefs[GENDER] = it } ?: prefs.remove(GENDER)
            profile.age?.let { prefs[AGE] = it } ?: prefs.remove(AGE)
            profile.weightKg?.let { prefs[WEIGHT_KG] = it } ?: prefs.remove(WEIGHT_KG)
            profile.heightCm?.let { prefs[HEIGHT_CM] = it } ?: prefs.remove(HEIGHT_CM)
        }
    }

    private companion object {
        val NAME = stringPreferencesKey("profile_name")
        val GENDER = stringPreferencesKey("profile_gender")
        val AGE = intPreferencesKey("profile_age")
        val WEIGHT_KG = doublePreferencesKey("profile_weight_kg")
        val HEIGHT_CM = intPreferencesKey("profile_height_cm")
    }
}
