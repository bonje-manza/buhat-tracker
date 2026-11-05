package com.buhat.tracker.ui

import android.Manifest
import androidx.room.withTransaction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.Settings
import com.buhat.tracker.data.seededId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
@SdkSuppress(minSdkVersion = 33)
class RestTimerTest {

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

    private val container
        get() = ApplicationProvider.getApplicationContext<BuhatApplication>().container

    @Before
    fun grantNotifications() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<BuhatApplication>().packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    private fun startPushDayA() {
        compose.onNodeWithTag("tab-Workouts").performClick()
        compose.onNodeWithTag("start-workout-Push Day A").performClick()
        awaitTag("screen-Active")
    }

    private fun logWorkingSet() {
        compose.onNodeWithTag("weight-input").performTextReplacement("80")
        compose.onNodeWithTag("reps-input").performTextReplacement("8")
        compose.onNodeWithTag("log-set").performScrollTo().performClick()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun blockDatabaseWrites(): Pair<CountDownLatch, Thread> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = Thread {
            runBlocking {
                container.database.withTransaction {
                    container.database.dao().deleteSetById("transaction-blocker")
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        return release to blocker
    }

    private fun releaseDatabaseWrites(block: Pair<CountDownLatch, Thread>) {
        block.first.countDown()
        block.second.join(5_000)
    }

    @Test
    fun loggingASetShowsTheCountdownAndSkipClearsIt() {
        runBlocking { container.settingsStore.save(Settings(restWorkingSeconds = 90)) }
        startPushDayA()

        logWorkingSet()

        awaitTag("rest-card")
        compose.onNodeWithTag("rest-countdown").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("rest-countdown").assertIsDisplayed()

        compose.onNodeWithTag("skip-rest").performClick()

        awaitGone("rest-card")
        assertNull(runBlocking { container.settingsStore.settings.first().restEndsAt })
    }

    @Test
    fun loggingSetAndLeavingActiveKeepsTheBackgroundAlarm() {
        startPushDayA()
        val databaseBlock = blockDatabaseWrites()
        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)

        try {
            compose.onNodeWithTag("weight-input").performTextReplacement("80")
            compose.onNodeWithTag("reps-input").performTextReplacement("8")
            compose.onNodeWithTag("log-set").performScrollTo().performClick()
            compose.onNodeWithTag("tab-Workouts").performClick()
        } finally {
            releaseDatabaseWrites(databaseBlock)
        }

        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.settings.first().restEndsAt != null } &&
                notifications.activeNotifications.any {
                    it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
                } && alarms.nextAlarmClock != null
        }
    }

    @Test
    fun loggingZeroRestAndLeavingActiveCancelsThePreviousAlarm() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")
        runBlocking { container.settingsStore.save(Settings(restWorkingSeconds = 0)) }
        assertEquals(0, runBlocking { container.settingsStore.settings.first().restWorkingSeconds })

        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        compose.waitUntil(5_000) {
            notifications.activeNotifications.any {
                it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
            } && alarms.nextAlarmClock != null
        }
        val databaseBlock = blockDatabaseWrites()
        try {
            compose.onNodeWithTag("log-set").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("tab-Workouts").performClick()
        } finally {
            releaseDatabaseWrites(databaseBlock)
        }

        val exercise = runBlocking {
            val session = container.repository.activeSession().first()!!
            container.repository.sessionExercises(session.id).first().first()
        }
        compose.waitUntil(5_000) {
            runBlocking { container.repository.loggedSets(exercise.id).first().size == 2 }
        }
        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.settings.first().restEndsAt == null }
        }
        compose.waitUntil(5_000) {
            notifications.activeNotifications.none {
                it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
            } && alarms.nextAlarmClock == null
        }
    }

    @Test
    fun expiredRestCleanupCannotClearAnInFlightNewRest() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        val databaseBlock = blockDatabaseWrites()
        try {
            compose.onNodeWithTag("log-set").performScrollTo().performClick()
            compose.waitForIdle()
            // Deliver an expired saved deadline while the new log owns the session write lock.
            runBlocking {
                container.settingsStore.setRestEndsAt(System.currentTimeMillis() - 1_000)
            }
            compose.waitForIdle()
        } finally {
            releaseDatabaseWrites(databaseBlock)
        }

        compose.waitUntil(5_000) {
            val endsAt = runBlocking { container.settingsStore.settings.first().restEndsAt }
            endsAt != null && endsAt > System.currentTimeMillis() &&
                alarms.nextAlarmClock?.triggerTime == endsAt
        }
    }

    @Test
    fun runningRestIsRestoredAfterActivityRecreate() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")
        val endsAt = runBlocking { container.settingsStore.settings.first().restEndsAt }!!

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        awaitTag("rest-card")
        compose.onNodeWithTag("rest-countdown").performScrollTo().assertIsDisplayed()
        assertEquals(endsAt, runBlocking { container.settingsStore.settings.first().restEndsAt })
    }

    @Test
    fun finishingTheWorkoutCancelsTheRest() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")

        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        compose.waitUntil(5_000) {
            notifications.activeNotifications.any { it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID }
        }

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").performClick()

        awaitGone("rest-card")
        awaitTag("active-empty")
        compose.waitUntil(5_000) {
            notifications.activeNotifications.none {
                it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
            }
        }
        compose.waitUntil(5_000) { alarms.nextAlarmClock == null }
    }

    @Test
    fun discardingTheWorkoutCancelsTheRest() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")

        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        compose.waitUntil(5_000) {
            notifications.activeNotifications.any { it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID }
        }

        compose.onNodeWithTag("active-menu").performScrollTo().performClick()
        compose.onNodeWithText("Discard workout").performClick()
        compose.onNodeWithTag("confirm-discard").performClick()

        awaitTag("active-empty")
        compose.waitUntil(5_000) {
            notifications.activeNotifications.none {
                it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
            } && alarms.nextAlarmClock == null
        }
    }

    @Test
    fun discardAndStartFailureStillCancelsTheOldRest() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")

        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        compose.waitUntil(5_000) {
            notifications.activeNotifications.any { it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID }
        }

        compose.onNodeWithTag("tab-Workouts").performClick()
        awaitTag("start-workout-Pull Day A")
        compose.onNodeWithTag("start-workout-Pull Day A").performClick()
        awaitText("Workout in progress")
        runBlocking {
            container.database.dao().deleteTemplates(listOf(seededId("template", "Pull Day A")))
        }
        compose.onNodeWithTag("discard-and-start").performClick()
        awaitText("Template not found.")

        compose.waitUntil(5_000) {
            notifications.activeNotifications.none {
                it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
            } && alarms.nextAlarmClock == null
        }
    }

    @Test
    fun leavingWorkoutsDuringDiscardStillCancelsTheOldRest() {
        startPushDayA()
        logWorkingSet()
        awaitTag("rest-card")
        val notifications = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.NotificationManager::class.java)
        val alarms = ApplicationProvider.getApplicationContext<BuhatApplication>()
            .getSystemService(android.app.AlarmManager::class.java)
        compose.waitUntil(5_000) {
            notifications.activeNotifications.any { it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID }
        }

        compose.onNodeWithTag("tab-Workouts").performClick()
        compose.onNodeWithTag("start-workout-Pull Day A").performClick()
        awaitText("Workout in progress")
        runBlocking {
            container.database.dao().deleteTemplates(listOf(seededId("template", "Pull Day A")))
        }
        val databaseBlock = blockDatabaseWrites()

        try {
            compose.onNodeWithTag("discard-and-start").performClick()
            compose.onNodeWithTag("tab-Home").performClick()
        } finally {
            releaseDatabaseWrites(databaseBlock)
        }

        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.settings.first().restEndsAt == null } &&
                notifications.activeNotifications.none {
                    it.id == com.buhat.tracker.timer.ONGOING_NOTIFICATION_ID
                } && alarms.nextAlarmClock == null
        }
    }

    @Test
    fun restDefaultsEditAndSurviveRecreate() {
        awaitTag("settings-button")
        compose.onNodeWithTag("settings-button").performClick()
        compose.onNodeWithTag("screen-Settings").assertIsDisplayed()

        awaitTag("rest-working-plus")
        compose.onNodeWithTag("rest-working-plus").performScrollTo().performClick()

        compose.waitUntil(5_000) {
            runBlocking { container.settingsStore.settings.first().restWorkingSeconds } == 135
        }
        awaitText("2:15")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        // Navigation state is restored, so Settings is still on screen after the recreate.
        awaitTag("screen-Settings")
        awaitText("2:15")
        assertEquals(135, runBlocking { container.settingsStore.settings.first().restWorkingSeconds })
    }
}
