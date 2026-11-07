package com.buhat.tracker.ui

import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.data.recordBenchWorkout
import com.buhat.tracker.diagnostics.DiagnosticsController
import com.buhat.tracker.diagnostics.DiagnosticsLog
import com.buhat.tracker.ui.theme.BuhatTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
    private val container get() = app.container

    @Before
    fun setUp() {
        app.resetForTest()
    }

    private fun testLog() = DiagnosticsLog(File(app.cacheDir, "diag-${UUID.randomUUID()}"), true)

    @Test
    fun diagnosticsExportContainsTheHeaderAndCrashText() = runBlocking {
        val log = testLog().apply {
            warn("backup", "export failed")
            recordCrash(Thread.currentThread(), IllegalStateException("boom"))
        }
        val controller = DiagnosticsController(
            log,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            appInfo = { "Buhat test\nDevice test" },
        )
        val file = File(app.cacheDir, "diag-${UUID.randomUUID()}.txt")

        controller.export(app.contentResolver, Uri.fromFile(file))

        withTimeout(5_000) {
            while (!file.exists() || !file.readText().contains("IllegalStateException: boom")) delay(10)
        }
        val text = file.readText()
        assertTrue(text.contains("Buhat test"))
        assertTrue(text.contains("export failed"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertFalse(text.contains("Bench Press"))

        controller.dismissCrash()
        assertFalse(controller.pendingCrash)
    }

    @Test
    fun diagnosticsExportAfterAWorkoutContainsNoTrainingData() = runBlocking {
        app.container.recordBenchWorkout()
        val file = File(app.cacheDir, "diag-${UUID.randomUUID()}.txt")

        app.container.diagnosticsController.export(app.contentResolver, Uri.fromFile(file))

        withTimeout(5_000) {
            while (!file.exists() || !file.readText().contains("Buhat")) delay(10)
        }
        val text = file.readText()
        assertFalse(text.contains("Bench Press"))
        assertFalse(text.contains("80.0"))
    }

    @Test
    fun aPendingCrashShowsANoticeThatCanBeDismissed() {
        val log = testLog().apply {
            recordCrash(Thread.currentThread(), IllegalStateException("boom"))
        }
        val controller = DiagnosticsController(
            log,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            appInfo = { "test" },
        )

        compose.setContent {
            BuhatTheme {
                BuhatApp(
                    container.repository,
                    container.profileStore,
                    container.restTimer,
                    container.backupController,
                    controller,
                )
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("crash-notice")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("crash-dismiss").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("crash-notice")).fetchSemanticsNodes().isEmpty()
        }
    }
}
