package com.buhat.tracker.data

data class SeedExercise(val name: String, val muscleGroup: String, val equipment: String)

data class SeedTemplateExercise(
    val exerciseName: String,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int? = null,
)

data class SeedTemplate(val name: String, val exercises: List<SeedTemplateExercise>)

object SeedContent {
    const val VERSION = 1

    private val GROUPED_EXERCISES = mapOf(
        "Chest" to listOf(
            "Bench Press" to "Barbell",
            "Incline Barbell Press" to "Barbell",
            "Dumbbell Bench Press" to "Dumbbell",
            "Incline Dumbbell Press" to "Dumbbell",
            "Cable Fly" to "Cable",
            "Pec Deck" to "Machine",
            "Push-up" to "Bodyweight",
            "Dip" to "Bodyweight",
        ),
        "Back" to listOf(
            "Barbell Row" to "Barbell",
            "Pendlay Row" to "Barbell",
            "Lat Pulldown" to "Cable",
            "Pull-up" to "Bodyweight",
            "Chin-up" to "Bodyweight",
            "Seated Cable Row" to "Cable",
            "Chest-Supported Row" to "Machine",
            "Single-Arm Dumbbell Row" to "Dumbbell",
            "Face Pull" to "Cable",
            "Straight-Arm Pulldown" to "Cable",
            "Back Extension" to "Bodyweight",
            "Deadlift" to "Barbell",
        ),
        "Shoulders" to listOf(
            "Overhead Press" to "Barbell",
            "Seated Dumbbell Press" to "Dumbbell",
            "Arnold Press" to "Dumbbell",
            "Lateral Raise" to "Dumbbell",
            "Rear Delt Fly" to "Machine",
            "Upright Row" to "Barbell",
        ),
        "Biceps" to listOf(
            "Barbell Curl" to "Barbell",
            "Dumbbell Curl" to "Dumbbell",
            "Hammer Curl" to "Dumbbell",
            "Preacher Curl" to "Machine",
            "Cable Curl" to "Cable",
        ),
        "Triceps" to listOf(
            "Close-Grip Bench Press" to "Barbell",
            "Triceps Pushdown" to "Cable",
            "Overhead Triceps Extension" to "Dumbbell",
            "Skull Crusher" to "Barbell",
            "Bench Dip" to "Bodyweight",
        ),
        "Quads" to listOf(
            "Back Squat" to "Barbell",
            "Front Squat" to "Barbell",
            "Leg Press" to "Machine",
            "Hack Squat" to "Machine",
            "Leg Extension" to "Machine",
            "Walking Lunge" to "Other",
            "Bulgarian Split Squat" to "Dumbbell",
        ),
        "Hamstrings" to listOf(
            "Romanian Deadlift" to "Barbell",
            "Good Morning" to "Barbell",
            "Leg Curl" to "Machine",
            "Nordic Curl" to "Bodyweight",
            "Stiff-Leg Deadlift" to "Barbell",
        ),
        "Glutes" to listOf(
            "Hip Thrust" to "Barbell",
            "Glute Bridge" to "Bodyweight",
            "Cable Kickback" to "Cable",
        ),
        "Calves" to listOf(
            "Standing Calf Raise" to "Machine",
            "Seated Calf Raise" to "Machine",
        ),
        "Core" to listOf(
            "Plank" to "Bodyweight",
            "Hanging Leg Raise" to "Bodyweight",
            "Cable Crunch" to "Cable",
            "Ab Wheel" to "Other",
        ),
    )

    val exercises: List<SeedExercise> = GROUPED_EXERCISES.flatMap { (group, entries) ->
        entries.map { (name, equipment) -> SeedExercise(name, group, equipment) }
    }

    val muscleGroups: List<String> = GROUPED_EXERCISES.keys.toList()

    val equipmentTypes: List<String> = GROUPED_EXERCISES.values
        .flatten()
        .map { it.second }
        .distinct()

    private fun template(name: String, exercises: List<SeedTemplateExercise>) = SeedTemplate(name, exercises)

    val templates: List<SeedTemplate> = listOf(
        template(
            "Push Day A",
            listOf(
                SeedTemplateExercise("Bench Press", 3, 6, 8),
                SeedTemplateExercise("Incline Dumbbell Press", 3, 8, 10),
                SeedTemplateExercise("Cable Fly", 3, 10, 12),
            ),
        ),
        template(
            "Pull Day A",
            listOf(
                SeedTemplateExercise("Barbell Row", 3, 6, 8),
                SeedTemplateExercise("Lat Pulldown", 3, 8, 10),
                SeedTemplateExercise("Seated Cable Row", 3, 8, 10),
                SeedTemplateExercise("Face Pull", 3, 12, 15),
                SeedTemplateExercise("Barbell Curl", 4, 8, 12),
            ),
        ),
        template(
            "Leg Day",
            listOf(
                SeedTemplateExercise("Back Squat", 3, 5, 8),
                SeedTemplateExercise("Romanian Deadlift", 3, 6, 8),
                SeedTemplateExercise("Leg Press", 4, 10, 12),
                SeedTemplateExercise("Leg Curl", 3, 10, 12),
                SeedTemplateExercise("Standing Calf Raise", 4, 12, 15),
            ),
        ),
        template(
            "Push Day B",
            listOf(
                SeedTemplateExercise("Overhead Press", 3, 6, 8),
                SeedTemplateExercise("Incline Barbell Press", 3, 8, 10),
                SeedTemplateExercise("Lateral Raise", 4, 12, 15),
                SeedTemplateExercise("Triceps Pushdown", 3, 10, 12),
            ),
        ),
        template(
            "Pull Day B",
            listOf(
                SeedTemplateExercise("Deadlift", 3, 5, 6),
                SeedTemplateExercise("Chin-up", 3, 6, 10),
                SeedTemplateExercise("Chest-Supported Row", 3, 8, 10),
                SeedTemplateExercise("Hammer Curl", 3, 10, 12),
            ),
        ),
        template(
            "Leg Day B",
            listOf(
                SeedTemplateExercise("Front Squat", 3, 6, 8),
                SeedTemplateExercise("Hip Thrust", 3, 8, 10),
                SeedTemplateExercise("Walking Lunge", 3, 10, 12),
                SeedTemplateExercise("Leg Extension", 3, 10, 12),
                SeedTemplateExercise("Seated Calf Raise", 4, 12, 15),
            ),
        ),
    )
}
