package com.buhat.tracker.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.net.Uri
import com.buhat.tracker.MainActivity
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val ONGOING_NOTIFICATION_ID = 1001
const val COMPLETE_NOTIFICATION_ID = 1002
const val ONGOING_CHANNEL_ID = "rest_timer"
// v2 adds an explicit vibration pattern; channels are immutable once created.
const val COMPLETE_CHANNEL_ID = "rest_complete_v2"
internal const val REST_ALARM_ACTION = "com.buhat.tracker.REST_ALARM"
internal const val REST_ENDS_AT_EXTRA = "rest_ends_at"

private fun notificationManager(context: Context) =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

private fun alarmManager(context: Context) =
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

private fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun ensureRestChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = notificationManager(context)
    manager.createNotificationChannel(
        NotificationChannel(
            ONGOING_CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_LOW,
        ),
    )
    manager.createNotificationChannel(
        NotificationChannel(
            COMPLETE_CHANNEL_ID,
            "Rest complete",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
        },
    )
}

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

fun showOngoingRestNotification(context: Context, endsAt: Long) {
    if (!canPostNotifications(context)) return
    ensureRestChannels(context)
    val notification = Notification.Builder(context, ONGOING_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Rest")
        .setContentText("Time until your next set")
        .setWhen(endsAt)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent(context))
        .build()
    notificationManager(context).notify(ONGOING_NOTIFICATION_ID, notification)
}

fun showRestCompleteNotification(context: Context) {
    if (!canPostNotifications(context)) return
    ensureRestChannels(context)
    val notification = Notification.Builder(context, COMPLETE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Rest complete")
        .setContentText("Time for your next set.")
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context))
        .build()
    notificationManager(context).notify(COMPLETE_NOTIFICATION_ID, notification)
}

fun cancelOngoingRestNotification(context: Context) {
    notificationManager(context).cancel(ONGOING_NOTIFICATION_ID)
}

/** Sound and vibration when notifications cannot carry the rest alert. */
fun playDirectAlert(context: Context) {
    runCatching {
        RingtoneManager.getRingtone(context, Settings.System.DEFAULT_NOTIFICATION_URI)?.play()
    }
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }
}

class RestTimer(
    context: Context,
    private val settingsStore: SettingsStore,
    private val canNotify: (Context) -> Boolean = ::canPostNotifications,
    private val directAlert: (Context) -> Unit = ::playDirectAlert,
) {

    private val appContext = context.applicationContext
    private val notificationsMutex = Mutex()

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(appContext).canScheduleExactAlarms()

    fun exactAlarmPermissionIntent(): Intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${appContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun start(endsAt: Long): Boolean = notificationsMutex.withLock {
        if (settingsStore.settings.first().restEndsAt != endsAt) return@withLock true
        if (!canScheduleExactAlarms()) return@withLock false
        try {
            scheduleAlarm(endsAt)
        } catch (_: SecurityException) {
            return@withLock false
        }
        showOngoingRestNotification(appContext, endsAt)
        true
    }

    suspend fun cancel() = notificationsMutex.withLock {
        if (settingsStore.settings.first().restEndsAt != null) return@withLock
        alarmManager(appContext).cancel(alarmIntent())
        cancelOngoingRestNotification(appContext)
    }

    suspend fun completeAlarm(endsAt: Long, repository: BuhatRepository): Boolean =
        notificationsMutex.withLock {
            if (!repository.completeRest(endsAt)) return@withLock false
            if (canNotify(appContext)) showRestCompleteNotification(appContext) else directAlert(appContext)
            cancelOngoingRestNotification(appContext)
            true
        }

    private fun scheduleAlarm(endsAt: Long) {
        alarmManager(appContext).setAlarmClock(
            AlarmManager.AlarmClockInfo(endsAt, openAppIntent(appContext)),
            alarmIntent(endsAt),
        )
    }

    private fun alarmIntent(endsAt: Long? = null): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, RestAlarmReceiver::class.java)
            .setAction(REST_ALARM_ACTION)
            .apply { endsAt?.let { putExtra(REST_ENDS_AT_EXTRA, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
