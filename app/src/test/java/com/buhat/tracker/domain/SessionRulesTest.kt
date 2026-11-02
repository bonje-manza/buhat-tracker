package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRulesTest {

    private fun set(
        id: String,
        type: SetType = SetType.WORKING,
        index: Int? = 1,
        weight: Double = 80.0,
        reps: Int = 10,
        rir: Int? = 2,
        at: Long = 0L,
    ) = LoggedSetRecord(
        id = id,
        setType = type,
        workingIndex = index,
        weightKg = weight,
        reps = reps,
        rir = rir,
        completedAt = at,
    )

    @Test
    fun prefillUsesTheRawSourceValuesForThePosition() {
        val source = set("source", weight = 80.0, reps = 10, rir = 2)
        val latest = set("latest", weight = 100.0, reps = 5, rir = 1)

        val prefill = prefillFor(source, latest)

        assertEquals(80.0, prefill.weightKg!!, 0.0)
        assertEquals(10, prefill.reps)
        assertEquals(2, prefill.rir)
    }

    @Test
    fun prefillFallsBackToTheLastSetInTheCurrentSession() {
        val latest = set("latest", type = SetType.WARMUP, weight = 40.0, reps = 12, rir = 4)

        val prefill = prefillFor(null, latest)

        assertEquals(40.0, prefill.weightKg!!, 0.0)
        assertEquals(12, prefill.reps)
        assertEquals(4, prefill.rir)
    }

    @Test
    fun prefillWithNoHistoryLeavesWeightAndRepsBlankAndDefaultsRir() {
        val prefill = prefillFor(null, null)

        assertNull(prefill.weightKg)
        assertNull(prefill.reps)
        assertEquals(2, prefill.rir)
    }

    @Test
    fun prefillDefaultsRirWhenTheSourceHasNone() {
        val source = set("source", weight = 60.0, reps = 8, rir = null)

        val prefill = prefillFor(source, null)

        assertEquals(60.0, prefill.weightKg!!, 0.0)
        assertEquals(2, prefill.rir)
    }

    @Test
    fun renumberingKeepsWarmupsNullAndNumbersWorkingAndFailureSetsInCompletionOrder() {
        // Stored indices are stale on purpose: 7 and 9, listed out of order.
        val failure = set("failure", type = SetType.FAILURE, index = 9, at = 30)
        val warmup = set("warmup", type = SetType.WARMUP, index = null, at = 10)
        val working = set("working", type = SetType.WORKING, index = 7, at = 20)

        val renumbered = renumberWorkingSets(listOf(failure, warmup, working))

        assertEquals(listOf("warmup", "working", "failure"), renumbered.map { it.id })
        assertEquals(listOf(null, 1, 2), renumbered.map { it.workingIndex })
    }

    @Test
    fun validationRejectsOutOfRangeSetFields() {
        assertNull(validateSet(weightKg = 1000.0, reps = 100, rir = 5))
        assertEquals("Weight must be 0 to 1000 kg.", validateSet(weightKg = 1000.5, reps = 8, rir = 2))
        assertEquals("Weight must be 0 to 1000 kg.", validateSet(weightKg = -1.0, reps = 8, rir = 2))
        assertEquals("Reps must be 0 to 100.", validateSet(weightKg = 80.0, reps = 101, rir = 2))
        assertEquals("RIR must be 0 to 5.", validateSet(weightKg = 80.0, reps = 8, rir = 6))
    }

    @Test
    fun elapsedShowsMinutesThenHours() {
        assertEquals("0:00", formatElapsed(startedAt = 1_000, now = 1_000))
        assertEquals(
            "1:05",
            formatElapsed(startedAt = 0, now = 65_000),
        )
        assertEquals(
            "1:02:05",
            formatElapsed(startedAt = 0, now = 3_725_000),
        )
        assertEquals("0:00", formatElapsed(startedAt = 5_000, now = 4_000))
    }
}
