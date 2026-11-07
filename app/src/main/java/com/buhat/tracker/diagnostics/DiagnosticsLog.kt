package com.buhat.tracker.diagnostics

import java.io.File
import java.time.Instant

private const val MAX_LOG_BYTES = 256L * 1024
private const val MAX_CRASH_CHARS = 64 * 1024
private const val MAX_CRASH_FILES = 5
private const val MAX_SNAPSHOT_CHARS = 512 * 1024

/**
 * App-private log and crash reports. Best effort: a failed write never throws into the caller.
 * Callers must never pass profile, exercise, workout, or backup values.
 */
class DiagnosticsLog(
    private val directory: File,
    private val debug: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxLogBytes: Long = MAX_LOG_BYTES,
    private val maxSnapshotChars: Int = MAX_SNAPSHOT_CHARS,
) {
    private val lock = Any()
    private val logFile = File(directory, "buhat.log")
    private val rotatedFile = File(directory, "buhat.log.1")
    private val pendingFile = File(directory, "crash-pending")

    fun info(tag: String, message: String) = write("I", tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        if (level == "I" && !debug) return
        val text = line(level, tag, message, error)
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                if (logFile.length() > 0 && logFile.length() + text.toByteArray().size > maxLogBytes) {
                    rotate()
                }
                logFile.appendText(text)
            }
        }
    }

    private fun line(level: String, tag: String, message: String, error: Throwable?): String =
        buildString {
            append(Instant.ofEpochMilli(clock())).append(' ').append(level).append('/')
                .append(tag).append(' ').append(message).append('\n')
            error?.let { append(it.stackTraceToString()).append('\n') }
        }

    private fun rotate() {
        rotatedFile.delete()
        logFile.renameTo(rotatedFile)
    }

    fun recordCrash(thread: Thread, throwable: Throwable) {
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                val at = clock()
                val report = File(directory, "crash-$at.txt")
                report.writeText(
                    buildString {
                        append("time=").append(Instant.ofEpochMilli(at)).append('\n')
                        append("thread=").append(thread.name).append('\n')
                        append(throwable.stackTraceToString().take(MAX_CRASH_CHARS))
                    },
                )
                pendingFile.writeText(report.name)
                pruneCrashes()
                logFile.appendText(line("E", "crash", "uncaught exception", null))
            }
        }
    }

    fun hasPendingCrash(): Boolean = pendingFile.isFile

    fun acknowledgeCrash() {
        runCatching { pendingFile.delete() }
    }

    fun snapshot(): String = synchronized(lock) {
        val builder = StringBuilder()
        fun append(file: File) {
            if (builder.length >= maxSnapshotChars || !file.isFile) return
            val text = runCatching { file.readText() }.getOrNull() ?: return
            builder.append(text.take(maxSnapshotChars - builder.length))
        }
        append(rotatedFile)
        append(logFile)
        crashFiles().forEach(::append)
        builder.toString()
    }

    private fun crashFiles(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("crash-") }
        .sortedBy { it.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: 0L }

    private fun pruneCrashes() {
        crashFiles().dropLast(MAX_CRASH_FILES).forEach { it.delete() }
    }
}
