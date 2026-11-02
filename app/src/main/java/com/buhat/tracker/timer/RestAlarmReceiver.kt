package com.buhat.tracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buhat.tracker.BuhatApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REST_ALARM_ACTION) return
        val endsAt = intent.getLongExtra(REST_ENDS_AT_EXTRA, 0L)
        if (endsAt <= 0L) return
        val app = context.applicationContext as? BuhatApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.restTimer.completeAlarm(endsAt, app.container.repository)
            } finally {
                pending.finish()
            }
        }
    }
}
