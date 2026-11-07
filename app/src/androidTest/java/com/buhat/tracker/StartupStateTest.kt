package com.buhat.tracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.diagnostics.DiagnosticsLog
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupStateTest {

    @Test
    fun seedFailureBecomesRetryableAndDoesNotCrash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<BuhatApplication>()
        var fail = true
        val log = DiagnosticsLog(File(context.cacheDir, "diag-${UUID.randomUUID()}"), true)
        val container = AppContainer(
            context,
            inMemory = true,
            diagnosticsLog = log,
            seed = { repository ->
                if (fail) throw IllegalStateException("disk full")
                repository.seedIfNeeded()
            },
        )
        try {
            container.startNow()
            assertTrue(container.startupState.value is StartupState.SeedError)

            fail = false
            container.startNow()
            assertEquals(StartupState.Ready, container.startupState.value)
            assertTrue(log.snapshot().contains("seed failed"))
        } finally {
            container.close()
        }
    }
}
