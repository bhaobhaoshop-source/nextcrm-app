package com.estatedesk.crm.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.receivers.TaskActionReceiver
import com.estatedesk.crm.ui.screens.FollowUpTodayActivity
import com.estatedesk.crm.ui.screens.HomeActivity

/** Local notification helper. Channels are created once at app start. */
object Notifier {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_BACKUP = "backups"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            ctx.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Follow-up, task and viewing reminders"
        }
        val backups = NotificationChannel(
            CHANNEL_BACKUP,
            ctx.getString(R.string.notif_channel_backups),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Backup reminders" }
        nm.createNotificationChannel(reminders)
        nm.createNotificationChannel(backups)
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    fun showTaskReminder(ctx: Context, task: Task, contactName: String) {
        if (!hasPermission(ctx)) return
        val title = when (task.kind) {
            Task.KIND_FOLLOW_UP -> ctx.getString(R.string.notif_followup_title, contactName.ifBlank { task.title })
            Task.KIND_VIEWING -> ctx.getString(R.string.notif_viewing_title, task.title)
            Task.KIND_MEETING -> ctx.getString(R.string.notif_meeting_title, task.title)
            else -> ctx.getString(R.string.notif_task_title, task.title)
        }
        val whenText = Fmt.friendlyDateTime(task.dueAt())
        val text = if (whenText.isNotBlank()) "$whenText" else task.notes

        val openIntent = PendingIntent.getActivity(
            ctx, 1000 + task.id.toInt(),
            Intent(ctx, FollowUpTodayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = PendingIntent.getBroadcast(
            ctx, 2000 + task.id.toInt(),
            Intent(ctx, TaskActionReceiver::class.java)
                .setAction("com.estatedesk.crm.action.DONE")
                .putExtra("task_id", task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            ctx, 3000 + task.id.toInt(),
            Intent(ctx, TaskActionReceiver::class.java)
                .setAction("com.estatedesk.crm.action.SNOOZE")
                .putExtra("task_id", task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\n\n${task.notes}"))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(0, ctx.getString(R.string.notif_action_done), doneIntent)
            .addAction(0, ctx.getString(R.string.notif_action_snooze), snoozeIntent)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(4000 + task.id.toInt(), n)
    }

    fun showTest(ctx: Context) {
        if (!hasPermission(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 9000,
            Intent(ctx, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(ctx.getString(R.string.settings_test_notification))
            .setContentText(ctx.getString(R.string.home_followups_today))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(9001, n)
    }

    fun showBackupReminder(ctx: Context) {
        if (!hasPermission(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 9100,
            Intent(ctx, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(ctx.getString(R.string.data_backup))
            .setContentText(ctx.getString(R.string.backup_what))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(9101, n)
    }
}
