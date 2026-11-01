package com.buhat.tracker

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.SettingsStore
import com.buhat.tracker.data.local.BuhatDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AppContainer(private val context: Context, private val inMemory: Boolean = false) {

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // A test container gets its own file: a closed DataStore keeps its file registered
    // briefly, and reusing the name would fail the next reset.
    private val dataStoreFile = if (inMemory) {
        File(context.cacheDir, "buhat-test-${UUID.randomUUID()}.preferences_pb")
    } else {
        context.preferencesDataStoreFile("buhat")
    }
    private val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
        dataStoreFile
    }

    val settingsStore = SettingsStore(dataStore)
    val profileStore = ProfileStore(dataStore)
    val database: BuhatDatabase = if (inMemory) {
        Room.inMemoryDatabaseBuilder(context, BuhatDatabase::class.java).build()
    } else {
        Room.databaseBuilder(context, BuhatDatabase::class.java, "buhat.db").build()
    }
    val repository = BuhatRepository(database, settingsStore)

    fun close() {
        database.close()
        dataStoreScope.cancel()
        if (inMemory) dataStoreFile.delete()
    }
}
