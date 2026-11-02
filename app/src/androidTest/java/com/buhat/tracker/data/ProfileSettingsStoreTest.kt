package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileSettingsStoreTest {

    private val scopes = mutableListOf<CoroutineScope>()
    private val files = mutableListOf<File>()

    private fun freshStores(): Pair<ProfileStore, SettingsStore> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "buhat-test-${UUID.randomUUID()}.preferences_pb")
        files.add(file)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes.add(scope)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return ProfileStore(dataStore) to SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        files.forEach { it.delete() }
    }

    @Test
    fun profileDefaultsAreAllEmpty() = runTest {
        val (profiles, _) = freshStores()

        val profile = profiles.profile.first()

        assertNull(profile.name)
        assertNull(profile.gender)
        assertNull(profile.age)
        assertNull(profile.weightKg)
        assertNull(profile.heightCm)
    }

    @Test
    fun settingsDefaultsMatchTheRoadmap() = runTest {
        val (_, settingsStore) = freshStores()

        val settings = settingsStore.settings.first()

        assertEquals(AppTheme.DARK, settings.theme)
        assertEquals(WeightUnit.KG, settings.unit)
        assertEquals(PreviousSetSource.ANY_WORKOUT, settings.previousSetSource)
        assertEquals(60, settings.restWarmupSeconds)
        assertEquals(120, settings.restWorkingSeconds)
        assertEquals(180, settings.restFailureSeconds)
        assertEquals(15, settings.restIncrementSeconds)
        assertNull(settings.restEndsAt)
        assertEquals(0, settings.seedVersion)
    }

    @Test
    fun savedProfileAndSettingsSurviveADataStoreRecreate() = runTest {
        val (profileStore, settingsStore) = freshStores()
        val file = files.last()

        profileStore.save(
            Profile(name = "Avery Kamp", gender = "Female", age = 29, weightKg = 64.5, heightCm = 171),
        )
        settingsStore.save(
            Settings(theme = AppTheme.LIGHT, unit = WeightUnit.LB, previousSetSource = PreviousSetSource.SAME_TEMPLATE),
        )
        settingsStore.markSeeded(3)

        scopes.forEach { it.cancel() }
        scopes.clear()

        // Recreate DataStore over the same file: values must come back from disk.
        val recreatedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes.add(recreatedScope)
        val recreated = PreferenceDataStoreFactory.create(scope = recreatedScope) { file }
        val recreatedProfile = ProfileStore(recreated).profile.first()
        val recreatedSettings = SettingsStore(recreated).settings.first()

        assertEquals("Avery Kamp", recreatedProfile.name)
        assertEquals("Female", recreatedProfile.gender)
        assertEquals(29, recreatedProfile.age)
        assertEquals(64.5, recreatedProfile.weightKg!!, 0.0)
        assertEquals(171, recreatedProfile.heightCm)
        assertEquals(AppTheme.LIGHT, recreatedSettings.theme)
        assertEquals(WeightUnit.LB, recreatedSettings.unit)
        assertEquals(PreviousSetSource.SAME_TEMPLATE, recreatedSettings.previousSetSource)
        assertEquals(3, recreatedSettings.seedVersion)
        // Untouched defaults survive a partial save unchanged.
        assertEquals(120, recreatedSettings.restWorkingSeconds)
        assertNull(recreatedSettings.restEndsAt)
    }

    @Test
    fun notificationPermissionIsRequestedOnlyOnce() = runTest {
        val (_, settingsStore) = freshStores()

        assertEquals(false, settingsStore.notificationPermissionRequested.first())
        assertEquals(true, settingsStore.markNotificationPermissionRequested())
        assertEquals(false, settingsStore.markNotificationPermissionRequested())
        assertEquals(true, settingsStore.notificationPermissionRequested.first())
    }

    @Test
    fun savingRestDefaultsDoesNotClearARunningRest() = runTest {
        val (_, settingsStore) = freshStores()
        val endsAt = 1_800_000_000_000L
        settingsStore.setRestEndsAt(endsAt)

        settingsStore.save(Settings(restWorkingSeconds = 135))

        assertEquals(endsAt, settingsStore.settings.first().restEndsAt)
        assertEquals(135, settingsStore.settings.first().restWorkingSeconds)
    }
}
