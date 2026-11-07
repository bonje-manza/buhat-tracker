package com.buhat.tracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.data.local.BuhatDatabase
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BuhatDatabase::class.java,
    )

    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    private fun newDatabaseName(): String =
        "migration-${UUID.randomUUID()}.db".also { databaseNames += it }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleString(sql: String): String? =
        query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun SupportSQLiteDatabase.indexNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    @Test
    fun migrate1To2KeepsRowsAndAddsTheHistoryIndexes() {
        val name = newDatabaseName()

        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO Exercise (id, name, nameLower, muscleGroup, equipment, isCustom, archivedAt, createdAt) " +
                    "VALUES ('e1', 'Bench Press', 'bench press', 'Chest', 'Barbell', 0, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO WorkoutSession " +
                    "(id, templateIdSnapshot, templateNameSnapshot, startedAt, finishedAt, status) " +
                    "VALUES ('s1', 't1', 'Push Day A', 1, 2, 'FINISHED')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(name, 2, true, BuhatDatabase.MIGRATION_1_2)

        migrated.use { db ->
            assertEquals(1, db.count("Exercise"))
            assertEquals("Bench Press", db.singleString("SELECT name FROM Exercise WHERE id = 'e1'"))
            assertEquals(1, db.count("WorkoutSession"))
            assertEquals(
                "Push Day A",
                db.singleString("SELECT templateNameSnapshot FROM WorkoutSession WHERE id = 's1'"),
            )
            assertEquals("FINISHED", db.singleString("SELECT status FROM WorkoutSession WHERE id = 's1'"))
            assertEquals(2L, db.singleString("SELECT finishedAt FROM WorkoutSession WHERE id = 's1'")?.toLong())

            val indexes = db.indexNames()
            assertTrue(indexes.contains("index_SessionExercise_exerciseId_sessionId"))
            assertTrue(indexes.contains("index_WorkoutSession_status_finishedAt"))
            // Every index that existed before the migration must survive it.
            assertTrue(indexes.containsAll(V1_INDEX_NAMES))
        }
    }

    private companion object {
        val V1_INDEX_NAMES = setOf(
            "index_Exercise_nameLower",
            "index_Template_nameLower",
            "index_TemplateExercise_templateId",
            "index_TemplateExercise_exerciseId",
            "index_TemplateExercise_templateId_position",
            "index_TemplateExercise_templateId_exerciseId",
            "index_WorkoutSession_status",
            "index_WorkoutSession_finishedAt",
            "index_SessionExercise_sessionId",
            "index_SessionExercise_sessionId_position",
            "index_LoggedSet_sessionExerciseId",
            "index_LoggedSet_sessionExerciseId_workingIndex",
        )
    }
}
