package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDao
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.DAY_MS
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.e1rm
import com.buhat.tracker.domain.fourWeekStats
import com.buhat.tracker.domain.homeAnalyticsNow
import com.buhat.tracker.domain.prSetIds
import com.buhat.tracker.domain.priorWindowStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class HomeDashboardTest {

    private lateinit var db: BuhatDatabase
    private lateinit var dao: BuhatDao
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val queries = mutableListOf<String>()

    @Before
    fun setUp() {
        // Isolation: in-memory Room with a statement log; the device's real database is never touched.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).setQueryCallback({ sql, _ -> synchronized(queries) { queries += sql } }, queryExecutor).build()
        dao = db.dao()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "buhat-dashboard-${UUID.randomUUID()}.preferences_pb")
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        repo = BuhatRepository(db, SettingsStore(dataStore), ProfileStore(dataStore))
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
        queryExecutor.shutdown()
    }

    private suspend fun insertSession(
        id: String,
        finishedAt: Long,
        exerciseId: String,
        weightKg: Double,
        reps: Int = 8,
        setType: SetType = SetType.WORKING,
    ) {
        dao.insertSessions(
            listOf(
                WorkoutSession(
                    id = id,
                    templateIdSnapshot = TEMPLATE_ID,
                    templateNameSnapshot = "Push Day A",
                    startedAt = finishedAt - 1_000,
                    finishedAt = finishedAt,
                    status = WorkoutStatus.FINISHED,
                ),
            ),
        )
        dao.insertSessionExercises(
            listOf(
                SessionExercise(
                    id = "se-$id",
                    sessionId = id,
                    exerciseId = exerciseId,
                    exerciseNameSnapshot = "Bench Press",
                    position = 1,
                    workingSets = 3,
                    repMin = 6,
                    repMax = 8,
                    targetRir = null,
                ),
            ),
        )
        dao.insertSets(
            listOf(
                LoggedSet(
                    id = "set-$id",
                    sessionExerciseId = "se-$id",
                    setType = setType,
                    workingIndex = if (setType == SetType.WARMUP) null else 1,
                    weightKg = weightKg,
                    reps = reps,
                    rir = 2,
                    completedAt = finishedAt,
                ),
            ),
        )
    }

    private fun statements() = synchronized(queries) { queries.map { it.replace(WHITESPACE, " ") } }

    @Test
    fun dashboardKeepsOnlyTheWindowRowsAndStillCountsAllHistoryInSql() = runTest {
        val now = 400L * DAY_MS
        db.withTransaction {
            repeat(200) { index ->
                insertSession("old-$index", now - 200 * DAY_MS + index * 60_000L, EXERCISE_BENCH, 80.0)
            }
            insertSession("recent-a", now - 2 * DAY_MS, EXERCISE_BENCH, 85.0)
            insertSession("recent-b", now - DAY_MS, EXERCISE_BENCH, 90.0)
        }

        val dashboard = repo.homeDashboard(now).first()

        assertEquals(listOf("recent-a", "recent-b"), dashboard.window.map { it.id })
        assertEquals("recent-b", dashboard.latest?.id)
        // 202 workouts, but only the two recent sessions were loaded as rows.
        assertEquals(202, dashboard.profileStats.totalWorkouts)
        assertEquals(now - 200 * DAY_MS, dashboard.profileStats.firstFinishedAt)
        assertEquals(e1rm(80.0, 8)!!, dashboard.bestBefore.getValue(EXERCISE_BENCH), 0.0)
    }

    @Test
    fun dashboardRunsTheBoundedQueriesAndNeverTheFullHistoryOne() = runTest {
        val now = 400L * DAY_MS
        db.withTransaction {
            insertSession("old", now - 200 * DAY_MS, EXERCISE_BENCH, 80.0)
            insertSession("recent", now - DAY_MS, EXERCISE_BENCH, 90.0)
        }
        synchronized(queries) { queries.clear() }

        repo.homeDashboard(now).first()

        val seen = statements()
        assertTrue("full history must not be loaded", seen.none { it.contains("ORDER BY finishedAt, id") })
        assertTrue("the window query must be bounded", seen.any { it.contains("s.finishedAt >= ?") })
        assertTrue("the seed must be one SQL aggregate", seen.any {
            it.contains("MAX(ls.weightKg * (1.0 + ls.reps / 30.0))")
        })
    }

    @Test
    fun sqlSeedMatchesTheKotlinEpleyForEveryShapeOfBest() = runTest {
        val now = 400L * DAY_MS
        db.withTransaction {
            insertSession("eight-by-ten", now - 400 * DAY_MS, EXERCISE_BENCH, 80.0, reps = 10)
            insertSession("hundred-by-five", now - 401 * DAY_MS, EXERCISE_SQUAT, 100.0, reps = 5)
            insertSession("tie-a", now - 402 * DAY_MS, EXERCISE_ROW, 60.0, reps = 12)
            insertSession("tie-b", now - 403 * DAY_MS, EXERCISE_ROW, 60.0, reps = 12)
            insertSession("warmup", now - 404 * DAY_MS, EXERCISE_ROW, 500.0, reps = 10, setType = SetType.WARMUP)
            insertSession("bodyweight", now - 405 * DAY_MS, EXERCISE_PRESS, 0.0, reps = 10)
        }

        val best = repo.homeDashboard(now).first().bestBefore

        assertEquals(e1rm(80.0, 10)!!, best.getValue(EXERCISE_BENCH), 0.0)
        assertEquals(e1rm(100.0, 5)!!, best.getValue(EXERCISE_SQUAT), 0.0)
        // Warmups and bodyweight rows never seed a best, so the tie stays at the 60×12 value.
        assertEquals(e1rm(60.0, 12)!!, best.getValue(EXERCISE_ROW), 0.0)
        assertTrue(EXERCISE_PRESS !in best)
    }

    @Test
    fun fourWeekPrsCountTiesAgainstTheAllTimeSeedAndRecomputeAfterDeletion() = runTest {
        val now = 400L * DAY_MS
        db.withTransaction {
            insertSession("old-record", now - 400 * DAY_MS, EXERCISE_BENCH, 100.0, reps = 5)
            insertSession("recent-tie", now - DAY_MS, EXERCISE_BENCH, 100.0, reps = 5)
        }

        val withRecord = repo.homeDashboard(now).first()
        assertEquals(listOf("recent-tie"), withRecord.window.map { it.id })
        assertEquals(setOf(EXERCISE_BENCH), withRecord.bestBefore.keys)
        assertEquals(emptySet<String>(), prSetIds(withRecord.window, withRecord.bestBefore))
        assertEquals(0.0, fourWeekStats(withRecord.window, now, withRecord.bestBefore).newPrs.current, 0.0)

        repo.deleteFinishedSession("old-record")
        val withoutRecord = repo.homeDashboard(now).first()

        assertTrue(withoutRecord.bestBefore.isEmpty())
        assertEquals(setOf("set-recent-tie"), prSetIds(withoutRecord.window, withoutRecord.bestBefore))
        assertEquals(1.0, fourWeekStats(withoutRecord.window, now, withoutRecord.bestBefore).newPrs.current, 0.0)
    }

    @Test
    fun dashboardWindowAndSeedProduceTheSameFourWeekStatsAsTheFullHistory() = runTest {
        val now = 400L * DAY_MS
        val priorStart = priorWindowStart(now)
        db.withTransaction {
            // Before the prior window: seeds the all-time best only (80 x 8 -> 101.3 kg).
            insertSession("old", priorStart - 10 * DAY_MS, EXERCISE_BENCH, 80.0, reps = 8)
            // Exactly on the prior-window boundary: a prior-window set, never part of the seed.
            insertSession("boundary", priorStart, EXERCISE_BENCH, 100.0, reps = 5)
            // Prior window, beaten by the boundary set (90 x 8 -> 114.0 kg).
            insertSession("prior", priorStart + DAY_MS, EXERCISE_BENCH, 90.0, reps = 8)
            // Current window, above every earlier e1RM (95 x 8 -> 120.3 kg).
            insertSession("current", now - DAY_MS, EXERCISE_BENCH, 95.0, reps = 8)
        }

        val dashboard = repo.homeDashboard(now).first()
        val fromHistory = fourWeekStats(repo.history().first(), now)
        val fromDashboard = fourWeekStats(dashboard.window, now, dashboard.bestBefore)

        // The DAO cutoffs must feed the same window rows and all-time seed the domain derives itself.
        assertEquals(fromHistory.workouts, fromDashboard.workouts)
        assertEquals(fromHistory.workingSets, fromDashboard.workingSets)
        assertEquals(fromHistory.volumeKg, fromDashboard.volumeKg)
        assertEquals(fromHistory.newPrs, fromDashboard.newPrs)
        // Boundary and prior sets stay in the prior window, and only the boundary set is its PR.
        assertEquals(1.0, fromDashboard.workouts.current, 0.0)
        assertEquals(2.0, fromDashboard.workouts.previous, 0.0)
        assertEquals(760.0, fromDashboard.volumeKg.current, 0.0)
        assertEquals(1_220.0, fromDashboard.volumeKg.previous, 0.0)
        assertEquals(1.0, fromDashboard.newPrs.current, 0.0)
        assertEquals(1.0, fromDashboard.newPrs.previous, 0.0)
    }

    @Test
    fun aFarFutureSessionDoesNotShiftTheRecentFourWeekStats() = runTest {
        val now = 400L * DAY_MS
        db.withTransaction {
            insertSession("imported-future", now + 60 * DAY_MS, EXERCISE_BENCH, 120.0)
            insertSession("recent", now - DAY_MS, EXERCISE_BENCH, 95.0, reps = 8)
        }

        val dashboard = repo.homeDashboard(now).first()
        // The window query only has a lower cutoff, so the skewed row is loaded and older-first.
        assertEquals(listOf("recent", "imported-future"), dashboard.window.map { it.id })

        // The Home clock may reach one day past the snapshot, never 60.
        val analyticsNow = homeAnalyticsNow(now, dashboard.window.maxOfOrNull { it.finishedAt })
        assertEquals(now + DAY_MS, analyticsNow)

        val stats = fourWeekStats(dashboard.window, analyticsNow, dashboard.bestBefore)

        // The real session keeps its four-week stats; the far-future row is not in the window.
        assertEquals(1.0, stats.workouts.current, 0.0)
        assertEquals(1.0, stats.workingSets.current, 0.0)
        assertEquals(95.0 * 8, stats.volumeKg.current, 0.0)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val TEMPLATE_ID = "template-a"
        const val EXERCISE_BENCH = "bench"
        const val EXERCISE_SQUAT = "squat"
        const val EXERCISE_ROW = "row"
        const val EXERCISE_PRESS = "press"
    }
}
