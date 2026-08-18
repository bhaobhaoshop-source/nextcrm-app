package com.estatedesk.crm

import android.app.Application
import android.content.Context
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Palette
import com.estatedesk.crm.data.Db
import com.estatedesk.crm.services.Notifier
import com.estatedesk.crm.services.ReminderScheduler

/** Application entry point. Initializes theme, database and reminders. */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Di.init(this)
        Db.open(this)
        Notifier.createChannels(this)
        ReminderScheduler.rescheduleAll(this)
    }

    override fun onTerminate() {
        Db.close()
        super.onTerminate()
    }

    companion object {
        fun ctx(): Context = Di.ctx
        fun palette(): Palette = Di.palette
    }
}
