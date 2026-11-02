package com.buhat.tracker.domain

const val REST_SECONDS_MAX = 600

fun restSecondsFor(
    setType: SetType,
    warmupSeconds: Int,
    workingSeconds: Int,
    failureSeconds: Int,
): Int = when (setType) {
    SetType.WARMUP -> warmupSeconds
    SetType.WORKING -> workingSeconds
    SetType.FAILURE -> failureSeconds
}

/** Null when the type is set to 0: no rest, no alarm, no notification. */
fun restEndsAt(nowMillis: Long, seconds: Int): Long? =
    if (seconds <= 0) null else nowMillis + seconds * 1_000L

fun restRemainingMillis(endsAt: Long, nowMillis: Long): Long =
    (endsAt - nowMillis).coerceAtLeast(0)

fun restDeadlineElapsed(endsAtWall: Long, wallNow: Long, elapsedNow: Long): Long =
    elapsedNow + restRemainingMillis(endsAtWall, wallNow)

/** Ceiling to whole seconds so the last second still reads "0:01" until it is gone. */
fun formatRest(remainingMillis: Long): String =
    formatRestSeconds(((remainingMillis + 999) / 1_000).toInt())

fun formatRestSeconds(totalSeconds: Int): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
