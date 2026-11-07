package com.buhat.tracker.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.BuildConfig
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.seededId
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
class UpdatePromptTest {

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
    fun freshInstallDoesNotPrompt() {
        compose.waitForIdle()
        compose.onNodeWithTag("update-prompt").assertDoesNotExist()
        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.lastRunVersionCode.first() } == BuildConfig.VERSION_CODE
        }
    }

    @Test
    fun versionChangePromptsOnceAndNotNowAcknowledges() {
        compose.waitForIdle()
        runBlocking { container.settingsStore.acknowledgeVersion(BuildConfig.VERSION_CODE - 1) }

        awaitTag("update-prompt")
        compose.onNodeWithTag("update-later").performClick()
        awaitNoTag("update-prompt")
        assertEquals(
            BuildConfig.VERSION_CODE,
            runBlocking { container.settingsStore.lastRunVersionCode.first() },
        )
    }

    @Test
    fun activeWorkoutDisablesExport() {
        compose.waitForIdle()
        runBlocking {
            container.settingsStore.acknowledgeVersion(BuildConfig.VERSION_CODE - 1)
            container.repository.startSession(seededId("template", "Push Day A"))
        }

        awaitTag("update-prompt")
        compose.onNodeWithTag("update-export").assertIsNotEnabled()
        compose.onNodeWithText("Finish or discard the workout first", substring = true).assertExists()
    }
}
