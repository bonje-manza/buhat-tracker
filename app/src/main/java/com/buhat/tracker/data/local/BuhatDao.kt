package com.buhat.tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
