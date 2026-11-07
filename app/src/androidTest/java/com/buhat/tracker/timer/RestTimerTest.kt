package com.buhat.tracker.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
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

private const val MUTED_COMPLETE_CHANNEL_ID = "rest_complete_muted_test"
private const val SILENT_COMPLETE_CHANNEL_ID = "rest_complete_silent_test"

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

    @After
    fun deleteMutedChannel() {
        notifications.deleteNotificationChannel(MUTED_COMPLETE_CHANNEL_ID)
        notifications.deleteNotificationChannel(SILENT_COMPLETE_CHANNEL_ID)
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
        assertNotNull("completion channel needs an explicit vibration pattern", channel.vibrationPattern)
    }

    @Test
    fun completionWithoutNotificationPermissionPlaysADirectAlert() = runBlocking {
        var alerted = false
        val timer = RestTimer(
            app,
            app.container.settingsStore,
            canNotify = { false },
            directAlert = {
                alerted = true
                true
            },
        )
        val endsAt = System.currentTimeMillis() + 1_000
        app.container.settingsStore.setRestEndsAt(endsAt)

        assertTrue(timer.completeAlarm(endsAt, app.container.repository))
        assertTrue("expected a sound/vibration alert when notifications are off", alerted)
        assertNull(active(COMPLETE_NOTIFICATION_ID))
    }

    @Test
    fun aMutedCompletionChannelPlaysADirectAlert() = runBlocking {
        // The real completion channel keeps its importance for the whole install once muted,
        // so the muted-channel path is exercised on a throwaway channel with the same settings.
        notifications.createNotificationChannel(
            NotificationChannel(
                MUTED_COMPLETE_CHANNEL_ID,
                "Muted rest complete",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )
        var alerted = false
        val timer = RestTimer(
            app,
            app.container.settingsStore,
            canNotify = { canShowRestComplete(it, MUTED_COMPLETE_CHANNEL_ID) },
            directAlert = {
                alerted = true
                true
            },
        )
        val endsAt = System.currentTimeMillis() + 1_000
        app.container.settingsStore.setRestEndsAt(endsAt)

        assertFalse("a muted channel cannot carry the completion alert", canShowRestComplete(app, MUTED_COMPLETE_CHANNEL_ID))
        assertTrue(timer.completeAlarm(endsAt, app.container.repository))
        assertTrue("expected a sound/vibration alert when the completion channel is off", alerted)
        assertNull(active(COMPLETE_NOTIFICATION_ID))
        assertNull(app.container.settingsStore.settings.first().restEndsAt)
    }

    @Test
    fun aSilencedCompletionChannelPlaysADirectAlert() = runBlocking {
        // A deliverable channel with neither sound nor vibration cannot carry the alert either.
        notifications.createNotificationChannel(
            NotificationChannel(
                SILENT_COMPLETE_CHANNEL_ID,
                "Silent rest complete",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val channel = notifications.getNotificationChannel(SILENT_COMPLETE_CHANNEL_ID)!!
        assertNull("a silenced channel must have no sound", channel.sound)
        assertFalse("a silenced channel must not vibrate", channel.shouldVibrate())

        var alerted = false
        val timer = RestTimer(
            app,
            app.container.settingsStore,
            canNotify = { canShowRestComplete(it, SILENT_COMPLETE_CHANNEL_ID) },
            directAlert = {
                alerted = true
                true
            },
        )
        val endsAt = System.currentTimeMillis() + 1_000
        app.container.settingsStore.setRestEndsAt(endsAt)

        assertFalse("a silent channel cannot carry the completion alert", canShowRestComplete(app, SILENT_COMPLETE_CHANNEL_ID))
        assertTrue(timer.completeAlarm(endsAt, app.container.repository))
        assertTrue("expected a sound/vibration alert when the completion channel is silent", alerted)
        assertNull(active(COMPLETE_NOTIFICATION_ID))
    }

    @Test
    fun aMissingCompletionChannelFallsBackThroughTheProductionCanNotifyWiring() = runBlocking {
        // No canNotify override: the default must notice the deleted channel and play the alert.
        var alerted = false
        val timer = RestTimer(
            app,
            app.container.settingsStore,
            directAlert = {
                alerted = true
                true
            },
        )
        val endsAt = System.currentTimeMillis() + 1_000
        app.container.settingsStore.setRestEndsAt(endsAt)
        notifications.deleteNotificationChannel(COMPLETE_CHANNEL_ID)
        try {
            assertNull(notifications.getNotificationChannel(COMPLETE_CHANNEL_ID))
            assertTrue(timer.completeAlarm(endsAt, app.container.repository))
            assertTrue("expected a sound/vibration alert when the completion channel is missing", alerted)
            assertNull(active(COMPLETE_NOTIFICATION_ID))
        } finally {
            ensureRestChannels(app)
        }
    }

    @Test
    fun directAlertUsesAlarmUsageSoBackgroundPlaybackIsAllowed() {
        assertEquals(AudioAttributes.USAGE_ALARM, directAlertAudioAttributes().usage)
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
        assertTrue(app.container.restTimer.start(endsAt))
        assertNotNull(awaitActive(ONGOING_NOTIFICATION_ID))
        assertEquals(endsAt, alarms.nextAlarmClock?.triggerTime)

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
    fun theAlarmPostsTheCompletionAlertAndClearsTheTimer() {
        val endsAt = System.currentTimeMillis() + 2_000
        runBlocking { app.container.settingsStore.setRestEndsAt(endsAt) }

        runBlocking { app.container.restTimer.start(endsAt) }

        assertNotNull(awaitActive(COMPLETE_NOTIFICATION_ID, timeoutMillis = 15_000))
        awaitGone(ONGOING_NOTIFICATION_ID)
        assertNull(runBlocking { app.container.settingsStore.settings.first().restEndsAt })
    }
}
