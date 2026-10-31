package com.buhat.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Exercise::class,
        Template::class,
        TemplateExercise::class,
        WorkoutSession::class,
        SessionExercise::class,
        LoggedSet::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BuhatDatabase : RoomDatabase() {
    abstract fun dao(): BuhatDao
}
