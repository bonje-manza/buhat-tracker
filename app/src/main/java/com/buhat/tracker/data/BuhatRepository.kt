package com.buhat.tracker.data

import androidx.room.withTransaction
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.ProfileStats
import com.buhat.tracker.data.local.Template
import com.buhat.tracker.data.local.TemplateExercise
import com.buhat.tracker.data.local.TemplateExerciseRow
import com.buhat.tracker.domain.ContentValidationException
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.validateExerciseName
import com.buhat.tracker.domain.validateTemplate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

fun seededId(type: String, name: String): String =
    UUID.nameUUIDFromBytes("$type:$name".toByteArray()).toString()

data class TemplateCard(
    val template: Template,
    val exercises: List<TemplateExerciseRow>,
)

data class TemplateDraft(
    val id: String,
    val name: String,
    val exercises: List<TemplateExerciseDraft>,
)

enum class DeleteExerciseResult { DELETED, ARCHIVED }

class BuhatRepository(
    private val database: BuhatDatabase,
    val settingsStore: SettingsStore,
) {

    fun exercises() = database.dao().observeExercises()

    fun templates() = database.dao().observeTemplates()

    fun templateCards(): Flow<List<TemplateCard>> =
        combine(
            database.dao().observeTemplates(),
            database.dao().observeTemplateExerciseRows(),
        ) { templates, rows ->
            templates.map { template ->
                TemplateCard(template, rows.filter { it.templateId == template.id })
            }
        }

    fun profileStats(): Flow<ProfileStats> = database.dao().observeProfileStats()

    suspend fun exercise(id: String): Exercise? = database.dao().exerciseById(id)

    suspend fun template(id: String): Template? = database.dao().templateById(id)

    suspend fun templateUsesFor(exerciseId: String): Int =
        database.dao().templateExerciseCountForExercise(exerciseId)

    suspend fun loadTemplateDraft(id: String): TemplateDraft? {
        val dao = database.dao()
        val template = dao.templateById(id) ?: return null
        return TemplateDraft(
            id = template.id,
            name = template.name,
            exercises = dao.templateExerciseRowsFor(id).map {
                TemplateExerciseDraft(
                    exerciseId = it.exerciseId,
                    exerciseName = it.exerciseName,
                    workingSets = it.workingSets,
                    repMin = it.repMin,
                    repMax = it.repMax,
                    targetRir = it.targetRir,
                )
            },
        )
    }

    suspend fun saveExercise(id: String?, name: String, muscleGroup: String, equipment: String): String {
        val dao = database.dao()
        val existing = id?.let { dao.exerciseById(it) ?: throw ContentValidationException("Exercise not found.") }
        if (existing != null && !existing.isCustom) {
            throw ContentValidationException("Premade exercises cannot be edited.")
        }
        validateExerciseName(name, dao.exerciseNamesExcept(id ?: ""))?.let {
            throw ContentValidationException(it)
        }
        val trimmed = name.trim()
        if (existing == null) {
            val newId = UUID.randomUUID().toString()
            dao.insertExercises(
                listOf(
                    Exercise(
                        id = newId,
                        name = trimmed,
                        nameLower = trimmed.lowercase(),
                        muscleGroup = muscleGroup,
                        equipment = equipment,
                        isCustom = true,
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            )
            return newId
        }
        dao.updateExercise(
            existing.copy(
                name = trimmed,
                nameLower = trimmed.lowercase(),
                muscleGroup = muscleGroup,
                equipment = equipment,
            ),
        )
        return existing.id
    }

    suspend fun archiveExercise(id: String) {
        val dao = database.dao()
        val exercise = dao.exerciseById(id) ?: return
        if (!exercise.isCustom) throw ContentValidationException("Premade exercises cannot be edited.")
        dao.updateExercise(exercise.copy(archivedAt = System.currentTimeMillis()))
    }

    suspend fun deleteExercise(id: String): DeleteExerciseResult {
        val dao = database.dao()
        val exercise = dao.exerciseById(id) ?: return DeleteExerciseResult.DELETED
        if (!exercise.isCustom) throw ContentValidationException("Premade exercises cannot be edited.")
        return if (dao.templateExerciseCountForExercise(id) > 0) {
            archiveExercise(id)
            DeleteExerciseResult.ARCHIVED
        } else {
            dao.deleteExerciseById(id)
            DeleteExerciseResult.DELETED
        }
    }

    suspend fun saveTemplate(
        id: String?,
        name: String,
        exercises: List<TemplateExerciseDraft>,
    ): String {
        val dao = database.dao()
        val existing = id?.let { dao.templateById(it) ?: throw ContentValidationException("Template not found.") }
        if (existing?.isPremade == true) {
            throw ContentValidationException("Premade templates are read-only.")
        }
        validateTemplate(name, dao.templateNamesExcept(id ?: ""), exercises)?.let {
            throw ContentValidationException(it)
        }
        val trimmed = name.trim()
        val now = System.currentTimeMillis()
        val templateId = id ?: UUID.randomUUID().toString()
        val template = existing?.copy(name = trimmed, nameLower = trimmed.lowercase(), updatedAt = now)
            ?: Template(
                id = templateId,
                name = trimmed,
                nameLower = trimmed.lowercase(),
                isPremade = false,
                createdAt = now,
                updatedAt = now,
            )
        database.withTransaction {
            if (existing == null) {
                dao.insertTemplates(listOf(template))
            } else {
                dao.updateTemplate(template)
            }
            dao.deleteTemplateExercises(templateId)
            dao.insertTemplateExercises(
                exercises.mapIndexed { index, draft ->
                    TemplateExercise(
                        id = UUID.randomUUID().toString(),
                        templateId = templateId,
                        exerciseId = draft.exerciseId,
                        position = index + 1,
                        workingSets = draft.workingSets,
                        repMin = draft.repMin,
                        repMax = draft.repMax,
                        targetRir = draft.targetRir,
                    )
                },
            )
        }
        return templateId
    }

    suspend fun duplicateTemplate(id: String): String {
        val dao = database.dao()
        val source = dao.templateById(id) ?: throw ContentValidationException("Template not found.")
        val taken = dao.templateNamesExcept("").toSet()
        var candidate = "${source.name} (copy)"
        var copyNumber = 2
        while (candidate.lowercase() in taken) {
            candidate = "${source.name} (copy $copyNumber)"
            copyNumber++
        }
        return saveTemplate(
            id = null,
            name = candidate,
            exercises = dao.templateExercisesFor(id).map {
                TemplateExerciseDraft(
                    exerciseId = it.exerciseId,
                    workingSets = it.workingSets,
                    repMin = it.repMin,
                    repMax = it.repMax,
                    targetRir = it.targetRir,
                )
            },
        )
    }

    suspend fun deleteTemplate(id: String) {
        val dao = database.dao()
        val template = dao.templateById(id) ?: return
        if (template.isPremade) throw ContentValidationException("Premade templates are read-only.")
        dao.deleteTemplates(listOf(id))
    }

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
