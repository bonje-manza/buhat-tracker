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
import com.buhat.tracker.BuildConfig
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.ui.theme.BuhatTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdatePromptExportTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
    private lateinit var file: File

    @Before
    fun setUp() {
        app.resetForTest()
        file = File(app.cacheDir, "update-export-${UUID.randomUUID()}.json")
        runBlocking {
            app.container.settingsStore.acknowledgeVersion(BuildConfig.VERSION_CODE - 1)
        }
    }

    @Test
    fun successfulExportAcknowledgesTheVersion() {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme {
                    BuhatApp(
                        app.container.repository,
                        app.container.profileStore,
                        app.container.restTimer,
                        app.container.backupController,
                        app.container.diagnosticsController,
                    )
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("update-prompt")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("update-export").performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.container.settingsStore.lastRunVersionCode.first() } == BuildConfig.VERSION_CODE
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("update-prompt")).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(file.exists())
    }
}
