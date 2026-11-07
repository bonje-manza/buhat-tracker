package com.buhat.tracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDao
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseHistoryQueryTest {

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

    private suspend fun finishedSession(
        id: String,
        templateId: String,
        finishedAt: Long,
        weightKg: Double,
        status: WorkoutStatus = WorkoutStatus.FINISHED,
        logSet: Boolean = true,
    ) {
        dao.insertSessions(
            listOf(
                WorkoutSession(
                    id = id,
                    templateIdSnapshot = templateId,
                    templateNameSnapshot = templateId,
                    startedAt = finishedAt - 1_000,
                    finishedAt = if (status == WorkoutStatus.FINISHED) finishedAt else null,
                    status = status,
                ),
            ),
        )
        val sessionExerciseId = "se-$id"
        dao.insertSessionExercises(
            listOf(
                SessionExercise(
                    id = sessionExerciseId,
                    sessionId = id,
                    exerciseId = EXERCISE_ID,
                    exerciseNameSnapshot = "Bench Press",
                    position = 1,
                    workingSets = 3,
                    repMin = 6,
                    repMax = 8,
                    targetRir = null,
                ),
            ),
        )
        if (!logSet) return
        dao.insertSets(
            listOf(
                LoggedSet(
                    id = "set-$id",
                    sessionExerciseId = sessionExerciseId,
                    setType = SetType.WORKING,
                    workingIndex = 1,
                    weightKg = weightKg,
                    reps = 8,
                    rir = 2,
                    completedAt = finishedAt,
                ),
            ),
        )
    }

    @Test
    fun latestRowsKeepTheNewestSessionAndTheLatestSessionForTheTemplate() = runTest {
        finishedSession("oldest", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 80.0)
        finishedSession("middle", templateId = TEMPLATE_A, finishedAt = 2_000, weightKg = 85.0)
        finishedSession("newest", templateId = "template-b", finishedAt = 3_000, weightKg = 90.0)

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        assertEquals(listOf("middle", "newest"), rows.map { it.sessionId })
        assertEquals(listOf(85.0, 90.0), rows.map { it.weightKg })
        assertEquals(listOf(EXERCISE_ID, EXERCISE_ID), rows.map { it.exerciseId })
    }

    @Test
    fun latestRowsCollapseToTheNewestSessionWhenItAlsoMatchesTheTemplate() = runTest {
        finishedSession("older", templateId = "template-b", finishedAt = 1_000, weightKg = 80.0)
        finishedSession("newest", templateId = TEMPLATE_A, finishedAt = 2_000, weightKg = 90.0)

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        assertEquals(listOf("newest"), rows.map { it.sessionId })
    }

    @Test
    fun latestRowsSkipSessionsThatAreStillActive() = runTest {
        finishedSession("finished", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 80.0)
        finishedSession(
            "active",
            templateId = TEMPLATE_A,
            finishedAt = 2_000,
            weightKg = 90.0,
            status = WorkoutStatus.ACTIVE,
        )

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        assertEquals(listOf("finished"), rows.map { it.sessionId })
        assertEquals(listOf(1_000L), rows.map { it.finishedAt })
    }

    @Test
    fun latestRowsIgnoreANewerSessionThatLoggedNoSetsForTheExercise() = runTest {
        finishedSession("with-sets", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 80.0)
        finishedSession("skipped", templateId = TEMPLATE_A, finishedAt = 2_000, weightKg = 0.0, logSet = false)

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        assertEquals(listOf("with-sets"), rows.map { it.sessionId })
        assertEquals(listOf(80.0), rows.map { it.weightKg })
        assertEquals(listOf(TEMPLATE_A), rows.map { it.templateNameSnapshot })
    }

    @Test
    fun latestRowsIgnoreANewerTemplateSessionThatLoggedNoSets() = runTest {
        finishedSession("template-with-sets", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 80.0)
        finishedSession("other-template", templateId = "template-b", finishedAt = 2_000, weightKg = 85.0)
        finishedSession(
            "template-without-sets",
            templateId = TEMPLATE_A,
            finishedAt = 3_000,
            weightKg = 0.0,
            logSet = false,
        )

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        // The template subquery's EXISTS skips the 3_000 session, so the template match stays at 1_000.
        assertEquals(listOf("template-with-sets", "other-template"), rows.map { it.sessionId })
        assertEquals(listOf(1_000L, 2_000L), rows.map { it.finishedAt })
    }

    @Test
    fun latestRowsBreakFinishedAtTiesBySessionIdDescending() = runTest {
        finishedSession("session-a", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 80.0)
        finishedSession("session-b", templateId = TEMPLATE_A, finishedAt = 1_000, weightKg = 85.0)

        val rows = dao.observeLatestHistoryRowsFor(EXERCISE_ID, TEMPLATE_A).first()

        assertEquals(listOf("session-b"), rows.map { it.sessionId })
        assertEquals(listOf(85.0), rows.map { it.weightKg })
    }

    private companion object {
        const val EXERCISE_ID = "exercise-bench"
        const val TEMPLATE_A = "template-a"
    }
}
