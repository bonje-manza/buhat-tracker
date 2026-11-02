package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.domain.SetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestTimerRepositoryTest {

    private lateinit var db: BuhatDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope
    private lateinit var file: File

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "buhat-rest-${UUID.randomUUID()}.preferences_pb",
        )
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { file })
        repo = BuhatRepository(db, settings)
        repo.seedIfNeeded()
        // Hand-set defaults so each type is distinguishable in the assertions.
        settings.save(Settings(restWarmupSeconds = 45, restWorkingSeconds = 90, restFailureSeconds = 150))
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
        file.delete()
    }

    private suspend fun restEndsAt() = settings.settings.first().restEndsAt

    private suspend fun logFirstExercise(type: SetType) {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(exercise.id, type, weightKg = 80.0, reps = 8, rir = 2)
    }

    @Test
    fun loggingASetSchedulesRestFromItsTypeDefault() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()

        val before = System.currentTimeMillis()
        repo.logSet(exercise.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = 4)
        val afterWarmup = System.currentTimeMillis()
        val warmupEnd = restEndsAt()!!
        assertTrue(warmupEnd in (before + 45_000)..(afterWarmup + 45_000))

        repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        val afterWorking = System.currentTimeMillis()
        val workingEnd = restEndsAt()!!
        assertTrue(workingEnd in (before + 90_000)..(afterWorking + 90_000))

        repo.logSet(exercise.id, SetType.FAILURE, weightKg = 80.0, reps = 6, rir = 0)
        val afterFailure = System.currentTimeMillis()
        val failureEnd = restEndsAt()!!
        assertTrue(failureEnd in (before + 150_000)..(afterFailure + 150_000))
    }

    @Test
    fun aZeroDefaultSchedulesNoRest() = runTest {
        settings.save(settings.settings.first().copy(restWorkingSeconds = 0))

        logFirstExercise(SetType.WORKING)

        assertNull(restEndsAt())
    }

    @Test
    fun loggingAnotherSetReplacesTheEndTime() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        val first = restEndsAt()!!

        repo.logSet(exercise.id, SetType.FAILURE, weightKg = 80.0, reps = 6, rir = 0)

        assertTrue(restEndsAt()!! > first)
    }

    @Test
    fun skippingClearsTheRunningRest() = runTest {
        logFirstExercise(SetType.WORKING)

        repo.clearRest()

        assertNull(restEndsAt())
    }

    @Test
    fun skippingAfterLoggingStartsClearsTheNewRest() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        val logging = launch {
            repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        }
        yield()

        repo.clearRest()
        logging.join()

        assertNull(restEndsAt())
    }

    @Test
    fun finishingClearsTheRunningRest() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)

        repo.finishSession(sessionId)

        assertNull(restEndsAt())
    }

    @Test
    fun discardingClearsTheRunningRest() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)

        repo.discardSession(sessionId)

        assertNull(restEndsAt())
    }

    @Test
    fun theEndTimeSurvivesARepositoryRecreate() = runTest {
        logFirstExercise(SetType.WORKING)
        val saved = restEndsAt()

        val reopened = BuhatRepository(db, settings)

        assertEquals(saved, reopened.settingsStore.settings.first().restEndsAt)
    }

    @Test
    fun aFinishedWorkoutCannotStartAnotherRest() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        repo.finishSession(sessionId)

        val error = try {
            repo.logSet(exercise.id, SetType.WORKING, weightKg = 80.0, reps = 9, rir = 1)
            null
        } catch (e: SessionValidationException) {
            e.message
        }

        assertEquals("Workout is not active.", error)
        assertNull(restEndsAt())
        assertEquals(1, repo.loggedSets(exercise.id).first().size)
    }
}
