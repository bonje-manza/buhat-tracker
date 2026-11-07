package com.buhat.tracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerCloseTest {

    @Test
    fun closingAndReopeningTheSameDataStoreFileDoesNotCollide() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<BuhatApplication>()
        val file = File(context.cacheDir, "close-test-${UUID.randomUUID()}.preferences_pb")
        val first = AppContainer(
            context = context,
            inMemory = true,
            dataStoreFileOverride = file,
        )
        first.settingsStore.markExported(123L)
        first.close()

        val second = AppContainer(
            context = context,
            inMemory = true,
            dataStoreFileOverride = file,
        )
        try {
            assertEquals(123L, second.settingsStore.lastExportAt.first())
        } finally {
            second.close()
        }
    }

    @Test
    fun closeAndDeleteRemovesTheRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<BuhatApplication>()
        val name = "delete-test-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "delete-test-${UUID.randomUUID()}.preferences_pb")
        val first = AppContainer(
            context = context,
            inMemory = false,
            databaseName = name,
            dataStoreFileOverride = file,
        )
        first.startNow(seedApp = false)
        first.repository.saveExercise(null, "Belt Squat", "Quads", "Machine")
        first.close()
        val deleted = context.deleteDatabase(name)
        assertTrue("deleteDatabase returned false", deleted)
        assertFalse("database file still exists", context.getDatabasePath(name).exists())

        val second = AppContainer(
            context = context,
            inMemory = false,
            databaseName = name,
            dataStoreFileOverride = file,
        )
        try {
            second.startNow(seedApp = false)
            assertEquals("rows after reopening the deleted database", 0, second.database.dao().exerciseCount())
            second.startNow(seedApp = true)
            val names = second.database.dao().allExercises().map { it.name }
            val seedNames = com.buhat.tracker.data.SeedContent.exercises.map { it.name }.toSet()
            val extra = names.filter { it !in seedNames }
            assertTrue("Extra exercises: $extra", extra.isEmpty())
            assertEquals(57, second.database.dao().exerciseCount())
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }
}
