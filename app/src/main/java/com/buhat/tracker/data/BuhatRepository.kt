package com.buhat.tracker.data

import androidx.room.withTransaction
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.Template
import com.buhat.tracker.data.local.TemplateExercise
import com.buhat.tracker.data.local.WorkoutSession
import java.util.UUID
import kotlinx.coroutines.flow.first

fun seededId(type: String, name: String): String =
    UUID.nameUUIDFromBytes("$type:$name".toByteArray()).toString()

class BuhatRepository(
    private val database: BuhatDatabase,
    val settingsStore: SettingsStore,
) {

    fun exercises() = database.dao().observeExercises()

    fun templates() = database.dao().observeTemplates()

    suspend fun seedIfNeeded() {
        val current = settingsStore.settings.first().seedVersion
        if (current >= SeedContent.VERSION) return

        val now = System.currentTimeMillis()
        val exercises = SeedContent.exercises.map { seed ->
            Exercise(
                id = seededId("exercise", seed.name),
                name = seed.name,
                muscleGroup = seed.muscleGroup,
                equipment = seed.equipment,
                isCustom = false,
                archivedAt = null,
                createdAt = now,
            )
        }
        val templates = SeedContent.templates.map { seed ->
            Template(
                id = seededId("template", seed.name),
                name = seed.name,
                isPremade = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        val templateIds = templates.associateBy { it.name }.mapValues { it.value.id }
        val templateExercises = SeedContent.templates.flatMap { seed ->
            seed.exercises.mapIndexed { index, seeded ->
                TemplateExercise(
                    id = seededId("templateExercise", "${seed.name}:${seeded.exerciseName}"),
                    templateId = templateIds.getValue(seed.name),
                    exerciseId = seededId("exercise", seeded.exerciseName),
                    position = index + 1,
                    workingSets = seeded.workingSets,
                    repMin = seeded.repMin,
                    repMax = seeded.repMax,
                    targetRir = seeded.targetRir,
                )
            }
        }

        val dao = database.dao()
        database.withTransaction {
            dao.insertExercises(exercises)
            dao.insertTemplates(templates)
            dao.insertTemplateExercises(templateExercises)
        }
        settingsStore.markSeeded(SeedContent.VERSION)
    }
}
