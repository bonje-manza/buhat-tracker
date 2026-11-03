package com.buhat.tracker.domain

const val DAY_MS = 86_400_000L

fun e1rm(weightKg: Double, reps: Int): Double? {
    if (weightKg <= 0.0 || reps <= 0) return null
    return weightKg * (1.0 + reps / 30.0)
}

fun prSetIds(history: List<HistorySession>): Set<String> {
    val bestByExercise = HashMap<String, Double>()
    val prs = HashSet<String>()
    history.sortedBy { it.finishedAt }.forEach { session ->
        session.exercises.forEach { exercise ->
            exercise.sets
                .filter { it.setType.countsForProgression }
                .sortedBy { it.completedAt }
                .forEach { set ->
                    val estimated = e1rm(set.weightKg, set.reps) ?: return@forEach
                    val best = bestByExercise[exercise.exerciseId]
                    if (best != null && estimated <= best) {
                        bestByExercise[exercise.exerciseId] = maxOf(best, estimated)
                        return@forEach
                    }
                    bestByExercise[exercise.exerciseId] = estimated
                    prs.add(set.id)
                }
        }
    }
    return prs
}

data class WindowStat(
    val current: Double,
    val previous: Double,
    val deltaPercent: Double?,
)

data class FourWeekStats(
    val workouts: WindowStat,
    val workingSets: WindowStat,
    val volumeKg: WindowStat,
    val newPrs: WindowStat,
)

private data class SessionStats(
    val finishedAt: Long,
    val workingSets: Int,
    val volumeKg: Double,
    val workingSetIds: Set<String>,
    val prSetIds: Set<String>,
)

private fun sessionStat(session: HistorySession, allPrs: Set<String>): SessionStats {
    val workingSession = session.exercises.flatMap { it.sets }.filter { it.setType.countsForProgression }
    val setIds = workingSession.map { it.id }.toSet()
    return SessionStats(
        finishedAt = session.finishedAt,
        workingSets = workingSession.size,
        volumeKg = workingSession.sumOf { it.weightKg * it.reps },
        workingSetIds = setIds,
        prSetIds = allPrs intersect setIds,
    )
}

fun fourWeekStats(history: List<HistorySession>, nowMillis: Long): FourWeekStats {
    val prs = prSetIds(history)
    val stats = history.map { sessionStat(it, prs) }
        .sortedBy { it.finishedAt }

    // Windows include the boundary: current [now-28d, now], prior [now-56d, now-28d).
    val currentStart = nowMillis - 28 * DAY_MS
    val priorStart = nowMillis - 56 * DAY_MS
    val current = stats.filter { it.finishedAt in currentStart..nowMillis }
    val prior = stats.filter { it.finishedAt in priorStart until currentStart }

    fun windowOf(currentCount: Double, previousCount: Double) = WindowStat(
        current = currentCount,
        previous = previousCount,
        deltaPercent = if (previousCount == 0.0) null else (currentCount - previousCount) / previousCount * 100.0,
    )

    return FourWeekStats(
        workouts = windowOf(
            current.count { it.workingSets > 0 }.toDouble(),
            prior.count { it.workingSets > 0 }.toDouble(),
        ),
        workingSets = windowOf(
            current.sumOf { it.workingSets }.toDouble(),
            prior.sumOf { it.workingSets }.toDouble(),
        ),
        volumeKg = windowOf(
            current.sumOf { it.volumeKg },
            prior.sumOf { it.volumeKg },
        ),
        newPrs = windowOf(
            current.sumOf { it.prSetIds.size }.toDouble(),
            prior.sumOf { it.prSetIds.size }.toDouble(),
        ),
    )
}

data class TrendPoint(val atMillis: Long, val e1rm: Double)

data class ExerciseStats(
    val bestE1rm: Double?,
    val heaviestSet: LoggedSetRecord?,
    val sessionCount: Int,
)

fun exerciseStats(history: List<HistorySession>, exerciseId: String): ExerciseStats {
    val sessionSets = history.map { session ->
        session.exercises.filter { it.exerciseId == exerciseId }
            .flatMap { it.sets }.filter { it.setType.countsForProgression }
    }
    val sets = sessionSets.flatten()
    return ExerciseStats(
        bestE1rm = sets.mapNotNull { e1rm(it.weightKg, it.reps) }.maxOrNull(),
        heaviestSet = sets.maxWithOrNull(compareBy<LoggedSetRecord> { it.weightKg }.thenBy { it.reps }),
        sessionCount = sessionSets.count { it.isNotEmpty() },
    )
}

fun e1rmTrend(
    history: List<HistorySession>,
    exerciseId: String,
    rangeDays: Int?,
    nowMillis: Long,
): List<TrendPoint> {
    val cutoff = nowMillis - (rangeDays ?: 0) * DAY_MS
    return history
        .filter { it.finishedAt <= nowMillis && (rangeDays == null || it.finishedAt >= cutoff) }
        .sortedBy { it.finishedAt }
        .mapNotNull { session ->
            val best = session.exercises
                .firstOrNull { it.exerciseId == exerciseId }
                ?.sets
                ?.filter { it.setType.countsForProgression }
                ?.mapNotNull { e1rm(it.weightKg, it.reps) }
                ?.maxOrNull()
                ?: return@mapNotNull null
            TrendPoint(session.finishedAt, best)
        }
}
