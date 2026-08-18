package com.estatedesk.crm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.services.ReminderScheduler

/** Notification actions: mark done / snooze. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0)
        if (taskId == 0L) return
        val pending = goAsync()
        Async.write({
            when (intent.action) {
                "com.estatedesk.crm.action.DONE" -> Di.store.tasks.complete(taskId)
                "com.estatedesk.crm.action.SNOOZE" -> Di.store.tasks.snooze(taskId, 60 * 60_000L)
            }
            val t = Di.store.tasks.byId(taskId)
            if (t != null) {
                ReminderScheduler.cancel(context, taskId)
                ReminderScheduler.schedule(context, t)
            }
        }) { pending.finish() }
    }
}
