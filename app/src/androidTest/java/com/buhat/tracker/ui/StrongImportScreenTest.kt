package com.buhat.tracker.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.data.StrongMapping
import com.buhat.tracker.data.seededId
import com.buhat.tracker.ui.screens.SettingsScreen
import com.buhat.tracker.ui.screens.StrongImportScreen
import com.buhat.tracker.ui.theme.BuhatTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrongImportScreenTest {

    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
    private val repo get() = app.container.repository
    private lateinit var file: File
    private lateinit var fixture: String

    @Before
    fun setUp() {
        app.resetForTest()
        fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("strong-export-sanitized.csv").bufferedReader().use { it.readText() }
        file = File(app.cacheDir, "strong-export-${System.nanoTime()}.csv")
        file.writeText(fixture)
    }

    @After
    fun cleanUp() {
        file.delete()
    }

    // Only the system picker is substituted. The file, screen, repository and database are real.
    private fun pickerOwner(): ActivityResultRegistryOwner {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                val intent = contract.createIntent(app, input)
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
                assertTrue(
                    intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty().contains("text/csv"),
                )
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            }
        }
        return object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
    }

    private fun startStrongScreen() {
        val owner = pickerOwner()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme { StrongImportScreen(repo, onBack = {}) }
            }
        }
    }

    private fun startFromSettings() {
        val owner = pickerOwner()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme {
                    var showImport by remember { mutableStateOf(false) }
                    if (showImport) {
                        StrongImportScreen(repo, onBack = { showImport = false })
                    } else {
                        SettingsScreen(
                            repository = repo,
                            backupController = app.container.backupController,
                            diagnosticsController = app.container.diagnosticsController,
                            onBack = {},
                            onOpenProfile = {},
                            onImportStrong = { showImport = true },
                        )
                    }
                }
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun previewMapAndImportAppendsWorkouts() {
        startFromSettings()
        compose.onNodeWithTag("import-strong").performScrollTo().performClick()
        awaitTag("strong-choose-file")

        compose.onNodeWithTag("strong-choose-file").performClick()
        awaitText("New workouts: 33")
        compose.onNodeWithText("Needs mapping", substring = true).assertExists()
        compose.onNodeWithTag("strong-confirm").assertIsNotEnabled()

        compose.onNodeWithTag("strong-source-Seated Row (Cable)").performScrollTo().performClick()
        awaitTag("strong-picker-search")
        compose.onNodeWithTag("strong-existing-Seated Cable Row").performScrollTo().performClick()
        awaitText("Matched to Seated Cable Row")
        compose.onNodeWithTag("strong-confirm").performScrollTo().assertIsEnabled().performClick()

        awaitText("Imported 33 workouts")
        compose.onNodeWithTag("strong-result").assertTextContains("Imported 33 workouts", substring = true)
        val history = runBlocking { repo.history().first() }
        assertEquals(33, history.size)
        val first = history.first { it.templateNameSnapshot == "FBEOD" }
        assertEquals("strong-import", first.templateIdSnapshot)
        assertTrue(first.exercises.first().note.orEmpty().contains("Felt strong; bar path"))
    }

    @Test
    fun reimportReportsZeroNewAndHidesConfirm() {
        runBlocking {
            repo.importStrong(
                fixture,
                listOf(
                    StrongMapping(
                        sourceName = "Seated Row (Cable)",
                        existingExerciseId = seededId("exercise", "Seated Cable Row"),
                    ),
                ),
            )
        }
        startStrongScreen()
        compose.onNodeWithTag("strong-choose-file").performClick()

        awaitText("No new workouts. Skipped 33 already imported.")
        compose.onNodeWithTag("strong-confirm").assertDoesNotExist()
        compose.onNodeWithTag("strong-preview").assertExists()
    }

    @Test
    fun allWarmupFileReportsSkippedEmpty() {
        file.writeText(
            "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;" +
                "Distance (meters);Seconds;Notes;Workout Notes\r\n" +
                "1;2026-06-24 08:00:00;FBEOD;3600;Bench Press;W;40;10;;;;;\r\n",
        )
        startStrongScreen()
        compose.onNodeWithTag("strong-choose-file").performClick()

        awaitText("No new workouts. Skipped 1 with no working sets.")
        compose.onNodeWithTag("strong-confirm").assertDoesNotExist()
    }

    @Test
    fun unsupportedRowReportsTheRowAndWritesNothing() {
        file.writeText(
            "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;" +
                "Distance (meters);Seconds;Notes;Workout Notes\r\n" +
                "1;2026-06-24 08:00:00;FBEOD;3600;Bench Press;1;80;5;8;100;;;\r\n",
        )
        startStrongScreen()
        compose.onNodeWithTag("strong-choose-file").performClick()

        awaitTag("strong-error")
        compose.onNodeWithTag("strong-error").assertTextContains("Row", substring = true)
        assertEquals(0, runBlocking { repo.history().first().size })
    }

    @Test
    fun activeWorkoutDisablesTheSettingsEntry() {
        runBlocking { repo.startSession(seededId("template", "Push Day A")) }
        val owner = pickerOwner()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme {
                    SettingsScreen(
                        repository = repo,
                        backupController = app.container.backupController,
                        diagnosticsController = app.container.diagnosticsController,
                        onBack = {},
                        onOpenProfile = {},
                        onImportStrong = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("import-strong").performScrollTo().assertIsNotEnabled()
    }
}
