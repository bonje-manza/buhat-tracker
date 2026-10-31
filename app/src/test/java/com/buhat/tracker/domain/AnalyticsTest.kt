package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsTest {

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 100L * DAY
    }

    private fun set(
        id: String,
        weight: Double,
        reps: Int,
        type: SetType = SetType.WORKING,
        at: Long = 0L,
    ) = LoggedSetRecord(
        id = id,
        setType = type,
        workingIndex = if (type == SetType.WARMUP) null else 1,
        weightKg = weight,
        reps = reps,
        rir = 2,
        completedAt = at,
    )

    private fun session(
        id: String,
        finishedAt: Long,
        vararg exercises: Pair<String, List<LoggedSetRecord>>,
    ) = HistorySession(
        id = id,
        templateIdSnapshot = "template",
        finishedAt = finishedAt,
        exercises = exercises.map { HistoryExercise(it.first, it.second) },
    )

    @Test
    fun epleyUsesWeightTimesOnePlusRepsOverThirty() {
        assertEquals(116.6667, e1rm(100.0, 5)!!, 0.0001)
        assertEquals(106.6667, e1rm(80.0, 10)!!, 0.0001)
    }

    @Test
    fun e1rmIsNullForBodyweightOrZeroReps() {
        assertNull(e1rm(0.0, 10))
        assertNull(e1rm(80.0, 0))
        assertNull(e1rm(-5.0, 5))
    }

    @Test
    fun onlyStrictlyGreaterE1rmSetsArePrs() {
        val history = listOf(
            session(
                "s1", 1L * DAY,
                "squat" to listOf(
                    set("s1-warmup", 100.0, 5, SetType.WARMUP, 1),
                    set("s1-a", 80.0, 5, at = 2),
                    set("s1-b", 85.0, 5, at = 3),
                ),
            ),
            session(
                "s2", 2L * DAY,
                "squat" to listOf(
                    set("s2-a", 90.0, 5, at = 4),
                    set("s2-tie", 90.0, 5, at = 5),
                    set("s2-pr", 95.0, 4, at = 6),
                    set("s2-bodyweight", 0.0, 20, at = 7),
                ),
            ),
            session(
                "s3", 3L * DAY,
                "squat" to listOf(set("s3-a", 90.0, 5, at = 8)),
                "bench" to listOf(set("s3-bench", 60.0, 5, at = 9)),
            ),
        )

        assertEquals(
            setOf("s1-a", "s1-b", "s2-a", "s2-pr", "s3-bench"),
            prSetIds(history),
        )
    }

    @Test
    fun fourWeekWindowsCountSetsVolumeAndPrsWithoutWarmups() {
        val history = listOf(
            session("outside-old", 43L * DAY, "squat" to listOf(set("o", 100.0, 1, at = 1))),
            session("prior-edge", 44L * DAY, "squat" to listOf(set("p1", 10.0, 1, at = 2))),
            session("prior-newest", 72L * DAY - 1, "squat" to listOf(set("p2", 1000.0, 1, at = 3))),
            session("current-edge", 72L * DAY, "squat" to listOf(set("c1", 50.0, 10, at = 4))),
            session(
                "warmup-only", 90L * DAY,
                "squat" to listOf(set("w", 1000.0, 5, SetType.WARMUP, 5)),
            ),
            session("current-newest", NOW, "squat" to listOf(set("c2", 1000.0, 5, at = 6))),
            session("future", NOW + DAY, "squat" to listOf(set("f", 1.0, 1, at = 7))),
        )

        val stats = fourWeekStats(history, NOW)

        assertEquals(2.0, stats.workouts.current, 0.0)
        assertEquals(2.0, stats.workouts.previous, 0.0)
        assertEquals(2.0, stats.workingSets.current, 0.0)
        assertEquals(2.0, stats.workingSets.previous, 0.0)
        assertEquals(5500.0, stats.volumeKg.current, 0.0)
        assertEquals(1010.0, stats.volumeKg.previous, 0.0)
        assertEquals(444.5545, stats.volumeKg.deltaPercent!!, 0.001)
        assertEquals(1.0, stats.newPrs.current, 0.0)
        assertEquals(1.0, stats.newPrs.previous, 0.0)
        assertEquals(0.0, stats.newPrs.deltaPercent!!, 0.0)
        assertEquals(0.0, stats.workouts.deltaPercent!!, 0.0)
    }

    @Test
    fun deltaIsNullWhenPreviousWindowIsEmpty() {
        val history = listOf(
            session("current", NOW, "squat" to listOf(set("c", 100.0, 5, at = 1))),
        )

        val stats = fourWeekStats(history, NOW)

        assertEquals(1.0, stats.workouts.current, 0.0)
        assertEquals(0.0, stats.workouts.previous, 0.0)
        assertNull(stats.workouts.deltaPercent)
        assertNull(stats.volumeKg.deltaPercent)
        assertNull(stats.newPrs.deltaPercent)
    }

    @Test
    fun trendHasOneMaxPointPerSessionAndRespectsRanges() {
        val history = listOf(
            session("old", 10L * DAY, "squat" to listOf(set("t1", 100.0, 5, at = 1))),
            session("mid", 5L * DAY, "squat" to listOf(set("t2", 90.0, 10, at = 2))),
            session("edge", 70L * DAY, "squat" to listOf(set("t3", 60.0, 10, at = 3))),
            session(
                "new", NOW,
                "squat" to listOf(
                    set("t4", 80.0, 5, at = 4),
                    set("t5", 90.0, 5, at = 5),
                    set("t6", 0.0, 20, at = 6),
                    set("t7", 100.0, 5, SetType.WARMUP, 7),
                ),
                "bench" to listOf(set("t8", 60.0, 5, at = 8)),
            ),
            session("future", NOW + DAY, "squat" to listOf(set("t9", 200.0, 5, at = 9))),
        )

        val ninety = e1rmTrend(history, "squat", rangeDays = 90, nowMillis = NOW)
        val thirty = e1rmTrend(history, "squat", rangeDays = 30, nowMillis = NOW)
        val all = e1rmTrend(history, "squat", rangeDays = null, nowMillis = NOW)

        // 3M = 90 days back from now=100d. Cutoff day 10, boundary included.
        // old is on the boundary; mid (day 5, 95 days back) is outside the window.
        // Within the window: old(10d), edge(70d), new(100d).
        assertEquals(listOf(10L * DAY, 70L * DAY, NOW), ninety.map { it.atMillis })
        assertEquals(116.6667, ninety[0].e1rm, 0.0001) // 100 kg × 5: 100 × (1 + 5/30)
        assertEquals(80.0, ninety[1].e1rm, 0.0001) // 60 kg × 10
        assertEquals(105.0, ninety[2].e1rm, 0.0001) // max(80×5=93.3, 90×5=105); bodyweight and warmup excluded

        // 1M = 30 days back: cutoff day 70. The boundary session is included.
        assertEquals(listOf(70L * DAY, NOW), thirty.map { it.atMillis })
        assertEquals(80.0, thirty[0].e1rm, 0.0001) // 60 kg × 10

        // All = no cutoff, oldest session first, future excluded.
        assertEquals(listOf(5L * DAY, 10L * DAY, 70L * DAY, NOW), all.map { it.atMillis })
        assertEquals(120.0, all[0].e1rm, 0.0001) // 90 kg × 10
    }
}
