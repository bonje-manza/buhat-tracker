package com.buhat.tracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.AppContainer
import com.buhat.tracker.domain.DAY_MS
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.exerciseStats
import com.buhat.tracker.domain.fourWeekStats
import com.buhat.tracker.domain.prSetIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private lateinit var container: AppContainer

    @Before fun setUp() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext(), inMemory = true)
        container.repository.seedIfNeeded()
    }

    @After fun tearDown() = container.close()

    @Test fun finishedHistoryPreservesSnapshotsSkippedExercisesAndWarmups() = runBlocking {
        val repo = container.repository
        val id = container.recordBenchWorkout()
        repo.startSession(seededId("template", "Pull Day A"))

        val history = repo.history().first()
        assertEquals(1, history.size)
        assertEquals(id, history.single().id)
        assertEquals("Push Day A", history.single().templateNameSnapshot)
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"),
            history.single().exercises.map { it.exerciseNameSnapshot })
        assertEquals(listOf(SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.FAILURE),
            history.single().exercises.first().sets.map { it.setType })
        assertTrue(history.single().exercises[1].sets.isEmpty())
        // 80×10 + 80×9 + 80×8 = 2160. Warmup 100×20 is deliberately bigger.
        val stats = fourWeekStats(history, System.currentTimeMillis())
        assertEquals(2160.0, stats.volumeKg.current, 0.0)
        assertEquals(3.0, stats.workingSets.current, 0.0)
        assertEquals(1.0, stats.workouts.current, 0.0)
        assertEquals(1.0, stats.newPrs.current, 0.0)
        assertEquals(106.6667, exerciseStats(history, seededId("exercise", "Bench Press")).bestE1rm!!, 0.0001)
    }

    @Test fun deletionEmitsNewHistoryCascadesAndRecomputesPrsWithoutChangingActiveRest() = runBlocking {
        val repo = container.repository
        val old = container.recordBenchWorkout(daysAgo = 40)
        val recent = container.recordBenchWorkout()
        val oldExercise = repo.sessionExercises(old).first().first()
        val oldSet = repo.loggedSets(oldExercise.id).first().first().id
        val active = repo.startSession(seededId("template", "Pull Day A"))
        val activeExercise = repo.sessionExercises(active).first().first()
        repo.logSet(activeExercise.id, SetType.WORKING, 60.0, 8, 2)
        val rest = container.settingsStore.settings.first().restEndsAt
        assertNotNull(rest)

        val updates = repo.history().produceIn(this)
        val before = withTimeout(5_000) { updates.receive() }
        assertEquals(2, before.size)
        assertEquals(0.0, fourWeekStats(before, System.currentTimeMillis()).newPrs.current, 0.0)
        repo.deleteFinishedSession(old)
        val after = withTimeout(5_000) { updates.receive() }
        updates.cancel()

        assertEquals(listOf(recent), after.map { it.id })
        assertEquals(1, prSetIds(after).size)
        assertEquals(1.0, fourWeekStats(after, System.currentTimeMillis()).newPrs.current, 0.0)
        assertEquals(1, repo.profileStats().first().totalWorkouts)
        assertNull(repo.session(old))
        assertTrue(repo.sessionExercises(old).first().isEmpty())
        assertNull(container.database.dao().setById(oldSet))
        assertEquals(active, repo.activeSession().first()!!.id)
        assertEquals(1, repo.loggedSets(activeExercise.id).first().size)
        assertEquals(rest, container.settingsStore.settings.first().restEndsAt)
    }

    @Test fun historyDeletionRejectsAnActiveWorkout() = runBlocking {
        val repo = container.repository
        val active = repo.startSession(seededId("template", "Push Day A"))
        val exercise = repo.sessionExercises(active).first().first()
        repo.logSet(exercise.id, SetType.WORKING, 80.0, 10, 2)
        val rest = container.settingsStore.settings.first().restEndsAt
        try {
            repo.deleteFinishedSession(active)
            fail("History deletion must reject an active workout")
        } catch (_: SessionValidationException) {
            assertEquals(active, repo.activeSession().first()!!.id)
            assertEquals(1, repo.loggedSets(exercise.id).first().size)
            assertEquals(rest, container.settingsStore.settings.first().restEndsAt)
        }
    }
}

// Captured from ProgressScreenTest's UI logging on SM-S711W. Shift only the clock for range tests.
internal suspend fun AppContainer.recordBenchWorkout(daysAgo: Int = 0): String {
    val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("phase5-bench-session.json")
        .bufferedReader().use { JSONObject(it.readText()) }
    val id = repository.startSession(fixture.getString("templateIdSnapshot"))
    val bench = repository.sessionExercises(id).first().first()
    val sets = fixture.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
    for (index in 0 until sets.length()) {
        val set = sets.getJSONObject(index)
        repository.logSet(bench.id, SetType.valueOf(set.getString("setType")), set.getDouble("weightKg"),
            set.getInt("reps"), if (set.isNull("rir")) null else set.getInt("rir"))
    }
    repository.finishSession(id)
    if (daysAgo > 0) {
        val session = repository.session(id)!!
        val offset = daysAgo * DAY_MS
        database.dao().updateSession(session.copy(startedAt = session.startedAt - offset, finishedAt = session.finishedAt!! - offset))
        repository.loggedSets(bench.id).first().forEach { set ->
            database.dao().updateSet(set.copy(completedAt = set.completedAt - offset))
        }
    }
    return id
}
