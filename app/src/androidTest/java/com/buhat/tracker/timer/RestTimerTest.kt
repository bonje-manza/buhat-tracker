package com.buhat.tracker.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.buhat.tracker.BuhatApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class RestTimerTest {

    private lateinit var app: BuhatApplication
    private lateinit var notifications: NotificationManager
    private lateinit var alarms: AlarmManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.resetForTest()
        notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notifications.cancelAll()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            app.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @After
    fun cancelAlarm() = runBlocking {
        app.container.repository.clearRest()
        app.container.restTimer.cancel()
    }

    private fun active(id: Int) = notifications.activeNotifications.firstOrNull { it.id == id }

    private fun awaitActive(id: Int, timeoutMillis: Long = 5_000) =
        active(id) ?: run {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline && active(id) == null) {
                Thread.sleep(100)
            }
            active(id)
        }

    private fun awaitGone(id: Int, timeoutMillis: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && active(id) != null) {
            Thread.sleep(100)
        }
        assertNull(active(id))
    }

    @Test
    fun completionChannelHasSoundAndVibration() {
        ensureRestChannels(app)

        val channel = notifications.getNotificationChannel(COMPLETE_CHANNEL_ID)
        assertNotNull(channel?.sound)
        assertTrue(channel!!.shouldVibrate())
    }

    @Test
    fun completionWithoutNotificationPermissionPlaysADirectAlert() = runBlocking {
        var alerted = false
        val timer = RestTimer(
            app,
            app.container.settingsStore,
            canNotify = { false },
            directAlert = { alerted = true },
        )
        val endsAt = System.currentTimeMillis() + 1_000
        app.container.settingsStore.setRestEndsAt(endsAt)

        assertTrue(timer.completeAlarm(endsAt, app.container.repository))
        assertTrue("expected a sound/vibration alert when notifications are off", alerted)
        assertNull(active(COMPLETE_NOTIFICATION_ID))
    }

    @Test
    fun aStaleCompletionCannotRemoveANewerNotification() = runBlocking {
        val staleEndsAt = System.currentTimeMillis() + 30_000
        val currentEndsAt = staleEndsAt + 30_000
        app.container.settingsStore.setRestEndsAt(currentEndsAt)
        showOngoingRestNotification(app, currentEndsAt)

        assertFalse(app.container.restTimer.completeAlarm(staleEndsAt, app.container.repository))

        assertEquals(currentEndsAt, app.container.settingsStore.settings.first().restEndsAt)
        assertNotNull(awaitActive(ONGOING_NOTIFICATION_ID))
    }

    @Test
    fun aDelayedStartCannotRearmAClearedRest() = runBlocking {
        val endsAt = System.currentTimeMillis() + 60_000
        app.container.settingsStore.setRestEndsAt(endsAt)
        app.container.restTimer.start(endsAt)
        app.container.repository.clearRest()
        app.container.restTimer.cancel()
        awaitGone(ONGOING_NOTIFICATION_ID)

        app.container.restTimer.start(endsAt)

        assertNull(alarms.nextAlarmClock)
        assertNull(active(ONGOING_NOTIFICATION_ID))
    }

    @Test
    fun aDelayedCancellationCannotRemoveANewerRest() = runBlocking {
        val endsAt = System.currentTimeMillis() + 60_000
        app.container.repository.clearRest()
        app.container.settingsStore.setRestEndsAt(endsAt)
        app.container.restTimer.start(endsAt)
        assertNotNull(awaitActive(ONGOING_NOTIFICATION_ID))

        app.container.restTimer.cancel()

        assertEquals(endsAt, alarms.nextAlarmClock?.triggerTime)
        assertNotNull(active(ONGOING_NOTIFICATION_ID))
    }

    @Test
    fun startingARestPostsTheCountdownAndSchedulesTheAlarm() = runBlocking {
        val endsAt = System.currentTimeMillis() + 60_000
        app.container.settingsStore.setRestEndsAt(endsAt)

        app.container.restTimer.start(endsAt)

        assertNotNull(awaitActive(ONGOING_NOTIFICATION_ID))
        assertEquals(endsAt, alarms.nextAlarmClock?.triggerTime)

        app.container.repository.clearRest()
        app.container.restTimer.cancel()

        awaitGone(ONGOING_NOTIFICATION_ID)
    }

    @Test
    fun aStaleAlarmDoesNotClearANewerRest() {
        val staleEndsAt = System.currentTimeMillis() + 2_000
        runBlocking { app.container.settingsStore.setRestEndsAt(staleEndsAt) }
        runBlocking { app.container.restTimer.start(staleEndsAt) }

        // A new rest replaces the old one before the old alarm fires.
        val newerEndsAt = System.currentTimeMillis() + 60_000
        runBlocking { app.container.settingsStore.setRestEndsAt(newerEndsAt) }
        assertEquals(staleEndsAt, alarms.nextAlarmClock?.triggerTime)

        Thread.sleep(10_000)

        assertNull(active(COMPLETE_NOTIFICATION_ID))
        assertEquals(
            newerEndsAt,
            runBlocking { app.container.settingsStore.settings.first().restEndsAt },
        )
    }

    @Test
    fun theAlarmPostsTheCompletionAlertAndClearsTheTimer() {
        val endsAt = System.currentTimeMillis() + 2_000
        runBlocking { app.container.settingsStore.setRestEndsAt(endsAt) }

        runBlocking { app.container.restTimer.start(endsAt) }

        assertNotNull(awaitActive(COMPLETE_NOTIFICATION_ID, timeoutMillis = 15_000))
        awaitGone(ONGOING_NOTIFICATION_ID)
        assertNull(runBlocking { app.container.settingsStore.settings.first().restEndsAt })
    }
}
