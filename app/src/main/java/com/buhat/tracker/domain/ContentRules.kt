package com.buhat.tracker.domain

/** A template row as the editor holds it. `exerciseName` is display-only. */
data class TemplateExerciseDraft(
    val exerciseId: String,
    val exerciseName: String = "",
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int? = null,
)

class ContentValidationException(message: String) : IllegalArgumentException(message)

const val MAX_TEXT_LENGTH = 200

fun validateTextLength(text: String?, field: String): String? =
    if (text != null && text.trim().length > MAX_TEXT_LENGTH) {
        "$field must be $MAX_TEXT_LENGTH characters or fewer."
    } else {
        null
    }

fun validateExerciseName(name: String, takenNames: Collection<String>): String? {
    validateTextLength(name, "Name")?.let { return it }
    if (name.isBlank()) return "Name is required."
    if (name.trim().lowercase() in takenNames) return "That name is already used."
    return null
}

/** Returns the first user-facing problem, or null when the template can be saved. */
fun validateTemplate(
    name: String,
    takenNames: Collection<String>,
    exercises: List<TemplateExerciseDraft>,
): String? {
    validateExerciseName(name, takenNames)?.let { return it }
    if (exercises.isEmpty()) return "Add at least one exercise."
    if (exercises.map { it.exerciseId }.toSet().size != exercises.size) {
        return "Each exercise can appear once."
    }
    exercises.forEach { exercise ->
        if (exercise.workingSets !in 1..10) return "Sets must be 1 to 10."
        if (exercise.repMin !in 1..30 || exercise.repMax !in 1..30) return "Reps must be 1 to 30."
        if (exercise.repMin > exercise.repMax) return "Minimum reps cannot exceed maximum reps."
        if (exercise.targetRir != null && exercise.targetRir !in 0..5) return "RIR must be 0 to 5."
    }
    return null
}
