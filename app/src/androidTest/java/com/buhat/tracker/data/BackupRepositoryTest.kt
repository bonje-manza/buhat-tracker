package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var db: BuhatDatabase
    private lateinit var repo: BuhatRepository
    private lateinit var profileStore: ProfileStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var storeScope: CoroutineScope
    private lateinit var preferences: DataStore<Preferences>

    private data class Counts(
        val exercises: Int,
        val templates: Int,
        val templateExercises: Int,
        val sessions: Int,
        val sessionExercises: Int,
        val sets: Int,
    )

    private suspend fun counts(): Counts {
        val dao = db.dao()
        return Counts(
            exercises = dao.allExercises().size,
            templates = dao.allTemplates().size,
            templateExercises = dao.allTemplateExercises().size,
            sessions = dao.allSessions().size,
            sessionExercises = dao.allSessionExercises().size,
            sets = dao.allSets().size,
        )
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "buhat-backup-${UUID.randomUUID()}.preferences_pb",
        )
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        preferences = dataStore
        profileStore = ProfileStore(dataStore)
        settingsStore = SettingsStore(dataStore)
        repo = BuhatRepository(db, settingsStore, profileStore)
        repo.seedIfNeeded()

        profileStore.save(Profile("Avery Kamp", "Female", 29, 64.5, 171))
        settingsStore.save(
            Settings(
                theme = AppTheme.LIGHT,
                unit = WeightUnit.LB,
                previousSetSource = PreviousSetSource.SAME_TEMPLATE,
                restWarmupSeconds = 45,
                restWorkingSeconds = 90,
                restFailureSeconds = 150,
                restIncrementSeconds = 10,
            ),
        )
        val sessionId = repo.startSession(seededId("template", "Push Day A"))
        val firstExercise = repo.sessionExercises(sessionId).first().first()
        repo.logSet(firstExercise.id, SetType.WARMUP, weightKg = 40.0, reps = 10, rir = null)
        repo.logSet(firstExercise.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        repo.finishSession(sessionId)
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    // Seed: 57 exercises, 6 templates, 26 template exercises. Push Day A: 3 exercises.
    // One finished session: 3 session exercises, 2 logged sets.
    private fun expectedCounts() = Counts(57, 6, 26, 1, 3, 2)

    @Test
    fun exportContainsEveryTableProfileSettingsAndNoRestState() = runTest {
        settingsStore.setRestEndsAt(1_800_000_000_000L)

        val json = repo.exportBackup()
        val backup = decodeBackup(json)

        assertEquals(1, backup.formatVersion)
        assertEquals(57, backup.exercises.size)
        assertEquals(6, backup.templates.size)
        assertEquals(26, backup.templateExercises.size)
        assertEquals(1, backup.sessions.size)
        assertEquals(3, backup.sessionExercises.size)
        assertEquals(2, backup.sets.size)
        assertEquals("Avery Kamp", backup.profile.name)
        assertEquals("LIGHT", backup.settings.theme)
        assertEquals("LB", backup.settings.unit)
        assertEquals("SAME_TEMPLATE", backup.settings.previousSetSource)
        assertEquals(90, backup.settings.restWorkingSeconds)
        assertTrue("Rest state must not be exported", !json.contains("restEndsAt"))
    }

    @Test
    fun importReplacesAllDataAndRestoresProfileAndSettings() = runTest {
        val exported = repo.exportBackup()
        val storedSet = db.dao().allSets().first()
        val empty = decodeBackup(exported).copy(
            profile = BackupProfile(),
            exercises = emptyList(),
            templates = emptyList(),
            templateExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            sets = emptyList(),
        )
        repo.importBackup(encodeBackup(empty))
        repo.seedIfNeeded()
        assertEquals(Counts(0, 0, 0, 0, 0, 0), counts())
        assertNull(profileStore.profile.first().name)

        settingsStore.setRestEndsAt(1_800_000_000_000L)
        repo.importBackup(exported)

        assertEquals(expectedCounts(), counts())
        assertEquals("Avery Kamp", profileStore.profile.first().name)
        assertEquals(AppTheme.LIGHT, settingsStore.settings.first().theme)
        assertEquals(WeightUnit.LB, settingsStore.settings.first().unit)
        assertEquals(PreviousSetSource.SAME_TEMPLATE, settingsStore.settings.first().previousSetSource)
        assertEquals(90, settingsStore.settings.first().restWorkingSeconds)
        assertNull(settingsStore.settings.first().restEndsAt)
        assertEquals(SeedContent.VERSION, settingsStore.settings.first().seedVersion)
        assertEquals(storedSet, db.dao().allSets().first { it.id == storedSet.id })
    }

    @Test
    fun corruptImportLeavesExistingDataUntouched() = runTest {
        val before = counts()

        val error = assertThrows(BackupException::class.java) { runBlocking { repo.importBackup("{ broken") } }

        assertTrue(error.message.orEmpty().contains("backup"))
        assertEquals(before, counts())
        assertEquals("Avery Kamp", profileStore.profile.first().name)
    }

    @Test
    fun newerVersionImportLeavesExistingDataUntouched() = runTest {
        val exported = repo.exportBackup()
        val newer = exported.replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        val before = counts()

        val error = assertThrows(BackupException::class.java) { runBlocking { repo.importBackup(newer) } }

        assertTrue(error.message.orEmpty().contains("newer version"))
        assertEquals(before, counts())
    }

    @Test
    fun activeSessionBlocksExportAndImport() = runTest {
        val activeId = repo.startSession(seededId("template", "Pull Day A"))
        val before = counts()

        val exportError = assertThrows(BackupException::class.java) { runBlocking { repo.exportBackup() } }
        val importError = assertThrows(BackupException::class.java) {
            runBlocking { repo.importBackup(encodeBackup(emptyBackup())) }
        }

        assertTrue(exportError.message.orEmpty().contains("Finish or discard"))
        assertTrue(importError.message.orEmpty().contains("Finish or discard"))
        assertEquals(activeId, db.dao().activeSession()?.id)
        assertEquals(before, counts())
    }

    @Test
    fun failedInsertRollsBackEveryTableAndPreservesPreferences() = runTest {
        val exported = repo.exportBackup()
        val before = decodeBackup(exported)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON LoggedSet BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )
        val error = assertThrows(BackupException::class.java) { runBlocking { repo.importBackup(exported) } }
        assertTrue(error.message.orEmpty().contains("write", ignoreCase = true))
        assertEquals(before.copy(exportedAt = 1), decodeBackup(repo.exportBackup()).copy(exportedAt = 1))
    }

    @Test
    fun capturedUserSessionRoundTripsThroughAFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = AppContainer(context, inMemory = true)
        val target = AppContainer(context, inMemory = true)
        try {
            val r = source.repository
            val exercise = r.saveExercise(null, "Belt Squat", "Quads", "Machine")
            val template = r.saveTemplate(null, "Belt Day", listOf(
                TemplateExerciseDraft(exerciseId = exercise, workingSets = 3, repMin = 6, repMax = 10, targetRir = 2),
            ))
            val session = r.startSession(template)
            val row = r.sessionExercises(session).first().single()
            r.logSet(row.id, SetType.WARMUP, 40.0, 10, null)
            r.logSet(row.id, SetType.WORKING, 80.0, 10, 2)
            r.logSet(row.id, SetType.FAILURE, 80.0, 8, 0)
            r.finishSession(session)
            r.archiveExercise(exercise)
            source.profileStore.save(Profile("Avery Kamp", "Female", 29, 64.5, 171))
            source.settingsStore.save(Settings(theme = AppTheme.LIGHT, unit = WeightUnit.LB))
            val file = File(context.cacheDir, "captured-phase6-backup.json")
            file.writeText(r.exportBackup())
            target.repository.importBackup(file.readText())
            val restored = decodeBackup(target.repository.exportBackup())
            assertEquals(listOf(1, 1, 1, 1, 1, 3), listOf(restored.exercises.size, restored.templates.size,
                restored.templateExercises.size, restored.sessions.size, restored.sessionExercises.size, restored.sets.size))
            assertEquals(listOf(40.0, 80.0, 80.0), restored.sets.map { it.weightKg })
            assertEquals(listOf(null, 1, 2), restored.sets.map { it.workingIndex })
            assertEquals(listOf(10, 10, 8), restored.sets.map { it.reps })
            assertEquals("Avery Kamp", restored.profile.name)
            assertEquals("LB", restored.settings.unit)
            assertEquals(decodeBackup(file.readText()).copy(exportedAt = 1), restored.copy(exportedAt = 1))
        } finally {
            source.close()
            target.close()
        }
    }

    @Test fun preferenceWriteFailureIsReportedAndSameFileCanBeRetried() = runTest {
        val file = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase6-backup.json").bufferedReader().use { it.readText() }
        var failWrites = true
        // Fault only the disk boundary. The real stores and Room replacement still execute.
        val failingDisk = object : DataStore<Preferences> by preferences {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failWrites) throw java.io.IOException("disk full")
                return preferences.updateData(transform)
            }
        }
        val importer = BuhatRepository(db, SettingsStore(failingDisk), ProfileStore(failingDisk))
        val error = assertThrows(BackupException::class.java) { runBlocking { importer.importBackup(file) } }
        assertTrue(error.message.orEmpty().contains("Import the same file again"))
        assertEquals(1, db.dao().allExercises().size)
        failWrites = false
        importer.importBackup(file)
        assertEquals("Avery Kamp", profileStore.profile.first().name)
        assertEquals(3, db.dao().allSets().size)
        assertEquals(15, settingsStore.settings.first().restIncrementSeconds)
    }

    private fun emptyBackup() = Backup(
        formatVersion = BACKUP_FORMAT_VERSION,
        appVersion = "1.0.0",
        exportedAt = 1_730_000_000_000L,
        profile = BackupProfile(),
        settings = Settings().toBackup(),
        exercises = emptyList(),
        templates = emptyList(),
        templateExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        sets = emptyList(),
    )
}
