package com.buhat.tracker.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.ui.theme.BuhatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ScreenTest {

    // Reset the container before the activity launches, so the UI never reads a closed database.
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

    private fun openWorkouts() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        awaitTag("template-card-Push Day A")
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
    }

    private fun addExercise(query: String, name: String) {
        compose.onNodeWithTag("add-exercise").performClick()
        type("picker-search", query)
        compose.onNodeWithTag("picker-$name").performClick()
    }

    private fun awaitText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Dropdown items live in a popup that is composed a frame or more after the menu button click,
    // so the item has to be awaited before it can be clicked or asserted on.
    private fun awaitMenuItem(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickMenuItem(text: String) {
        awaitMenuItem(text)
        compose.onNodeWithText(text).performClick()
    }

    private fun clickMenuTag(tag: String) {
        awaitTag(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    @Test
    fun bottomTabLabelsFitAtLargeFontScale() {
        var measurer: TextMeasurer? = null
        var tabStyle: TextStyle? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    BuhatTheme {
                        measurer = rememberTextMeasurer()
                        tabStyle = MaterialTheme.typography.titleSmall
                        Box(Modifier.requiredWidth(384.dp)) {
                            BuhatBottomBar(selected = BuhatTab.Home, onSelect = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        for (tab in listOf("Home", "Workouts", "Active", "Progress")) {
            val tabWidth = compose.onNodeWithTag("tab-$tab").fetchSemanticsNode().size.width
            val labelWidth = measurer!!.measure(AnnotatedString(tab), style = tabStyle!!).size.width
            assertTrue(
                "$tab needs ${labelWidth}px but its tab is ${tabWidth}px; the label is clipped or wrapped",
                labelWidth <= tabWidth,
            )
        }
    }

    @Test
    fun workoutsFiltersAndCreatesTemplate() {
        openWorkouts()
        compose.onNodeWithText("6 templates").assertExists()

        compose.onNodeWithTag("segmented-My templates").performClick()
        compose.onNodeWithText("No custom templates").assertExists()

        compose.onNodeWithTag("new-template").performClick()
        compose.onNodeWithTag("screen-TemplateEditor").assertIsDisplayed()
        type("template-name", "Leg Day C")
        addExercise("Bench", "Bench Press")
        type("sets-Bench Press", "4")
        type("repmin-Bench Press", "8")
        type("repmax-Bench Press", "12")
        compose.onNodeWithTag("save-template").performClick()

        compose.onNodeWithTag("screen-Workouts").assertIsDisplayed()
        compose.onNodeWithText("1 template").assertExists()
        compose.onNodeWithTag("template-card-Leg Day C").assertExists()
        compose.onNodeWithText("Bench Press").assertExists()
    }

    @Test
    fun templateDraftSurvivesActivityRecreation() {
        openWorkouts()
        compose.onNodeWithTag("new-template").performClick()
        type("template-name", "Rotate Probe")
        addExercise("Bench", "Bench Press")
        addExercise("Cable Fly", "Cable Fly")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("screen-TemplateEditor").assertIsDisplayed()
        compose.onNodeWithTag("template-name").assertTextContains("Rotate Probe")
        compose.onNodeWithTag("draft-Bench Press").assertExists()
        compose.onNodeWithTag("draft-Cable Fly").assertExists()
    }

    @Test
    fun existingTemplateEditsSurviveActivityRecreation() {
        openWorkouts()
        compose.onNodeWithTag("segmented-My templates").performClick()
        compose.onNodeWithTag("new-template").performClick()
        type("template-name", "Edit Probe")
        addExercise("Bench", "Bench Press")
        compose.onNodeWithTag("save-template").performClick()
        awaitTag("template-card-Edit Probe")

        compose.onNodeWithTag("template-menu-Edit Probe").performClick()
        clickMenuItem("Edit template")
        awaitTag("draft-Bench Press")
        addExercise("Cable Fly", "Cable Fly")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithTag("screen-TemplateEditor").assertIsDisplayed()
        compose.onNodeWithTag("template-name").assertTextContains("Edit Probe")
        compose.onNodeWithTag("draft-Bench Press").assertExists()
        compose.onNodeWithTag("draft-Cable Fly").assertExists()
    }

    @Test
    fun templateValidationBlocksBadInput() {
        openWorkouts()
        compose.onNodeWithTag("new-template").performClick()

        compose.onNodeWithTag("save-template").performClick()
        compose.onNodeWithTag("error-message").assertTextEquals("Name is required.")

        type("template-name", "Bad template")
        compose.onNodeWithTag("save-template").performClick()
        compose.onNodeWithTag("error-message").assertTextEquals("Add at least one exercise.")

        addExercise("Bench", "Bench Press")
        type("repmin-Bench Press", "12")
        type("repmax-Bench Press", "8")
        compose.onNodeWithTag("save-template").performClick()
        compose.onNodeWithTag("error-message")
            .assertTextEquals("Minimum reps cannot exceed maximum reps.")
    }

    @Test
    fun exercisePickerRowsMeetThe48dpTouchTarget() {
        openWorkouts()
        compose.onNodeWithTag("new-template").performClick()
        compose.onNodeWithTag("add-exercise").performClick()
        awaitTag("picker-Bench Press")
        compose.onNodeWithTag("picker-Bench Press").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun premadeIsReadOnlyAndDuplicates() {
        openWorkouts()
        compose.onNodeWithTag("template-menu-Push Day A").performClick()
        awaitMenuItem("Duplicate template")
        compose.onNodeWithText("Edit template").assertDoesNotExist()
        compose.onNodeWithText("Delete template").assertDoesNotExist()
        compose.onNodeWithText("Duplicate template").performClick()

        compose.onNodeWithTag("template-card-Push Day A (copy)").assertExists()
        compose.onNodeWithTag("segmented-My templates").performClick()
        compose.onNodeWithTag("template-card-Push Day A (copy)").assertExists()
    }

    @Test
    fun customExerciseEditsAndArchives() {
        openWorkouts()
        compose.onNodeWithTag("workouts-menu").performClick()
        clickMenuTag("open-library")
        compose.onNodeWithTag("screen-Library").assertIsDisplayed()

        compose.onNodeWithTag("new-exercise").performClick()
        type("exercise-name", "Belt Squat")
        compose.onNodeWithTag("muscle-Quads").performClick()
        compose.onNodeWithTag("equipment-Machine").performClick()
        compose.onNodeWithTag("save-exercise").performScrollTo().performClick()
        awaitTag("screen-Library")
        compose.onNodeWithTag("screen-Library").assertIsDisplayed()
        compose.onNodeWithTag("exercise-row-Belt Squat").assertExists()
        compose.onNodeWithTag("custom-badge-Belt Squat").assertExists()

        compose.onNodeWithTag("exercise-menu-Belt Squat").performClick()
        clickMenuItem("Edit exercise")
        compose.onNodeWithTag("screen-ExerciseEditor").assertIsDisplayed()
        type("exercise-name", "Belt Squat Machine")
        compose.onNodeWithTag("save-exercise").performScrollTo().performClick()
        awaitTag("screen-Library")

        compose.onNodeWithTag("exercise-row-Belt Squat Machine").assertExists()
        compose.onNodeWithTag("exercise-menu-Belt Squat Machine").performClick()
        clickMenuItem("Archive exercise")
        compose.onNodeWithTag("exercise-row-Belt Squat Machine").assertDoesNotExist()
    }

    @Test
    fun deletingReferencedExerciseOffersArchive() {
        openWorkouts()
        compose.onNodeWithTag("workouts-menu").performClick()
        clickMenuTag("open-library")
        compose.onNodeWithTag("new-exercise").performClick()
        type("exercise-name", "Belt Squat")
        compose.onNodeWithTag("muscle-Quads").performClick()
        compose.onNodeWithTag("equipment-Machine").performClick()
        compose.onNodeWithTag("save-exercise").performScrollTo().performClick()
        awaitTag("screen-Library")
        compose.onNodeWithTag("back").performClick()
        awaitTag("new-template")

        compose.onNodeWithTag("new-template").performClick()
        type("template-name", "Belt Day")
        addExercise("Belt", "Belt Squat")
        compose.onNodeWithTag("save-template").performClick()

        compose.onNodeWithTag("workouts-menu").performClick()
        clickMenuTag("open-library")
        compose.onNodeWithTag("exercise-menu-Belt Squat").performClick()
        clickMenuItem("Delete exercise")
        compose.onNodeWithText("Used in a template").assertExists()
        compose.onNodeWithTag("confirm-archive").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("exercise-row-Belt Squat"))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun profileEditsPersistAndSettingsOpens() {
        compose.onNodeWithTag("profile-card").performClick()
        compose.onNodeWithTag("screen-ProfileEditor").assertIsDisplayed()
        type("profile-name", "Alex")
        type("profile-gender", "Male")
        type("profile-age", "30")
        type("profile-weight", "72")
        type("profile-height", "178")
        compose.onNodeWithTag("save-profile").performClick()

        awaitText("profile-card-name", "Alex")
        compose.onNodeWithText("72 kg", useUnmergedTree = true).assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        awaitText("profile-card-name", "Alex")

        compose.onNodeWithTag("settings-button").performClick()
        compose.onNodeWithTag("screen-Settings").assertIsDisplayed()
        compose.onNodeWithTag("settings-profile").performClick()
        compose.onNodeWithTag("screen-ProfileEditor").assertIsDisplayed()
    }
}
