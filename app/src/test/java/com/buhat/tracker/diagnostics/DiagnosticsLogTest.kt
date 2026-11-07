package com.buhat.tracker.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsLogTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun log(
        debug: Boolean = true,
        clock: () -> Long = { 1_800_000_000_000L },
        maxLogBytes: Long = 256 * 1024,
        maxSnapshotChars: Int = 512 * 1024,
    ) = DiagnosticsLog(temporary.root, debug, clock, maxLogBytes, maxSnapshotChars)

    @Test
    fun `release keeps warnings and errors but drops info`() {
        val log = log(debug = false)

        log.info("backup", "export started")
        log.warn("backup", "export failed")
        log.error("database", "open failed", IllegalStateException("boom"))

        val snapshot = log.snapshot()
        assertFalse(snapshot.contains("export started"))
        assertTrue(snapshot.contains("export failed"))
        assertTrue(snapshot.contains("open failed"))
        assertTrue(snapshot.contains("IllegalStateException: boom"))
    }

    @Test
    fun `debug keeps info`() {
        val log = log(debug = true)

        log.info("backup", "export started")

        assertTrue(log.snapshot().contains("export started"))
    }

    @Test
    fun `rotation keeps the previous file`() {
        val log = log(maxLogBytes = 80)

        repeat(6) { log.info("backup", "message number $it") }

        val current = File(temporary.root, "buhat.log")
        val previous = File(temporary.root, "buhat.log.1")
        assertTrue(current.isFile)
        assertTrue(previous.isFile)
        assertTrue(current.length() <= 80)
    }

    @Test
    fun `a crash writes a report and a pending marker`() {
        val log = log()

        log.recordCrash(Thread.currentThread(), RuntimeException("boom"))

        assertTrue(log.hasPendingCrash())
        val snapshot = log.snapshot()
        assertTrue(snapshot.contains("RuntimeException: boom"))
        log.acknowledgeCrash()
        assertFalse(log.hasPendingCrash())
    }

    @Test
    fun `only the newest five crash reports are kept`() {
        var clock = 1L
        val log = log(clock = { clock++ })

        repeat(7) { log.recordCrash(Thread.currentThread(), RuntimeException("boom $it")) }

        val crashes = temporary.root.listFiles().orEmpty().filter { it.name.startsWith("crash-") }
        assertEquals(5, crashes.size)
        assertFalse(crashes.any { it.name == "crash-1.txt" })
        assertFalse(crashes.any { it.name == "crash-2.txt" })
    }

    @Test
    fun `snapshot respects its character cap`() {
        val log = log(maxSnapshotChars = 40)

        repeat(10) { log.info("backup", "a long line of diagnostic text $it") }

        assertTrue(log.snapshot().length <= 40)
    }
}
