package com.buhat.tracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.buhat.tracker.domain.SetType

// All IDs are UUID strings, timestamps epoch millis UTC, weights kilograms (roadmap 4.2).
// nameLower carries a unique index so name uniqueness is case-insensitive at the database level.

@Entity(tableName = "Exercise", indices = [Index(value = ["nameLower"], unique = true)])
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "nameLower") val nameLower: String = name.lowercase(),
    val muscleGroup: String,
    val equipment: String,
    val isCustom: Boolean,
    val archivedAt: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "Template",
    indices = [Index(value = ["nameLower"], unique = true)],
)
data class Template(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "nameLower") val nameLower: String = name.lowercase(),
    val isPremade: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "TemplateExercise",
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [
        Index("templateId"),
        Index("exerciseId"),
        Index(value = ["templateId", "position"], unique = true),
        Index(value = ["templateId", "exerciseId"], unique = true),
    ],
)
data class TemplateExercise(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
)

@Entity(
    tableName = "WorkoutSession",
    indices = [
        Index("status"),
        Index("finishedAt"),
    ],
)
data class WorkoutSession(
    @PrimaryKey val id: String,
    // Snapshot only, deliberately not a foreign key: the template can be deleted later.
    val templateIdSnapshot: String,
    val templateNameSnapshot: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: WorkoutStatus,
)

enum class WorkoutStatus { ACTIVE, FINISHED }

@Entity(
    tableName = "SessionExercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "position"], unique = true),
    ],
)
data class SessionExercise(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val position: Int,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
)

@Entity(
    tableName = "LoggedSet",
    foreignKeys = [
        ForeignKey(
            entity = SessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionExerciseId"),
        // SQLite treats NULLs as distinct, so several warmup rows (workingIndex null) are allowed.
        Index(value = ["sessionExerciseId", "workingIndex"], unique = true),
    ],
)
data class LoggedSet(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val setType: SetType,
    // 1-based among working/failure sets of this exercise; null for warmups.
    val workingIndex: Int?,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val completedAt: Long,
)
