package com.buhat.tracker.domain

const val DEFAULT_RIR = 2

data class Prefill(
    val weightKg: Double?,
    val reps: Int?,
    val rir: Int,
)

/** Raw values for the logging card: the matching source position, else the last set just logged. */
fun prefillFor(source: LoggedSetRecord?, lastSetInSession: LoggedSetRecord?): Prefill {
    val base = source ?: lastSetInSession
    return Prefill(base?.weightKg, base?.reps, base?.rir ?: DEFAULT_RIR)
}

/** Rewrites working indices to 1..N in completion order; warmups stay null. */
fun renumberWorkingSets(sets: List<LoggedSetRecord>): List<LoggedSetRecord> {
    var position = 0
    return sets.sortedBy { it.completedAt }.map { set ->
        if (set.setType.countsForProgression) set.copy(workingIndex = ++position)
        else set.copy(workingIndex = null)
    }
}

fun validateSet(weightKg: Double, reps: Int, rir: Int?): String? = when {
    !weightKg.isFinite() || weightKg < 0.0 || weightKg > 1000.0 -> "Weight must be 0 to 1000 kg."
    reps < 0 || reps > 100 -> "Reps must be 0 to 100."
    rir != null && (rir < 0 || rir > 5) -> "RIR must be 0 to 5."
    else -> null
}

fun formatElapsed(startedAt: Long, now: Long): String {
    val totalSeconds = (now - startedAt).coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
