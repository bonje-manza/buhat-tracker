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
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.data.*
import com.buhat.tracker.ui.screens.SettingsScreen
import com.buhat.tracker.ui.theme.BuhatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class BackupScreenTest {
    @get:Rule val compose = createComposeRule()
    private val restoration = StateRestorationTester(compose)
    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
    private val repo get() = app.container.repository
    private lateinit var file: File
    private lateinit var captured: String
    private var cancelPicker = false

    @Before fun setUp() {
        app.resetForTest()
        captured = InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase6-backup.json").bufferedReader().use { it.readText() }
        runBlocking { repo.importBackup(captured) }
        file = File(app.cacheDir, "screen-backup.json")
    }

    // Only substitute the system picker. The file, screen, codec and stores are real.
    private fun startScreen(controller: BackupController = app.container.backupController) {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                val intent = contract.createIntent(app, input)
                assertTrue(intent.action in listOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT))
                assertEquals("application/json", if (intent.action == Intent.ACTION_CREATE_DOCUMENT) intent.type
                    else intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.single())
                dispatchResult(requestCode, if (cancelPicker) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    Intent().setData(Uri.fromFile(file)))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BuhatTheme { SettingsScreen(repo, controller, onBack = {}, onOpenProfile = {}) }
            }
        }
    }

    @After fun cleanUp() { file.delete() }

    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun testController(
        maxBackupBytes: Int = MAX_BACKUP_BYTES,
        ioTimeoutMillis: Long = DEFAULT_IO_TIMEOUT_MILLIS,
    ) = BackupController(
        repo, app.container.restTimer, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        maxBackupBytes, ioTimeoutMillis,
    )

    @Test fun fileRoundTripRequiresConfirmationAndReloadsProfile() {
        startScreen()
        click("export-data")
        awaitText("Backup exported.")
        val exported = decodeBackup(file.readText())
        assertEquals("Avery Kamp", exported.profile.name)
        assertEquals(listOf(40.0, 80.0, 80.0), exported.sets.map { it.weightKg })
        runBlocking {
            repo.profileStore.save(Profile("Local profile", null, null, null, null))
            repo.saveExercise(null, "Local only", "Chest", "Machine")
            val endsAt = System.currentTimeMillis() + 60_000
            repo.settingsStore.setRestEndsAt(endsAt)
            app.container.restTimer.start(endsAt)
        }
        click("import-data")
        awaitText("1 exercises, 1 templates, 1 workouts, 3 sets.")
        assertEquals("Local profile", runBlocking { repo.profileStore.profile.first().name })
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, runBlocking { decodeBackup(repo.exportBackup()).exercises.size })
        click("import-data")
        awaitText("Replace all local data?")
        compose.onNodeWithTag("confirm-import").performClick()
        awaitText("Imported 1 exercises")
        assertNull(app.getSystemService(android.app.AlarmManager::class.java).nextAlarmClock)
        assertEquals(exported.copy(exportedAt = 1), runBlocking { decodeBackup(repo.exportBackup()).copy(exportedAt = 1) })
        compose.onNodeWithTag("settings-profile").performScrollTo()
        compose.onNodeWithText("Avery Kamp").assertIsDisplayed()
    }

    @Test fun invalidFilesAndCancelledPickerLeaveDataAlone() {
        startScreen()
        val before = runBlocking { decodeBackup(repo.exportBackup()).copy(exportedAt = 1) }
        for ((text, message) in listOf(
            "{}" to "not a Buhat backup",
            captured.take(captured.length / 2) to "corrupt",
            captured.replace("\"formatVersion\": 1", "\"formatVersion\": 2") to "newer version",
        )) {
            file.writeText(text)
            click("import-data")
            awaitText(message)
            compose.onNodeWithTag("confirm-import").assertDoesNotExist()
            assertEquals(before, runBlocking { decodeBackup(repo.exportBackup()).copy(exportedAt = 1) })
        }
        cancelPicker = true
        click("import-data")
        compose.onNodeWithTag("confirm-import").assertDoesNotExist()
    }

    @Test fun exportWriteFailureIsSpecificAndImportFailureIsVisibleInDialog() {
        startScreen()
        file.mkdir()
        click("export-data")
        awaitText("Could not write the backup file")
        file.delete()
        file.writeText(captured)
        click("import-data")
        awaitText("Replace all local data?")
        app.container.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON LoggedSet BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )
        compose.onNodeWithTag("confirm-import").performClick()
        awaitText("Could not write the backup.")
        compose.onNodeWithTag("import-error").assertIsDisplayed()
    }

    @Test fun importFailureAndRetrySurviveScreenRecreation() {
        startScreen()
        file.writeText(captured)
        click("import-data")
        awaitText("Replace all local data?")
        app.container.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON LoggedSet BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )
        compose.onNodeWithTag("confirm-import").performClick()
        awaitText("Could not write the backup.")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("import-error").assertIsDisplayed()
        compose.onNodeWithTag("confirm-import").assertIsEnabled()
        app.container.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_import")
        compose.onNodeWithTag("confirm-import").performClick()
        awaitText("Imported 1 exercises")
        assertEquals("Avery Kamp", runBlocking { repo.profileStore.profile.first().name })
    }

    @Test fun exportOverTheLimitFailsBeforeTouchingTheChosenFile() {
        startScreen(testController(maxBackupBytes = 64))
        file.writeText("keep me")
        click("export-data")
        awaitText("This backup is larger than 20 MB. Export is not possible.")
        assertEquals("keep me", file.readText())
        compose.onNodeWithTag("export-data").assertIsEnabled()
    }

    @Test fun timedOutExportReportsAndUnlocksTheScreen() {
        startScreen(testController(ioTimeoutMillis = 0))
        file.writeText("keep me")
        click("export-data")
        awaitText("The file operation took too long. Try again.")
        assertEquals("keep me", file.readText())
        compose.onNodeWithTag("backup-busy").assertDoesNotExist()
        compose.onNodeWithTag("export-data").assertIsEnabled()
    }

    @Test fun timedOutImportReportsAndUnlocksTheScreen() {
        startScreen(testController(ioTimeoutMillis = 0))
        file.writeText(captured)
        click("import-data")
        awaitText("The file operation took too long. Try again.")
        compose.onNodeWithTag("confirm-import").assertDoesNotExist()
        compose.onNodeWithTag("backup-busy").assertDoesNotExist()
        compose.onNodeWithTag("import-data").assertIsEnabled()
    }
}
