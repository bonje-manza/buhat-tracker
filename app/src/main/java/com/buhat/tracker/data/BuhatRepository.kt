package com.buhat.tracker.data

import androidx.room.withTransaction
import com.buhat.tracker.BuildConfig
import com.buhat.tracker.data.local.BuhatDao
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
import com.buhat.tracker.domain.MAX_NOTE_LENGTH
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.STRONG_TEMPLATE_ID
import com.buhat.tracker.domain.SetSide
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.StrongCsvException
import com.buhat.tracker.domain.StrongWorkout
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.countsForProgression
import com.buhat.tracker.domain.effectiveWorkingRest
import com.buhat.tracker.domain.homeWindowStart
import com.buhat.tracker.domain.parseStrongCsv
import com.buhat.tracker.domain.priorWindowStart
import com.buhat.tracker.domain.renumberWorkingSets
import com.buhat.tracker.domain.restEndsAt
import com.buhat.tracker.domain.restSecondsFor
import com.buhat.tracker.domain.sideKey
import com.buhat.tracker.domain.validateExerciseName
import com.buhat.tracker.domain.validatePrescription
import com.buhat.tracker.domain.validateSet
import com.buhat.tracker.domain.validateTemplate
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    val exercises: List<Exercise>,
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

data class StrongSource(val name: String, val matchedExerciseId: String?)

data class StrongPreview(
    val newWorkouts: Int,
    val skippedDuplicates: Int,
    val skippedEmptyWorkouts: Int,
    val firstStartedAt: Long?,
    val lastStartedAt: Long?,
    val warmupSets: Int,
    val workingSets: Int,
    val failureSets: Int,
    val sources: List<StrongSource>,
    val exercises: List<Exercise>,
)

data class StrongMapping(
    val sourceName: String,
    val existingExerciseId: String? = null,
    val newExerciseName: String? = null,
    val muscleGroup: String? = null,
    val equipment: String? = null,
)

data class StrongImportResult(val workouts: Int, val skippedDuplicates: Int, val sets: Int)

class BuhatRepository(
    private val database: BuhatDatabase,
    val settingsStore: SettingsStore,
    val profileStore: ProfileStore,
) {

    // Serialize set writes with finish/discard so a late timer write cannot resurrect a rest.
    private val sessionWriteMutex = Mutex()

    fun exercises() = database.dao().observeExercises()

    fun observeExercise(id: String): Flow<Exercise?> = database.dao().observeExercise(id)

    fun allExercises(): Flow<List<Exercise>> = database.dao().observeAllExercises()

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

    suspend fun saveExercisePreferences(
        exerciseId: String,
        unitOverride: String?,
        restWorkingSecondsOverride: Int?,
        note: String?,
        isUnilateral: Boolean,
    ) {
        if (unitOverride != null && WeightUnit.entries.none { it.name == unitOverride }) {
            throw ContentValidationException("Unit must be KG or LB.")
        }
        if (restWorkingSecondsOverride != null && restWorkingSecondsOverride !in 0..600) {
            throw ContentValidationException("Rest must be 0 to 600 seconds.")
        }
        if (note != null && note.length > MAX_NOTE_LENGTH) {
            throw ContentValidationException("Note must be $MAX_NOTE_LENGTH characters or fewer.")
        }
        if (database.dao().updateExercisePreferences(
                exerciseId, unitOverride, restWorkingSecondsOverride, note?.takeUnless { it.isBlank() }, isUnilateral,
            ) == 0) {
            throw ContentValidationException("Exercise not found.")
        }
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
        return saveTemplate(
            id = null,
            name = uniqueCopyName(dao, source.name),
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

    suspend fun reorderSessionExercises(sessionId: String, orderedIds: List<String>) = sessionWriteMutex.withLock {
        database.withTransaction {
            val dao = database.dao()
            if (dao.sessionById(sessionId)?.status != WorkoutStatus.ACTIVE) {
                throw SessionValidationException("Workout is not active.")
            }
            val exercises = dao.sessionExercisesFor(sessionId)
            if (orderedIds.size != exercises.size || orderedIds.toSet() != exercises.map { it.id }.toSet()) {
                throw SessionValidationException("Order must include each workout exercise exactly once.")
            }
            // Negative positions avoid collisions with the unique (sessionId, position) index.
            dao.negateSessionExercisePositions(sessionId)
            orderedIds.forEachIndexed { index, id -> dao.updateSessionExercisePosition(id, index + 1) }
            dao.markSessionStructureDirty(sessionId)
        }
    }

    suspend fun addSessionExercise(
        sessionId: String,
        exerciseId: String,
        workingSets: Int,
        repMin: Int,
        repMax: Int,
        targetRir: Int?,
    ): String = sessionWriteMutex.withLock {
        database.withTransaction {
            val dao = database.dao()
            requireActiveSession(dao, sessionId)
            validatePrescription(workingSets, repMin, repMax, targetRir)
                ?.let { throw SessionValidationException(it) }
            val rows = dao.sessionExercisesFor(sessionId)
            if (rows.any { it.exerciseId == exerciseId }) {
                throw SessionValidationException("That exercise is already in the workout.")
            }
            val exercise = dao.exerciseById(exerciseId)
                ?: throw SessionValidationException("Exercise not found.")
            val id = UUID.randomUUID().toString()
            dao.insertSessionExercises(
                listOf(
                    SessionExercise(
                        id = id,
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        exerciseNameSnapshot = exercise.name,
                        position = (rows.maxOfOrNull { it.position } ?: 0) + 1,
                        workingSets = workingSets,
                        repMin = repMin,
                        repMax = repMax,
                        targetRir = targetRir,
                    ),
                ),
            )
            dao.markSessionStructureDirty(sessionId)
            id
        }
    }

    suspend fun editSessionExercise(
        sessionExerciseId: String,
        workingSets: Int,
        repMin: Int,
        repMax: Int,
        targetRir: Int?,
    ) = sessionWriteMutex.withLock {
        database.withTransaction {
            val dao = database.dao()
            val row = requireActiveSessionExercise(dao, sessionExerciseId)
            validatePrescription(workingSets, repMin, repMax, targetRir)
                ?.let { throw SessionValidationException(it) }
            dao.updateSessionExercise(
                row.copy(workingSets = workingSets, repMin = repMin, repMax = repMax, targetRir = targetRir),
            )
            dao.markSessionStructureDirty(row.sessionId)
        }
    }

    suspend fun replaceSessionExercise(sessionExerciseId: String, exerciseId: String): String =
        sessionWriteMutex.withLock {
            database.withTransaction {
                val dao = database.dao()
                val row = requireActiveSessionExercise(dao, sessionExerciseId)
                val rows = dao.sessionExercisesFor(row.sessionId)
                if (rows.any { it.id != row.id && it.exerciseId == exerciseId }) {
                    throw SessionValidationException("That exercise is already in the workout.")
                }
                val exercise = dao.exerciseById(exerciseId)
                    ?: throw SessionValidationException("Exercise not found.")
                if (dao.selectSetsFor(row.id).isEmpty()) {
                    dao.updateSessionExercise(
                        row.copy(exerciseId = exerciseId, exerciseNameSnapshot = exercise.name),
                    )
                    dao.markSessionStructureDirty(row.sessionId)
                    return@withTransaction row.id
                }
                val replacement = SessionExercise(
                    id = UUID.randomUUID().toString(),
                    sessionId = row.sessionId,
                    exerciseId = exerciseId,
                    exerciseNameSnapshot = exercise.name,
                    // Rewritten with the rest of the order below.
                    position = 0,
                    workingSets = row.workingSets,
                    repMin = row.repMin,
                    repMax = row.repMax,
                    targetRir = row.targetRir,
                    replacesSessionExerciseId = row.id,
                )
                val index = rows.indexOfFirst { it.id == row.id }
                val order = rows.toMutableList().apply { add(index + 1, replacement) }
                dao.negateSessionExercisePositions(row.sessionId)
                dao.insertSessionExercises(listOf(replacement))
                order.forEachIndexed { position, item -> dao.updateSessionExercisePosition(item.id, position + 1) }
                dao.markSessionStructureDirty(row.sessionId)
                replacement.id
            }
        }

    suspend fun removeSessionExercise(sessionExerciseId: String) = sessionWriteMutex.withLock {
        database.withTransaction {
            val dao = database.dao()
            val row = requireActiveSessionExercise(dao, sessionExerciseId)
            if (dao.selectSetsFor(row.id).isNotEmpty()) {
                throw SessionValidationException("An exercise with logged sets cannot be removed.")
            }
            dao.clearReplacementLinks(row.id)
            dao.deleteSessionExerciseById(row.id)
            val remaining = dao.sessionExercisesFor(row.sessionId)
            dao.negateSessionExercisePositions(row.sessionId)
            remaining.forEachIndexed { index, item -> dao.updateSessionExercisePosition(item.id, index + 1) }
            dao.markSessionStructureDirty(row.sessionId)
        }
    }

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
        database.dao().observeAllExercises(),
    ) { stats, latest, rows, best, exercises ->
        HomeDashboard(
            profileStats = stats,
            latest = latest?.toHistorySession(),
            window = rows.toHistorySessions(),
            bestBefore = best.associate { sideKey(it.exerciseId, it.side) to it.bestE1rm },
            exercises = exercises,
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
        side: SetSide? = null,
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
                    side = side,
                ),
            ),
        )
        if (rowIds.any { it == -1L }) throw SessionValidationException("Could not save the set. Try again.")
        val current = settingsStore.settings.first()
        val exercise = dao.sessionExerciseById(sessionExerciseId)?.let { dao.exerciseById(it.exerciseId) }
        val workingSeconds = exercise?.effectiveWorkingRest(current.restWorkingSeconds)
            ?: current.restWorkingSeconds
        settingsStore.setRestEndsAt(
            restEndsAt(
                now,
                restSecondsFor(
                    setType,
                    current.restWarmupSeconds,
                    workingSeconds,
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
        side: SetSide? = null,
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
                    side = side,
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

    suspend fun finishSession(sessionId: String, updateTemplate: Boolean = false) = sessionWriteMutex.withLock {
        val dao = database.dao()
        val session = dao.sessionById(sessionId) ?: return@withLock
        if (session.status != WorkoutStatus.ACTIVE) return@withLock
        database.withTransaction {
            if (dao.workingSetCount(sessionId) == 0) throw NoWorkingSetsException()
            if (updateTemplate) {
                val rows = dao.sessionExercisesFor(sessionId)
                val replacedIds = rows.mapNotNull { it.replacesSessionExerciseId }.toSet()
                val drafts = rows.filterNot { it.id in replacedIds }.map {
                    TemplateExerciseDraft(
                        exerciseId = it.exerciseId,
                        exerciseName = it.exerciseNameSnapshot,
                        workingSets = it.workingSets,
                        repMin = it.repMin,
                        repMax = it.repMax,
                        targetRir = it.targetRir,
                    )
                }
                val target = dao.templateById(session.templateIdSnapshot)
                when {
                    target == null -> saveTemplate(null, availableTemplateName(dao, session.templateNameSnapshot), drafts)
                    target.isPremade -> saveTemplate(null, uniqueCopyName(dao, target.name), drafts)
                    else -> saveTemplate(target.id, target.name, drafts)
                }
            }
            dao.updateSession(
                session.copy(
                    status = WorkoutStatus.FINISHED,
                    finishedAt = System.currentTimeMillis(),
                    structureDirty = false,
                ),
            )
        }
        settingsStore.setRestEndsAt(null)
    }

    suspend fun discardSession(sessionId: String) = sessionWriteMutex.withLock {
        database.dao().deleteSessionById(sessionId)
        settingsStore.setRestEndsAt(null)
    }

    private suspend fun availableTemplateName(dao: BuhatDao, name: String): String =
        if (name.lowercase() in dao.templateNamesExcept("")) uniqueCopyName(dao, name) else name

    private suspend fun uniqueCopyName(dao: BuhatDao, name: String): String {
        val taken = dao.templateNamesExcept("").toSet()
        var candidate = "$name (copy)"
        var copyNumber = 2
        while (candidate.lowercase() in taken) {
            candidate = "$name (copy $copyNumber)"
            copyNumber++
        }
        return candidate
    }

    private suspend fun requireActiveSession(dao: BuhatDao, sessionId: String): WorkoutSession {
        val session = dao.sessionById(sessionId) ?: throw SessionValidationException("Workout not found.")
        if (session.status != WorkoutStatus.ACTIVE) throw SessionValidationException("Workout is not active.")
        return session
    }

    private suspend fun requireActiveSessionExercise(dao: BuhatDao, id: String): SessionExercise {
        val row = dao.sessionExerciseById(id) ?: throw SessionValidationException("Exercise not found.")
        requireActiveSession(dao, row.sessionId)
        return row
    }

    private suspend fun renumberPositions(dao: BuhatDao, sessionExerciseId: String) {
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
        side = side,
    )

    private fun FinishedWorkout.toHistorySession() = HistorySession(
        id = session.id,
        templateIdSnapshot = session.templateIdSnapshot,
        finishedAt = requireNotNull(session.finishedAt),
        templateNameSnapshot = session.templateNameSnapshot,
        note = session.note,
        importKey = session.importKey,
        exercises = exercises.sortedBy { it.exercise.position }.map { row ->
            HistoryExercise(
                exerciseId = row.exercise.exerciseId,
                exerciseNameSnapshot = row.exercise.exerciseNameSnapshot,
                sets = row.sets.sortedBy { it.completedAt }.map { it.toRecord() },
                note = row.exercise.note,
                replacesSessionExerciseId = row.exercise.replacesSessionExerciseId,
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
                note = first.sessionNote,
                importKey = first.importKey,
                exercises = rows.groupBy { it.exerciseId }.map { (exerciseId, sets) ->
                    HistoryExercise(
                        exerciseId = exerciseId,
                        sets = sets.map { it.toRecord() },
                        note = sets.first().exerciseNote,
                        replacesSessionExerciseId = sets.first().replacesSessionExerciseId,
                    )
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

    suspend fun previewStrongImport(text: String): StrongPreview = withContext(Dispatchers.IO) {
        val dao = database.dao()
        if (dao.activeSessionCount() > 0) throw StrongCsvException(listOf(ACTIVE_WORKOUT_BLOCK))
        val parsed = parseStrongCsv(text)
        val imported = dao.importKeys().toSet()
        val newWorkouts = parsed.workouts.filter { it.identity !in imported }
        val allExercises = dao.allExercises()
        StrongPreview(
            newWorkouts = newWorkouts.size,
            skippedDuplicates = parsed.workouts.size - newWorkouts.size,
            skippedEmptyWorkouts = parsed.skippedEmptyWorkouts,
            firstStartedAt = newWorkouts.minOfOrNull { it.startedAt },
            lastStartedAt = newWorkouts.maxOfOrNull { it.startedAt },
            warmupSets = newWorkouts.countSets(SetType.WARMUP),
            workingSets = newWorkouts.countSets(SetType.WORKING),
            failureSets = newWorkouts.countSets(SetType.FAILURE),
            sources = newWorkouts.flatMap { it.exercises }
                .map { it.sourceName }
                .distinct()
                .map { name ->
                    StrongSource(
                        name = name,
                        matchedExerciseId = allExercises.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id,
                    )
                },
            exercises = allExercises.filter { it.archivedAt == null }.sortedBy { it.name },
        )
    }

    suspend fun importStrong(text: String, mappings: List<StrongMapping>): StrongImportResult =
        withContext(Dispatchers.IO) {
            sessionWriteMutex.withLock {
                val dao = database.dao()
                if (dao.activeSessionCount() > 0) throw StrongCsvException(listOf(ACTIVE_WORKOUT_BLOCK))
                val parsed = parseStrongCsv(text)
                val imported = dao.importKeys().toSet()
                val newWorkouts = parsed.workouts.filter { it.identity !in imported }
                val skippedDuplicates = parsed.workouts.size - newWorkouts.size
                if (newWorkouts.isEmpty()) return@withLock StrongImportResult(0, skippedDuplicates, 0)

                val allExercises = dao.allExercises()
                val byId = allExercises.associateBy { it.id }
                val namesTaken = allExercises.map { it.nameLower }.toMutableSet()
                val mappingsBySource = mappings.groupBy { it.sourceName }
                val resolved = mutableMapOf<String, String>()
                val newCustom = mutableListOf<Exercise>()
                val errors = mutableListOf<String>()
                val sourceNames = newWorkouts.flatMap { it.exercises }.map { it.sourceName }.distinct()

                sourceNames.forEach { source ->
                    val options = mappingsBySource[source].orEmpty()
                    val match = allExercises.firstOrNull { it.name.equals(source, ignoreCase = true) }
                    when {
                        options.size > 1 -> errors += "Map \"$source\" to exactly one exercise."
                        options.isEmpty() && match != null -> resolved[source] = match.id
                        options.isEmpty() -> errors += "Map \"$source\" to a Buhat exercise."
                        else -> {
                            val mapping = options.single()
                            when {
                                mapping.existingExerciseId != null && mapping.newExerciseName != null ->
                                    errors += "Choose an existing exercise or create one for \"$source\", not both."
                                mapping.existingExerciseId != null -> {
                                    val existing = byId[mapping.existingExerciseId]
                                    if (existing == null) errors += "Exercise not found for \"$source\"."
                                    else resolved[source] = existing.id
                                }
                                else -> {
                                    val name = mapping.newExerciseName?.trim().orEmpty()
                                    val muscleGroup = mapping.muscleGroup?.trim().orEmpty()
                                    val equipment = mapping.equipment?.trim().orEmpty()
                                    val nameError = validateExerciseName(name, namesTaken)
                                    when {
                                        nameError != null -> errors += "$nameError Source: \"$source\"."
                                        muscleGroup.isEmpty() || equipment.isEmpty() ->
                                            errors += "Muscle group and equipment are required for \"$source\"."
                                        else -> {
                                            val exercise = Exercise(
                                                id = UUID.randomUUID().toString(),
                                                name = name,
                                                nameLower = name.lowercase(),
                                                muscleGroup = muscleGroup,
                                                equipment = equipment,
                                                isCustom = true,
                                                createdAt = System.currentTimeMillis(),
                                            )
                                            namesTaken += exercise.nameLower
                                            newCustom += exercise
                                            resolved[source] = exercise.id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (errors.isNotEmpty()) throw StrongCsvException(errors)

                val sessions = mutableListOf<WorkoutSession>()
                val sessionExercises = mutableListOf<SessionExercise>()
                val sets = mutableListOf<LoggedSet>()
                newWorkouts.forEach { workout ->
                    val sessionId = UUID.randomUUID().toString()
                    sessions += WorkoutSession(
                        id = sessionId,
                        templateIdSnapshot = STRONG_TEMPLATE_ID,
                        templateNameSnapshot = workout.name,
                        startedAt = workout.startedAt,
                        finishedAt = workout.finishedAt,
                        status = WorkoutStatus.FINISHED,
                        note = workout.note,
                        importKey = workout.identity,
                    )
                    workout.exercises.forEachIndexed { position, exercise ->
                        val sessionExerciseId = UUID.randomUUID().toString()
                        val working = exercise.sets.filter { it.setType != SetType.WARMUP }
                        val reps = working.map { it.reps }
                        // shortcut: imported prescriptions are display-only, clamped so backups re-validate
                        val workingSets = working.size.coerceIn(1, 10)
                        val repMin = (reps.minOrNull() ?: 1).coerceIn(1, 30)
                        val repMax = (reps.maxOrNull() ?: 1).coerceIn(repMin, 30)
                        sessionExercises += SessionExercise(
                            id = sessionExerciseId,
                            sessionId = sessionId,
                            exerciseId = resolved.getValue(exercise.sourceName),
                            exerciseNameSnapshot = exercise.sourceName,
                            position = position + 1,
                            workingSets = workingSets,
                            repMin = repMin,
                            repMax = repMax,
                            targetRir = null,
                            note = exercise.note,
                        )
                        exercise.sets.forEach { set ->
                            sets += LoggedSet(
                                id = UUID.randomUUID().toString(),
                                sessionExerciseId = sessionExerciseId,
                                setType = set.setType,
                                workingIndex = set.workingIndex,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                rir = set.rir,
                                completedAt = set.completedAt,
                                side = null,
                            )
                        }
                    }
                }

                try {
                    database.withTransaction {
                        if (dao.activeSessionCount() > 0) throw StrongCsvException(listOf(ACTIVE_WORKOUT_BLOCK))
                        if (newCustom.isNotEmpty() && dao.insertExercises(newCustom).any { it == -1L }) {
                            throw StrongCsvException(listOf("An exercise name is already used. Nothing was saved."))
                        }
                        val inserted = dao.insertSessions(sessions) +
                            dao.insertSessionExercises(sessionExercises) +
                            dao.insertSets(sets)
                        if (inserted.any { it == -1L }) {
                            throw StrongCsvException(listOf("Could not import the Strong export. Nothing was saved."))
                        }
                    }
                } catch (e: StrongCsvException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw StrongCsvException(listOf("Could not import the Strong export. Nothing was saved."))
                }

                StrongImportResult(newWorkouts.size, skippedDuplicates, sets.size)
            }
        }

    private fun List<StrongWorkout>.countSets(type: SetType): Int =
        sumOf { workout -> workout.exercises.sumOf { exercise -> exercise.sets.count { it.setType == type } } }

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
    side = side,
)


