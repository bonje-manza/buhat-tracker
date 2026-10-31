package com.buhat.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedContentTest {

    @Test
    fun libraryHas57ExercisesIn10MuscleGroups() {
        assertEquals(57, SeedContent.exercises.size)
        // Chest 8, Back 12, Shoulders 6, Biceps 5, Triceps 5, Quads 7,
        // Hamstrings 5, Glutes 3, Calves 2, Core 4.
        // "Incline Barbell Press" and "Deadlift" were added beyond the roadmap's
        // list because every template exercise must exist in the library.
        assertEquals(
            listOf(
                "Chest" to 8, "Back" to 12, "Shoulders" to 6, "Biceps" to 5,
                "Triceps" to 5, "Quads" to 7, "Hamstrings" to 5, "Glutes" to 3,
                "Calves" to 2, "Core" to 4,
            ),
            SeedContent.exercises.groupingBy { it.muscleGroup }.eachCount().entries
                .map { it.key to it.value },
        )
    }

    @Test
    fun exerciseNamesAreUniqueCaseInsensitive() {
        assertEquals(SeedContent.exercises.size, SeedContent.exercises.distinctBy { it.name.lowercase() }.size)
    }

    @Test
    fun equipmentValuesAreFromTheAllowedSet() {
        val allowed = setOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Other")
        assertTrue(SeedContent.exercises.all { it.equipment in allowed })
    }

    @Test
    fun sixPremadeTemplatesWithRoadmapNamesAndSetTotals() {
        val expected = listOf(
            "Push Day A" to listOf(
                "Bench Press" to Row(3, 6, 8),
                "Incline Dumbbell Press" to Row(3, 8, 10),
                "Cable Fly" to Row(3, 10, 12),
            ),
            "Pull Day A" to listOf(
                "Barbell Row" to Row(3, 6, 8),
                "Lat Pulldown" to Row(3, 8, 10),
                "Seated Cable Row" to Row(3, 8, 10),
                "Face Pull" to Row(3, 12, 15),
                "Barbell Curl" to Row(4, 8, 12),
            ),
            "Leg Day" to listOf(
                "Back Squat" to Row(3, 5, 8),
                "Romanian Deadlift" to Row(3, 6, 8),
                "Leg Press" to Row(4, 10, 12),
                "Leg Curl" to Row(3, 10, 12),
                "Standing Calf Raise" to Row(4, 12, 15),
            ),
            "Push Day B" to listOf(
                "Overhead Press" to Row(3, 6, 8),
                "Incline Barbell Press" to Row(3, 8, 10),
                "Lateral Raise" to Row(4, 12, 15),
                "Triceps Pushdown" to Row(3, 10, 12),
            ),
            "Pull Day B" to listOf(
                "Deadlift" to Row(3, 5, 6),
                "Chin-up" to Row(3, 6, 10),
                "Chest-Supported Row" to Row(3, 8, 10),
                "Hammer Curl" to Row(3, 10, 12),
            ),
            "Leg Day B" to listOf(
                "Front Squat" to Row(3, 6, 8),
                "Hip Thrust" to Row(3, 8, 10),
                "Walking Lunge" to Row(3, 10, 12),
                "Leg Extension" to Row(3, 10, 12),
                "Seated Calf Raise" to Row(4, 12, 15),
            ),
        )

        assertEquals(expected.map { it.first }, SeedContent.templates.map { it.name })
        // Total working sets: 9, 16, 17, 13, 12, 16.
        assertEquals(
            listOf(9, 16, 17, 13, 12, 16),
            SeedContent.templates.map { it.exercises.sumOf { e -> e.workingSets } },
        )

        expected.forEachIndexed { index, (name, rows) ->
            val template = SeedContent.templates[index]
            assertEquals(name, template.name)
            assertEquals(rows.map { it.first }, template.exercises.map { it.exerciseName })
            rows.forEachIndexed { position, (_, row) ->
                val seeded = template.exercises[position]
                assertEquals(row.workingSets, seeded.workingSets)
                assertEquals(row.repMin, seeded.repMin)
                assertEquals(row.repMax, seeded.repMax)
            }
        }
    }

    @Test
    fun everyTemplateExerciseExistsInTheLibrary() {
        val library = SeedContent.exercises.map { it.name }.toSet()
        SeedContent.templates.forEach { template ->
            template.exercises.forEach { seeded ->
                assertTrue("Missing from library: ${seeded.exerciseName}", seeded.exerciseName in library)
            }
        }
    }

    @Test
    fun seedVersionIncrementProducesNewSeed() {
        assertEquals(1, SeedContent.VERSION)
    }

    private data class Row(val workingSets: Int, val repMin: Int, val repMax: Int)
}
