package com.buhat.tracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.data.local.BuhatDatabase
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.local.WorkoutStatus
import com.buhat.tracker.domain.ContentValidationException
import com.buhat.tracker.domain.TemplateExerciseDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ContentRepositoryTest {

    private lateinit var db: BuhatDatabase
    private lateinit var repo: BuhatRepository
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() = runTest {
        // Isolation: in-memory Room; the device's real file database is never touched.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BuhatDatabase::class.java,
        ).build()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "buhat-content-${UUID.randomUUID()}.preferences_pb")
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        repo = BuhatRepository(db, SettingsStore(dataStore), ProfileStore(dataStore))
        repo.seedIfNeeded()
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private fun draft(
        exerciseId: String,
        name: String = "",
        sets: Int = 3,
        repMin: Int = 6,
        repMax: Int = 8,
        rir: Int? = null,
    ) = TemplateExerciseDraft(
        exerciseId = exerciseId,
        exerciseName = name,
        workingSets = sets,
        repMin = repMin,
        repMax = repMax,
        targetRir = rir,
    )

    private suspend fun premadeExercise(name: String) = repo.exercise(seededId("exercise", name))!!

    private suspend fun premadeTemplate(name: String) = repo.template(seededId("template", name))!!

    @Test
    fun customExerciseAndTemplateRoundTrip() = runTest {
        val exerciseId = repo.saveExercise(null, "Belt Squat", "Quads", "Machine")
        val templateId = repo.saveTemplate(
            null,
            "Leg Day C",
            listOf(draft(exerciseId, "Belt Squat", sets = 4, repMin = 8, repMax = 12, rir = 2)),
        )

        val saved = repo.loadTemplateDraft(templateId)!!
        assertEquals("Leg Day C", saved.name)
        assertEquals(listOf("Belt Squat"), saved.exercises.map { it.exerciseName })
        assertEquals(4, saved.exercises.single().workingSets)
        assertEquals(2, saved.exercises.single().targetRir)

        repo.saveExercise(exerciseId, "Belt Squat Machine", "Quads", "Machine")
        repo.saveTemplate(templateId, "Leg Day C+", listOf(draft(exerciseId, sets = 5)))

        assertEquals("Belt Squat Machine", repo.exercise(exerciseId)!!.name)
        assertEquals("Leg Day C+", repo.loadTemplateDraft(templateId)!!.name)
        assertEquals(5, repo.loadTemplateDraft(templateId)!!.exercises.single().workingSets)
    }

    @Test
    fun premadeTemplateIsReadOnlyAndDeletableOnlyWhenCustom() = runTest {
        val push = premadeTemplate("Push Day A")

        val editError = try {
            repo.saveTemplate(push.id, "Push Day Z", emptyList())
            null
        } catch (e: ContentValidationException) {
            e.message
        }
        assertEquals("Premade templates are read-only.", editError)

        val deleteError = try {
            repo.deleteTemplate(push.id)
            null
        } catch (e: ContentValidationException) {
            e.message
        }
        assertEquals("Premade templates are read-only.", deleteError)
    }

    @Test
    fun duplicateCopiesTheExercisesIntoACustomTemplate() = runTest {
        val source = premadeTemplate("Push Day A")
        val copyId = repo.duplicateTemplate(source.id)

        val copy = repo.template(copyId)!!
        assertFalse(copy.isPremade)
        assertEquals("Push Day A (copy)", copy.name)
        val rows = repo.loadTemplateDraft(copyId)!!.exercises
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"), rows.map { it.exerciseName })
        assertEquals(listOf(3, 3, 3), rows.map { it.workingSets })
    }

    @Test
    fun reorderAndRemoveSaveInPositionOrder() = runTest {
        val bench = premadeExercise("Bench Press")
        val fly = premadeExercise("Cable Fly")
        val templateId = repo.saveTemplate(null, "Order Test", listOf(draft(bench.id), draft(fly.id)))

        repo.saveTemplate(templateId, "Order Test", listOf(draft(fly.id), draft(bench.id)))
        assertEquals(
            listOf("Cable Fly", "Bench Press"),
            repo.loadTemplateDraft(templateId)!!.exercises.map { it.exerciseName },
        )

        repo.saveTemplate(templateId, "Order Test", listOf(draft(bench.id)))
        assertEquals(listOf("Bench Press"), repo.loadTemplateDraft(templateId)!!.exercises.map { it.exerciseName })
    }

    @Test
    fun validationRejectsBadTemplates() = runTest {
        val bench = premadeExercise("Bench Press")

        suspend fun errorFor(name: String, exercises: List<TemplateExerciseDraft>): String? =
            try {
                repo.saveTemplate(null, name, exercises)
                null
            } catch (e: ContentValidationException) {
                e.message
            }

        assertEquals("Name is required.", errorFor("  ", listOf(draft(bench.id))))
        assertEquals("That name is already used.", errorFor("push day a", listOf(draft(bench.id))))
        assertEquals("Add at least one exercise.", errorFor("Empty", emptyList()))
        assertEquals("Each exercise can appear once.", errorFor("Twice", listOf(draft(bench.id), draft(bench.id))))
        assertEquals("Sets must be 1 to 10.", errorFor("Sets", listOf(draft(bench.id, sets = 11))))
        assertEquals("Reps must be 1 to 30.", errorFor("Reps", listOf(draft(bench.id, repMin = 0))))
        assertEquals("Minimum reps cannot exceed maximum reps.", errorFor("Range", listOf(draft(bench.id, repMin = 12, repMax = 8))))
        assertEquals("RIR must be 0 to 5.", errorFor("Rir", listOf(draft(bench.id, rir = 6))))
        assertEquals(6, db.dao().templateCount()) // nothing invalid was written
    }

    @Test
    fun exerciseNameRulesMatchTheDatabase() = runTest {
        suspend fun errorFor(name: String): String? =
            try {
                repo.saveExercise(null, name, "Quads", "Machine")
                null
            } catch (e: ContentValidationException) {
                e.message
            }

        assertEquals("Name is required.", errorFor("  "))
        assertEquals("That name is already used.", errorFor("bench press"))
        assertEquals(57, db.dao().exerciseCount())
    }

    @Test
    fun premadeExerciseCannotBeEditedArchivedOrDeleted() = runTest {
        val bench = premadeExercise("Bench Press")

        val editError = try {
            repo.saveExercise(bench.id, "Bench", "Chest", "Barbell")
            null
        } catch (e: ContentValidationException) {
            e.message
        }
        assertEquals("Premade exercises cannot be edited.", editError)

        val archiveError = try {
            repo.archiveExercise(bench.id)
            null
        } catch (e: ContentValidationException) {
            e.message
        }
        assertEquals("Premade exercises cannot be edited.", archiveError)
    }

    @Test
    fun archivingHidesAnExerciseFromPickersButKeepsItsTemplateRow() = runTest {
        val exerciseId = repo.saveExercise(null, "Belt Squat", "Quads", "Machine")
        val templateId = repo.saveTemplate(null, "Belt Day", listOf(draft(exerciseId, "Belt Squat")))

        repo.archiveExercise(exerciseId)

        assertNull(repo.exercises().first().find { it.id == exerciseId })
        assertEquals(
            listOf("Belt Squat"),
            repo.loadTemplateDraft(templateId)!!.exercises.map { it.exerciseName },
        )
        assertEquals(0, repo.exercises().first().count { it.isCustom })
    }

    @Test
    fun deletingAReferencedExerciseArchivesItAndAnUnreferencedOneIsRemoved() = runTest {
        val referenced = repo.saveExercise(null, "Belt Squat", "Quads", "Machine")
        repo.saveTemplate(null, "Belt Day", listOf(draft(referenced, "Belt Squat")))

        assertEquals(DeleteExerciseResult.ARCHIVED, repo.deleteExercise(referenced))
        assertTrue(repo.exercise(referenced)!!.archivedAt != null)

        val unreferenced = repo.saveExercise(null, "Sled Push", "Quads", "Other")
        assertEquals(DeleteExerciseResult.DELETED, repo.deleteExercise(unreferenced))
        assertNull(repo.exercise(unreferenced))
    }

    @Test
    fun templateCardsCarryCountsAndExerciseNamesInOrder() = runTest {
        val push = repo.templateCards().first().single { it.template.name == "Push Day A" }

        assertEquals(3, push.exercises.size)
        assertEquals(9, push.exercises.sumOf { it.workingSets })
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"), push.exercises.map { it.exerciseName })
        assertEquals(listOf(1, 2, 3), push.exercises.map { it.position })
    }

    @Test
    fun profileStatsCountFinishedSessionsAndKeepTheEarliestDate() = runTest {
        val dao = db.dao()
        dao.insertSessions(
            listOf(
                WorkoutSession("a", "t", "Push Day A", startedAt = 100, finishedAt = 1_000, status = WorkoutStatus.FINISHED),
                WorkoutSession("b", "t", "Push Day A", startedAt = 200, finishedAt = 500, status = WorkoutStatus.FINISHED),
                WorkoutSession("c", "t", "Push Day A", startedAt = 300, finishedAt = null, status = WorkoutStatus.ACTIVE),
            ),
        )

        val stats = repo.profileStats().first()

        assertEquals(2, stats.totalWorkouts)
        assertEquals(500L, stats.firstFinishedAt)
    }
}
