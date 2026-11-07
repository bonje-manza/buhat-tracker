package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    private fun set(
        id: String,
        type: SetType = SetType.WORKING,
        index: Int? = 1,
        weight: Double = 80.0,
        reps: Int = 10,
        at: Long = 0L,
    ) = LoggedSetRecord(
        id = id,
        setType = type,
        workingIndex = index,
        weightKg = weight,
        reps = reps,
        rir = 2,
        completedAt = at,
    )

    private fun session(
        id: String,
        template: String = "template-a",
        finishedAt: Long,
        sets: List<LoggedSetRecord>,
        exerciseId: String = "squat",
    ) = HistorySession(
        id = id,
        templateIdSnapshot = template,
        finishedAt = finishedAt,
        exercises = listOf(HistoryExercise(exerciseId, sets)),
    )

    @Test
    fun warmupsAreSkippedAndFailureCountsWhenNumberingWorkingSets() {
        val sets = listOf(
            set("warmup", SetType.WARMUP, index = null, at = 1),
            set("working", SetType.WORKING, index = 1, at = 2),
            set("failure", SetType.FAILURE, index = 2, at = 3),
            set("warmup-2", SetType.WARMUP, index = null, at = 4),
        )

        val numbered = numberWorkingSets(sets)

        assertEquals(listOf("working", "failure"), numbered.map { it.set.id })
        assertEquals(listOf(1, 2), numbered.map { it.position })
    }

    @Test
    fun perPositionTargetsFollowDoubleProgression() {
        val source = session(
            id = "previous",
            finishedAt = 100,
            sets = listOf(
                set("p1", index = 1, reps = 10, at = 1),
                set("p2", index = 2, reps = 9, at = 2),
                set("p3", index = 3, reps = 8, at = 3),
            ),
        )

        val targets = (1..3).map { position ->
            nextTarget(
                sourceSetForPosition(listOf(source), "squat", position, PreviousSetSource.ANY_WORKOUT, ""),
                repMin = 6,
                repMax = 10,
            )
        }

        assertEquals(82.5, targets[0].weightKg!!, 0.0)
        assertEquals(6, targets[0].reps)
        assertEquals(80.0, targets[1].weightKg!!, 0.0)
        assertEquals(10, targets[1].reps)
        assertEquals(80.0, targets[2].weightKg!!, 0.0)
        assertEquals(9, targets[2].reps)
    }

    @Test
    fun missingPositionFallsBackToLastWorkingSet() {
        val source = session(
            id = "previous",
            finishedAt = 100,
            sets = listOf(
                set("p1", index = 1, reps = 9, at = 1),
                set("p2", index = 2, reps = 8, at = 2),
            ),
        )

        val picked = sourceSetForPosition(
            listOf(source), "squat", position = 3, PreviousSetSource.ANY_WORKOUT, "",
        )

        assertEquals("p2", picked!!.id)
    }

    @Test
    fun sameTemplatePrefersTheMatchingTemplate() {
        val anyWorkout = session(
            id = "newer", template = "template-b", finishedAt = 200,
            sets = listOf(set("b1", weight = 100.0, reps = 5, at = 1)),
        )
        val sameTemplate = session(
            id = "older", template = "template-a", finishedAt = 100,
            sets = listOf(set("a1", weight = 80.0, reps = 5, at = 1)),
        )

        val any = sourceSetForPosition(
            listOf(anyWorkout, sameTemplate), "squat", 1, PreviousSetSource.ANY_WORKOUT, "template-a",
        )
        val same = sourceSetForPosition(
            listOf(anyWorkout, sameTemplate), "squat", 1, PreviousSetSource.SAME_TEMPLATE, "template-a",
        )

        assertEquals("b1", any!!.id)
        assertEquals("a1", same!!.id)
    }

    @Test
    fun sameTemplateFallsBackToAnyWorkout() {
        val only = session(
            id = "other", template = "template-b", finishedAt = 200,
            sets = listOf(set("b1", weight = 100.0, reps = 5, at = 1)),
        )

        val picked = sourceSetForPosition(
            listOf(only), "squat", 1, PreviousSetSource.SAME_TEMPLATE, "template-a",
        )

        assertEquals("b1", picked!!.id)
    }

    @Test
    fun newerSessionWinsRegardlessOfInputOrder() {
        val older = session(
            id = "older", finishedAt = 100,
            sets = listOf(set("old", weight = 80.0, at = 1)),
        )
        val newer = session(
            id = "newer", finishedAt = 200,
            sets = listOf(set("new", weight = 90.0, at = 1)),
        )

        val picked = sourceSetForPosition(
            listOf(older, newer), "squat", 1, PreviousSetSource.ANY_WORKOUT, "",
        )

        assertEquals("new", picked!!.id)
    }

    @Test
    fun noHistoryReturnsFirstTimeTarget() {
        val target = nextTarget(null, repMin = 6, repMax = 10)

        assertNull(target.weightKg)
        assertEquals(6, target.reps)
        assertEquals("First time. Set your working weight.", target.message)
    }

    @Test
    fun sourceWithOnlyWarmupsIsFirstTime() {
        val source = session(
            id = "previous", finishedAt = 100,
            sets = listOf(set("w", SetType.WARMUP, index = null, at = 1)),
        )

        val picked = sourceSetForPosition(
            listOf(source), "squat", 1, PreviousSetSource.ANY_WORKOUT, "",
        )

        assertNull(picked)
    }

    @Test
    fun bodyweightNeverAddsWeight() {
        val belowTop = nextTarget(set("b", weight = 0.0, reps = 8), repMin = 6, repMax = 10)
        val atTop = nextTarget(set("c", weight = 0.0, reps = 10), repMin = 6, repMax = 10)

        assertEquals(0.0, belowTop.weightKg!!, 0.0)
        assertEquals(9, belowTop.reps)
        assertEquals("Beat 8 reps.", belowTop.message)
        assertEquals(0.0, atTop.weightKg!!, 0.0)
        assertEquals(10, atTop.reps)
    }

    @Test
    fun topOfRangeAddsWeightAndRestartsAtRepMin() {
        val target = nextTarget(set("top", weight = 80.0, reps = 10), repMin = 6, repMax = 10)

        assertEquals(82.5, target.weightKg!!, 0.0)
        assertEquals(6, target.reps)
        assertEquals("Top of range reached. Add 2.5 kg, restart at 6.", target.message)
    }

    @Test
    fun belowTopOfRangeRepeatsWeightAndAddsARep() {
        val target = nextTarget(set("mid", weight = 77.5, reps = 9), repMin = 6, repMax = 10)

        assertEquals(77.5, target.weightKg!!, 0.0)
        assertEquals(10, target.reps)
        assertEquals("Beat 9 reps at 77.5 kg.", target.message)
    }

    @Test
    fun targetMessageReferencesTheSetBeingBeaten() {
        val target = nextTarget(set("mid", weight = 70.0, reps = 8), repMin = 6, repMax = 10)

        assertEquals(70.0, target.weightKg!!, 0.0)
        assertEquals(9, target.reps)
        assertEquals("Beat 8 reps at 70 kg.", target.message)
    }

    @Test
    fun failureSetIsAValidSource() {
        val source = set("f", SetType.FAILURE, weight = 80.0, reps = 7, at = 1)

        val target = nextTarget(source, repMin = 6, repMax = 10)

        assertEquals(80.0, target.weightKg!!, 0.0)
        assertEquals(8, target.reps)
    }

    @Test
    fun warmupIsNotAValidSource() {
        val source = set("w", SetType.WARMUP, index = null, weight = 80.0, reps = 10, at = 1)

        val target = nextTarget(source, repMin = 6, repMax = 10)

        assertNull(target.weightKg)
        assertEquals(6, target.reps)
    }
}
