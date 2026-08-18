package com.estatedesk.crm.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.estatedesk.crm.services.ReminderScheduler

/** Re-arms reminders after a device reboot so follow-ups are never lost. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
