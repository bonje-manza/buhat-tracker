package com.buhat.tracker.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.DatabaseFailure
import com.buhat.tracker.ui.screens.RecoveryScreen
import com.buhat.tracker.ui.theme.BuhatTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
    private lateinit var importFile: File
    private lateinit var exportFile: File
    private var resetCalls = 0
    private var imported: String? = null

    @Before
    fun setUp() {
        app.resetForTest()
        importFile = File(app.cacheDir, "recovery-${UUID.randomUUID()}.json")
        exportFile = File(app.cacheDir, "recovery-${UUID.randomUUID()}.txt")
        resetCalls = 0
        imported = null
    }

    private fun startScreen(importText: String) {
        importFile.writeText(importText)
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val intent = contract.createIntent(app, input)
                when (intent.action) {
                    Intent.ACTION_OPEN_DOCUMENT ->
                        dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(importFile)))
                    Intent.ACTION_CREATE_DOCUMENT ->
                        dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(exportFile)))
                    else -> dispatchResult(requestCode, Activity.RESULT_CANCELED, Intent())
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme {
                    RecoveryScreen(
                        reason = DatabaseFailure.DOWNGRADE,
                        diagnosticsController = app.container.diagnosticsController,
                        onReset = { resetCalls++ },
                        onImport = { imported = it },
                    )
                }
            }
        }
    }

    private fun capturedBackup(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase6-backup.json").bufferedReader().use { it.readText() }

    @Test
    fun aValidBackupShowsThePreviewAndImportsAfterConfirmation() {
        val captured = capturedBackup()
        startScreen(captured)

        compose.onNodeWithTag("recovery-import").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("recovery-confirm-import")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recovery-confirm-import").performClick()

        assertEquals(captured, imported)
    }

    @Test
    fun anInvalidBackupShowsTheErrorAndDoesNotOfferConfirmation() {
        startScreen("{}")

        compose.onNodeWithTag("recovery-import").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("recovery-error")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recovery-confirm-import").assertDoesNotExist()
        assertNull(imported)
    }

    @Test
    fun resetRequiresConfirmation() {
        startScreen("{}")

        compose.onNodeWithTag("recovery-reset").performClick()
        compose.onNodeWithTag("recovery-confirm-reset").performClick()

        assertEquals(1, resetCalls)
    }

    @Test
    fun diagnosticsExportWritesToTheChosenFile() {
        startScreen("{}")

        compose.onNodeWithTag("recovery-export").performClick()
        compose.waitUntil(5_000) { exportFile.exists() && exportFile.readText().contains("Buhat") }

        assertTrue(exportFile.readText().contains("Buhat"))
    }
}
