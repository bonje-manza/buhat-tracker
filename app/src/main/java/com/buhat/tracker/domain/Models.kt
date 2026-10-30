package com.buhat.tracker.domain

enum class SetType { WARMUP, WORKING, FAILURE }

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
)

data class HistoryExercise(
    val exerciseId: String,
    val sets: List<LoggedSetRecord>,
)

data class HistorySession(
    val id: String,
    val templateIdSnapshot: String,
    val finishedAt: Long,
    val exercises: List<HistoryExercise>,
)
