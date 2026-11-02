package com.buhat.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
