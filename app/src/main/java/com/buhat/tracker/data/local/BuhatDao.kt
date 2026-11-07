package com.buhat.tracker.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.buhat.tracker.domain.SetType
import kotlinx.coroutines.flow.Flow

@Dao
interface BuhatDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(exercises: List<Exercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(templates: List<Template>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplateExercises(templateExercises: List<TemplateExercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<WorkoutSession>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionExercises(sessionExercises: List<SessionExercise>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSets(sets: List<LoggedSet>): List<Long>

    @Query("SELECT * FROM Exercise WHERE archivedAt IS NULL ORDER BY name")
    fun observeExercises(): kotlinx.coroutines.flow.Flow<List<Exercise>>

    @Query("SELECT * FROM Template WHERE archivedAt IS NULL ORDER BY createdAt")
    fun observeTemplates(): kotlinx.coroutines.flow.Flow<List<Template>>

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun exerciseById(id: String): Exercise?

    @Query("SELECT * FROM Template WHERE id = :id")
    suspend fun templateById(id: String): Template?

    @Query("SELECT nameLower FROM Exercise WHERE id != :excludeId")
    suspend fun exerciseNamesExcept(excludeId: String): List<String>

    @Query("SELECT nameLower FROM Template WHERE id != :excludeId")
    suspend fun templateNamesExcept(excludeId: String): List<String>

    @Query(
        """
        SELECT te.templateId AS templateId, te.exerciseId AS exerciseId, e.name AS exerciseName,
               te.position AS position, te.workingSets AS workingSets, te.repMin AS repMin,
               te.repMax AS repMax, te.targetRir AS targetRir
        FROM TemplateExercise te
        JOIN Exercise e ON e.id = te.exerciseId
        ORDER BY te.templateId, te.position
        """,
    )
    fun observeTemplateExerciseRows(): Flow<List<TemplateExerciseRow>>

    @Query(
        """
        SELECT te.templateId AS templateId, te.exerciseId AS exerciseId, e.name AS exerciseName,
               te.position AS position, te.workingSets AS workingSets, te.repMin AS repMin,
               te.repMax AS repMax, te.targetRir AS targetRir
        FROM TemplateExercise te
        JOIN Exercise e ON e.id = te.exerciseId
        WHERE te.templateId = :templateId
        ORDER BY te.position
        """,
    )
    suspend fun templateExerciseRowsFor(templateId: String): List<TemplateExerciseRow>

    @Query("SELECT * FROM TemplateExercise WHERE templateId = :templateId ORDER BY position")
    suspend fun templateExercisesFor(templateId: String): List<TemplateExercise>

    @Query("SELECT COUNT(*) FROM TemplateExercise WHERE exerciseId = :exerciseId")
    suspend fun templateExerciseCountForExercise(exerciseId: String): Int

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Update
    suspend fun updateTemplate(template: Template)

    @Query("DELETE FROM Exercise WHERE id = :id")
    suspend fun deleteExerciseById(id: String)

    @Query("DELETE FROM TemplateExercise WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: String)

    @Query(
        """
        SELECT COUNT(*) AS totalWorkouts, MIN(finishedAt) AS firstFinishedAt
        FROM WorkoutSession WHERE status = 'FINISHED'
        """,
    )
    fun observeProfileStats(): Flow<ProfileStats>

    @Query("SELECT * FROM LoggedSet WHERE sessionExerciseId = :sessionExerciseId ORDER BY completedAt")
    suspend fun selectSetsFor(sessionExerciseId: String): List<LoggedSet>

    @Query("SELECT * FROM LoggedSet WHERE sessionExerciseId = :sessionExerciseId ORDER BY completedAt")
    fun observeSetsFor(sessionExerciseId: String): Flow<List<LoggedSet>>

    @Query("SELECT * FROM LoggedSet WHERE id = :id")
    suspend fun setById(id: String): LoggedSet?

    @Update
    suspend fun updateSet(set: LoggedSet)

    @Query("UPDATE LoggedSet SET workingIndex = NULL WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun clearWorkingIndices(sessionExerciseId: String)

    @Query("UPDATE LoggedSet SET workingIndex = :workingIndex WHERE id = :id")
    suspend fun updateWorkingIndex(id: String, workingIndex: Int?)

    @Query("DELETE FROM LoggedSet WHERE id = :id")
    suspend fun deleteSetById(id: String)

    @Query("SELECT * FROM WorkoutSession WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSession?>

    @Query("SELECT * FROM WorkoutSession WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun activeSession(): WorkoutSession?

    @Query("SELECT COUNT(*) FROM WorkoutSession WHERE status = 'ACTIVE'")
    suspend fun activeSessionCount(): Int

    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    suspend fun sessionById(id: String): WorkoutSession?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM WorkoutSession s
            JOIN SessionExercise se ON se.sessionId = s.id
            WHERE se.id = :sessionExerciseId AND s.status = 'ACTIVE'
        )
        """,
    )
    suspend fun sessionExerciseIsActive(sessionExerciseId: String): Boolean

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Query("DELETE FROM WorkoutSession WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY position")
    fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>>

    @Query(
        """
        SELECT COUNT(*) FROM LoggedSet
        WHERE sessionExerciseId IN (SELECT id FROM SessionExercise WHERE sessionId = :sessionId)
          AND workingIndex IS NOT NULL
        """,
    )
    suspend fun workingSetCount(sessionId: String): Int

    @Query(
        """
        SELECT s.id AS sessionId, s.templateIdSnapshot AS templateIdSnapshot,
               s.templateNameSnapshot AS templateNameSnapshot,
               s.finishedAt AS finishedAt, se.exerciseId AS exerciseId,
               ls.id AS setId, ls.setType AS setType, ls.workingIndex AS workingIndex,
               ls.weightKg AS weightKg, ls.reps AS reps, ls.rir AS rir,
               ls.completedAt AS completedAt
        FROM WorkoutSession s
        JOIN SessionExercise se ON se.sessionId = s.id
        JOIN LoggedSet ls ON ls.sessionExerciseId = se.id
        WHERE s.status = 'FINISHED' AND se.exerciseId = :exerciseId
        ORDER BY s.finishedAt, ls.completedAt
        """,
    )
    fun observeHistoryRowsFor(exerciseId: String): Flow<List<HistorySetRow>>

    @Query(
        """
        SELECT s.id AS sessionId, s.templateIdSnapshot AS templateIdSnapshot,
               s.templateNameSnapshot AS templateNameSnapshot,
               s.finishedAt AS finishedAt, se.exerciseId AS exerciseId,
               ls.id AS setId, ls.setType AS setType, ls.workingIndex AS workingIndex,
               ls.weightKg AS weightKg, ls.reps AS reps, ls.rir AS rir,
               ls.completedAt AS completedAt
        FROM WorkoutSession s
        JOIN SessionExercise se ON se.sessionId = s.id
        JOIN LoggedSet ls ON ls.sessionExerciseId = se.id
        WHERE s.status = 'FINISHED' AND se.exerciseId = :exerciseId
          AND (
            s.id IN (
                SELECT latest.id FROM WorkoutSession latest
                JOIN SessionExercise latestSe ON latestSe.sessionId = latest.id
                WHERE latest.status = 'FINISHED' AND latestSe.exerciseId = :exerciseId
                  AND EXISTS(SELECT 1 FROM LoggedSet latestSet WHERE latestSet.sessionExerciseId = latestSe.id)
                ORDER BY latest.finishedAt DESC, latest.id DESC
                LIMIT 1
            )
            OR s.id IN (
                SELECT matching.id FROM WorkoutSession matching
                JOIN SessionExercise matchingSe ON matchingSe.sessionId = matching.id
                WHERE matching.status = 'FINISHED' AND matchingSe.exerciseId = :exerciseId
                  AND matching.templateIdSnapshot = :templateIdSnapshot
                  AND EXISTS(SELECT 1 FROM LoggedSet matchingSet WHERE matchingSet.sessionExerciseId = matchingSe.id)
                ORDER BY matching.finishedAt DESC, matching.id DESC
                LIMIT 1
            )
          )
        ORDER BY s.finishedAt, ls.completedAt
        """,
    )
    fun observeLatestHistoryRowsFor(exerciseId: String, templateIdSnapshot: String): Flow<List<HistorySetRow>>

    @Query(
        """
        SELECT s.id AS sessionId, s.templateIdSnapshot AS templateIdSnapshot,
               s.templateNameSnapshot AS templateNameSnapshot,
               s.finishedAt AS finishedAt, se.exerciseId AS exerciseId,
               ls.id AS setId, ls.setType AS setType, ls.workingIndex AS workingIndex,
               ls.weightKg AS weightKg, ls.reps AS reps, ls.rir AS rir,
               ls.completedAt AS completedAt
        FROM WorkoutSession s
        JOIN SessionExercise se ON se.sessionId = s.id
        JOIN LoggedSet ls ON ls.sessionExerciseId = se.id
        WHERE s.status = 'FINISHED' AND s.finishedAt >= :cutoffMillis
        ORDER BY s.finishedAt, s.id, se.position, ls.completedAt
        """,
    )
    fun observeHistoryRowsSince(cutoffMillis: Long): Flow<List<HistorySetRow>>

    @Query(
        """
        SELECT se.exerciseId AS exerciseId,
               MAX(ls.weightKg * (1.0 + ls.reps / 30.0)) AS bestE1rm
        FROM LoggedSet ls
        JOIN SessionExercise se ON se.id = ls.sessionExerciseId
        JOIN WorkoutSession s ON s.id = se.sessionId
        WHERE s.status = 'FINISHED' AND s.finishedAt < :cutoffMillis
          AND ls.setType != 'WARMUP' AND ls.weightKg > 0 AND ls.reps > 0
        GROUP BY se.exerciseId
        """,
    )
    fun observeBestE1rmBefore(cutoffMillis: Long): Flow<List<ExerciseBestE1rm>>

    @Query(
        """
        SELECT se.exerciseId AS exerciseId, se.exerciseNameSnapshot AS exerciseNameSnapshot,
               s.finishedAt AS finishedAt, s.id AS sessionId,
               EXISTS(SELECT 1 FROM LoggedSet ls WHERE ls.sessionExerciseId = se.id) AS hasSets
        FROM SessionExercise se
        JOIN WorkoutSession s ON s.id = se.sessionId
        WHERE s.status = 'FINISHED'
        ORDER BY s.finishedAt DESC, s.id DESC, se.position
        """,
    )
    fun observeHistoryExerciseChoices(): Flow<List<HistoryExerciseChoice>>

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE status = 'FINISHED' ORDER BY finishedAt DESC, id ASC LIMIT 1")
    fun observeLatestFinishedWorkout(): Flow<FinishedWorkout?>

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE status = 'FINISHED' ORDER BY finishedAt, id")
    fun observeFinishedWorkouts(): Flow<List<FinishedWorkout>>

    @Query("DELETE FROM WorkoutSession WHERE id = :id AND status = 'FINISHED'")
    suspend fun deleteFinishedSession(id: String): Int

    @Query("DELETE FROM Template WHERE id IN (:ids)")
    suspend fun deleteTemplates(ids: List<String>)

    @Query("SELECT COUNT(*) FROM Exercise")
    suspend fun exerciseCount(): Int

    @Query("SELECT COUNT(*) FROM Template")
    suspend fun templateCount(): Int

    @Query("SELECT COUNT(*) FROM TemplateExercise")
    suspend fun templateExerciseCount(): Int

    @Query("SELECT SUM(workingSets) FROM TemplateExercise GROUP BY templateId")
    suspend fun templateWorkingSetTotalsByTemplate(): List<Int>

    @Query("SELECT COUNT(*) FROM TemplateExercise GROUP BY templateId")
    suspend fun templateExerciseCountsByTemplate(): List<Int>

    @Query("SELECT * FROM Exercise ORDER BY createdAt, id")
    suspend fun allExercises(): List<Exercise>

    @Query("SELECT * FROM Template ORDER BY createdAt, id")
    suspend fun allTemplates(): List<Template>

    @Query("SELECT * FROM TemplateExercise ORDER BY templateId, position")
    suspend fun allTemplateExercises(): List<TemplateExercise>

    @Query("SELECT * FROM WorkoutSession ORDER BY startedAt, id")
    suspend fun allSessions(): List<WorkoutSession>

    @Query("SELECT * FROM SessionExercise ORDER BY sessionId, position")
    suspend fun allSessionExercises(): List<SessionExercise>

    @Query("SELECT * FROM LoggedSet ORDER BY completedAt, id")
    suspend fun allSets(): List<LoggedSet>

    @Query("DELETE FROM LoggedSet")
    suspend fun deleteAllSets()

    @Query("DELETE FROM SessionExercise")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM WorkoutSession")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM TemplateExercise")
    suspend fun deleteAllTemplateExercises()

    @Query("DELETE FROM Template")
    suspend fun deleteAllTemplates()

    @Query("DELETE FROM Exercise")
    suspend fun deleteAllExercises()
}

data class TemplateExerciseRow(
    val templateId: String,
    val exerciseId: String,
    val exerciseName: String,
    val position: Int,
    val workingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
)

data class ProfileStats(
    val totalWorkouts: Int,
    val firstFinishedAt: Long?,
)

data class HistorySetRow(
    val sessionId: String,
    val templateIdSnapshot: String,
    val templateNameSnapshot: String,
    val finishedAt: Long?,
    val exerciseId: String,
    val setId: String,
    val setType: SetType,
    val workingIndex: Int?,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val completedAt: Long,
)

data class ExerciseBestE1rm(
    val exerciseId: String,
    val bestE1rm: Double,
)

data class HistoryExerciseChoice(
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val finishedAt: Long,
    val sessionId: String,
    val hasSets: Boolean,
)

data class FinishedWorkout(
    @Embedded val session: WorkoutSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = SessionExercise::class)
    val exercises: List<HistoryExerciseRows>,
)

data class HistoryExerciseRows(
    @Embedded val exercise: SessionExercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<LoggedSet>,
)
