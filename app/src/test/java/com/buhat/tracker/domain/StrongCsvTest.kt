package com.buhat.tracker.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongCsvTest {

    private val header =
        "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;Distance (meters);Seconds;Notes;Workout Notes"

    private fun row(
        workout: String = "1",
        date: String = "2026-06-24 08:00:00",
        name: String = "FBEOD",
        duration: String = "3600",
        exercise: String = "Bench Press",
        setOrder: String,
        weight: String = "80",
        reps: String = "5",
        rpe: String = "8",
        distance: String = "",
        seconds: String = "",
        notes: String = "",
        workoutNotes: String = "",
    ): String = listOf(
        workout, date, name, duration, exercise, setOrder, weight, reps, rpe,
        distance, seconds, notes, workoutNotes,
    ).joinToString(";")

    private fun csv(vararg rows: String): String =
        (listOf(header) + rows).joinToString("\r\n")

    private fun parseRow(vararg rows: String): StrongWorkout =
        parseStrongCsv(csv(*rows)).workouts.single()

    @Test
    fun `sanitized export parses thirty three workouts across its date range`() {
        val text = requireNotNull(javaClass.getResource("/strong-export-sanitized.csv")).readText()

        val result = parseStrongCsv(text)

        assertEquals(33, result.workouts.size)
        assertEquals("strong|2026-06-24 08:00:00|FBEOD", result.workouts.first().identity)
        assertTrue(result.workouts.last().identity.startsWith("strong|2026-08-30"))
        result.workouts.forEach { workout ->
            assertEquals(3_600_000L, workout.finishedAt - workout.startedAt)
        }
        assertEquals(0, result.skippedEmptyWorkouts)
    }

    @Test
    fun `rpe table converts half steps half up`() {
        val table = listOf(
            6.0 to 4,
            6.5 to 4,
            7.0 to 3,
            7.5 to 3,
            8.0 to 2,
            8.5 to 2,
            9.0 to 1,
            9.5 to 1,
            10.0 to 0,
        )

        table.forEach { (rpe, rir) -> assertEquals("RPE $rpe", rir, rirFromRpe(rpe)) }
        assertEquals(4, rirFromRpe(5.9))
        assertEquals(0, rirFromRpe(10.1))
    }

    @Test
    fun `rest and note rows never become sets`() {
        val workout = parseRow(
            row(setOrder = "W", weight = "40", reps = "10", rpe = ""),
            row(setOrder = "1"),
            row(setOrder = "Rest Timer", weight = "", reps = "", rpe = "", seconds = "120"),
            row(setOrder = "Note", weight = "", reps = "", rpe = "", notes = "paused reps"),
            row(setOrder = "2"),
            row(setOrder = "F"),
        )

        val exercise = workout.exercises.single()
        assertEquals(
            listOf(SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.FAILURE),
            exercise.sets.map { it.setType },
        )
        assertEquals("paused reps", exercise.note)
    }

    @Test
    fun `failure keeps its recorded reps and counts as working`() {
        val workout = parseRow(
            row(setOrder = "1", reps = "6"),
            row(setOrder = "F", reps = "5", weight = "90"),
        )

        val failure = workout.exercises.single().sets.last()
        assertEquals(SetType.FAILURE, failure.setType)
        assertEquals(5, failure.reps)
        assertEquals(2, failure.workingIndex)
    }

    @Test
    fun `blank weight is zero and blank rpe is null`() {
        val workout = parseRow(row(setOrder = "1", weight = "", rpe = ""))

        val set = workout.exercises.single().sets.single()
        assertEquals(0.0, set.weightKg, 0.0)
        assertNull(set.rir)
    }

    @Test
    fun `repeated exercise name creates separate occurrences`() {
        val workout = parseRow(
            row(exercise = "Bench Press", setOrder = "1"),
            row(exercise = "Barbell Row", setOrder = "1"),
            row(exercise = "Bench Press", setOrder = "2"),
        )

        assertEquals(
            listOf("Bench Press", "Barbell Row", "Bench Press"),
            workout.exercises.map { it.sourceName },
        )
    }

    @Test
    fun `multiple notes join with blank lines and a blank note stays null`() {
        val joined = parseRow(
            row(setOrder = "Note", weight = "", reps = "", rpe = "", notes = "first"),
            row(setOrder = "Note", weight = "", reps = "", rpe = "", notes = "second"),
            row(setOrder = "1"),
        )
        assertEquals("first\n\nsecond", joined.exercises.single().note)

        val blank = parseRow(
            row(setOrder = "Note", weight = "", reps = "", rpe = "", notes = ""),
            row(setOrder = "1"),
        )
        assertNull(blank.exercises.single().note)
    }

    @Test
    fun `workout notes use the first nonblank and reject conflicts`() {
        val same = parseRow(
            row(setOrder = "1", workoutNotes = "heavy day"),
            row(setOrder = "2", workoutNotes = "  heavy day  "),
        )
        assertEquals("heavy day", same.note)

        val error = assertThrows(StrongCsvException::class.java) {
            parseRow(
                row(setOrder = "1", workoutNotes = "heavy day"),
                row(setOrder = "2", workoutNotes = "light day"),
            )
        }
        assertTrue("was '${error.message}'", error.message.orEmpty().contains("heavy day"))
    }

    @Test
    fun `workouts without working sets are skipped and counted`() {
        val result = parseStrongCsv(
            csv(
                row(setOrder = "W", weight = "40", reps = "10", rpe = ""),
                row(setOrder = "Rest Timer", weight = "", reps = "", rpe = ""),
            ),
        )

        assertEquals(0, result.workouts.size)
        assertEquals(1, result.skippedEmptyWorkouts)
    }

    @Test
    fun `duplicate identity inside one file is rejected`() {
        val error = assertThrows(StrongCsvException::class.java) {
            parseStrongCsv(
                csv(
                    row(workout = "1", setOrder = "1"),
                    row(workout = "2", setOrder = "1"),
                ),
            )
        }

        assertTrue("was '${error.message}'", error.message.orEmpty().contains("duplicate"))
    }

    @Test
    fun `unknown set tag distance and missing reps block with row numbers`() {
        val tag = assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "Drop"))
        }
        assertTrue("was '${tag.message}'", tag.message.orEmpty().contains("Row 2"))
        assertTrue("was '${tag.message}'", tag.message.orEmpty().contains("Drop"))

        val distance = assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1", distance = "100"))
        }
        assertTrue("was '${distance.message}'", distance.message.orEmpty().contains("Row 2"))
        assertTrue("was '${distance.message}'", distance.message.orEmpty().contains("100"))

        val reps = assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1", reps = ""))
        }
        assertTrue("was '${reps.message}'", reps.message.orEmpty().contains("Row 2"))
        assertTrue("was '${reps.message}'", reps.message.orEmpty().contains("Reps"))
    }

    @Test
    fun `date name and duration must agree inside one group`() {
        assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1"), row(setOrder = "2", date = "2026-06-25 08:00:00"))
        }
        assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1"), row(setOrder = "2", name = "FB x3"))
        }
        assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1"), row(setOrder = "2", duration = "1800"))
        }
    }

    @Test
    fun `date parses as device local time`() {
        val workout = parseRow(row(setOrder = "1"))

        val local = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 6, 24, 8, 0), local)
    }

    @Test
    fun `quoted semicolon escaped quote and multiline note survive`() {
        val text = "\uFEFF" +
            "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";" +
            "\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\"\r\n" +
            "\"1\";\"2026-06-24 08:00:00\";\"FB; x3\";\"3600\";\"Bench; Press\";\"Note\";\"\";\"\";\"\";\"\";\"\";" +
            "\"He said \"\"go\"\"\nthen rest\";\"Plan \"\"A\"\"; deload\"\r\n" +
            "\"1\";\"2026-06-24 08:00:00\";\"FB; x3\";\"3600\";\"Bench; Press\";\"1\";\"80\";\"5\";\"8\";\"\";\"\";" +
            "\"\";\"Plan \"\"A\"\"; deload\"\r\n"

        val workout = parseStrongCsv(text).workouts.single()

        assertEquals("FB; x3", workout.name)
        assertEquals("Plan \"A\"; deload", workout.note)
        val exercise = workout.exercises.single()
        assertEquals("Bench; Press", exercise.sourceName)
        assertEquals("He said \"go\"\nthen rest", exercise.note)
    }

    @Test
    fun `names that cannot fit a backup block with the row number`() {
        val workoutName = assertThrows(StrongCsvException::class.java) {
            parseRow(row(name = "W".repeat(174), setOrder = "1"))
        }
        assertTrue("was '${workoutName.message}'", workoutName.message.orEmpty().contains("Row 2"))
        assertTrue("was '${workoutName.message}'", workoutName.message.orEmpty().contains("too long"))

        val exerciseName = assertThrows(StrongCsvException::class.java) {
            parseRow(row(exercise = "E".repeat(201), setOrder = "1"))
        }
        assertTrue("was '${exerciseName.message}'", exerciseName.message.orEmpty().contains("Row 2"))
        assertTrue("was '${exerciseName.message}'", exerciseName.message.orEmpty().contains("Exercise Name"))
    }

    @Test
    fun `error report caps rows and truncates values`() {
        val many = (1..25).map { index -> row(workout = index.toString(), setOrder = "Drop") }
        val capped = assertThrows(StrongCsvException::class.java) {
            parseStrongCsv(csv(*many.toTypedArray()))
        }
        assertTrue("was '${capped.message}'", capped.message.orEmpty().contains("And 5 more rows failed."))
        assertTrue("length ${capped.message!!.length}", capped.message!!.length < 10_000)

        val longValue = assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1", distance = "x".repeat(1000)))
        }
        assertTrue("length ${longValue.message!!.length}", longValue.message!!.length < 1_000)
    }

    @Test
    fun `nonpositive start and absurd duration block`() {
        val date = assertThrows(StrongCsvException::class.java) {
            parseRow(row(date = "0001-01-01 00:00:00", setOrder = "1"))
        }
        assertTrue("was '${date.message}'", date.message.orEmpty().contains("Invalid Date"))

        val duration = assertThrows(StrongCsvException::class.java) {
            parseRow(row(setOrder = "1", duration = "9223372036854775807"))
        }
        assertTrue("was '${duration.message}'", duration.message.orEmpty().contains("Duration"))
    }

    @Test
    fun `zero duration workout keeps sets inside start and finish`() {
        val workout = parseRow(
            row(setOrder = "1", duration = "0"),
            row(setOrder = "2", duration = "0"),
        )

        assertEquals(workout.startedAt, workout.finishedAt)
        workout.exercises.single().sets.forEach { set ->
            assertEquals(workout.startedAt, set.completedAt)
        }
    }
}
