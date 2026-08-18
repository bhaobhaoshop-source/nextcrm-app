package com.estatedesk.crm.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.receivers.ReminderReceiver
import java.util.Calendar

/** Schedules local alarms for task/follow-up/viewing reminders. Works
 *  offline; alarms are re-armed on boot and on every app start. */
object ReminderScheduler {

    fun schedule(ctx: Context, task: Task) {
        if (!task.reminder || task.status != Task.STATUS_OPEN) return
        val dueAt = task.dueAt()
        if (dueAt <= System.currentTimeMillis()) return
        val leadMinutes = when (task.kind) {
            Task.KIND_VIEWING -> 30
            Task.KIND_MEETING -> 60
            else -> 15
        }
        val fireAt = dueAt - leadMinutes * 60_000L
        if (fireAt <= System.currentTimeMillis()) return

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderIntent(ctx, task.id, fireAt)
        if (Build.VERSION.SDK_INT < 23) {
            am.setExact(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else if (Build.VERSION.SDK_INT >= 31) {
            val canExact = am.canScheduleExactAlarms()
            try {
                if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                else am.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 15 * 60_000L, pi)
            } catch (t: Throwable) {
                am.setWindow(AlarmManager.RTC_WAKEUP, fireAt, 15 * 60_000L, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    fun cancel(ctx: Context, taskId: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderIntent(ctx, taskId, 0)
        am.cancel(pi)
    }

    private fun reminderIntent(ctx: Context, taskId: Long, fireAt: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction("com.estatedesk.crm.reminder.$taskId")
            .putExtra("task_id", taskId)
        return PendingIntent.getBroadcast(
            ctx, (taskId % 100_000).toInt(),
            i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Re-arm all upcoming reminders (next 7 days). Called at app start & boot. */
    fun rescheduleAll(ctx: Context) {
        Async.db({
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 7)
            Di.store.tasks.upcomingWithReminders(cal.timeInMillis)
        }) { list ->
            list.forEach { schedule(ctx, it) }
        }
    }

    /** Re-arm a single task after save. */
    fun rescheduleTask(ctx: Context, task: Task) {
        cancel(ctx, task.id)
        schedule(ctx, task)
    }
}
