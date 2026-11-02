package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {

    @Test
    fun eachSetTypeUsesItsConfiguredDefault() {
        assertEquals(60, restSecondsFor(SetType.WARMUP, 60, 120, 180))
        assertEquals(120, restSecondsFor(SetType.WORKING, 60, 120, 180))
        assertEquals(180, restSecondsFor(SetType.FAILURE, 60, 120, 180))
    }

    @Test
    fun aZeroRestDurationStartsNoTimer() {
        assertNull(restEndsAt(nowMillis = 1_000L, seconds = 0))
        assertEquals(91_000L, restEndsAt(nowMillis = 1_000L, seconds = 90))
    }

    @Test
    fun remainingTimeNeverGoesNegative() {
        assertEquals(90_000L, restRemainingMillis(endsAt = 91_000L, nowMillis = 1_000L))
        assertEquals(0L, restRemainingMillis(endsAt = 91_000L, nowMillis = 92_000L))
    }

    @Test
    fun wallClockChangesDoNotChangeAnAlreadyStartedCountdown() {
        val elapsedDeadline = restDeadlineElapsed(
            endsAtWall = 61_000L,
            wallNow = 1_000L,
            elapsedNow = 5_000L,
        )

        // Later wall-clock adjustment has no effect on the elapsed-realtime deadline.
        assertEquals(65_000L, elapsedDeadline)
        assertEquals(59_000L, restRemainingMillis(elapsedDeadline, nowMillis = 6_000L))
    }

    @Test
    fun countdownRoundsUpToWholeSeconds() {
        assertEquals("2:00", formatRest(120_000))
        assertEquals("1:01", formatRest(60_100))
        assertEquals("1:00", formatRest(59_999))
        assertEquals("0:00", formatRest(0))
    }

    @Test
    fun secondsFormatAsMinutesAndSeconds() {
        assertEquals("0:00", formatRestSeconds(0))
        assertEquals("0:59", formatRestSeconds(59))
        assertEquals("1:00", formatRestSeconds(60))
        assertEquals("10:00", formatRestSeconds(600))
    }
}
