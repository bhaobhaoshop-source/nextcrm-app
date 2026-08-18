package com.estatedesk.crm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.data.Activity
import com.estatedesk.crm.services.Notifier

/** Fires a local notification when a task reminder comes due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0)
        if (taskId == 0L) return
        val pending = goAsync()
        Async.db({
            val task = Di.store.tasks.byId(taskId)
            if (task != null && task.status == "Open") {
                val contactName = task.contactId.takeIf { it > 0 }
                    ?.let { Di.store.contacts.byId(it)?.displayName() } ?: ""
                Notifier.showTaskReminder(context, task, contactName)
                Di.store.activities.add(
                    Activity.T_REMINDER,
                    "Reminder: ${task.title}",
                    task.notes,
                    contactId = task.contactId,
                    leadId = task.leadId,
                    taskId = task.id
                )
            }
        }) { pending.finish() }
    }
}
