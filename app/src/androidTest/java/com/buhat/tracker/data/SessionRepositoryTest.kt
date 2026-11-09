package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.sourceSetForPosition
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var db: BuhatDatabase
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() = runTest {
        // Isolation: in-memory Room; the device's real file database is never touched.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "buhat-session-${UUID.randomUUID()}.preferences_pb",
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

    private suspend fun premadeTemplateId(name: String) = seededId("template", name)

    private suspend fun firstExerciseOf(sessionId: String) =
        repo.sessionExercises(sessionId).first().first()

    @Test
    fun startCopiesTheTemplateIntoSessionSnapshots() = runTest {
        val pushId = premadeTemplateId("Push Day A")

        val sessionId = repo.startSession(pushId)

        val session = repo.session(sessionId)!!
        assertEquals(WorkoutStatus.ACTIVE, session.status)
        assertEquals(pushId, session.templateIdSnapshot)
        assertEquals("Push Day A", session.templateNameSnapshot)

        val exercises = repo.sessionExercises(sessionId).first()
        assertEquals(
            listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"),
            exercises.map { it.exerciseNameSnapshot },
        )
        assertEquals(listOf(1, 2, 3), exercises.map { it.position })
        assertEquals(listOf(3, 3, 3), exercises.map { it.workingSets })
        assertEquals(listOf(6, 8, 10), exercises.map { it.repMin })
        assertEquals(listOf(8, 10, 12), exercises.map { it.repMax })
    }

    @Test
    fun reorderedSessionKeepsSetsRestAndTemplateAcrossReloadAndFinish() = runTest {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase5-bench-session.json").bufferedReader().use { JSONObject(it.readText()) }
        val templateId = fixture.getString("templateIdSnapshot")
        val templateBefore = db.dao().templateExercisesFor(templateId)
        val sessionId = repo.startSession(templateId)
        val original = repo.sessionExercises(sessionId).first()
        val capturedSets = fixture.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
        for (index in 0 until capturedSets.length()) {
            val set = capturedSets.getJSONObject(index)
            repo.logSet(original[0].id, SetType.valueOf(set.getString("setType")),
                set.getDouble("weightKg"), set.getInt("reps"), set.getInt("rir"))
        }
        val setsBefore = db.dao().allSets()
        val restBefore = repo.settingsStore.settings.first().restEndsAt
        assertNotNull(restBefore)

        repo.reorderSessionExercises(sessionId, listOf(original[2].id, original[0].id, original[1].id))

        val reopened = BuhatRepository(db, repo.settingsStore, repo.profileStore)
        val restored = reopened.sessionExercises(sessionId).first()
        assertEquals(listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press"),
            restored.map { it.exerciseNameSnapshot })
        assertEquals(listOf(1, 2, 3), restored.map { it.position })
        assertEquals(listOf(original[2].copy(position = 1), original[0].copy(position = 2),
            original[1].copy(position = 3)), restored)
        assertEquals(setsBefore, db.dao().allSets())
        assertEquals(restBefore, reopened.settingsStore.settings.first().restEndsAt)
        assertEquals(templateBefore, db.dao().templateExercisesFor(templateId))

        reopened.finishSession(sessionId)

        val finished = reopened.history().first().single()
        assertEquals(listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press"),
            finished.exercises.map { it.exerciseNameSnapshot })
        assertEquals(listOf(100.0, 80.0, 80.0, 80.0), finished.exercises[1].sets.map { it.weightKg })
        assertEquals(listOf(20, 10, 9, 8), finished.exercises[1].sets.map { it.reps })
        assertTrue(finished.exercises[0].sets.isEmpty())
        assertTrue(finished.exercises[2].sets.isEmpty())
        assertEquals(templateBefore, db.dao().templateExercisesFor(templateId))
    }

    @Test
    fun reorderRejectsMissingDuplicateForeignAndExtraIdsWithoutWriting() = runTest {
        val previous = repo.startSession(premadeTemplateId("Push Day A"))
        val foreign = firstExerciseOf(previous)
        repo.logSet(foreign.id, SetType.WORKING, 80.0, 8, 2)
        repo.finishSession(previous)
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val ids = repo.sessionExercises(sessionId).first().map { it.id }
        val before = db.dao().allSessionExercises()
        val invalidLists = listOf(
            emptyList(), ids.dropLast(1), listOf(ids[0], ids[0], ids[2]),
            listOf(ids[0], ids[1], foreign.id), ids + foreign.id, ids + ids[0],
        )
        for (invalid in invalidLists) {
            val error = runCatching { repo.reorderSessionExercises(sessionId, invalid) }.exceptionOrNull()
            assertTrue("Invalid order must be rejected: $invalid", error is SessionValidationException)
            assertEquals(before, db.dao().allSessionExercises())
        }
    }

    @Test
    fun reorderRejectsFinishedAndMissingSessionsWithoutWriting() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        repo.logSet(firstExerciseOf(sessionId).id, SetType.WORKING, 80.0, 8, 2)
        repo.finishSession(sessionId)
        val before = db.dao().allSessionExercises()
        val ids = before.map { it.id }.reversed()
        for (id in listOf(sessionId, "missing-session")) {
            val error = runCatching { repo.reorderSessionExercises(id, ids) }.exceptionOrNull()
            assertTrue("Inactive workout must be rejected", error is SessionValidationException)
            assertEquals(before, db.dao().allSessionExercises())
        }
    }

    @Test
    fun reorderRollsBackEveryPositionWhenAWriteFails() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val before = repo.sessionExercises(sessionId).first()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_reorder BEFORE UPDATE OF position ON SessionExercise " +
                "WHEN NEW.position = 2 BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )

        val error = runCatching {
            repo.reorderSessionExercises(sessionId, before.map { it.id }.reversed())
        }.exceptionOrNull()

        assertTrue(error is android.database.sqlite.SQLiteException)
        assertEquals(before, repo.sessionExercises(sessionId).first())
    }

    @Test
    fun finishWaitsForAnInFlightReorder() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val original = repo.sessionExercises(sessionId).first()
        repo.logSet(original[0].id, SetType.WORKING, 80.0, 8, 2)
        val reordering = launch {
            repo.reorderSessionExercises(sessionId, listOf(original[2].id, original[0].id, original[1].id))
        }
        yield()

        repo.finishSession(sessionId)
        reordering.join()

        assertEquals(WorkoutStatus.FINISHED, repo.session(sessionId)!!.status)
        assertEquals(listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press"),
            repo.history().first().single().exercises.map { it.exerciseNameSnapshot })
        assertNull(repo.settingsStore.settings.first().restEndsAt)
    }

    @Test
    fun editingTheTemplateAfterStartLeavesTheSessionAlone() = runTest {
        val bench = seededId("exercise", "Bench Press")
        val fly = seededId("exercise", "Cable Fly")
        val templateId = repo.saveTemplate(
            null,
            "Snapshot Test",
            listOf(
                TemplateExerciseDraft(bench, "Bench Press", 3, 6, 8, null),
                TemplateExerciseDraft(fly, "Cable Fly", 3, 10, 12, null),
            ),
        )
        val sessionId = repo.startSession(templateId)

        repo.saveTemplate(
            templateId,
            "Snapshot Test",
            listOf(TemplateExerciseDraft(fly, "Cable Fly", 5, 12, 15, 2)),
        )

        val exercises = repo.sessionExercises(sessionId).first()
        assertEquals(listOf("Bench Press", "Cable Fly"), exercises.map { it.exerciseNameSnapshot })
        assertEquals(listOf(3, 3), exercises.map { it.workingSets })
        assertEquals(listOf(6, 10), exercises.map { it.repMin })
    }

    @Test
    fun onlyOneSessionCanBeActiveAtATime() = runTest {
        val first = repo.startSession(premadeTemplateId("Push Day A"))

        val error = try {
            repo.startSession(premadeTemplateId("Pull Day A"))
            null
        } catch (e: ActiveSessionException) {
            e
        }

        assertEquals(first, error!!.sessionId)
        assertEquals(1, db.dao().activeSessionCount())
    }

    @Test
    fun loggingWarmupWorkingAndFailureKeepsTapOrderAndWorkingPositions() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)

        repo.logSet(bench.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = 4)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        repo.logSet(bench.id, SetType.FAILURE, weightKg = 80.0, reps = 6, rir = 0)

        val sets = repo.loggedSets(bench.id).first()
        assertEquals(listOf(40.0, 80.0, 80.0), sets.map { it.weightKg })
        assertEquals(listOf(10, 8, 6), sets.map { it.reps })
        // Warmups are not numbered; working and failure share the 1..N sequence.
        assertEquals(listOf(null, 1, 2), sets.map { it.workingIndex })
    }

    @Test
    fun logSetRejectsOutOfRangeValues() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)

        val error = try {
            repo.logSet(bench.id, SetType.WORKING, weightKg = 1000.5, reps = 8, rir = 2)
            null
        } catch (e: SessionValidationException) {
            e.message
        }

        assertEquals("Weight must be 0 to 1000 kg.", error)
        assertTrue(repo.loggedSets(bench.id).first().isEmpty())
    }

    @Test
    fun logSetRejectsANonFiniteWeightBeforeStoringTheSetOrStartingARest() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)

        val error = try {
            repo.logSet(bench.id, SetType.WORKING, weightKg = Double.NaN, reps = 8, rir = 2)
            null
        } catch (e: SessionValidationException) {
            e.message
        }

        assertEquals("Weight must be 0 to 1000 kg.", error)
        assertTrue(repo.loggedSets(bench.id).first().isEmpty())
        assertTrue(db.dao().allSets().isEmpty())
        assertNull(repo.settingsStore.settings.first().restEndsAt)
    }

    @Test
    fun finishingWritesAFinishedSessionAndClearsTheActiveOne() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)

        repo.finishSession(sessionId)

        assertNull(repo.activeSession().first())
        val finished = repo.session(sessionId)!!
        assertEquals(WorkoutStatus.FINISHED, finished.status)
        assertNotNull(finished.finishedAt)
    }

    @Test
    fun finishingWithOnlyWarmupsIsRefused() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)
        repo.logSet(bench.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = 4)

        val error = try {
            repo.finishSession(sessionId)
            null
        } catch (e: NoWorkingSetsException) {
            e
        }

        assertNotNull(error)
        assertEquals(WorkoutStatus.ACTIVE, repo.session(sessionId)!!.status)
    }

    @Test
    fun discardingRemovesTheSessionExercisesAndSets() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)

        repo.discardSession(sessionId)

        assertNull(repo.session(sessionId))
        assertTrue(repo.sessionExercises(sessionId).first().isEmpty())
        assertTrue(repo.loggedSets(bench.id).first().isEmpty())
    }

    @Test
    fun aFreshRepositoryStillSeesTheActiveSessionAndItsSets() = runTest {
        val sessionId = repo.startSession(premadeTemplateId("Push Day A"))
        val bench = firstExerciseOf(sessionId)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)

        // Same database, new repository: what process death and relaunch observes.
        val reopened = BuhatRepository(db, repo.settingsStore, repo.profileStore)

        assertEquals(sessionId, reopened.activeSession().first()!!.id)
        val sets = reopened.loggedSets(bench.id).first()
        assertEquals(1, sets.size)
        assertEquals(80.0, sets.single().weightKg, 0.0)
        assertEquals(8, sets.single().reps)
        assertEquals(1, sets.single().workingIndex)
    }

    @Test
    fun historyCarriesFinishedSessionsOnlyGroupedWithTheirSets() = runTest {
        val benchExerciseId = seededId("exercise", "Bench Press")
        val finished = repo.startSession(premadeTemplateId("Push Day A"))
        val firstBench = firstExerciseOf(finished)
        repo.logSet(firstBench.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = 4)
        repo.logSet(firstBench.id, SetType.WORKING, weightKg = 80.0, reps = 10, rir = 2)
        repo.finishSession(finished)

        val active = repo.startSession(premadeTemplateId("Push Day A"))
        repo.logSet(firstExerciseOf(active).id, SetType.WORKING, weightKg = 90.0, reps = 5, rir = 1)

        val history = repo.historyForExercise(benchExerciseId).first()

        assertEquals(1, history.size)
        val session = history.single()
        assertEquals(finished, session.id)
        assertEquals(premadeTemplateId("Push Day A"), session.templateIdSnapshot)
        assertEquals("Push Day A", session.templateNameSnapshot)
        assertEquals(listOf(benchExerciseId), session.exercises.map { it.exerciseId })
        // Warmups are stored but never feed progression.
        assertEquals(
            listOf(SetType.WARMUP, SetType.WORKING),
            session.exercises.single().sets.map { it.setType },
        )
    }

    @Test
    fun boundedHistoryFeedsBothProgressionSources() = runTest {
        val bench = seededId("exercise", "Bench Press")
        val pushId = premadeTemplateId("Push Day A")
        val benchOnlyId = repo.saveTemplate(
            null,
            "Bench Only",
            listOf(TemplateExerciseDraft(bench, "Bench Press", 3, 6, 8, null)),
        )

        suspend fun finish(templateId: String, weightKg: Double, finishedAt: Long): String {
            val sessionId = repo.startSession(templateId)
            val benchRow = firstExerciseOf(sessionId)
            repo.logSet(benchRow.id, SetType.WORKING, weightKg = weightKg, reps = 8, rir = 2)
            repo.finishSession(sessionId)
            // Explicit timestamps keep the ordering independent of the wall clock.
            db.dao().updateSession(
                repo.session(sessionId)!!.copy(startedAt = finishedAt - 1_000, finishedAt = finishedAt),
            )
            return sessionId
        }

        val oldest = finish(pushId, weightKg = 80.0, finishedAt = 1_000)
        val middle = finish(pushId, weightKg = 85.0, finishedAt = 2_000)
        val newest = finish(benchOnlyId, weightKg = 90.0, finishedAt = 3_000)

        val history = repo.latestHistoryForExercise(bench, pushId).first()

        assertEquals(listOf(middle, newest), history.map { it.id })
        assertTrue(oldest !in history.map { it.id })
        assertEquals(
            85.0,
            sourceSetForPosition(history, bench, 1, PreviousSetSource.SAME_TEMPLATE, pushId)!!.weightKg,
            0.0,
        )
        assertEquals(
            90.0,
            sourceSetForPosition(history, bench, 1, PreviousSetSource.ANY_WORKOUT, pushId)!!.weightKg,
            0.0,
        )
    }
}
