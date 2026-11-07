package com.buhat.tracker.data

import androidx.room.withTransaction
import com.buhat.tracker.BuildConfig
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.FinishedWorkout
import com.buhat.tracker.data.local.HistorySetRow
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.ProfileStats
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.Template
import com.buhat.tracker.data.local.TemplateExercise
import com.buhat.tracker.data.local.TemplateExerciseRow
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.ContentValidationException
import com.buhat.tracker.domain.HistoryExercise
import com.buhat.tracker.domain.HistorySession
import com.buhat.tracker.domain.LoggedSetRecord
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.countsForProgression
import com.buhat.tracker.domain.homeWindowStart
import com.buhat.tracker.domain.priorWindowStart
import com.buhat.tracker.domain.renumberWorkingSets
import com.buhat.tracker.domain.restEndsAt
import com.buhat.tracker.domain.restSecondsFor
import com.buhat.tracker.domain.validateExerciseName
import com.buhat.tracker.domain.validateSet
import com.buhat.tracker.domain.validateTemplate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

fun seededId(type: String, name: String): String =
    UUID.nameUUIDFromBytes("$type:$name".toByteArray()).toString()

class ActiveSessionException(val sessionId: String) : Exception("A workout is already active.")

class SessionValidationException(message: String) : Exception(message)

class NoWorkingSetsException : Exception("Log at least one working set before finishing.")

private const val ACTIVE_WORKOUT_BLOCK = "Finish or discard the workout first."


data class TemplateCard(
    val template: Template,
    val exercises: List<TemplateExerciseRow>,
)

data class HomeDashboard(
    val profileStats: ProfileStats,
    val latest: HistorySession?,
    val window: List<HistorySession>,
    val bestBefore: Map<String, Double>,
)

data class ExerciseChoice(
    val exerciseId: String,
    val exerciseName: String,
    val hasSets: Boolean,
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
    val profileStore: ProfileStore,
) {

    // Serialize set writes with finish/discard so a late timer write cannot resurrect a rest.
    private val sessionWriteMutex = Mutex()

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

    fun activeSession(): Flow<WorkoutSession?> = database.dao().observeActiveSession()

    suspend fun session(id: String): WorkoutSession? = database.dao().sessionById(id)

    fun sessionExercises(sessionId: String): Flow<List<SessionExercise>> =
        database.dao().observeSessionExercises(sessionId)

    fun loggedSets(sessionExerciseId: String): Flow<List<LoggedSet>> =
        database.dao().observeSetsFor(sessionExerciseId)

    fun history(): Flow<List<HistorySession>> =
        database.dao().observeFinishedWorkouts().map { workouts -> workouts.map { it.toHistorySession() } }

    suspend fun deleteFinishedSession(sessionId: String) {
        if (database.dao().deleteFinishedSession(sessionId) == 0) {
            throw SessionValidationException("Finished workout not found.")
        }
    }

    fun homeDashboard(nowMillis: Long): Flow<HomeDashboard> = combine(
        database.dao().observeProfileStats(),
        database.dao().observeLatestFinishedWorkout(),
        database.dao().observeHistoryRowsSince(homeWindowStart(nowMillis)),
        database.dao().observeBestE1rmBefore(priorWindowStart(nowMillis)),
    ) { stats, latest, rows, best ->
        HomeDashboard(
            profileStats = stats,
            latest = latest?.toHistorySession(),
            window = rows.toHistorySessions(),
            bestBefore = best.associate { it.exerciseId to it.bestE1rm },
        )
    }.flowOn(Dispatchers.Default)

    /** Every finished session that logged the exercise, oldest first. */
    fun historyForExercise(exerciseId: String): Flow<List<HistorySession>> =
        database.dao().observeHistoryRowsFor(exerciseId).map { it.toHistorySessions() }
            .flowOn(Dispatchers.Default)

    /** The two finished sessions progression can read: latest overall and latest for the template. */
    fun latestHistoryForExercise(exerciseId: String, templateIdSnapshot: String): Flow<List<HistorySession>> =
        database.dao().observeLatestHistoryRowsFor(exerciseId, templateIdSnapshot).map { it.toHistorySessions() }

    fun historyExerciseChoices(): Flow<List<ExerciseChoice>> =
        database.dao().observeHistoryExerciseChoices().map { rows ->
            rows.groupBy { it.exerciseId }.map { (exerciseId, entries) ->
                ExerciseChoice(exerciseId, entries.first().exerciseNameSnapshot, entries.any { it.hasSets })
            }
        }.flowOn(Dispatchers.Default)

    suspend fun startSession(templateId: String): String = sessionWriteMutex.withLock {
        val dao = database.dao()
        dao.activeSession()?.let { throw ActiveSessionException(it.id) }
        val template = dao.templateById(templateId)
            ?: throw SessionValidationException("Template not found.")
        val rows = dao.templateExerciseRowsFor(templateId)
        if (rows.isEmpty()) throw SessionValidationException("Template has no exercises.")

        val sessionId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertSessions(
                listOf(
                    WorkoutSession(
                        id = sessionId,
                        templateIdSnapshot = template.id,
                        templateNameSnapshot = template.name,
                        startedAt = System.currentTimeMillis(),
                        finishedAt = null,
                        status = WorkoutStatus.ACTIVE,
                    ),
                ),
            )
            dao.insertSessionExercises(
                rows.map { row ->
                    SessionExercise(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        exerciseId = row.exerciseId,
                        exerciseNameSnapshot = row.exerciseName,
                        position = row.position,
                        workingSets = row.workingSets,
                        repMin = row.repMin,
                        repMax = row.repMax,
                        targetRir = row.targetRir,
                    )
                },
            )
        }
        sessionId
    }

    suspend fun logSet(
        sessionExerciseId: String,
        setType: SetType,
        weightKg: Double,
        reps: Int,
        rir: Int?,
    ): String = sessionWriteMutex.withLock {
        validateSet(weightKg, reps, rir)?.let { throw SessionValidationException(it) }
        val dao = database.dao()
        if (!dao.sessionExerciseIsActive(sessionExerciseId)) {
            throw SessionValidationException("Workout is not active.")
        }
        val workingIndex = if (setType.countsForProgression) {
            val existing = dao.selectSetsFor(sessionExerciseId)
            (existing.maxOfOrNull { it.workingIndex ?: 0 } ?: 0) + 1
        } else {
            null
        }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val rowIds = dao.insertSets(
            listOf(
                LoggedSet(
                    id = id,
                    sessionExerciseId = sessionExerciseId,
                    setType = setType,
                    workingIndex = workingIndex,
                    weightKg = weightKg,
                    reps = reps,
                    rir = rir,
                    completedAt = now,
                ),
            ),
        )
        if (rowIds.any { it == -1L }) throw SessionValidationException("Could not save the set. Try again.")
        val current = settingsStore.settings.first()
        settingsStore.setRestEndsAt(
            restEndsAt(
                now,
                restSecondsFor(
                    setType,
                    current.restWarmupSeconds,
                    current.restWorkingSeconds,
                    current.restFailureSeconds,
                ),
            ),
        )
        id
    }

    suspend fun clearRest() = sessionWriteMutex.withLock { settingsStore.setRestEndsAt(null) }

    suspend fun completeRest(endsAt: Long) = settingsStore.clearRestIfMatches(endsAt)
    suspend fun updateSet(
        setId: String,
        setType: SetType,
        weightKg: Double,
        reps: Int,
        rir: Int?,
    ) {
        validateSet(weightKg, reps, rir)?.let { throw SessionValidationException(it) }
        val dao = database.dao()
        val existing = dao.setById(setId) ?: return
        database.withTransaction {
            dao.updateSet(
                existing.copy(
                    setType = setType,
                    weightKg = weightKg,
                    reps = reps,
                    rir = rir,
                    workingIndex = if (setType == existing.setType) existing.workingIndex else null,
                ),
            )
            if (setType != existing.setType) renumberPositions(dao, existing.sessionExerciseId)
        }
    }

    suspend fun deleteSet(setId: String) {
        val dao = database.dao()
        val existing = dao.setById(setId) ?: return
        database.withTransaction {
            dao.deleteSetById(setId)
            if (existing.setType.countsForProgression) renumberPositions(dao, existing.sessionExerciseId)
        }
    }

    suspend fun finishSession(sessionId: String) = sessionWriteMutex.withLock {
        val dao = database.dao()
        val session = dao.sessionById(sessionId) ?: return@withLock
        if (dao.workingSetCount(sessionId) == 0) throw NoWorkingSetsException()
        dao.updateSession(
            session.copy(status = WorkoutStatus.FINISHED, finishedAt = System.currentTimeMillis()),
        )
        settingsStore.setRestEndsAt(null)
    }

    suspend fun discardSession(sessionId: String) = sessionWriteMutex.withLock {
        database.dao().deleteSessionById(sessionId)
        settingsStore.setRestEndsAt(null)
    }

    private suspend fun renumberPositions(dao: com.buhat.tracker.data.local.BuhatDao, sessionExerciseId: String) {
        // Clear first so the unique (sessionExerciseId, workingIndex) index never collides mid-update.
        dao.clearWorkingIndices(sessionExerciseId)
        renumberWorkingSets(dao.selectSetsFor(sessionExerciseId).map { it.toRecord() })
            .forEach { dao.updateWorkingIndex(it.id, it.workingIndex) }
    }

    private fun HistorySetRow.toRecord() = LoggedSetRecord(
        id = setId,
        setType = setType,
        workingIndex = workingIndex,
        weightKg = weightKg,
        reps = reps,
        rir = rir,
        completedAt = completedAt,
    )

    private fun FinishedWorkout.toHistorySession() = HistorySession(
        id = session.id,
        templateIdSnapshot = session.templateIdSnapshot,
        finishedAt = requireNotNull(session.finishedAt),
        templateNameSnapshot = session.templateNameSnapshot,
        exercises = exercises.sortedBy { it.exercise.position }.map { row ->
            HistoryExercise(
                exerciseId = row.exercise.exerciseId,
                exerciseNameSnapshot = row.exercise.exerciseNameSnapshot,
                sets = row.sets.sortedBy { it.completedAt }.map { it.toRecord() },
            )
        },
    )

    private fun List<HistorySetRow>.toHistorySessions(): List<HistorySession> =
        groupBy { it.sessionId }.map { (sessionId, rows) ->
            val first = rows.first()
            HistorySession(
                id = sessionId,
                templateIdSnapshot = first.templateIdSnapshot,
                finishedAt = requireNotNull(first.finishedAt),
                templateNameSnapshot = first.templateNameSnapshot,
                exercises = rows.groupBy { it.exerciseId }.map { (exerciseId, sets) ->
                    HistoryExercise(exerciseId, sets.map { it.toRecord() })
                },
            )
        }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        sessionWriteMutex.withLock {
            database.withTransaction {
                val dao = database.dao()
                if (dao.activeSessionCount() > 0) throw BackupException(ACTIVE_WORKOUT_BLOCK)
                encodeBackup(
                    Backup(
                        formatVersion = BACKUP_FORMAT_VERSION,
                        appVersion = BuildConfig.VERSION_NAME,
                        exportedAt = System.currentTimeMillis(),
                        profile = profileStore.profile.first().toBackup(),
                        settings = settingsStore.settings.first().toBackup(),
                        exercises = dao.allExercises().map { it.toBackup() },
                        templates = dao.allTemplates().map { it.toBackup() },
                        templateExercises = dao.allTemplateExercises().map { it.toBackup() },
                        sessions = dao.allSessions().map { it.toBackup() },
                        sessionExercises = dao.allSessionExercises().map { it.toBackup() },
                        sets = dao.allSets().map { it.toBackup() },
                    ),
                )
            }
        }
    }

    suspend fun previewBackup(text: String): BackupSummary = withContext(Dispatchers.IO) {
        if (database.dao().activeSessionCount() > 0) throw BackupException(ACTIVE_WORKOUT_BLOCK)
        decodeBackup(text).summary()
    }

    suspend fun importBackup(text: String) = withContext(Dispatchers.IO) {
        sessionWriteMutex.withLock {
            val dao = database.dao()
            if (dao.activeSessionCount() > 0) throw BackupException(ACTIVE_WORKOUT_BLOCK)
            val backup = decodeBackup(text)
            // Once replacement begins, navigation must not interrupt the post-commit preferences write.
            withContext(NonCancellable) {
                try {
                    database.withTransaction {
                        if (dao.activeSessionCount() > 0) throw BackupException(ACTIVE_WORKOUT_BLOCK)
                        dao.deleteAllSets()
                        dao.deleteAllSessionExercises()
                        dao.deleteAllSessions()
                        dao.deleteAllTemplateExercises()
                        dao.deleteAllTemplates()
                        dao.deleteAllExercises()
                        val inserted = listOf(
                            dao.insertExercises(backup.exercises.map { it.toExercise() }),
                            dao.insertTemplates(backup.templates.map { it.toTemplate() }),
                            dao.insertTemplateExercises(backup.templateExercises.map { it.toTemplateExercise() }),
                            dao.insertSessions(backup.sessions.map { it.toSession() }),
                            dao.insertSessionExercises(backup.sessionExercises.map { it.toSessionExercise() }),
                            dao.insertSets(backup.sets.map { it.toLoggedSet() }),
                        )
                        check(inserted.all { rows -> rows.none { it == -1L } }) { "A backup row could not be inserted." }
                    }
                } catch (e: BackupException) {
                    throw e
                } catch (e: Exception) {
                    throw BackupException("Could not write the backup. Local data is unchanged. Try importing again.")
                }
                try {
                    profileStore.save(backup.profile.toProfile())
                    settingsStore.save(backup.settings.toSettings())
                    settingsStore.markSeeded(SeedContent.VERSION)
                    settingsStore.setRestEndsAt(null)
                } catch (e: Exception) {
                    throw BackupException(
                        "Training data was imported, but profile and settings could not be saved. Import the same file again.",
                    )
                }
            }
        }
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

fun LoggedSet.toRecord() = LoggedSetRecord(
    id = id,
    setType = setType,
    workingIndex = workingIndex,
    weightKg = weightKg,
    reps = reps,
    rir = rir,
    completedAt = completedAt,
)
