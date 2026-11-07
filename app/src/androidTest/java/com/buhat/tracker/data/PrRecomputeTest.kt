package com.buhat.tracker.data

import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.AppContainer
import com.buhat.tracker.domain.DAY_MS
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.prSetIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrRecomputeTest {

    private lateinit var container: AppContainer

    @Before fun setUp() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext(), inMemory = true)
        container.repository.seedIfNeeded()
    }

    @After fun tearDown() = container.close()

    private val benchId get() = seededId("exercise", "Bench Press")

    private suspend fun recordSet(weightKg: Double, reps: Int, daysAgo: Int = 0): Pair<String, String> {
        val repo = container.repository
        val dao = container.database.dao()
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val setId = repo.logSet(repo.sessionExercises(sessionId).first().first().id, SetType.WORKING, weightKg, reps, 2)
        repo.finishSession(sessionId)
        if (daysAgo > 0) {
            val session = repo.session(sessionId)!!
            val offset = daysAgo * DAY_MS
            dao.updateSession(
                session.copy(startedAt = session.startedAt - offset, finishedAt = session.finishedAt!! - offset),
            )
            val set = dao.setById(setId)!!
            dao.updateSet(set.copy(completedAt = set.completedAt - offset))
        }
        return sessionId to setId
    }

    private suspend fun currentPrs(): Set<String> = prSetIds(container.repository.historyForExercise(benchId).first())

    private suspend fun clearHistory() {
        val dao = container.database.dao()
        container.database.withTransaction {
            dao.deleteAllSets()
            dao.deleteAllSessionExercises()
            dao.deleteAllSessions()
        }
    }

    @Test
    fun editingALaterSetAboveAndBelowTheEarlierRecordMovesTheBadge() = runBlocking {
        val (_, early) = recordSet(100.0, 6, daysAgo = 10)
        val (_, later) = recordSet(80.0, 10, daysAgo = 1)
        assertEquals(setOf(early), currentPrs())

        container.repository.updateSet(later, SetType.WORKING, 110.0, 5, 2)
        assertEquals(setOf(early, later), currentPrs())

        container.repository.updateSet(later, SetType.WORKING, 60.0, 5, 2)
        assertEquals(setOf(early), currentPrs())
    }

    @Test
    fun deletingTheEarlyRecordPromotesTheNextSetToAPr() = runBlocking {
        val (earlySession, early) = recordSet(100.0, 5, daysAgo = 10)
        val (_, later) = recordSet(100.0, 5, daysAgo = 1)
        assertEquals(setOf(early), currentPrs())

        container.repository.deleteFinishedSession(earlySession)

        assertEquals(setOf(later), currentPrs())
    }

    @Test
    fun importingABackupReplacesEveryPreviousPrSet() = runBlocking {
        recordSet(100.0, 5, daysAgo = 400)
        recordSet(100.0, 5, daysAgo = 1)
        val firstBackup = container.repository.exportBackup()
        val firstPrs = currentPrs()
        assertEquals(1, firstPrs.size)

        clearHistory()
        recordSet(70.0, 10, daysAgo = 1)
        val secondBackup = container.repository.exportBackup()
        val secondPrs = currentPrs()
        assertEquals(1, secondPrs.size)
        assertTrue(firstPrs.intersect(secondPrs).isEmpty())

        container.repository.importBackup(firstBackup)
        assertEquals(firstPrs, currentPrs())

        container.repository.importBackup(secondBackup)
        assertEquals(secondPrs, currentPrs())
        assertTrue(currentPrs().none { it in firstPrs })
    }
}
