package com.buhat.tracker.data

import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.Template
import com.buhat.tracker.data.local.TemplateExercise
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.MAX_TEXT_LENGTH
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.WeightUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

const val BACKUP_FORMAT_VERSION = 1
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

class BackupException(message: String) : Exception(message)

@Serializable
data class BackupProfile(
    @Required val name: String? = null,
    @Required val gender: String? = null,
    @Required val age: Int? = null,
    @Required val weightKg: Double? = null,
    @Required val heightCm: Int? = null,
)

@Serializable
data class BackupSettings(
    val theme: String,
    val unit: String,
    val previousSetSource: String,
    val restWarmupSeconds: Int,
    val restWorkingSeconds: Int,
    val restFailureSeconds: Int,
    val restIncrementSeconds: Int,
)

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val isCustom: Boolean,
    @Required val archivedAt: Long? = null,
    val createdAt: Long,
)

@Serializable
data class BackupTemplate(
    val id: String,
    val name: String,
    val isPremade: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    @Required val archivedAt: Long? = null,
)

@Serializable
data class BackupTemplateExercise(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    @Required val targetRir: Int? = null,
)

@Serializable
data class BackupSession(
    val id: String,
    val templateIdSnapshot: String,
    val templateNameSnapshot: String,
    val startedAt: Long,
    @Required val finishedAt: Long? = null,
    val status: String,
)

@Serializable
data class BackupSessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val position: Int,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    @Required val targetRir: Int? = null,
)

@Serializable
data class BackupSet(
    val id: String,
    val sessionExerciseId: String,
    val setType: String,
    @Required val workingIndex: Int? = null,
    val weightKg: Double,
    val reps: Int,
    @Required val rir: Int? = null,
    val completedAt: Long,
)

@Serializable
data class Backup(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val profile: BackupProfile,
    val settings: BackupSettings,
    val exercises: List<BackupExercise>,
    val templates: List<BackupTemplate>,
    val templateExercises: List<BackupTemplateExercise>,
    val sessions: List<BackupSession>,
    val sessionExercises: List<BackupSessionExercise>,
    val sets: List<BackupSet>,
)

data class BackupSummary(
    val exportedAt: Long,
    val exercises: Int,
    val templates: Int,
    val sessions: Int,
    val sets: Int,
)

private val backupJson = Json { prettyPrint = true }

private const val NOT_A_BACKUP = "This file is not a Buhat backup."
private const val CORRUPT = "This backup file is corrupt."
private const val NEWER_VERSION = "This backup was made by a newer version of the app."
private const val OUT_OF_RANGE = "This backup contains values outside the allowed range."

fun encodeBackup(backup: Backup): String = backupJson.encodeToString(backup)

fun decodeBackup(text: String): Backup {
    val root = try {
        backupJson.parseToJsonElement(text)
    } catch (e: SerializationException) {
        throw BackupException(if (text.contains("\"formatVersion\"")) CORRUPT else NOT_A_BACKUP)
    }
    val obj = root as? JsonObject ?: throw BackupException(NOT_A_BACKUP)
    val version = (obj["formatVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()
        ?: throw BackupException(NOT_A_BACKUP)
    if (version > BACKUP_FORMAT_VERSION) throw BackupException(NEWER_VERSION)
    if (version != BACKUP_FORMAT_VERSION) throw BackupException(NOT_A_BACKUP)

    val backup = try {
        backupJson.decodeFromString<Backup>(root.toString())
    } catch (e: SerializationException) {
        throw BackupException(CORRUPT)
    }
    validateJsonTypes(root, backupJson.encodeToJsonElement(backup))
    validateBackup(backup)
    return backup
}

fun Backup.summary() = BackupSummary(exportedAt, exercises.size, templates.size, sessions.size, sets.size)

private fun corrupt(): Nothing = throw BackupException(CORRUPT)

private fun outOfRange(): Nothing = throw BackupException(OUT_OF_RANGE)

private fun validId(id: String): Boolean =
    runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)

// Kotlin serialization accepts quoted numbers. Backups require the declared JSON field types.
private fun validateJsonTypes(input: JsonElement, decoded: JsonElement) {
    when (decoded) {
        is JsonObject -> decoded.forEach { (key, value) -> validateJsonTypes((input as JsonObject).getValue(key), value) }
        is JsonArray -> decoded.forEachIndexed { index, value -> validateJsonTypes((input as JsonArray)[index], value) }
        is JsonPrimitive -> if ((input as JsonPrimitive).isString != decoded.isString) corrupt()
    }
}

private fun validateBackup(backup: Backup) {
    if (backup.appVersion.isBlank() || backup.appVersion.length > MAX_TEXT_LENGTH || backup.exportedAt <= 0) corrupt()
    if (listOfNotNull(backup.profile.name, backup.profile.gender).any { it.length > MAX_TEXT_LENGTH }) outOfRange()
    backup.profile.age?.let { if (it !in 1..120) outOfRange() }
    backup.profile.weightKg?.let { if (it !in 0.0..1000.0) outOfRange() }
    backup.profile.heightCm?.let { if (it !in 1..300) outOfRange() }

    runCatching { AppTheme.valueOf(backup.settings.theme) }.getOrNull() ?: corrupt()
    runCatching { WeightUnit.valueOf(backup.settings.unit) }.getOrNull() ?: corrupt()
    runCatching { PreviousSetSource.valueOf(backup.settings.previousSetSource) }.getOrNull() ?: corrupt()
    listOf(
        backup.settings.restWarmupSeconds,
        backup.settings.restWorkingSeconds,
        backup.settings.restFailureSeconds,
    ).forEach { if (it !in 0..600) outOfRange() }
    if (backup.settings.restIncrementSeconds !in 5..60) outOfRange()

    val uniqueIds = backup.exercises.map { it.id } +
        backup.templates.map { it.id } +
        backup.templateExercises.map { it.id } +
        backup.sessions.map { it.id } +
        backup.sessionExercises.map { it.id } +
        backup.sets.map { it.id }
    if (uniqueIds.any { !validId(it) }) corrupt()
    listOf(
        backup.exercises.map { it.id },
        backup.templates.map { it.id },
        backup.templateExercises.map { it.id },
        backup.sessions.map { it.id },
        backup.sessionExercises.map { it.id },
        backup.sets.map { it.id },
    ).forEach { if (it.size != it.toSet().size) throw BackupException("This backup has duplicate IDs.") }

    backup.exercises.forEach {
        if (it.name.isBlank() || it.createdAt <= 0 || (it.archivedAt ?: it.createdAt) < it.createdAt) corrupt()
        if (listOf(it.name, it.muscleGroup, it.equipment).any { text -> text.length > MAX_TEXT_LENGTH }) outOfRange()
    }
    if (backup.exercises.map { it.name.lowercase() }.toSet().size != backup.exercises.size) {
        throw BackupException("This backup has duplicate exercise names.")
    }
    backup.templates.forEach {
        if (it.name.isBlank() || it.createdAt <= 0 || it.updatedAt < it.createdAt ||
            (it.archivedAt ?: it.createdAt) < it.createdAt) corrupt()
        if (it.name.length > MAX_TEXT_LENGTH) outOfRange()
    }
    if (backup.templates.map { it.name.lowercase() }.toSet().size != backup.templates.size) {
        throw BackupException("This backup has duplicate template names.")
    }

    val exerciseIds = backup.exercises.map { it.id }.toSet()
    val templateIds = backup.templates.map { it.id }.toSet()
    if (backup.templateExercises.map { it.templateId }.toSet() != templateIds) corrupt()
    backup.templateExercises.forEach { row ->
        if (row.templateId !in templateIds) throw BackupException("This backup references a missing template: ${row.templateId}.")
        if (row.exerciseId !in exerciseIds) throw BackupException("This backup references a missing exercise: ${row.exerciseId}.")
        if (row.position < 1 || row.workingSets !in 1..10) outOfRange()
        if (row.repMin !in 1..30 || row.repMax !in row.repMin..30) outOfRange()
        row.targetRir?.let { if (it !in 0..5) outOfRange() }
    }
    backup.templateExercises.groupBy { it.templateId }.forEach { (_, rows) ->
        if (rows.map { it.position }.sorted() != (1..rows.size).toList()) corrupt()
        if (rows.map { it.exerciseId }.toSet().size != rows.size) corrupt()
    }

    val sessionIds = backup.sessions.map { it.id }.toSet()
    if (backup.sessionExercises.map { it.sessionId }.toSet() != sessionIds) corrupt()
    backup.sessions.forEach {
        if (!validId(it.templateIdSnapshot) || it.templateNameSnapshot.isBlank()) corrupt()
        if (it.templateNameSnapshot.length > MAX_TEXT_LENGTH) outOfRange()
        if (it.startedAt <= 0) corrupt()
        if (it.status != WorkoutStatus.FINISHED.name) {
            throw BackupException("This backup contains a session that is still active.")
        }
        if (it.finishedAt == null || it.finishedAt < it.startedAt) corrupt()
    }
    backup.sessionExercises.forEach { row ->
        if (!validId(row.exerciseId)) corrupt()
        if (row.sessionId !in sessionIds) throw BackupException("This backup references a missing session: ${row.sessionId}.")
        if (row.exerciseNameSnapshot.isBlank() || row.position < 1 || row.workingSets !in 1..10 ||
            row.exerciseNameSnapshot.length > MAX_TEXT_LENGTH) outOfRange()
        if (row.repMin !in 1..30 || row.repMax !in row.repMin..30) outOfRange()
        row.targetRir?.let { if (it !in 0..5) outOfRange() }
    }
    backup.sessionExercises.groupBy { it.sessionId }.forEach { (_, rows) ->
        if (rows.map { it.position }.sorted() != (1..rows.size).toList()) corrupt()
        if (rows.map { it.exerciseId }.toSet().size != rows.size) corrupt()
    }

    val sessionExerciseIds = backup.sessionExercises.map { it.id }.toSet()
    val sessions = backup.sessions.associateBy { it.id }
    val sessionExercises = backup.sessionExercises.associateBy { it.id }
    backup.sets.forEach { row ->
        val type = runCatching { SetType.valueOf(row.setType) }.getOrNull() ?: corrupt()
        if (row.sessionExerciseId !in sessionExerciseIds) {
            throw BackupException("This backup references a missing session exercise: ${row.sessionExerciseId}.")
        }
        if (row.weightKg !in 0.0..1000.0 || row.reps !in 0..100 || row.completedAt <= 0) outOfRange()
        val session = sessions.getValue(sessionExercises.getValue(row.sessionExerciseId).sessionId)
        if (row.completedAt < session.startedAt || row.completedAt > requireNotNull(session.finishedAt)) corrupt()
        row.rir?.let { if (it !in 0..5) outOfRange() }
        if (type == SetType.WARMUP) {
            if (row.workingIndex != null) corrupt()
        } else if (row.workingIndex == null || row.workingIndex < 1) corrupt()
    }
    backup.sets.filter { it.setType != SetType.WARMUP.name }
        .groupBy { it.sessionExerciseId }
        .forEach { (_, rows) ->
            if (rows.sortedWith(compareBy({ it.completedAt }, { it.workingIndex })).map { it.workingIndex } != (1..rows.size).toList()) {
                throw BackupException("This backup has non-contiguous working set positions.")
            }
        }
}

fun Profile.toBackup() = BackupProfile(name, gender, age, weightKg, heightCm)

fun BackupProfile.toProfile() = Profile(name, gender, age, weightKg, heightCm)

fun Settings.toBackup() = BackupSettings(
    theme = theme.name,
    unit = unit.name,
    previousSetSource = previousSetSource.name,
    restWarmupSeconds = restWarmupSeconds,
    restWorkingSeconds = restWorkingSeconds,
    restFailureSeconds = restFailureSeconds,
    restIncrementSeconds = restIncrementSeconds,
)

fun BackupSettings.toSettings() = Settings(
    theme = AppTheme.valueOf(theme),
    unit = WeightUnit.valueOf(unit),
    previousSetSource = PreviousSetSource.valueOf(previousSetSource),
    restWarmupSeconds = restWarmupSeconds,
    restWorkingSeconds = restWorkingSeconds,
    restFailureSeconds = restFailureSeconds,
    restIncrementSeconds = restIncrementSeconds,
)

fun Exercise.toBackup() = BackupExercise(id, name, muscleGroup, equipment, isCustom, archivedAt, createdAt)

fun BackupExercise.toExercise() = Exercise(
    id = id,
    name = name,
    nameLower = name.lowercase(),
    muscleGroup = muscleGroup,
    equipment = equipment,
    isCustom = isCustom,
    archivedAt = archivedAt,
    createdAt = createdAt,
)

fun Template.toBackup() = BackupTemplate(id, name, isPremade, createdAt, updatedAt, archivedAt)

fun BackupTemplate.toTemplate() = Template(
    id = id,
    name = name,
    nameLower = name.lowercase(),
    isPremade = isPremade,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
)

fun TemplateExercise.toBackup() = BackupTemplateExercise(
    id, templateId, exerciseId, position, workingSets, repMin, repMax, targetRir,
)

fun BackupTemplateExercise.toTemplateExercise() = TemplateExercise(
    id = id,
    templateId = templateId,
    exerciseId = exerciseId,
    position = position,
    workingSets = workingSets,
    repMin = repMin,
    repMax = repMax,
    targetRir = targetRir,
)

fun WorkoutSession.toBackup() = BackupSession(
    id, templateIdSnapshot, templateNameSnapshot, startedAt, finishedAt, status.name,
)

fun BackupSession.toSession() = WorkoutSession(
    id = id,
    templateIdSnapshot = templateIdSnapshot,
    templateNameSnapshot = templateNameSnapshot,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = WorkoutStatus.valueOf(status),
)

fun SessionExercise.toBackup() = BackupSessionExercise(
    id, sessionId, exerciseId, exerciseNameSnapshot, position, workingSets, repMin, repMax, targetRir,
)

fun BackupSessionExercise.toSessionExercise() = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseNameSnapshot = exerciseNameSnapshot,
    position = position,
    workingSets = workingSets,
    repMin = repMin,
    repMax = repMax,
    targetRir = targetRir,
)

fun LoggedSet.toBackup() = BackupSet(
    id, sessionExerciseId, setType.name, workingIndex, weightKg, reps, rir, completedAt,
)

fun BackupSet.toLoggedSet() = LoggedSet(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setType = SetType.valueOf(setType),
    workingIndex = workingIndex,
    weightKg = weightKg,
    reps = reps,
    rir = rir,
    completedAt = completedAt,
)
