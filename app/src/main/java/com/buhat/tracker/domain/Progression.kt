package com.buhat.tracker.domain

import java.math.BigDecimal

const val WEIGHT_INCREMENT_KG = 2.5

data class SetAtPosition(val set: LoggedSetRecord, val position: Int)

data class ProgressionTarget(
    val weightKg: Double?,
    val reps: Int,
    val message: String,
)

fun numberWorkingSets(sets: List<LoggedSetRecord>): List<SetAtPosition> =
    sets.sortedBy { it.completedAt }
        .filter { it.setType.countsForProgression }
        .mapIndexed { index, set -> SetAtPosition(set, index + 1) }

fun sourceSetForPosition(
    history: List<HistorySession>,
    exerciseId: String,
    position: Int,
    source: PreviousSetSource,
    templateIdSnapshot: String,
): LoggedSetRecord? {
    val ordered = history.sortedByDescending { it.finishedAt }
    val matchingTemplate = ordered.firstOrNull { session ->
        session.templateIdSnapshot == templateIdSnapshot &&
            session.exercises.any { it.exerciseId == exerciseId }
    }
    val session = when (source) {
        PreviousSetSource.SAME_TEMPLATE -> matchingTemplate ?: ordered.firstOrNull { session ->
            session.exercises.any { it.exerciseId == exerciseId }
        }
        PreviousSetSource.ANY_WORKOUT -> ordered.firstOrNull { session ->
            session.exercises.any { it.exerciseId == exerciseId }
        }
    } ?: return null

    val working = session.exercises
        .first { it.exerciseId == exerciseId }
        .sets
        .filter { it.setType.countsForProgression }
    return working.firstOrNull { it.workingIndex == position }
        ?: working.maxByOrNull { it.completedAt }
}

fun nextTarget(source: LoggedSetRecord?, repMin: Int, repMax: Int, unit: WeightUnit = WeightUnit.KG): ProgressionTarget {
    if (source == null || !source.setType.countsForProgression) {
        return ProgressionTarget(null, repMin, "First time. Set your working weight.")
    }

    if (source.weightKg <= 0.0) {
        val reps = minOf(source.reps + 1, repMax)
        return ProgressionTarget(0.0, reps, "Beat ${source.reps} reps.")
    }

    if (source.reps >= repMax) {
        return ProgressionTarget(
            weightKg = source.weightKg + WEIGHT_INCREMENT_KG,
            reps = repMin,
            message = "Top of range reached. Add ${formatWeight(WEIGHT_INCREMENT_KG, unit)}, restart at $repMin.",
        )
    }

    val reps = minOf(source.reps + 1, repMax)
    return ProgressionTarget(
        weightKg = source.weightKg,
        reps = reps,
        message = "Beat ${source.reps} reps at ${formatWeight(source.weightKg, unit)}.",
    )
}

fun formatKg(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
