package com.buhat.tracker

import android.app.Application
import android.os.Process
import com.buhat.tracker.diagnostics.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val RECOVERY_IMPORT_ERROR =
    "Training data was reset, but the backup did not import. Your backup file is unchanged. Import it again from Settings."

class BuhatApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recoveryMutex = Mutex()
    private val _containerGeneration = MutableStateFlow(0)
    val containerGeneration: StateFlow<Int> = _containerGeneration
    private val _recoveryError = MutableStateFlow<String?>(null)
    val recoveryError: StateFlow<String?> = _recoveryError

    lateinit var diagnosticsLog: DiagnosticsLog
        private set
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        diagnosticsLog = DiagnosticsLog(File(filesDir, "diagnostics"), BuildConfig.DEBUG)
        installCrashHandler()
        container = newContainer()
        container.start()
    }

    private fun newContainer(inMemory: Boolean = false) =
        AppContainer(this, inMemory = inMemory, diagnosticsLog = diagnosticsLog)

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            diagnosticsLog.recordCrash(thread, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    // Test-only hook (see .opencode/skills/e2e-testing, Isolation): swap in an in-memory
    // database so a UI test starts from a known state, then seed it synchronously.
    fun resetForTest() {
        runBlocking { container.close() }
        diagnosticsLog.acknowledgeCrash()
        container = newContainer(inMemory = true)
        runBlocking { container.startNow() }
        _containerGeneration.value++
    }

    fun resetLocalData() {
        appScope.launch {
            recoveryMutex.withLock { replaceContainer(importText = null) }
        }
    }

    fun recoverWithBackup(text: String) {
        appScope.launch {
            recoveryMutex.withLock { replaceContainer(importText = text) }
        }
    }

    fun dismissRecoveryError() {
        _recoveryError.value = null
    }

    private suspend fun replaceContainer(importText: String?) {
        val old = container
        old.stop()
        old.settingsStore.clearAll()
        old.close()
        deleteDatabase(DATABASE_NAME)
        val fresh = newContainer()
        if (importText == null) {
            fresh.startNow(seedApp = true)
            _recoveryError.value = null
        } else {
            fresh.startNow(seedApp = false)
            runCatching { fresh.repository.importBackup(importText) }
                .onSuccess { _recoveryError.value = null }
                .onFailure {
                    diagnosticsLog.error("recovery", "import failed", it)
                    _recoveryError.value = RECOVERY_IMPORT_ERROR
                    runCatching { fresh.startNow(seedApp = true) }
                }
        }
        container = fresh
        _containerGeneration.value++
    }
}
