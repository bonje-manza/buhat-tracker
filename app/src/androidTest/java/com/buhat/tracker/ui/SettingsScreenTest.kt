package com.buhat.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.data.seededId
import com.buhat.tracker.data.decodeBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val reset = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                ApplicationProvider.getApplicationContext<BuhatApplication>().resetForTest()
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
    }

    private fun awaitText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun profileTextOverTheBackupLimitIsRejected() {
        compose.onNodeWithTag("profile-card").performClick()
        awaitTag("profile-name")
        type("profile-name", "a".repeat(201))
        compose.onNodeWithTag("save-profile").performScrollTo().performClick()
        compose.onNodeWithText("Name must be 200 characters or fewer.").assertIsDisplayed()
        compose.onNodeWithTag("screen-ProfileEditor").assertIsDisplayed()
    }

    @Test
    fun settingsChangeThemeUnitsAndPreviousSetAndApplyImmediately() {
        compose.onNodeWithTag("profile-card").performClick()
        awaitTag("profile-name")
        type("profile-name", "Alex")
        type("profile-weight", "72")
        compose.onNodeWithTag("save-profile").performClick()
        awaitText("profile-card-name", "Alex")
        compose.onNodeWithText("72 kg", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("settings-button").performClick()
        compose.onNodeWithTag("screen-Settings").assertIsDisplayed()
        awaitTag("segmented-Light")
        compose.onNodeWithTag("segmented-Light").performScrollTo().performClick()
        compose.onNodeWithTag("segmented-lb").performScrollTo().performClick()
        compose.onNodeWithTag("segmented-Same template").performScrollTo().performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("segmented-Same template") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("segmented-Light").assertIsSelected()
        compose.onNodeWithTag("segmented-lb").assertIsSelected()
        compose.onNodeWithTag("segmented-Same template").assertIsSelected()
        // An uncovered corner uses the screen background, proving the actual palette changed.
        val background = compose.onNodeWithTag("screen-Settings").captureToImage().toPixelMap()[0, 0]
        assertEquals(0xFFF6F6F8.toInt(), background.toArgb())
        compose.onNodeWithTag("export-data").assertExists()
        compose.onNodeWithTag("import-data").assertExists()

        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        val stored = runBlocking { container.settingsStore.settings.first() }
        assertEquals(AppTheme.LIGHT, stored.theme)
        assertEquals(WeightUnit.LB, stored.unit)
        assertEquals(PreviousSetSource.SAME_TEMPLATE, stored.previousSetSource)
        compose.runOnIdle {
            assertTrue(WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .isAppearanceLightStatusBars)
        }

        // 72 kg x 2.20462 = 158.73264, displayed to 0.1 lb.
        compose.onNodeWithTag("back").performClick()
        awaitText("profile-card-name", "Alex")
        compose.onNodeWithText("158.7 lb", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("profile-card").performClick()
        compose.onNodeWithTag("profile-weight").assertTextContains("158.7")
        compose.onNodeWithTag("save-profile").performScrollTo().performClick()
        awaitText("profile-card-name", "Alex")
        // Saving an unchanged rounded display must preserve the stored kilograms exactly.
        assertEquals(72.0, runBlocking { container.profileStore.profile.first().weightKg }!!, 0.0)
    }

    @Test
    fun exportAndImportAreDisabledWhileASessionIsActive() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        compose.onNodeWithTag("start-workout-Push Day A").performClick()
        compose.onNodeWithTag("tab-Home").performClick()
        compose.onNodeWithTag("settings-button").performClick()

        compose.onNodeWithTag("export-data").assertIsNotEnabled()
        compose.onNodeWithTag("import-data").assertIsNotEnabled()
        compose.onNodeWithTag("settings-active-hint").assertExists()
    }

    @Test fun previousSourceChangesFutureTargetsAndUnitsWithoutRewritingHistory() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        val repo = container.repository
        val original = runBlocking {
            container.settingsStore.update { it.copy(restWorkingSeconds = 0) }
            val a = seededId("template", "Push Day A")
            val b = repo.duplicateTemplate(a)
            for ((template, weight) in listOf(a to 80.0, b to 60.0)) {
                val session = repo.startSession(template)
                repo.logSet(repo.sessionExercises(session).first().first().id, SetType.WORKING, weight, 7, 2)
                repo.finishSession(session)
            }
            decodeBackup(repo.exportBackup()).also { repo.startSession(a) }
        }
        compose.onNodeWithTag("tab-Active").performClick()
        awaitText("target-value", "60 kg × 8")
        compose.onNodeWithTag("tab-Home").performClick()
        compose.onNodeWithTag("settings-button").performClick()
        compose.onNodeWithTag("segmented-Same template").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.settings.first().previousSetSource } == PreviousSetSource.SAME_TEMPLATE
        }
        compose.onNodeWithTag("segmented-lb").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.settingsStore.settings.first().unit } == WeightUnit.LB }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("tab-Active").performClick()
        // Same template supplies 80 kg, 7 reps. Target is 80 kg = 176.4 lb, 8 reps.
        awaitText("target-value", "176.4 lb × 8")
        compose.onNodeWithTag("target-message").assertTextContains("176.4 lb", substring = true)
        compose.onNodeWithTag("weight-input").assertTextContains("176.4")
        runBlocking {
            repo.discardSession(repo.activeSession().first()!!.id)
            val after = decodeBackup(repo.exportBackup())
            assertEquals(original.sessions, after.sessions)
            assertEquals(original.sessionExercises, after.sessionExercises)
            assertEquals(original.sets, after.sets)
        }
    }

    @Test fun poundsModeLoggingAndEditingPreserveExactKilograms() {
        val container = ApplicationProvider.getApplicationContext<BuhatApplication>().container
        val repo = container.repository
        val sessionExerciseId = runBlocking {
            container.settingsStore.update { it.copy(unit = WeightUnit.LB, restWorkingSeconds = 0) }
            val template = seededId("template", "Push Day A")
            val previous = repo.startSession(template)
            repo.logSet(repo.sessionExercises(previous).first().first().id, SetType.WORKING, 80.0, 7, 2)
            repo.finishSession(previous)
            val current = repo.startSession(template)
            repo.sessionExercises(current).first().first().id
        }
        compose.onNodeWithTag("tab-Active").performClick()
        // 80 kg prefill must display as 176.4 lb and still log as exactly 80 kg.
        awaitText("weight-input", "176.4")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { repo.loggedSets(sessionExerciseId).first().singleOrNull()?.weightKg == 80.0 }
        }
        compose.onNodeWithTag("set-row-1").performScrollTo().performClick()
        compose.onNodeWithTag("save-set").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("save-set").fetchSemanticsNodes().isEmpty() }
        // Saving an unchanged rounded display must preserve the stored kilograms exactly.
        assertEquals(80.0, runBlocking { repo.loggedSets(sessionExerciseId).first().single().weightKg }!!, 0.0)
    }
}
