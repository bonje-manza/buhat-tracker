package com.buhat.tracker.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.buhat.tracker.data.BackupException
import com.buhat.tracker.data.BackupSummary
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.MAX_BACKUP_BYTES
import com.buhat.tracker.data.Settings
import com.buhat.tracker.data.requireBackupFits
import com.buhat.tracker.timer.RestTimer
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

const val DEFAULT_IO_TIMEOUT_MILLIS = 30_000L

private const val TIMEOUT_MESSAGE = "The file operation took too long. Try again."

/**
 * Backup state lives outside the composition so an in-flight import and its retry
 * message survive activity recreation.
 */
class BackupController(
    private val repository: BuhatRepository,
    private val restTimer: RestTimer,
    private val scope: CoroutineScope,
    private val maxBackupBytes: Int = MAX_BACKUP_BYTES,
    private val ioTimeoutMillis: Long = DEFAULT_IO_TIMEOUT_MILLIS,
) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pendingImport by mutableStateOf<Pair<String, BackupSummary>?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set

    fun updateSettings(change: (Settings) -> Settings) {
        scope.launch {
            try {
                withContext(NonCancellable) { repository.settingsStore.update(change) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Could not save settings. Try again."
            }
        }
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        scope.launch {
            busy = true
            message = null
            try {
                // Encoding is O(size of the whole backup), so it must not run on the main thread.
                val bytes = withContext(Dispatchers.IO) { repository.exportBackup().toByteArray() }
                requireBackupFits(bytes.size, maxBackupBytes)
                // Cancellation cannot interrupt a blocking SAF write, so a slow-but-successful export may time out.
                withTimeout(ioTimeoutMillis) {
                    withContext(Dispatchers.IO) {
                        val stream = resolver.openOutputStream(uri, "rwt")
                            ?: throw IOException("Could not open the file.")
                        stream.use { it.write(bytes) }
                    }
                }
                message = "Backup exported."
            } catch (e: TimeoutCancellationException) {
                message = TIMEOUT_MESSAGE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = if (e is BackupException) e.message
                else "Could not write the backup file. Try another location."
            } finally {
                busy = false
            }
        }
    }

    fun importFile(resolver: ContentResolver, uri: Uri) {
        scope.launch {
            busy = true
            message = null
            try {
                // Cancellation cannot interrupt a blocking SAF read, so a slow-but-successful import may time out.
                val text = withTimeout(ioTimeoutMillis) {
                    withContext(Dispatchers.IO) { readBackupText(resolver, uri) }
                }
                pendingImport = text to repository.previewBackup(text)
                importError = null
            } catch (e: TimeoutCancellationException) {
                message = TIMEOUT_MESSAGE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = if (e is BackupException) e.message
                else "Could not read the backup file. Try selecting it again."
            } finally {
                busy = false
            }
        }
    }

    fun confirmImport() {
        val pending = pendingImport ?: return
        scope.launch {
            busy = true
            importError = null
            try {
                withContext(NonCancellable) {
                    repository.importBackup(pending.first)
                    restTimer.cancel()
                }
                pendingImport = null
                message = "Imported ${pending.second.exercises} exercises, " +
                    "${pending.second.templates} templates, ${pending.second.sessions} workouts, " +
                    "${pending.second.sets} sets."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError = if (e is BackupException) e.message else "Could not import the backup. Try again."
            } finally {
                busy = false
            }
        }
    }

    fun cancelImport() {
        if (!busy) pendingImport = null
    }
}

private fun readBackupText(resolver: ContentResolver, uri: Uri): String {
    val stream = resolver.openInputStream(uri)
        ?: throw BackupException("Could not read the backup file.")
    return stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) {
                throw BackupException("This backup file is larger than 20 MB.")
            }
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}
