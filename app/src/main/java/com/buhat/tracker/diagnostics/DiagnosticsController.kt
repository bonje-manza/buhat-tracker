package com.buhat.tracker.diagnostics

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val DEFAULT_IO_TIMEOUT_MILLIS = 30_000L
private const val TIMEOUT_MESSAGE = "The file operation took too long. Try again."

class DiagnosticsController(
    private val log: DiagnosticsLog,
    private val scope: CoroutineScope,
    private val appInfo: () -> String,
    private val ioTimeoutMillis: Long = DEFAULT_IO_TIMEOUT_MILLIS,
) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pendingCrash by mutableStateOf(log.hasPendingCrash())
        private set

    fun dismissCrash() {
        log.acknowledgeCrash()
        pendingCrash = false
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        scope.launch {
            busy = true
            message = null
            try {
                withTimeout(ioTimeoutMillis) {
                    val text = withContext(Dispatchers.IO) {
                        buildString {
                            append(appInfo()).append("\n\n")
                            append(log.snapshot())
                        }
                    }
                    withContext(Dispatchers.IO) {
                        val stream = resolver.openOutputStream(uri, "rwt")
                            ?: throw IOException("Could not open the file.")
                        stream.use { it.write(text.toByteArray()) }
                    }
                }
                log.info("diagnostics", "export succeeded")
                message = "Diagnostics exported."
            } catch (e: TimeoutCancellationException) {
                log.warn("diagnostics", "export timed out")
                message = TIMEOUT_MESSAGE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("diagnostics", "export failed", e)
                message = "Could not write the diagnostics file."
            } finally {
                busy = false
            }
        }
    }
}
