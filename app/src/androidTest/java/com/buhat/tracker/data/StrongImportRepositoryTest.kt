package com.buhat.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.StrongCsvException
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrongImportRepositoryTest {

    private lateinit var db: BuhatDatabase
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope

    private val header =
        "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;Distance (meters);Seconds;Notes;Workout Notes"

    private fun row(
        workout: String = "1",
        date: String = "2026-06-24 08:00:00",
        name: String = "FBEOD",
        duration: String = "3600",
        exercise: String = "Bench Press",
        setOrder: String,
        weight: String = "80",
        reps: String = "5",
        rpe: String = "8",
        distance: String = "",
        seconds: String = "",
        notes: String = "",
        workoutNotes: String = "",
    ): String = listOf(
        workout, date, name, duration, exercise, setOrder, weight, reps, rpe,
        distance, seconds, notes, workoutNotes,
    ).joinToString(";")

    private fun csv(vararg rows: String): String = (listOf(header) + rows).joinToString("\r\n")

    private fun twoWorkoutCsv(): String = csv(
        row(exercise = "Bench Press", setOrder = "W", weight = "40", reps = "10", rpe = ""),
        row(exercise = "Bench Press", setOrder = "1"),
        row(exercise = "Bench Press", setOrder = "2"),
        row(exercise = "Seated Row (Cable)", setOrder = "1", weight = "60", reps = "10", rpe = "9"),
        row(workout = "2", date = "2026-06-25 08:00:00", name = "FB x3", setOrder = "1"),
    )

    private fun millis(date: String): Long = LocalDateTime
        .parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private data class Counts(
        val exercises: Int,
        val sessions: Int,
        val sessionExercises: Int,
        val sets: Int,
    )

    private suspend fun counts(): Counts {
        val dao = db.dao()
        return Counts(
            exercises = dao.allExercises().size,
            sessions = dao.allSessions().size,
            sessionExercises = dao.allSessionExercises().size,
            sets = dao.allSets().size,
        )
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "buhat-strong-${UUID.randomUUID()}.preferences_pb",
        )
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        repo = BuhatRepository(db, SettingsStore(dataStore), ProfileStore(dataStore))
        repo.seedIfNeeded()
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    @Test
    fun previewCountsNewAndDuplicateWorkoutsAndPreselectsExactNames() = runTest {
        val preview = repo.previewStrongImport(twoWorkoutCsv())

        assertEquals(2, preview.newWorkouts)
        assertEquals(0, preview.skippedDuplicates)
        assertEquals(0, preview.skippedEmptyWorkouts)
        assertEquals(1, preview.warmupSets)
        assertEquals(4, preview.workingSets)
        assertEquals(0, preview.failureSets)
        assertEquals(millis("2026-06-24 08:00:00"), preview.firstStartedAt)
        assertEquals(millis("2026-06-25 08:00:00"), preview.lastStartedAt)
        assertEquals(listOf("Bench Press", "Seated Row (Cable)"), preview.sources.map { it.name })
        assertEquals(seededId("exercise", "Bench Press"), preview.sources.first().matchedExerciseId)
        assertNull(preview.sources.last().matchedExerciseId)
        assertTrue(preview.exercises.any { it.id == seededId("exercise", "Bench Press") })
    }

    @Test
    fun importAppendsFinishedSessionsWithSentinelSnapshotNotesAndRir() = runTest {
        val text = csv(
            row(setOrder = "W", weight = "40", reps = "10", rpe = "", workoutNotes = "heavy day"),
            row(setOrder = "1", weight = "80", reps = "5", rpe = "8", workoutNotes = "heavy day"),
            row(setOrder = "2", weight = "85", reps = "5", rpe = "9.5", workoutNotes = "heavy day"),
            row(
                exercise = "Seated Cable Row", setOrder = "Note", weight = "", reps = "", rpe = "",
                notes = "paused reps", workoutNotes = "heavy day",
            ),
            row(
                exercise = "Seated Cable Row", setOrder = "1", weight = "60", reps = "10", rpe = "9",
                workoutNotes = "heavy day",
            ),
        )

        val result = repo.importStrong(text, emptyList())

        assertEquals(1, result.workouts)
        assertEquals(0, result.skippedDuplicates)
        assertEquals(4, result.sets)
        val dao = db.dao()
        val session = dao.allSessions().single()
        assertEquals("strong-import", session.templateIdSnapshot)
        assertEquals("FBEOD", session.templateNameSnapshot)
        assertEquals("FINISHED", session.status.name)
        assertEquals(millis("2026-06-24 08:00:00"), session.startedAt)
        assertEquals(3_600_000L, session.finishedAt!! - session.startedAt)
        assertEquals("strong|2026-06-24 08:00:00|FBEOD", session.importKey)
        assertEquals("heavy day", session.note)

        val exercises = dao.allSessionExercises().sortedBy { it.position }
        assertEquals(listOf("Bench Press", "Seated Cable Row"), exercises.map { it.exerciseNameSnapshot })
        assertEquals(seededId("exercise", "Bench Press"), exercises.first().exerciseId)
        assertEquals("paused reps", exercises.last().note)

        val benchSets = dao.allSets().filter { it.sessionExerciseId == exercises.first().id }.sortedBy { it.workingIndex ?: 0 }
        assertEquals(listOf(SetType.WARMUP, SetType.WORKING, SetType.WORKING), benchSets.map { it.setType })
        assertEquals(listOf(null, 1, 2), benchSets.map { it.workingIndex })
        assertEquals(listOf(null, 2, 1), benchSets.map { it.rir })
        benchSets.forEach { assertNull(it.side) }
    }

    @Test
    fun reimportingTheSameTextImportsNothing() = runTest {
        val text = twoWorkoutCsv()
        val mappings = listOf(
            StrongMapping(
                sourceName = "Seated Row (Cable)",
                existingExerciseId = seededId("exercise", "Seated Cable Row"),
            ),
        )
        repo.importStrong(text, mappings)
        val before = counts()

        val preview = repo.previewStrongImport(text)
        val result = repo.importStrong(text, mappings)

        assertEquals(0, preview.newWorkouts)
        assertEquals(2, preview.skippedDuplicates)
        assertEquals(StrongImportResult(workouts = 0, skippedDuplicates = 2, sets = 0), result)
        assertEquals(before, counts())
    }

    @Test
    fun overlappingExportAddsOnlyNewIdentities() = runTest {
        repo.importStrong(
            twoWorkoutCsv(),
            listOf(
                StrongMapping(
                    sourceName = "Seated Row (Cable)",
                    existingExerciseId = seededId("exercise", "Seated Cable Row"),
                ),
            ),
        )
        val overlapping = csv(
            row(setOrder = "1"),
            row(workout = "2", date = "2026-06-26 08:00:00", name = "FB x3", setOrder = "1"),
        )

        val preview = repo.previewStrongImport(overlapping)
        val result = repo.importStrong(overlapping, emptyList())

        assertEquals(1, preview.newWorkouts)
        assertEquals(1, preview.skippedDuplicates)
        assertEquals(1, result.workouts)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(3, db.dao().allSessions().size)
    }

    @Test
    fun mappingTwoSourceNamesToOneExerciseKeepsSeparateOccurrences() = runTest {
        val text = csv(
            row(exercise = "Seated Row (Cable)", setOrder = "1", weight = "60", reps = "10"),
            row(exercise = "Cable Row", setOrder = "1", weight = "65", reps = "8"),
        )
        val target = seededId("exercise", "Seated Cable Row")

        repo.importStrong(
            text,
            listOf(
                StrongMapping(sourceName = "Seated Row (Cable)", existingExerciseId = target),
                StrongMapping(sourceName = "Cable Row", existingExerciseId = target),
            ),
        )

        val history = repo.history().first().single()
        assertEquals(
            listOf("Seated Row (Cable)", "Cable Row"),
            history.exercises.map { it.exerciseNameSnapshot },
        )
        assertEquals(listOf(target, target), history.exercises.map { it.exerciseId })
        assertEquals(2, history.exercises.size)
    }

    @Test
    fun newCustomExerciseRequiresNameMuscleGroupAndEquipment() = runTest {
        val text = csv(row(exercise = "Seated Row (Cable)", setOrder = "1"))
        val bad = listOf(
            StrongMapping(sourceName = "Seated Row (Cable)", newExerciseName = " ", muscleGroup = "Back", equipment = "Cable"),
            StrongMapping(sourceName = "Seated Row (Cable)", newExerciseName = "Seated Row (Cable)", muscleGroup = "", equipment = "Cable"),
            StrongMapping(sourceName = "Seated Row (Cable)", newExerciseName = "Seated Row (Cable)", muscleGroup = "Back", equipment = " "),
        )
        val before = counts()
        bad.forEach { mapping ->
            assertThrows(StrongCsvException::class.java) {
                runBlocking { repo.importStrong(text, listOf(mapping)) }
            }
        }
        assertEquals(before, counts())

        repo.importStrong(
            text,
            listOf(
                StrongMapping(
                    sourceName = "Seated Row (Cable)",
                    newExerciseName = "Seated Row (Cable)",
                    muscleGroup = "Back",
                    equipment = "Cable",
                ),
            ),
        )

        val created = db.dao().allExercises().single { it.name == "Seated Row (Cable)" }
        assertTrue(created.isCustom)
        assertEquals("Back", created.muscleGroup)
        assertEquals("Cable", created.equipment)
        assertEquals(created.id, db.dao().allSessionExercises().single().exerciseId)
        assertEquals(58, db.dao().allExercises().size)
    }

    @Test
    fun unsupportedRowBlocksPreviewAndWritesNothing() = runTest {
        val text = csv(row(setOrder = "1", distance = "100"))
        val before = counts()

        val previewError = assertThrows(StrongCsvException::class.java) {
            runBlocking { repo.previewStrongImport(text) }
        }
        val importError = assertThrows(StrongCsvException::class.java) {
            runBlocking { repo.importStrong(text, emptyList()) }
        }

        assertTrue(previewError.message.orEmpty().contains("Row"))
        assertTrue(importError.message.orEmpty().contains("Row"))
        assertEquals(before, counts())
    }

    @Test
    fun forcedWriteFailureRollsBackNewExercisesAndSessions() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER block_session BEFORE INSERT ON WorkoutSession " +
                "BEGIN SELECT RAISE(ABORT, 'blocked'); END;",
        )
        val text = csv(row(exercise = "Seated Row (Cable)", setOrder = "1"))
        val before = counts()

        val error = assertThrows(StrongCsvException::class.java) {
            runBlocking {
                repo.importStrong(
                    text,
                    listOf(
                        StrongMapping(
                            sourceName = "Seated Row (Cable)",
                            newExerciseName = "Seated Row (Cable)",
                            muscleGroup = "Back",
                            equipment = "Cable",
                        ),
                    ),
                )
            }
        }

        assertTrue("was '${error.message}'", error.message.orEmpty().contains("Nothing was saved"))
        assertEquals(before, counts())
    }

    @Test
    fun activeWorkoutBlocksPreviewAndImport() = runTest {
        repo.startSession(seededId("template", "Push Day A"))
        val text = twoWorkoutCsv()

        val previewError = assertThrows(StrongCsvException::class.java) {
            runBlocking { repo.previewStrongImport(text) }
        }
        val importError = assertThrows(StrongCsvException::class.java) {
            runBlocking { repo.importStrong(text, emptyList()) }
        }

        assertEquals("Finish or discard the workout first.", previewError.message)
        assertEquals("Finish or discard the workout first.", importError.message)
    }

    @Test
    fun importedDataSurvivesABackupRoundTripAndAppearsInHistory() = runTest {
        val text = csv(
            row(setOrder = "W", weight = "40", reps = "10", rpe = ""),
            row(setOrder = "1", weight = "80", reps = "5", rpe = "8", notes = "", workoutNotes = "heavy day"),
            row(
                exercise = "Seated Cable Row", setOrder = "Note", weight = "", reps = "", rpe = "",
                notes = "paused reps", workoutNotes = "heavy day",
            ),
            row(
                exercise = "Seated Cable Row", setOrder = "1", weight = "60", reps = "10", rpe = "9",
                workoutNotes = "heavy day",
            ),
        )
        repo.importStrong(text, emptyList())
        val exported = repo.exportBackup()

        repo.importBackup(exported)

        val history = repo.history().first().single()
        assertEquals("strong-import", history.templateIdSnapshot)
        assertEquals("FBEOD", history.templateNameSnapshot)
        assertEquals("heavy day", history.note)
        assertEquals(listOf("Bench Press", "Seated Cable Row"), history.exercises.map { it.exerciseNameSnapshot })
        assertEquals("paused reps", history.exercises.last().note)
        assertTrue(history.exercises.flatMap { it.sets }.all { it.side == null })
        assertEquals(3, history.exercises.flatMap { it.sets }.size)
    }

    @Test
    fun workoutsWithMoreThanTenSetsStillExportValidPrescriptions() = runTest {
        val rows = (1..12).map { index ->
            row(setOrder = index.toString(), weight = "100", reps = "35", rpe = "")
        }
        repo.importStrong(csv(*rows.toTypedArray()), emptyList())

        val sessionExercise = db.dao().allSessionExercises().single()
        assertEquals(10, sessionExercise.workingSets)
        assertEquals(30, sessionExercise.repMin)
        assertEquals(30, sessionExercise.repMax)
        assertEquals(12, db.dao().allSets().size)
        assertTrue(db.dao().allSets().all { it.reps == 35 })

        val exported = repo.exportBackup()
        val decoded = decodeBackup(exported)
        assertEquals(12, decoded.sets.size)
        assertEquals(30, decoded.sessionExercises.single().repMax)
    }
}
