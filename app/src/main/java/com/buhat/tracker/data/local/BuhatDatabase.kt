package com.buhat.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Template::class,
        TemplateExercise::class,
        WorkoutSession::class,
        SessionExercise::class,
        LoggedSet::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BuhatDatabase : RoomDatabase() {
    abstract fun dao(): BuhatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_SessionExercise_exerciseId_sessionId " +
                        "ON SessionExercise(exerciseId, sessionId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_WorkoutSession_status_finishedAt " +
                        "ON WorkoutSession(status, finishedAt)",
                )
            }
        }
    }
}
