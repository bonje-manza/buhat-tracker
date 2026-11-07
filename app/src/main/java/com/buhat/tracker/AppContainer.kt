package com.buhat.tracker

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.SettingsStore
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.diagnostics.DiagnosticsController
import com.buhat.tracker.diagnostics.DiagnosticsLog
import com.buhat.tracker.timer.RestTimer
import com.buhat.tracker.ui.BackupController
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DATABASE_NAME = "buhat.db"

sealed interface StartupState {
    data object Checking : StartupState
    data object Ready : StartupState
    data class DatabaseError(val reason: DatabaseFailure) : StartupState
    data class SeedError(val message: String) : StartupState
}

enum class DatabaseFailure { DOWNGRADE, CORRUPT_OR_UNREADABLE }

fun classifyDatabaseFailure(error: Throwable): DatabaseFailure =
    if (error.message?.contains("downgrade", ignoreCase = true) == true) {
        DatabaseFailure.DOWNGRADE
    } else {
        DatabaseFailure.CORRUPT_OR_UNREADABLE
    }

class AppContainer(
    private val context: Context,
    private val inMemory: Boolean = false,
    val diagnosticsLog: DiagnosticsLog = DiagnosticsLog(File(context.filesDir, "diagnostics"), BuildConfig.DEBUG),
    private val seed: suspend (BuhatRepository) -> Unit = { it.seedIfNeeded() },
    private val databaseName: String = DATABASE_NAME,
    private val dataStoreFileOverride: File? = null,
) {

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // A test container gets its own file: a closed DataStore keeps its file registered
    // briefly, and reusing the name would fail the next reset.
    private val dataStoreFile = dataStoreFileOverride ?: if (inMemory) {
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
        Room.databaseBuilder(context, BuhatDatabase::class.java, databaseName)
            .addMigrations(BuhatDatabase.MIGRATION_1_2)
            .build()
    }
    val repository = BuhatRepository(database, settingsStore, profileStore)
    val restTimer = RestTimer(context, settingsStore)
    val backupController = BackupController(repository, restTimer, appScope, log = diagnosticsLog)
    val diagnosticsController = DiagnosticsController(
        diagnosticsLog,
        appScope,
        appInfo = {
            "Buhat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Android ${Build.VERSION.SDK_INT}\n" +
                "Device ${Build.MANUFACTURER} ${Build.MODEL}"
        },
    )

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Checking)
    val startupState: StateFlow<StartupState> = _startupState
    private var startupJob: Job? = null

    fun start() {
        startupJob?.cancel()
        startupJob = appScope.launch { startNow() }
    }

    suspend fun startNow(seedApp: Boolean = true) {
        _startupState.value = StartupState.Checking
        try {
            withContext(Dispatchers.IO) { database.openHelper.writableDatabase }
        } catch (e: Exception) {
            diagnosticsLog.error("database", "open failed", e)
            _startupState.value = StartupState.DatabaseError(classifyDatabaseFailure(e))
            return
        }
        try {
            withContext(Dispatchers.IO) {
                if (settingsStore.lastRunVersionCode.first() == null) {
                    settingsStore.acknowledgeVersion(BuildConfig.VERSION_CODE)
                }
                if (seedApp) seed(repository)
            }
            _startupState.value = StartupState.Ready
        } catch (e: Exception) {
            diagnosticsLog.error("seed", "seed failed", e)
            _startupState.value = StartupState.SeedError("Setup could not finish. Retry to finish preparing the app.")
        }
    }

    suspend fun stop() {
        startupJob?.cancelAndJoin()
        startupJob = null
    }

    suspend fun close() {
        startupJob?.cancelAndJoin()
        startupJob = null
        dataStoreScope.coroutineContext[Job]?.cancelAndJoin()
        appScope.cancel()
        database.close()
        if (inMemory && dataStoreFileOverride == null) dataStoreFile.delete()
    }
}
