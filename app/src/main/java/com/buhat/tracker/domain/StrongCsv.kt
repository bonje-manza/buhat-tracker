package com.buhat.tracker.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.math.floor

const val STRONG_TEMPLATE_ID = "strong-import"

private const val MAX_REPORTED_ERRORS = 20
private const val MAX_ERROR_LENGTH = 300

class StrongCsvException(messages: List<String>) : Exception(
    buildString {
        messages.take(MAX_REPORTED_ERRORS).forEach { message ->
            if (isNotEmpty()) append('\n')
            append(message.take(MAX_ERROR_LENGTH))
        }
        val remaining = messages.size - MAX_REPORTED_ERRORS
        if (remaining > 0) append("\nAnd $remaining more rows failed.")
    },
)

data class StrongSet(
    val setType: SetType,
    val workingIndex: Int?,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val completedAt: Long,
)

data class StrongExercise(
    val sourceName: String,
    val note: String?,
    val sets: List<StrongSet>,
)

data class StrongWorkout(
    val identity: String,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long,
    val note: String?,
    val exercises: List<StrongExercise>,
)

data class StrongCsvImport(
    val workouts: List<StrongWorkout>,
    val skippedEmptyWorkouts: Int,
)

private val STRONG_COLUMNS = listOf(
    "Workout #", "Date", "Workout Name", "Duration (sec)", "Exercise Name",
    "Set Order", "Weight (kg)", "Reps", "RPE", "Distance (meters)",
    "Seconds", "Notes", "Workout Notes",
)

private val STRONG_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

fun strongIdentity(rawDateTime: String, name: String): String =
    "strong|${rawDateTime.trim()}|${name.trim()}"

fun rirFromRpe(rpe: Double): Int = floor((10.0 - rpe) + 0.5).toInt().coerceIn(0, 5)

fun parseStrongCsv(text: String): StrongCsvImport {
    val rows = try {
        parseCsv(text)
    } catch (e: CsvException) {
        throw StrongCsvException(listOf(e.message.orEmpty()))
    }
    if (rows.isEmpty()) throw StrongCsvException(listOf("The file is empty."))

    val header = rows.first().fields.map { it.trim() }
    val column = mutableMapOf<String, Int>()
    val headerErrors = mutableListOf<String>()
    header.forEachIndexed { index, name ->
        if (column.put(name, index) != null) headerErrors += "Header column \"$name\" appears twice."
    }
    STRONG_COLUMNS.forEach { name ->
        if (name !in column) headerErrors += "Missing header column \"$name\"."
    }
    if (headerErrors.isNotEmpty()) throw StrongCsvException(headerErrors)

    val errors = mutableListOf<String>()
    val groups = LinkedHashMap<String, MutableList<CsvRow>>()
    rows.drop(1).forEach { row ->
        if (row.fields.size != header.size) {
            errors += "Row ${row.line}: expected ${header.size} values but found ${row.fields.size}."
            return@forEach
        }
        val number = row.cell(column, "Workout #")
        if (number.isEmpty()) {
            errors += "Row ${row.line}: Workout # is blank."
            return@forEach
        }
        groups.getOrPut(number) { mutableListOf() } += row
    }

    val workouts = mutableListOf<StrongWorkout>()
    val seen = mutableSetOf<String>()
    var skipped = 0
    groups.forEach { (_, groupRows) ->
        val workout = parseGroup(groupRows, column, errors) ?: return@forEach
        if (!seen.add(workout.identity)) {
            errors += "The file contains a duplicate workout identity \"${workout.identity}\"."
            return@forEach
        }
        val trained = workout.exercises.any { exercise ->
            exercise.sets.any { it.setType != SetType.WARMUP }
        }
        if (trained) workouts += workout else skipped++
    }
    if (errors.isNotEmpty()) throw StrongCsvException(errors)
    return StrongCsvImport(workouts, skipped)
}

private fun parseGroup(
    rows: List<CsvRow>,
    column: Map<String, Int>,
    errors: MutableList<String>,
): StrongWorkout? {
    val first = rows.first()
    val rawDate = first.cell(column, "Date")
    val name = first.cell(column, "Workout Name")
    val rawDuration = first.cell(column, "Duration (sec)")
    val workoutNotes = linkedSetOf<String>()

    for (row in rows) {
        val date = row.cell(column, "Date")
        if (date != rawDate) {
            errors += "Row ${row.line}: Date \"$date\" does not match \"$rawDate\" in the same workout."
            return null
        }
        val rowName = row.cell(column, "Workout Name")
        if (rowName != name) {
            errors += "Row ${row.line}: Workout Name \"$rowName\" does not match \"$name\" in the same workout."
            return null
        }
        val duration = row.cell(column, "Duration (sec)")
        if (duration != rawDuration) {
            errors += "Row ${row.line}: Duration (sec) \"$duration\" does not match \"$rawDuration\" in the same workout."
            return null
        }
        val workoutNote = row.cell(column, "Workout Notes")
        if (workoutNote.isNotEmpty()) workoutNotes += workoutNote
    }
    if (rawDate.isEmpty()) {
        errors += "Row ${first.line}: Date is blank."
        return null
    }
    if (name.isEmpty()) {
        errors += "Row ${first.line}: Workout Name is blank."
        return null
    }
    if (strongIdentity(rawDate, name).length > MAX_TEXT_LENGTH) {
        errors += "Row ${first.line}: Workout Name is too long to store."
        return null
    }
    if (workoutNotes.size > 1) {
        errors += "Row ${first.line}: Workout Notes conflict inside one workout: ${workoutNotes.joinToString(" | ")}."
        return null
    }
    val startedAt = try {
        LocalDateTime.parse(rawDate, STRONG_DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        errors += "Row ${first.line}: Invalid Date \"$rawDate\"."
        return null
    }
    if (startedAt <= 0) {
        errors += "Row ${first.line}: Invalid Date \"$rawDate\"."
        return null
    }
    val durationSeconds = rawDuration.toLongOrNull()
    if (durationSeconds == null || durationSeconds !in 0..86_400) {
        errors += "Row ${first.line}: Invalid Duration (sec) \"$rawDuration\"."
        return null
    }
    val span = durationSeconds * 1000

    class Occurrence(val sourceName: String) {
        val notes = mutableListOf<String>()
        val sets = mutableListOf<StrongSet>()
        var workingCount = 0
    }

    val occurrences = mutableListOf<Occurrence>()
    var current: Occurrence? = null
    var lastExercise: String? = null

    for (row in rows) {
        val order = row.cell(column, "Set Order")
        val distance = row.cell(column, "Distance (meters)")
        if (distance.isNotEmpty()) {
            errors += "Row ${row.line}: Distance \"$distance\" is not supported."
            return null
        }
        if (order == "Rest Timer") continue

        val exerciseName = row.cell(column, "Exercise Name")
        if (exerciseName.isEmpty()) {
            errors += "Row ${row.line}: Exercise Name is blank."
            return null
        }
        if (exerciseName.length > MAX_TEXT_LENGTH) {
            errors += "Row ${row.line}: Exercise Name is longer than $MAX_TEXT_LENGTH characters."
            return null
        }
        if (exerciseName != lastExercise) {
            current = Occurrence(exerciseName)
            occurrences += current
            lastExercise = exerciseName
        }
        val occurrence = current ?: return null

        when (order) {
            "Note" -> {
                val note = row.cell(column, "Notes")
                if (note.isNotEmpty()) occurrence.notes += note
            }
            "W" -> {
                occurrence.sets += buildSet(row, column, SetType.WARMUP, null, errors) ?: return null
            }
            "F" -> {
                occurrence.workingCount++
                occurrence.sets += buildSet(
                    row, column, SetType.FAILURE, occurrence.workingCount, errors,
                ) ?: return null
            }
            else -> {
                val number = order.toIntOrNull()
                if (number == null || number < 1) {
                    errors += "Row ${row.line}: Unsupported Set Order \"$order\"."
                    return null
                }
                occurrence.workingCount++
                occurrence.sets += buildSet(
                    row, column, SetType.WORKING, occurrence.workingCount, errors,
                ) ?: return null
            }
        }
    }

    val setCount = occurrences.sumOf { it.sets.size }
    var position = 0
    val exercises = occurrences.mapNotNull { occurrence ->
        if (occurrence.sets.isEmpty()) return@mapNotNull null
        val sets = occurrence.sets.map { set ->
            val completedAt = if (span == 0L) startedAt else startedAt + span * (position + 1) / (setCount + 1)
            position++
            set.copy(completedAt = completedAt)
        }
        val note = occurrence.notes.joinToString("\n\n").takeIf { it.isNotEmpty() }?.take(MAX_NOTE_LENGTH)
        StrongExercise(occurrence.sourceName, note, sets)
    }

    return StrongWorkout(
        identity = strongIdentity(rawDate, name),
        name = name,
        startedAt = startedAt,
        finishedAt = startedAt + span,
        note = workoutNotes.firstOrNull()?.take(MAX_NOTE_LENGTH),
        exercises = exercises,
    )
}

private fun buildSet(
    row: CsvRow,
    column: Map<String, Int>,
    setType: SetType,
    workingIndex: Int?,
    errors: MutableList<String>,
): StrongSet? {
    val weightText = row.cell(column, "Weight (kg)")
    val weight = if (weightText.isEmpty()) 0.0 else weightText.toDoubleOrNull()
    if (weight == null || !weight.isFinite() || weight < 0.0 || weight > 1000.0) {
        errors += "Row ${row.line}: Invalid Weight (kg) \"$weightText\"."
        return null
    }
    val repsText = row.cell(column, "Reps")
    val reps = repsText.toIntOrNull()
    if (reps == null || reps < 0 || reps > 100) {
        errors += "Row ${row.line}: Missing or invalid Reps \"$repsText\"."
        return null
    }
    val rpeText = row.cell(column, "RPE")
    val rir = if (rpeText.isEmpty()) {
        null
    } else {
        val rpe = rpeText.toDoubleOrNull()
        if (rpe == null || !rpe.isFinite() || rpe < 0.0 || rpe > 10.0) {
            errors += "Row ${row.line}: Invalid RPE \"$rpeText\"."
            return null
        }
        rirFromRpe(rpe)
    }
    return StrongSet(setType, workingIndex, weight, reps, rir, 0L)
}

private fun CsvRow.cell(column: Map<String, Int>, name: String): String =
    fields[column.getValue(name)].trim()
