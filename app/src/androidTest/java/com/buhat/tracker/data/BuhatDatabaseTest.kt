package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDao
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.Template
import com.buhat.tracker.data.local.TemplateExercise
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
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
class BuhatDatabaseTest {

    private lateinit var db: BuhatDatabase
    private lateinit var dao: BuhatDao

    @Before
    fun setUp() {
        // Isolation: in-memory Room; the device's real file database is never touched.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        dao = db.dao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repository(): BuhatRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "buhat-seed-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return BuhatRepository(db, SettingsStore(dataStore), ProfileStore(dataStore))
    }

    @Test
    fun exerciseNamesAreUniqueCaseInsensitive() = runTest {
        val first = Exercise(
            id = "a", name = "Bench Press", muscleGroup = "Chest", equipment = "Barbell",
            isCustom = false, createdAt = 1,
        )
        val duplicate = first.copy(id = "b", name = "bench press")

        val ids = dao.insertExercises(listOf(first, duplicate))

        assertEquals(1, dao.exerciseCount())
        assertEquals(listOf(1L, -1L), ids)
    }

    @Test
    fun templateNamesAreUniqueCaseInsensitive() = runTest {
        val template = Template(id = "t1", name = "Push Day A", isPremade = true, createdAt = 1, updatedAt = 1)
        val ids = dao.insertTemplates(listOf(template, template.copy(id = "t2", name = "PUSH day a")))

        assertEquals(1, dao.templateCount())
        assertEquals(listOf(1L, -1L), ids)
    }

    @Test
    fun deletingTemplateCascadesToItsExercises() = runTest {
        val template = Template(id = "t", name = "Custom", isPremade = false, createdAt = 1, updatedAt = 1)
        dao.insertTemplates(listOf(template))
        dao.insertExercises(
            listOf(
                Exercise(id = "e", name = "Back Squat", muscleGroup = "Quads", equipment = "Barbell", isCustom = false, archivedAt = null, createdAt = 1),
                Exercise(id = "e2", name = "Leg Press", muscleGroup = "Quads", equipment = "Machine", isCustom = false, archivedAt = null, createdAt = 2),
            ),
        )
        dao.insertTemplateExercises(
            listOf(
                TemplateExercise(id = "te1", templateId = "t", exerciseId = "e", position = 1, workingSets = 3, repMin = 5, repMax = 8, targetRir = null),
                TemplateExercise(id = "te2", templateId = "t", exerciseId = "e2", position = 2, workingSets = 3, repMin = 5, repMax = 8, targetRir = null),
            ),
        )
        assertEquals(2, dao.templateExerciseCount())

        db.withTransaction { dao.deleteTemplates(listOf("t")) }

        assertEquals(0, dao.templateExerciseCount())
        assertEquals(2, dao.exerciseCount()) // history keeps the exercises even though the template is gone
    }

    @Test
    fun logPositionIsUniquePerExerciseWithMultipleWarmupsAllowed() = runTest {
        dao.insertSessions(
            listOf(
                WorkoutSession(id = "s", templateIdSnapshot = "t", templateNameSnapshot = "Leg Day", startedAt = 1, finishedAt = null, status = WorkoutStatus.ACTIVE),
            ),
        )
        dao.insertSessionExercises(
            listOf(
                SessionExercise(
                    id = "se", sessionId = "s", exerciseId = "e", exerciseNameSnapshot = "Back Squat",
                    position = 1, workingSets = 3, repMin = 5, repMax = 8, targetRir = null,
                ),
            ),
        )

        val warmupA = LoggedSet(id = "w1", sessionExerciseId = "se", setType = SetType.WARMUP, workingIndex = null, weightKg = 40.0, reps = 10, rir = null, completedAt = 1)
        val warmupB = warmupA.copy(id = "w2", weightKg = 40.0, completedAt = 2)
        val positionOne = LoggedSet(id = "p1", sessionExerciseId = "se", setType = SetType.WORKING, workingIndex = 1, weightKg = 60.0, reps = 8, rir = 2, completedAt = 3)
        val duplicate = positionOne.copy(id = "p2", completedAt = 4)

        dao.insertSets(listOf(warmupA, warmupB, positionOne, duplicate))

        val stored = dao.selectSetsFor("se")
        assertEquals(listOf("w1", "w2", "p1"), stored.map { it.id })
        assertEquals(listOf(null, null, 1), stored.map { it.workingIndex })
    }

    @Test
    fun seedingTwiceLeavesExactlyTheSeed() = runTest {
        val repository = repository()

        repository.seedIfNeeded()
        repository.seedIfNeeded()

        assertEquals(57, dao.exerciseCount())
        assertEquals(6, dao.templateCount())
        // 26 template-exercise rows across the six premades: 3, 5, 5, 4, 4, 5 exercises.
        assertEquals(26, dao.templateExerciseCount())
        assertEquals(listOf(3, 4, 4, 5, 5, 5), dao.templateExerciseCountsByTemplate().sorted())
        // Working-set totals per template: 9, 16, 17, 13, 12, 16.
        assertEquals(listOf(9, 12, 13, 16, 16, 17), dao.templateWorkingSetTotalsByTemplate().sorted())
        assertEquals(SeedContent.VERSION, repository.settingsStore.settings.first().seedVersion)
    }
}
