package com.buhat.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.Profile
import com.buhat.tracker.data.recordBenchWorkout
import com.buhat.tracker.data.seededId
import com.buhat.tracker.domain.DAY_MS
import com.buhat.tracker.domain.SetSide
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {
    @get:Rule(order = 0)
    val reset = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<BuhatApplication>()
                app.resetForTest()
                runBlocking {
                    val store = app.container.settingsStore
                    store.save(store.settings.first().copy(restWarmupSeconds = 0, restWorkingSeconds = 0, restFailureSeconds = 0))
                }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = ApplicationProvider.getApplicationContext<BuhatApplication>().container
    private val benchId = seededId("exercise", "Bench Press")

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitValue(tag: String, value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(value), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun homeValue(tag: String, value: String) {
        compose.onNodeWithTag(tag).performScrollTo().assertTextContains(value)
    }

    private fun progressItem(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("screen-Progress").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun openProgress() {
        compose.onNodeWithTag("view-progress").performScrollTo().performClick()
        awaitTag("progress-exercise-picker")
    }

    @Test fun loggedWorkoutFeedsDashboardHistoryAndConfirmedDeletion() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        awaitTag("start-workout-Push Day A")
        compose.onNodeWithTag("start-workout-Push Day A").performClick()
        awaitTag("weight-input")
        var logged = 0
        fun log(type: String, weight: String, reps: String) {
            compose.onNodeWithTag("segmented-$type").performScrollTo().performClick()
            compose.onNodeWithTag("weight-input").performScrollTo().performTextReplacement(weight)
            compose.onNodeWithTag("reps-input").performScrollTo().performTextReplacement(reps)
            compose.onNodeWithTag("log-set").performScrollTo().performClick()
            awaitTag("set-row-${++logged}")
        }
        log("Warmup", "100", "20")
        log("Working", "80", "10")
        log("Working", "80", "9")
        log("Failure", "80", "8")
        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()
        awaitTag("active-empty")
        val session = runBlocking { container.repository.history().first().single() }

        compose.onNodeWithTag("tab-Home").performClick()
        awaitValue("home-trend-latest", "106.7 kg")
        // Hand calculated: 800+720+640=2160. Warmup 2000 is excluded; only the 80×10 set is a PR.
        homeValue("home-workouts-value", "1")
        homeValue("home-working-sets-value", "3")
        homeValue("home-volume-value", "2160 kg")
        homeValue("home-prs-value", "1")
        openProgress()
        compose.onNodeWithTag("progress-exercise-picker").assertTextContains("Bench Press")
        progressItem("progress-best-value").assertTextEquals("106.7 kg")
        progressItem("progress-heaviest-value").assertTextEquals("80 kg × 10")
        progressItem("progress-sessions-value").assertTextEquals("1")
        progressItem("history-${session.id}").performClick()
        compose.onNodeWithTag("history-set-${session.exercises.first().sets.first().id}")
            .assertTextContains("100 kg × 20", substring = true)
        compose.onNodeWithTag("history-pr-${session.id}", useUnmergedTree = true).assertExists()
        progressItem("delete-workout-${session.id}").performClick()
        compose.onNodeWithTag("cancel-delete-workout").performClick()
        compose.onNodeWithTag("history-${session.id}").assertExists()
        progressItem("delete-workout-${session.id}").performClick()
        compose.onNodeWithTag("confirm-delete-workout").performClick()
        progressItem("progress-sessions-value").assertTextEquals("0")
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("home-empty")
        homeValue("home-volume-value", "0 kg")
        homeValue("home-workouts-value", "0")
        compose.onNodeWithText("No workouts yet", useUnmergedTree = true).assertExists()
    }

    @Test fun homeStrengthCardCarriesTheDesignEstimatedOneRepMaxLabel() {
        runBlocking { container.recordBenchWorkout() }
        awaitValue("home-trend-latest", "106.7 kg")
        compose.onNodeWithText("Est. 1RM", useUnmergedTree = true).assertExists()
    }

    @Test fun benchProgressAndHomeTrendUseTheExerciseUnitWhileVolumeStaysGlobal() {
        val sessionId = runBlocking {
            container.repository.saveExercisePreferences(benchId, "LB", null, null, false)
            container.recordBenchWorkout()
        }
        // 80 kg × 2.20462 = 176.3696 → 176.4 lb. e1RM 106.6667 kg → 235.2 lb. Volume stays global.
        awaitValue("home-trend-latest", "235.2 lb")
        homeValue("home-volume-value", "2160 kg")

        openProgress()
        compose.onNodeWithTag("progress-exercise-picker").assertTextContains("Bench Press")
        progressItem("progress-best-value").assertTextEquals("235.2 lb")
        progressItem("progress-heaviest-value").assertTextEquals("176.4 lb × 10")
        progressItem("history-$sessionId").performClick()
        val heaviestSet = runBlocking { container.repository.history().first().single().exercises.first().sets[1] }
        progressItem("history-set-${heaviestSet.id}").assertTextContains("176.4 lb × 10", substring = true)
    }

    @Test fun rangesUseAllTimePrsAndDeletingTheRecordRecalculatesBadges() {
        val old = recordSingleSet(100.0, 6, 400) // e1RM 120, outside 1Y.
        runBlocking { container.recordBenchWorkout(daysAgo = 40) } // e1RM 106.6667.
        val recent = recordSingleSet(90.0, 10, 0) // e1RM 120, tie with the old record, not a PR.
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitValue("progress-trend-latest", "120 kg")
        compose.onNodeWithTag("progress-trend-change").assertTextEquals("+13.3 kg (+12.5%)")
        compose.onNodeWithTag("segmented-3M").assertIsSelected()
        progressItem("history-$recent").assertExists()
        compose.onNodeWithTag("history-pr-$recent", useUnmergedTree = true).assertDoesNotExist()
        progressItem("progress-sessions-value").assertTextEquals("3")
        progressItem("segmented-1M").performClick()
        compose.onNodeWithTag("progress-trend-change").assertTextEquals("0 kg (0%)")
        compose.onNodeWithTag("progress-chart").assertContentDescriptionEquals(
            "Bench Press, 1M: 1 session. First 120 kg, latest 120 kg. Change 0 kg (0%).",
        )
        compose.onNodeWithTag("segmented-1Y").performClick()
        compose.onNodeWithTag("progress-trend-change").assertTextEquals("+13.3 kg (+12.5%)")
        compose.onNodeWithTag("segmented-All").performClick()
        compose.onNodeWithTag("progress-trend-change").assertTextEquals("0 kg (0%)")
        progressItem("history-$old").performClick()
        progressItem("delete-workout-$old").performClick()
        compose.onNodeWithTag("confirm-delete-workout").performClick()
        progressItem("history-$recent").assertExists()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("history-pr-$recent"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        progressItem("progress-best-value").assertTextEquals("120 kg")
        progressItem("progress-heaviest-value").assertTextEquals("90 kg × 10")
        progressItem("progress-sessions-value").assertTextEquals("2")
        compose.onNodeWithTag("tab-Home").performClick()
        awaitValue("home-trend-latest", "120 kg")
        homeValue("home-prs-value", "1")
        homeValue("home-workouts-delta", "0%")
        homeValue("home-working-sets-delta", "-66.7%")
        homeValue("home-volume-delta", "-58.3%") // (900-2160)/2160.
    }

    @Test fun emptyHistoryAndEmptyRangeKeepAllTimeHistoryAvailable() {
        awaitTag("home-empty")
        compose.onNodeWithTag("profile-card-name", useUnmergedTree = true).assertTextEquals("Set up profile")
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("progress-empty")
        compose.onNodeWithTag("segmented-History").performClick()
        awaitTag("workout-history-empty")
        compose.onNodeWithTag("segmented-Exercise").performClick()
        awaitTag("progress-empty")
        val id = runBlocking { container.recordBenchWorkout(daysAgo = 40) }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitValue("home-trend-latest", "106.7 kg")
        openProgress()
        compose.onNodeWithTag("segmented-1M").performClick()
        awaitTag("progress-trend-empty")
        compose.onNodeWithText("No data in range").assertExists()
        progressItem("progress-best-value").assertTextEquals("106.7 kg")
        progressItem("history-$id").assertExists()
    }

    @Test fun recentChipsUseOnlyLatestWorkoutAndCarrySelectionToProgress() {
        runBlocking {
            container.recordBenchWorkout()
            val repo = container.repository
            val id = repo.startSession(seededId("template", "Pull Day A"))
            repo.logSet(repo.sessionExercises(id).first().first().id, SetType.WORKING, 60.0, 8, 2)
            repo.finishSession(id)
        }
        val lat = seededId("exercise", "Lat Pulldown")
        awaitTag("home-exercise-$lat")
        listOf("Barbell Row", "Lat Pulldown", "Seated Cable Row", "Face Pull").forEach {
            compose.onNodeWithTag("home-exercise-${seededId("exercise", it)}").assertExists()
        }
        compose.onNodeWithTag("home-exercise-$benchId").assertDoesNotExist()
        compose.onNodeWithTag("home-exercise-${seededId("exercise", "Barbell Curl")}").assertDoesNotExist()
        compose.onNodeWithTag("home-exercise-$lat").performScrollTo().performClick()
        openProgress()
        compose.onNodeWithTag("progress-exercise-picker").assertTextContains("Lat Pulldown")
        progressItem("progress-sessions-value").assertTextEquals("0")
    }

    @Test fun skippedFirstExerciseDoesNotHideLoggedData() {
        runBlocking {
            val repo = container.repository
            val id = repo.startSession(seededId("template", "Push Day A"))
            // Bench is first but skipped; Incline is second and logged. 20 × 10 gives 26.6667.
            repo.logSet(repo.sessionExercises(id).first()[1].id, SetType.WORKING, 20.0, 10, 2)
            repo.finishSession(id)
        }
        val incline = seededId("exercise", "Incline Dumbbell Press")
        awaitValue("home-trend-latest", "26.7 kg")
        compose.onNodeWithTag("home-exercise-$incline").assertIsSelected()
        openProgress()
        compose.onNodeWithTag("progress-exercise-picker").assertTextContains("Incline Dumbbell Press")
        progressItem("progress-best-value").assertTextEquals("26.7 kg")
    }

    @Test fun archivedBodyweightHistoryRemainsSelectableAfterTemplateDeletion() {
        val exerciseId = runBlocking {
            val repo = container.repository
            val exercise = repo.saveExercise(null, "Pull-up Ω", "Back", "Bodyweight")
            val template = repo.saveTemplate(null, "Bodyweight day", listOf(TemplateExerciseDraft(exercise, workingSets = 1, repMin = 6, repMax = 10)))
            val session = repo.startSession(template)
            repo.logSet(repo.sessionExercises(session).first().first().id, SetType.WORKING, 0.0, 10, 2)
            repo.finishSession(session)
            repo.saveExercisePreferences(exercise, "LB", null, null, false)
            repo.archiveExercise(exercise)
            repo.deleteTemplate(template)
            exercise
        }
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("progress-exercise-picker")
        compose.onNodeWithTag("progress-exercise-picker").performClick()
        compose.onNodeWithTag("progress-exercise-search").performTextReplacement("Ω")
        compose.onNodeWithTag("progress-pick-$exerciseId").performClick()
        awaitTag("progress-trend-empty")
        progressItem("progress-best-value").assertTextEquals("—")
        progressItem("progress-heaviest-value").assertTextEquals("0 lb × 10")
        progressItem("progress-sessions-value").assertTextEquals("1")
        compose.onNodeWithTag("screen-Progress").performScrollToNode(hasText("Bodyweight day", substring = true))
        compose.onNodeWithText("Bodyweight day", substring = true).assertExists()
    }

    @Test fun profileUnitsAndSelectedRangeSurviveRecreation() {
        runBlocking {
            container.recordBenchWorkout()
            container.profileStore.save(Profile("Alex", "Male", 30, 72.0, 178))
            val store = container.settingsStore
            store.save(store.settings.first().copy(unit = WeightUnit.LB))
        }
        awaitValue("home-trend-latest", "235.2 lb")
        compose.onNodeWithTag("home-weight-value", useUnmergedTree = true).assertTextEquals("158.7 lb")
        compose.onNodeWithTag("home-height-value", useUnmergedTree = true).assertTextEquals("5'10\"")
        compose.onNodeWithText("30 years", useUnmergedTree = true).assertDoesNotExist()
        openProgress()
        compose.onNodeWithTag("segmented-1Y").performClick()
        compose.activityRule.scenario.recreate()
        awaitValue("progress-trend-latest", "235.2 lb")
        compose.onNodeWithTag("segmented-1Y").assertIsSelected()
        compose.onNodeWithTag("progress-exercise-picker").assertTextContains("Bench Press")
        progressItem("progress-heaviest-value").assertTextEquals("176.4 lb × 10")
        assertEquals(80.0, runBlocking { container.repository.history().first().single().exercises.first().sets[1].weightKg }, 0.0)
    }

    @Test fun loadFailuresOfferRetryAndRecover() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        // A finished row without finishedAt makes mapping fail on every load, unlike a closed Room DB.
        val id = runBlocking { container.recordBenchWorkout() }
        val finishedAt = runBlocking { container.repository.session(id)!!.finishedAt!! }
        fun breakSession() = runBlocking {
            val session = container.repository.session(id)!!
            container.database.dao().updateSession(session.copy(finishedAt = null))
        }
        fun repairSession() = runBlocking {
            val session = container.repository.session(id)!!
            container.database.dao().updateSession(session.copy(finishedAt = finishedAt))
        }
        breakSession()
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("progress-retry")
        compose.onNodeWithTag("progress-empty").assertDoesNotExist()
        repairSession()
        compose.onNodeWithTag("progress-retry").performClick()
        awaitTag("progress-exercise-picker")
        progressItem("progress-best-value").assertTextEquals("106.7 kg")
        compose.onNodeWithTag("tab-Workouts").performClick()
        breakSession()
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("home-retry")
        compose.onNodeWithTag("home-empty").assertDoesNotExist()
        repairSession()
        compose.onNodeWithTag("home-retry").performClick()
        awaitValue("home-trend-latest", "106.7 kg")
    }

    @Test fun unilateralProgressShowsOneSideAndHistoryLabels() {
        val (sessionId, setIds) = runBlocking {
            val repo = container.repository
            repo.saveExercisePreferences(benchId, null, null, null, true)
            val id = repo.startSession(seededId("template", "Push Day A"))
            val bench = repo.sessionExercises(id).first().first()
            repo.logSet(bench.id, SetType.WORKING, 40.0, 8, 2, side = SetSide.LEFT)
            repo.logSet(bench.id, SetType.WORKING, 20.0, 8, 2, side = SetSide.RIGHT)
            repo.logSet(bench.id, SetType.FAILURE, 30.0, 6, 0, side = SetSide.LEFT)
            repo.logSet(bench.id, SetType.WARMUP, 10.0, 10, 2, side = SetSide.RIGHT)
            repo.finishSession(id)
            id to repo.loggedSets(bench.id).first().map { it.id }
        }
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("progress-exercise-picker")
        awaitTag("segmented-Right")
        // The latest set was the right side, so the summary starts there.
        compose.onNodeWithTag("segmented-Right").assertIsSelected()
        awaitValue("progress-trend-latest", "25.3 kg")
        progressItem("progress-best-value").assertTextEquals("25.3 kg")
        progressItem("progress-heaviest-value").assertTextEquals("20 kg × 8")
        progressItem("progress-sessions-value").assertTextEquals("1")

        compose.onNodeWithTag("segmented-Left").performScrollTo().performClick()
        awaitValue("progress-trend-latest", "50.7 kg")
        progressItem("progress-best-value").assertTextEquals("50.7 kg")
        progressItem("progress-heaviest-value").assertTextEquals("40 kg × 8")

        progressItem("history-$sessionId").performClick()
        progressItem("history-set-${setIds[0]}").assertTextContains("L1")
        progressItem("history-set-${setIds[1]}").assertTextContains("R1")
        progressItem("history-set-${setIds[2]}").assertTextContains("L2")
        progressItem("history-set-${setIds[3]}").assertTextContains("R")
    }

    @Test fun homeTrendUsesTheSelectedSideForUnilateralExercises() {
        runBlocking {
            val repo = container.repository
            repo.saveExercisePreferences(benchId, null, null, null, true)
            val id = repo.startSession(seededId("template", "Push Day A"))
            val bench = repo.sessionExercises(id).first().first()
            repo.logSet(bench.id, SetType.WORKING, 40.0, 8, 2, side = SetSide.LEFT)
            repo.logSet(bench.id, SetType.WORKING, 20.0, 8, 2, side = SetSide.RIGHT)
            repo.finishSession(id)
        }
        awaitValue("home-trend-latest", "25.3 kg")
        compose.onNodeWithTag("segmented-Right").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("segmented-Left").performScrollTo().performClick()
        awaitValue("home-trend-latest", "50.7 kg")
        compose.onNodeWithTag("segmented-Right").performScrollTo().performClick()
        awaitValue("home-trend-latest", "25.3 kg")
    }

    @Test fun bilateralExercisesHideTheSideControl() {
        runBlocking { container.recordBenchWorkout() }
        awaitValue("home-trend-latest", "106.7 kg")
        compose.onNodeWithTag("segmented-Left").assertDoesNotExist()
        openProgress()
        compose.onNodeWithTag("segmented-Left").assertDoesNotExist()
        compose.onNodeWithTag("segmented-Right").assertDoesNotExist()
    }

    @Test fun homeBannerShowsTrainingSinceAndThreeEqualHeightStatCards() {
        runBlocking {
            container.profileStore.save(Profile("Alex", "Male", 30, 72.0, 178))
            val repo = container.repository
            val id = repo.startSession(seededId("template", "Push Day A"))
            repo.logSet(repo.sessionExercises(id).first().first().id, SetType.WORKING, 80.0, 8, 2)
            repo.finishSession(id)
            // Captured fixture: the first workout finished 2024-11-16 12:00 UTC.
            val finishedAt = 1_731_758_400_000L
            val session = repo.session(id)!!
            container.database.dao().updateSession(session.copy(startedAt = finishedAt - 1_000, finishedAt = finishedAt))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("home-sessions-value"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Training since Nov 2024", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Training since Nov 2024 · 1 workout", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Gender / age", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("30 years", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home-weight-value", useUnmergedTree = true).assertTextEquals("72 kg")
        compose.onNodeWithTag("home-height-value", useUnmergedTree = true).assertTextEquals("178 cm")
        compose.onNodeWithTag("home-sessions-value", useUnmergedTree = true).assertTextEquals("1 total")

        val weight = compose.onNodeWithTag("home-weight-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val height = compose.onNodeWithTag("home-height-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sessions = compose.onNodeWithTag("home-sessions-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(weight.height, height.height, 0.5f)
        assertEquals(weight.height, sessions.height, 0.5f)
        assertEquals(weight.top, height.top, 0.5f)
        assertEquals(weight.top, sessions.top, 0.5f)
    }

    @Test fun progressHistoryRendersStoredRirFiveAsFourPlus() {
        val id = runBlocking {
            val repo = container.repository
            val session = repo.startSession(seededId("template", "Push Day A"))
            repo.logSet(repo.sessionExercises(session).first().first().id, SetType.WORKING, 80.0, 8, 5)
            repo.finishSession(session)
            session
        }
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("progress-exercise-picker")
        progressItem("history-$id").performClick()
        compose.onNodeWithText("RIR 4+").assertExists()
    }

    @Test fun workoutHistoryListsEveryFinishedWorkoutWithNotesUnitsAndDeletion() {
        val (push, pull) = runBlocking {
            val repo = container.repository
            repo.saveExercisePreferences(benchId, "LB", null, null, false)
            val push = container.recordBenchWorkout()
            // Pull Day A starts with Lat Pulldown and never touches Bench Press.
            val pull = repo.startSession(seededId("template", "Pull Day A"))
            val lat = repo.sessionExercises(pull).first().first()
            repo.logSet(lat.id, SetType.WORKING, 60.0, 8, 2)
            repo.finishSession(pull)
            // Strong imports carry a workout note and a per-exercise note. Both must show in history.
            container.database.dao().updateSession(repo.session(pull)!!.copy(note = "Felt strong today"))
            container.database.dao().updateSessionExercise(lat.copy(note = "Cable stack felt heavy"))
            push to pull
        }
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("segmented-History")
        compose.onNodeWithTag("segmented-Exercise").assertIsSelected()
        compose.onNodeWithTag("segmented-History").performClick()
        // The selected exercise is Bench Press, so the Pull Day workout proves no exercise choice is needed.
        awaitTag("workout-history-$push")
        awaitTag("workout-history-$pull")

        progressItem("workout-history-$pull").performClick()
        compose.onNodeWithText("Felt strong today").assertExists()
        compose.onNodeWithText("Lat Pulldown").assertExists()
        compose.onNodeWithText("Cable stack felt heavy").assertExists()
        compose.onNodeWithText("60 kg × 8").assertExists()
        compose.onNodeWithTag("workout-history-pr-$pull", useUnmergedTree = true).assertExists()

        progressItem("workout-history-$push").performClick()
        // Bench is lb for this exercise only: 100 kg x 2.20462 = 220.462 -> 220.5 lb, 80 kg -> 176.4 lb.
        compose.onNodeWithText("220.5 lb × 20").assertExists()
        compose.onNodeWithText("176.4 lb × 10").assertExists()
        compose.onNodeWithTag("workout-history-pr-$push", useUnmergedTree = true).assertExists()

        compose.activityRule.scenario.recreate()
        awaitTag("segmented-History")
        compose.onNodeWithTag("segmented-History").assertIsSelected()

        progressItem("workout-history-$pull").performClick()
        progressItem("delete-workout-$pull").performClick()
        compose.onNodeWithTag("confirm-delete-workout").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("workout-history-$pull")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitValue("home-workouts-value", "1")
    }

    @Test fun workoutHistoryShowsReplacedExerciseAsTwoOccurrencesWithSideLabels() {
        val (sessionId, firstSet, secondSet) = runBlocking {
            val repo = container.repository
            repo.saveExercisePreferences(benchId, null, null, null, true)
            val id = repo.startSession(seededId("template", "Push Day A"))
            val bench = repo.sessionExercises(id).first().first()
            val first = repo.logSet(bench.id, SetType.WORKING, 70.0, 5, 2, side = SetSide.LEFT)
            val replacement = repo.replaceSessionExercise(bench.id, benchId)
            val second = repo.logSet(replacement, SetType.WORKING, 80.0, 10, 2, side = SetSide.RIGHT)
            repo.finishSession(id)
            Triple(id, first, second)
        }
        compose.onNodeWithTag("tab-Progress").performClick()
        awaitTag("segmented-History")
        compose.onNodeWithTag("segmented-History").performClick()
        progressItem("workout-history-$sessionId").performClick()
        // Two Bench Press occurrences in one workout stay separate, each with its own set.
        compose.onAllNodesWithText("Bench Press").assertCountEquals(2)
        progressItem("history-set-$firstSet").assertTextContains("L1", substring = true)
        progressItem("history-set-$firstSet").assertTextContains("70 kg × 5", substring = true)
        progressItem("history-set-$secondSet").assertTextContains("R1", substring = true)
        progressItem("history-set-$secondSet").assertTextContains("80 kg × 10", substring = true)
    }

    private fun recordSingleSet(weight: Double, reps: Int, daysAgo: Int): String = runBlocking {
        val repo = container.repository
        val id = repo.startSession(seededId("template", "Push Day A"))
        repo.logSet(repo.sessionExercises(id).first().first().id, SetType.WORKING, weight, reps, 2)
        repo.finishSession(id)
        val session = repo.session(id)!!
        container.database.dao().updateSession(session.copy(
            startedAt = session.startedAt - daysAgo * DAY_MS,
            finishedAt = session.finishedAt!! - daysAgo * DAY_MS,
        ))
        id
    }

}
