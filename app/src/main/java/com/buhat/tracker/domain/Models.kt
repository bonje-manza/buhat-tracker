package com.buhat.tracker.domain

enum class SetType { WARMUP, WORKING, FAILURE }

enum class SetSide { LEFT, RIGHT }

enum class PreviousSetSource { ANY_WORKOUT, SAME_TEMPLATE }

val SetType.countsForProgression: Boolean
    get() = this != SetType.WARMUP

data class LoggedSetRecord(
    val id: String,
    val setType: SetType,
    val workingIndex: Int?,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val completedAt: Long,
    val side: SetSide? = null,
)

data class HistoryExercise(
    val exerciseId: String,
    val sets: List<LoggedSetRecord>,
    val exerciseNameSnapshot: String = exerciseId,
    val note: String? = null,
    val replacesSessionExerciseId: String? = null,
)

data class HistorySession(
    val id: String,
    val templateIdSnapshot: String,
    val finishedAt: Long,
    val exercises: List<HistoryExercise>,
    val templateNameSnapshot: String = templateIdSnapshot,
    val note: String? = null,
    val importKey: String? = null,
)
