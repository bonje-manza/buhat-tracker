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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SetEditTest {

    private lateinit var db: BuhatDatabase
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "buhat-set-edit-${UUID.randomUUID()}.preferences_pb",
        )
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        repo = BuhatRepository(db, SettingsStore(dataStore), ProfileStore(dataStore))
        repo.seedIfNeeded()
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private suspend fun threeWorkingSets(): Pair<String, String> {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val bench = repo.sessionExercises(sessionId).first().first()
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 10, rir = 2)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 9, rir = 2)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        return sessionId to bench.id
    }

    @Test
    fun retypingAWorkingSetToWarmupKeepsLaterPositionsContiguous() = runTest {
        val (_, benchId) = threeWorkingSets()
        val middle = repo.loggedSets(benchId).first()[1]

        repo.updateSet(middle.id, SetType.WARMUP, weightKg = 80.0, reps = 9, rir = 2)

        val sets = repo.loggedSets(benchId).first()
        assertEquals(listOf(10, 9, 8), sets.map { it.reps })
        assertEquals(listOf(1, null, 2), sets.map { it.workingIndex })
    }

    @Test
    fun deletingAMiddleWorkingSetRenumbersTheRest() = runTest {
        val (_, benchId) = threeWorkingSets()
        val middle = repo.loggedSets(benchId).first()[1]

        repo.deleteSet(middle.id)

        val sets = repo.loggedSets(benchId).first()
        assertEquals(listOf(10, 8), sets.map { it.reps })
        assertEquals(listOf(1, 2), sets.map { it.workingIndex })
    }

    @Test
    fun retypingAWarmupToWorkingAppendsItToThePositionSequence() = runTest {
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val bench = repo.sessionExercises(sessionId).first().first()
        repo.logSet(bench.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = 4)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        val warmup = repo.loggedSets(bench.id).first().first()

        repo.updateSet(warmup.id, SetType.WORKING, weightKg = 40.0, reps = 10, rir = 4)

        val sets = repo.loggedSets(bench.id).first()
        assertEquals(listOf(40.0, 80.0), sets.map { it.weightKg })
        assertEquals(listOf(1, 2), sets.map { it.workingIndex })
    }

    @Test
    fun editingWeightRepsAndRirKeepsThePosition() = runTest {
        val (_, benchId) = threeWorkingSets()
        val first = repo.loggedSets(benchId).first().first()

        repo.updateSet(first.id, SetType.WORKING, weightKg = 82.5, reps = 7, rir = 1)

        val edited = repo.loggedSets(benchId).first().first()
        assertEquals(82.5, edited.weightKg, 0.0)
        assertEquals(7, edited.reps)
        assertEquals(1, edited.rir)
        assertEquals(1, edited.workingIndex)
    }
}
