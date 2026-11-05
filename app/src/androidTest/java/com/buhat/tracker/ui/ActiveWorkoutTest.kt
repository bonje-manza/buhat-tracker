package com.buhat.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.seededId
import com.buhat.tracker.domain.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

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

    private fun startPushDayA() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        compose.onNodeWithTag("start-workout-Push Day A").performClick()
        awaitTag("screen-Active")
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

    @Test
    fun idleRestCardExplainsAutomaticStartWithoutSkip() {
        startPushDayA()
        compose.onNodeWithText("Ready for next set").assertIsDisplayed()
        compose.onNodeWithText("Starts automatically after each set").assertIsDisplayed()
        compose.onNodeWithTag("skip-rest").assertDoesNotExist()
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
        compose.onNodeWithTag("start-workout-Pull Day A").performClick()

        awaitText("Workout in progress")
        compose.onNodeWithTag("resume-workout").performClick()

        awaitTag("screen-Active")
        compose.onNodeWithText("Push Day A").assertExists()
    }

    @Test
    fun activeTabWithoutASessionPointsAtWorkouts() {
        compose.onNodeWithTag("tab-Active").performClick()

        awaitTag("active-empty")
        compose.onNodeWithTag("active-empty").assertIsDisplayed()
        compose.onNodeWithText("Start a workout from Workouts.").assertExists()
    }
}
