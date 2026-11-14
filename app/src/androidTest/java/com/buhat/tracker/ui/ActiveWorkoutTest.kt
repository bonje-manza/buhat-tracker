package com.buhat.tracker.ui

import android.Manifest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.seededId
import com.buhat.tracker.domain.SetSide
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.lbToKg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ActiveWorkoutTest {

    // Reset the container before the activity launches, so the UI never reads a closed database.
    @get:Rule(order = 0)
    val reset = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
                app.resetForTest()
                // Timer/permission behavior has its own suite; keep logging tests out of the system prompt.
                runBlocking { app.container.settingsStore.update { it.copy(restWorkingSeconds = 0) } }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun grantNotifications() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<BuhatApplication>().packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun startPushDayA() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        awaitTag("start-workout-Push Day A")
        compose.onNodeWithTag("start-workout-Push Day A").performClick()
        awaitTag("weight-input")
    }

    /** Captured fixture: the user's last Bench Press session was 80 kg for 10, 9, 8 reps. */
    private fun seedPreviousBenchSession() = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
        val session = repo.startSession(seededId("template", "Push Day A"))
        val bench = repo.sessionExercises(session).first().first()
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 10, rir = 2)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 9, rir = 2)
        repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 2)
        repo.finishSession(session)
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitPrescription(name: String, workingSets: Int, repMin: Int, repMax: Int) {
        compose.waitUntil(5_000) {
            runBlocking {
                val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
                val session = repo.activeSession().first() ?: return@runBlocking false
                val row = repo.sessionExercises(session.id).first().singleOrNull {
                    it.exerciseNameSnapshot == name
                }
                row != null && row.workingSets == workingSets &&
                    row.repMin == repMin && row.repMax == repMax
            }
        }
    }

    @Test
    fun idleRestCardExplainsAutomaticStartWithoutSkip() {
        startPushDayA()
        compose.onNodeWithText("Ready for next set").assertIsDisplayed()
        compose.onNodeWithText("Starts automatically after each set").assertIsDisplayed()
        compose.onNodeWithTag("skip-rest").assertDoesNotExist()
    }

    @Test
    fun loggingCardCarriesTheDesignRirHint() {
        startPushDayA()
        compose.onNodeWithText("RIR · 0 = failure, 1 = one more rep possible").assertExists()
    }

    @Test
    fun rirSelectorOffersExactlyZeroThroughFourPlus() {
        startPushDayA()
        for (tag in listOf("rir-0", "rir-1", "rir-2", "rir-3", "rir-4+")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("rir-5").assertDoesNotExist()
        compose.onNodeWithTag("rir-2").assertIsSelected()
    }

    @Test
    fun loggingFourPlusStoresFourAndTheRowReadsFourPlus() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("rir-4+").performScrollTo().performClick()
        compose.onNodeWithTag("log-set").performScrollTo().performClick()

        awaitTag("set-row-1")
        compose.onNodeWithText("RIR 4+").assertExists()
        val stored = runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val exercise = repo.sessionExercises(session.id).first().first()
            repo.loggedSets(exercise.id).first().single().rir
        }
        assertEquals(4, stored)
    }

    @Test
    fun storedRirFiveRendersAsFourPlusInTheLoggedRow() {
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.startSession(seededId("template", "Push Day A"))
            val bench = repo.sessionExercises(session).first().first()
            repo.logSet(bench.id, SetType.WORKING, weightKg = 80.0, reps = 8, rir = 5)
        }
        compose.onNodeWithTag("tab-Active").performClick()

        awaitTag("set-row-1")
        compose.onNodeWithText("RIR 4+").assertExists()
    }

    @Test
    fun targetCardRendersStoredFiveAsFourPlus() {
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val template = repo.saveTemplate(
                id = null,
                name = "RIR five day",
                exercises = listOf(
                    TemplateExerciseDraft(
                        exerciseId = seededId("exercise", "Bench Press"),
                        workingSets = 1,
                        repMin = 6,
                        repMax = 8,
                        targetRir = 5,
                    ),
                ),
            )
            repo.startSession(template)
        }
        compose.onNodeWithTag("tab-Active").performClick()

        awaitTag("target-card")
        compose.onNodeWithText("Target RIR 4+").assertExists()
    }

    @Test
    fun setEditDialogUsesTheSameFourPlusSelector() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()

        awaitTag("set-row-1")
        compose.onNodeWithTag("set-row-1").performScrollTo().performClick()
        for (tag in listOf("edit-rir-0", "edit-rir-1", "edit-rir-2", "edit-rir-3", "edit-rir-4+")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("edit-rir-5").assertDoesNotExist()
        compose.onNodeWithTag("edit-rir-2").assertIsSelected()
    }

    @Test
    fun numericInputsHaveNamed48dpTouchTargetsIncludingTheEditor() {
        seedPreviousBenchSession()
        startPushDayA()
        awaitTag("weight-input")
        for ((tag, label) in listOf("weight-input" to "Weight (kg)", "reps-input" to "Reps")) {
            compose.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp)
                .assertContentDescriptionEquals(label)
        }
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("set-row-1").performScrollTo().performClick()
        for ((tag, label) in listOf("edit-weight-input" to "Weight (kg)", "edit-reps-input" to "Reps")) {
            compose.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertContentDescriptionEquals(label)
        }
    }

    @Test
    fun logsAWorkingSetAndShowsTheNextPositionTarget() {
        seedPreviousBenchSession()
        startPushDayA()

        // Position 1 source was 10 reps at 80 kg on a 6-8 exercise: 80 + 2.5 kg, restart at 6.
        awaitText("82.5 kg × 6")
        compose.onNodeWithText("Top of range reached. Add 2.5 kg, restart at 6.").assertExists()
        // Prefill is the raw source, not the target.
        compose.onNodeWithTag("weight-input").assertTextContains("80")
        compose.onNodeWithTag("reps-input").assertTextContains("10")

        compose.onNodeWithTag("weight-input").performTextReplacement("82.5")
        compose.onNodeWithTag("reps-input").performTextReplacement("7")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()

        awaitText("82.5 kg × 7")
        compose.onNodeWithText("RIR 2").assertExists()
        // Position 2 source was 9 reps at 80 kg: still top of range.
        compose.onNodeWithText("82.5 kg × 6").assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        awaitText("Push Day A")
        awaitText("82.5 kg × 7")
    }

    @Test
    fun lbExerciseLogsAndTargetsInPounds() {
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            repo.saveExercisePreferences(seededId("exercise", "Bench Press"), "LB", null, null, false)
            val session = repo.startSession(seededId("template", "Push Day A"))
            val bench = repo.sessionExercises(session).first().first()
            repo.logSet(bench.id, SetType.WORKING, weightKg = lbToKg(135.0), reps = 10, rir = 2)
            repo.finishSession(session)
        }
        startPushDayA()

        // 135 lb top of range: 135 + 5 lb, restart at 6.
        awaitText("140 lb × 6")
        compose.onNodeWithText("Top of range reached. Add 5 lb, restart at 6.").assertExists()
        compose.onNodeWithTag("target-exercise-settings").assertExists()
        compose.onNodeWithTag("weight-input").assertTextContains("135")
        compose.onNodeWithTag("weight-input-plus").performClick()
        compose.onNodeWithTag("weight-input").assertTextContains("140")

        compose.onNodeWithTag("weight-input").performTextReplacement("135")
        compose.onNodeWithTag("reps-input").performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitText("135 lb × 8")

        val stored = runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val exercise = repo.sessionExercises(session.id).first().first()
            repo.loggedSets(exercise.id).first().last().weightKg
        }
        assertEquals(lbToKg(135.0), stored, 0.0001)
    }

    @Test
    fun eachExerciseUsesItsOwnUnitInTheSameWorkout() {
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            repo.saveExercisePreferences(seededId("exercise", "Bench Press"), "LB", null, null, false)
            repo.saveExercisePreferences(seededId("exercise", "Leg Extension"), "KG", null, null, false)
            val template = repo.saveTemplate(
                id = null,
                name = "Mixed units day",
                exercises = listOf(
                    TemplateExerciseDraft(
                        exerciseId = seededId("exercise", "Bench Press"),
                        workingSets = 1,
                        repMin = 6,
                        repMax = 10,
                        targetRir = 2,
                    ),
                    TemplateExerciseDraft(
                        exerciseId = seededId("exercise", "Leg Extension"),
                        workingSets = 1,
                        repMin = 6,
                        repMax = 10,
                        targetRir = 2,
                    ),
                ),
            )
            repo.startSession(template)
        }
        compose.onNodeWithTag("tab-Active").performClick()
        awaitTag("weight-input")

        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitText("100 lb × 8")

        compose.onNodeWithTag("exercise-chip-Leg Extension").performScrollTo().performClick()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("50")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitText("50 kg × 8")

        compose.onNodeWithTag("exercise-chip-Bench Press").performScrollTo().performClick()
        awaitText("100 lb × 8")
    }

    @Test
    fun targetCardSettingsChangesUnitMidSessionWithoutTouchingTheLoggedSetOrRest() {
        seedPreviousBenchSession()
        runBlocking {
            ApplicationProvider.getApplicationContext<BuhatApplication>().container.settingsStore
                .update { it.copy(restWorkingSeconds = 120) }
        }
        startPushDayA()
        awaitText("82.5 kg × 6")
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("10")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")

        awaitTag("skip-rest")
        val restBefore = runBlocking {
            ApplicationProvider.getApplicationContext<BuhatApplication>().container
                .settingsStore.settings.first().restEndsAt
        }
        assertNotNull(restBefore)

        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        awaitTag("exercise-settings")
        compose.onNodeWithTag("segmented-lb").performClick()
        compose.onNodeWithTag("pref-rest-value-plus").performClick()
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("exercise-settings")).fetchSemanticsNodes().isEmpty()
        }

        // The logged 80 kg now reads 176.4 lb; the next target adds 5 lb. The running rest is untouched.
        awaitText("181.4 lb × 6")
        compose.onNodeWithText("Top of range reached. Add 5 lb, restart at 6.").assertExists()
        compose.onNodeWithTag("weight-input").assertTextContains("176.4")

        val after = runBlocking {
            val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
            val session = app.container.repository.activeSession().first()!!
            val exercise = app.container.repository.sessionExercises(session.id).first().first()
            Triple(
                app.container.repository.exercise(exercise.exerciseId)!!,
                app.container.repository.loggedSets(exercise.id).first().single().weightKg,
                app.container.settingsStore.settings.first().restEndsAt,
            )
        }
        assertEquals("LB", after.first.weightUnitOverride)
        assertEquals(135, after.first.restWorkingSecondsOverride)
        assertEquals(80.0, after.second, 0.0001)
        assertEquals(restBefore, after.third)
    }

    @Test
    fun exerciseNoteEditsPersistIntoNextWorkoutAndClearWithoutChangingHistory() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        runBlocking {
            container.repository.saveExercisePreferences(seededId("exercise", "Bench Press"), null, null, null, true)
        }
        startPushDayA()
        compose.onNodeWithTag("exercise-note").assertDoesNotExist()
        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement("Keep elbows tucked")
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        awaitText("Keep elbows tucked")
        compose.onNodeWithTag("exercise-note").performScrollTo().assertTextEquals("Keep elbows tucked")
        assertTrue(runBlocking { container.repository.exercise(seededId("exercise", "Bench Press"))!!.isUnilateral })

        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").assertTextContains("Keep elbows tucked")
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement("Unsaved draft")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("exercise-note").assertTextEquals("Keep elbows tucked")

        compose.activityRule.scenario.recreate()
        awaitTag("exercise-note")
        compose.onNodeWithTag("exercise-note").assertTextEquals("Keep elbows tucked")
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("active-empty")
        startPushDayA()
        awaitTag("exercise-note")
        compose.onNodeWithTag("exercise-note").assertTextEquals("Keep elbows tucked")
        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement(" \n\t")
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("exercise-settings")).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("exercise-note").assertDoesNotExist()
        runBlocking {
            assertNull(container.repository.exercise(seededId("exercise", "Bench Press"))!!.note)
            assertTrue(container.repository.exercise(seededId("exercise", "Bench Press"))!!.isUnilateral)
            assertNull(container.repository.history().first().single().exercises.first().note)
            assertEquals(false, container.repository.activeSession().first()!!.structureDirty)
        }
    }

    @Test
    fun exerciseNoteLimitRejectsOverlongDraftWithoutTruncating() {
        startPushDayA()
        awaitTag("target-exercise-settings")
        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement("n".repeat(2000))
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        awaitTag("exercise-note")
        compose.onNodeWithTag("exercise-note").assertTextEquals("n".repeat(2000))
        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement("n".repeat(2001))
        compose.onNodeWithTag("save-exercise-prefs").assertIsNotEnabled()
        compose.onNodeWithTag("pref-note").assertTextContains("n".repeat(2001))
        compose.onNodeWithText("Note must be 2000 characters or fewer.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("n".repeat(2000), runBlocking {
            ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
                .exercise(seededId("exercise", "Bench Press"))!!.note
        })
    }

    @Test
    fun failedExerciseNoteSaveKeepsDraftAndCanRetry() {
        startPushDayA()
        val database = ApplicationProvider.getApplicationContext<BuhatApplication>().container.database
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_note BEFORE UPDATE ON Exercise BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )
        compose.onNodeWithTag("target-exercise-settings").performScrollTo().performClick()
        compose.onNodeWithTag("pref-note").performScrollTo().performTextReplacement("Keep elbows tucked")
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        awaitText("Could not save exercise settings. Try again.")
        compose.onNodeWithTag("pref-note").assertTextContains("Keep elbows tucked")
        assertNull(runBlocking { database.dao().exerciseById(seededId("exercise", "Bench Press"))!!.note })
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_note")
        compose.onNodeWithTag("save-exercise-prefs").performClick()
        awaitTag("exercise-note")
        compose.onNodeWithTag("exercise-note").assertTextEquals("Keep elbows tucked")
    }

    @Test
    fun selectingExerciseHidesPreviousNoteWhileItsDatabaseReadIsPending() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        runBlocking {
            container.repository.saveExercisePreferences(
                seededId("exercise", "Bench Press"), null, null, "Keep elbows tucked", false,
            )
            container.repository.saveExercisePreferences(
                seededId("exercise", "Incline Dumbbell Press"), null, null, "Pause at the bottom", false,
            )
        }
        startPushDayA()
        awaitTag("exercise-note")
        compose.onNodeWithTag("exercise-note").assertTextEquals("Keep elbows tucked")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Hold the real in-memory SQLite connection so the next exercise query cannot finish.
        val blocker = Thread {
            runBlocking {
                container.database.withTransaction {
                    container.database.dao().deleteSetById("transaction-blocker")
                    entered.countDown()
                    release.await(20, TimeUnit.SECONDS)
                }
            }
        }.apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.onNodeWithTag("exercise-chip-Incline Dumbbell Press").performScrollTo().performClick()
            compose.onNodeWithTag("exercise-chip-Incline Dumbbell Press").assertIsSelected()
            compose.onNodeWithTag("exercise-note").assertDoesNotExist()
            compose.onNodeWithTag("target-exercise-settings").assertDoesNotExist()
        } finally {
            release.countDown()
            blocker.join(5_000)
        }
        awaitText("Pause at the bottom")
        compose.onNodeWithTag("exercise-note").assertTextEquals("Pause at the bottom")
        compose.onNodeWithTag("target-exercise-settings").assertExists()
    }

    @Test
    fun reorderKeepsSelectionSetsAndRestThroughRecreationAndFinish() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        runBlocking { container.settingsStore.update { it.copy(restWorkingSeconds = 600) } }
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        val restBefore = runBlocking { container.settingsStore.settings.first().restEndsAt }
        assertNotNull(restBefore)

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithTag("edit-order").performClick()
        compose.onNodeWithTag("order-up-Bench Press").assertIsNotEnabled()
        compose.onNodeWithTag("order-down-Cable Fly").assertIsNotEnabled()
        compose.onNodeWithTag("order-up-Cable Fly").assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("Move Cable Fly up").performClick()
        awaitOrder(listOf("Bench Press", "Cable Fly", "Incline Dumbbell Press"))
        compose.onNodeWithTag("order-up-Cable Fly").performClick()
        awaitOrder(listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press"))
        compose.onNodeWithTag("order-up-Cable Fly").assertIsNotEnabled()
        val rowTops = listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press").map {
            compose.onNodeWithTag("order-up-$it").getUnclippedBoundsInRoot().top
        }
        assertTrue(rowTops[0] < rowTops[1] && rowTops[1] < rowTops[2])
        compose.onNodeWithTag("close-order").performClick()

        assertReorderedChips()
        compose.onNodeWithTag("exercise-chip-Bench Press").assertIsSelected()
        compose.onNodeWithText("80 kg × 8").assertExists()
        assertEquals(restBefore, runBlocking { container.settingsStore.settings.first().restEndsAt })

        compose.activityRule.scenario.recreate()
        awaitTag("exercise-chip-Bench Press")
        assertReorderedChips()
        compose.onNodeWithTag("exercise-chip-Bench Press").assertIsSelected()
        awaitText("80 kg × 8")
        assertEquals(restBefore, runBlocking { container.settingsStore.settings.first().restEndsAt })

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
        compose.onNodeWithTag("finish-this-workout-only").performClick()
        awaitTag("active-empty")
        runBlocking {
            val history = container.repository.history().first().single()
            assertEquals(listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press"),
                history.exercises.map { it.exerciseNameSnapshot })
            assertEquals(80.0, history.exercises[1].sets.single().weightKg, 0.0)
            assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"),
                container.repository.loadTemplateDraft(seededId("template", "Push Day A"))!!
                    .exercises.map { it.exerciseName })
        }
        compose.onNodeWithTag("tab-Home").performClick()
        val homeTags = listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press")
            .map { "home-exercise-${seededId("exercise", it)}" }
        awaitTag(homeTags[0])
        val lefts = homeTags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().left }
        assertTrue(lefts[0] < lefts[1] && lefts[1] < lefts[2])
    }

    @Test
    fun singleExerciseOrderHasDisabledArrows() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        runBlocking {
            val template = container.repository.saveTemplate(null, "Bench only",
                listOf(TemplateExerciseDraft(seededId("exercise", "Bench Press"), workingSets = 3, repMin = 6, repMax = 8)))
            container.repository.startSession(template)
        }
        compose.onNodeWithTag("tab-Active").performClick()
        awaitTag("active-menu")
        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("edit-order").performClick()

        compose.onNodeWithTag("order-up-Bench Press").assertIsNotEnabled()
        compose.onNodeWithTag("order-down-Bench Press").assertIsNotEnabled()
        compose.onNodeWithTag("close-order").performClick()
        compose.onNodeWithTag("exercise-chip-Bench Press").assertIsSelected()
    }

    @Test
    fun failedReorderShowsErrorAndCanBeRetried() {
        startPushDayA()
        val database = ApplicationProvider.getApplicationContext<BuhatApplication>().container.database
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_reorder BEFORE UPDATE OF position ON SessionExercise " +
                "WHEN NEW.position = 2 BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )
        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("edit-order").performClick()
        compose.onNodeWithTag("order-up-Cable Fly").performClick()
        awaitTag("order-error")
        compose.onNodeWithTag("order-error").assertIsDisplayed()
        awaitOrder(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"))

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reorder")
        compose.onNodeWithTag("order-up-Cable Fly").performClick()
        awaitOrder(listOf("Bench Press", "Cable Fly", "Incline Dumbbell Press"))
        compose.onNodeWithTag("order-error").assertDoesNotExist()
        // Exercise the downward move as well as the upward path.
        compose.onNodeWithTag("order-down-Cable Fly").performClick()
        awaitOrder(listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly"))
    }

    @Test
    fun reorderDisablesArrowsWhileTheDatabaseWriteIsPending() {
        startPushDayA()
        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("edit-order").performClick()
        val database = ApplicationProvider.getApplicationContext<BuhatApplication>().container.database
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = Thread {
            runBlocking {
                database.withTransaction {
                    database.dao().deleteSetById("transaction-blocker")
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        }.apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.onNodeWithTag("order-up-Cable Fly").performClick()
            for (name in listOf("Bench Press", "Incline Dumbbell Press", "Cable Fly")) {
                compose.onNodeWithTag("order-up-$name").assertIsNotEnabled()
                compose.onNodeWithTag("order-down-$name").assertIsNotEnabled()
            }
        } finally {
            release.countDown()
            blocker.join(5_000)
        }
        awaitOrder(listOf("Bench Press", "Cable Fly", "Incline Dumbbell Press"))
    }

    @Test
    fun twoMovesInTheSameFrameKeepTheFirstAndRejectTheSecond() {
        startPushDayA()
        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("edit-order").performClick()
        awaitTag("order-up-Cable Fly")

        // Both handlers come from one composition, before anything can disable them.
        val up = compose.onNodeWithTag("order-up-Cable Fly").fetchSemanticsNode()
        val down = compose.onNodeWithTag("order-down-Bench Press").fetchSemanticsNode()
        compose.runOnIdle {
            up.config[SemanticsActions.OnClick].action!!.invoke()
            down.config[SemanticsActions.OnClick].action!!.invoke()
        }

        awaitOrder(listOf("Bench Press", "Cable Fly", "Incline Dumbbell Press"))
    }

    private fun awaitOrder(names: List<String>) {
        compose.waitUntil(5_000) {
            runBlocking {
                val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
                val session = repo.activeSession().first()!!
                repo.sessionExercises(session.id).first().map { it.exerciseNameSnapshot } == names
            }
        }
        compose.waitForIdle()
    }

    private fun assertReorderedChips() {
        val lefts = listOf("Cable Fly", "Bench Press", "Incline Dumbbell Press").map {
            compose.onNodeWithTag("exercise-chip-$it").getUnclippedBoundsInRoot().left
        }
        assertTrue(lefts[0] < lefts[1] && lefts[1] < lefts[2])
    }

    @Test
    fun addExerciseMidWorkoutPersistsAndPromptsOnFinish() {
        startPushDayA()

        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("add-session-exercise").performClick()
        awaitTag("session-picker-search")
        compose.onNodeWithTag("session-picker-search").performTextReplacement("cable row")
        compose.onNodeWithTag("session-picker-Seated Cable Row").performClick()
        awaitTag("session-sets")
        compose.onNodeWithTag("session-sets").performTextReplacement("4")
        compose.onNodeWithTag("session-rep-min").performTextReplacement("6")
        compose.onNodeWithTag("session-rep-max").performTextReplacement("8")
        compose.onNodeWithTag("session-rir").performTextReplacement("1")
        compose.onNodeWithTag("save-session-exercise").performClick()

        awaitTag("exercise-chip-Seated Cable Row")
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val added = repo.sessionExercises(session.id).first()
                .single { it.exerciseNameSnapshot == "Seated Cable Row" }
            assertEquals(
                listOf(4, 4, 6, 8, 1),
                listOf(added.position, added.workingSets, added.repMin, added.repMax, added.targetRir),
            )
            assertTrue(session.structureDirty)
        }

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
    }

    @Test
    fun editExerciseUpdatesTheTargetRangeAndThisWorkoutOnlyLeavesTheTemplate() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")

        compose.onNodeWithTag("edit-session-exercise").performScrollTo().performClick()
        awaitTag("session-sets")
        compose.onNodeWithTag("session-sets").performTextReplacement("4")
        compose.onNodeWithTag("session-rep-min").performTextReplacement("8")
        compose.onNodeWithTag("session-rep-max").performTextReplacement("10")
        compose.onNodeWithTag("save-session-exercise").performClick()
        awaitPrescription("Bench Press", 4, 8, 10)
        compose.onNodeWithText("Target today · 8–10 rep range").assertExists()

        val sessionId = runBlocking {
            ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
                .activeSession().first()!!.id
        }
        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
        compose.onNodeWithTag("finish-this-workout-only").performClick()
        awaitTag("active-empty")

        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val bench = repo.sessionExercises(sessionId).first().first()
            assertEquals(listOf(4, 8, 10), listOf(bench.workingSets, bench.repMin, bench.repMax))
            val templateBench = repo.loadTemplateDraft(seededId("template", "Push Day A"))!!.exercises.first()
            assertEquals(listOf(3, 6, 8), listOf(templateBench.workingSets, templateBench.repMin, templateBench.repMax))
        }
    }

    @Test
    fun replaceAfterSetsKeepsEarlierSetsAndSelectsTheReplacement() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("7")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-2")

        compose.onNodeWithTag("replace-session-exercise").performScrollTo().performClick()
        awaitTag("session-picker-search")
        compose.onNodeWithTag("session-picker-Seated Cable Row").performScrollTo().performClick()
        awaitTag("exercise-chip-Seated Cable Row")
        compose.onNodeWithTag("exercise-chip-Seated Cable Row").assertIsSelected()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("85")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("6")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("85")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("5")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-2")

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
        compose.onNodeWithTag("finish-this-workout-only").performClick()
        awaitTag("active-empty")

        runBlocking {
            val history = ApplicationProvider.getApplicationContext<BuhatApplication>()
                .container.repository.history().first().single()
            assertEquals(
                listOf("Bench Press", "Seated Cable Row", "Incline Dumbbell Press", "Cable Fly"),
                history.exercises.map { it.exerciseNameSnapshot },
            )
            assertEquals(listOf(80.0, 80.0), history.exercises[0].sets.map { it.weightKg })
            assertEquals(listOf(85.0, 85.0), history.exercises[1].sets.map { it.weightKg })
        }
    }

    @Test
    fun unloggedExerciseCanBeRemovedAndALoggedOneCannot() {
        startPushDayA()
        compose.onNodeWithTag("exercise-chip-Incline Dumbbell Press").performScrollTo().performClick()
        compose.onNodeWithTag("remove-session-exercise").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("exercise-chip-Incline Dumbbell Press"))
                .fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val rows = repo.sessionExercises(session.id).first()
            assertEquals(listOf("Bench Press", "Cable Fly"), rows.map { it.exerciseNameSnapshot })
            assertEquals(listOf(1, 2), rows.map { it.position })
        }

        compose.onNodeWithTag("exercise-chip-Bench Press").performScrollTo().performClick()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("remove-session-exercise").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun finishWithUpdateTemplateCreatesACustomCopyOfThePremade() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        compose.onNodeWithTag("edit-session-exercise").performScrollTo().performClick()
        awaitTag("session-sets")
        compose.onNodeWithTag("session-sets").performTextReplacement("4")
        compose.onNodeWithTag("save-session-exercise").performClick()
        awaitPrescription("Bench Press", 4, 6, 8)

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
        compose.onNodeWithTag("finish-update-template").performClick()
        awaitTag("active-empty")

        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val premade = repo.loadTemplateDraft(seededId("template", "Push Day A"))!!
            assertEquals(3, premade.exercises.first().workingSets)
            val copy = repo.templates().first().single { it.name == "Push Day A (copy)" }
            assertEquals(false, copy.isPremade)
            assertEquals(4, repo.loadTemplateDraft(copy.id)!!.exercises.first().workingSets)
        }
    }

    @Test
    fun failedTemplateSaveBackShowsTheErrorAndKeepsTheWorkoutActive() {
        startPushDayA()
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        runBlocking {
            val session = container.repository.activeSession().first()!!
            val bench = container.repository.sessionExercises(session.id).first().first()
            container.repository.editSessionExercise(bench.id, workingSets = 4, repMin = 6, repMax = 8, targetRir = null)
            container.repository.logSet(bench.id, SetType.WORKING, 80.0, 8, 2)
        }
        container.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_finish_template BEFORE INSERT ON TemplateExercise " +
                "BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-prompt")
        compose.onNodeWithTag("finish-update-template").performClick()
        awaitTag("finish-error")
        runBlocking {
            val session = container.repository.activeSession().first()!!
            assertTrue(session.structureDirty)
            assertNull(container.repository.templates().first().singleOrNull { it.name == "Push Day A (copy)" })
        }

        container.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_finish_template")
        compose.onNodeWithTag("finish-update-template").performClick()
        awaitTag("active-empty")
        runBlocking {
            assertNull(container.repository.activeSession().first())
            assertNotNull(container.repository.templates().first().singleOrNull { it.name == "Push Day A (copy)" })
        }
    }

    @Test
    fun finishFailureWithoutAStructureChangeShowsTheErrorAndKeepsTheWorkoutActive() {
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitTag("set-row-1")
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        container.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_finish_session BEFORE UPDATE ON WorkoutSession " +
                "BEGIN SELECT RAISE(ABORT, 'disk write failed'); END",
        )

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("finish-error")
        runBlocking {
            assertNotNull(container.repository.activeSession().first())
        }

        container.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_finish_session")
        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("active-empty")
    }

    @Test
    fun finishingWithNoWorkingSetsOffersDiscard() {
        startPushDayA()

        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithText("Finish workout").performClick()

        awaitText("No working sets yet.")
        compose.onNodeWithTag("confirm-discard").performClick()

        awaitTag("active-empty")
        compose.onNodeWithText("Start a workout from Workouts.").assertExists()
    }

    @Test
    fun startingASecondWorkoutAsksToResumeOrDiscard() {
        startPushDayA()

        compose.onNodeWithTag("tab-Workouts").performClick()
        awaitTag("start-workout-Pull Day A")
        compose.onNodeWithTag("start-workout-Pull Day A").performClick()

        awaitText("Workout in progress")
        compose.onNodeWithTag("resume-workout").performClick()

        awaitTag("screen-Active")
        awaitText("Push Day A")
    }

    @Test
    fun activeTabWithoutASessionPointsAtWorkouts() {
        compose.onNodeWithTag("tab-Active").performClick()

        awaitTag("active-empty")
        compose.onNodeWithTag("active-empty").assertIsDisplayed()
        compose.onNodeWithText("Start a workout from Workouts.").assertExists()
    }

    private fun markUnilateral(name: String) = runBlocking {
        ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            .saveExercisePreferences(seededId("exercise", name), null, null, null, true)
    }

    private fun awaitTagText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitRowText(index: Int, text: String) {
        awaitTagText("set-row-$index", text)
    }

    @Test
    fun unilateralLoggingAlternatesSidesAndKeepsEachSet() {
        markUnilateral("Bench Press")
        startPushDayA()
        awaitTag("segmented-Left")
        compose.onNodeWithTag("segmented-Left").assertIsSelected()

        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("40")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitRowText(1, "L")
        compose.onNodeWithTag("segmented-Right").assertIsSelected()

        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("20")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitRowText(2, "R")

        compose.onNodeWithTag("set-row-1").assert(hasText("L"))
        compose.onNodeWithTag("set-row-2").assert(hasText("R"))
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val bench = repo.sessionExercises(session.id).first()
                .single { it.exerciseNameSnapshot == "Bench Press" }
            val sets = repo.loggedSets(bench.id).first()
            assertEquals(listOf(SetSide.LEFT, SetSide.RIGHT), sets.map { it.side })
            assertEquals(listOf(1, 2), sets.map { it.workingIndex })
        }
    }

    @Test
    fun sideSelectionSurvivesRecreation() {
        markUnilateral("Bench Press")
        startPushDayA()
        awaitTag("segmented-Right")
        compose.onNodeWithTag("segmented-Right").performClick()
        compose.onNodeWithTag("segmented-Right").assertIsSelected()

        compose.activityRule.scenario.recreate()
        awaitTag("segmented-Right")
        compose.onNodeWithTag("segmented-Right").assertIsSelected()
        compose.onNodeWithTag("segmented-Left").assertIsNotSelected()
    }

    @Test
    fun editingASetCanCorrectItsSide() {
        markUnilateral("Bench Press")
        startPushDayA()
        compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement("40")
        compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        awaitRowText(1, "L")

        compose.onNodeWithTag("set-row-1").performClick()
        awaitTag("edit-side-Right")
        compose.onNodeWithTag("edit-side-Right").performClick()
        compose.onNodeWithTag("save-set").performClick()

        awaitRowText(1, "R")
        runBlocking {
            val repo = ApplicationProvider.getApplicationContext<BuhatApplication>().container.repository
            val session = repo.activeSession().first()!!
            val bench = repo.sessionExercises(session.id).first()
                .single { it.exerciseNameSnapshot == "Bench Press" }
            assertEquals(SetSide.RIGHT, repo.loggedSets(bench.id).first().single().side)
        }
    }

    @Test
    fun perSidePrescriptionCopy() {
        markUnilateral("Seated Cable Row")
        startPushDayA()

        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("add-session-exercise").performClick()
        awaitTag("session-picker-search")
        compose.onNodeWithTag("session-picker-search").performTextReplacement("cable row")
        compose.onNodeWithTag("session-picker-Seated Cable Row").performClick()
        awaitTag("session-sets")
        compose.onNodeWithText("Sets/side").assertExists()
        compose.onNodeWithText("Sets").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("active-menu").performClick()
        compose.onNodeWithTag("add-session-exercise").performClick()
        awaitTag("session-picker-search")
        compose.onNodeWithTag("session-picker-search").performTextReplacement("leg extension")
        compose.onNodeWithTag("session-picker-Leg Extension").performClick()
        awaitTag("session-sets")
        compose.onNodeWithText("Sets").assertExists()
        compose.onNodeWithText("Sets/side").assertDoesNotExist()
    }
}
