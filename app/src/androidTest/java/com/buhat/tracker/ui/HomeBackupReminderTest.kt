package com.buhat.tracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.recordBenchWorkout
import com.buhat.tracker.data.seededId
import com.buhat.tracker.domain.DAY_MILLIS
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class HomeBackupReminderTest {

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

    private val container get() = ApplicationProvider.getApplicationContext<BuhatApplication>().container

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitNoTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun reminderAppearsAfterTheFirstWorkoutAndSnoozeHidesIt() {
        compose.waitForIdle()
        compose.onNodeWithTag("backup-reminder").assertDoesNotExist()

        runBlocking { container.recordBenchWorkout() }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("backup-reminder")
        compose.onNodeWithTag("backup-reminder").performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("backup-reminder-snooze").performScrollTo().performClick()
        awaitNoTag("backup-reminder")
    }

    @Test
    fun exportClearsTheReminder() {
        runBlocking { container.recordBenchWorkout() }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("backup-reminder")

        runBlocking { container.settingsStore.markExported(System.currentTimeMillis()) }

        awaitNoTag("backup-reminder")
    }

    @Test
    fun aStaleExportBringsTheReminderBack() {
        runBlocking {
            container.recordBenchWorkout()
            container.settingsStore.markExported(System.currentTimeMillis() - 15 * DAY_MILLIS)
        }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("backup-reminder")
        compose.onNodeWithTag("backup-reminder").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeWorkoutDisablesExportAndShowsTheHint() {
        runBlocking {
            container.recordBenchWorkout()
            container.repository.startSession(seededId("template", "Push Day A"))
        }
        compose.onNodeWithTag("tab-Home").performClick()
        awaitTag("backup-reminder")

        compose.onNodeWithTag("backup-reminder-export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Finish or discard the workout first.", substring = true).assertExists()
    }
}
