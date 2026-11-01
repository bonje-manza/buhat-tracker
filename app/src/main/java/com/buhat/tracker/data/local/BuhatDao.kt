package com.buhat.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
