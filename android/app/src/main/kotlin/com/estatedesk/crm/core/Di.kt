package com.estatedesk.crm.core

import android.content.Context
import android.content.SharedPreferences
import com.estatedesk.crm.data.Db
import com.estatedesk.crm.data.Store

/** Tiny service locator — the app has no DI framework by design. */
object Di {
    lateinit var ctx: Context
        private set
    lateinit var palette: Palette
        private set
    lateinit var store: Store
        private set

    fun init(context: Context) {
        ctx = context.applicationContext
        palette = Palette()
        palette.init(ctx)
        store = Store(Db.helper(ctx))
    }

    fun prefs(): SharedPreferences =
        ctx.getSharedPreferences("estatedesk", Context.MODE_PRIVATE)
}

/** Convenience typed preferences wrapper. */
class Prefs(private val ctx: Context) {
    companion object {
        const val KEY_THEME = "theme"
        const val KEY_LOCK_ENABLED = "lock_enabled"
        const val KEY_LOCK_PIN_SALT = "lock_pin_salt"
        const val KEY_LOCK_PIN_HASH = "lock_pin_hash"
        const val KEY_LOCK_BIOMETRIC = "lock_biometric"
        const val KEY_LOCK_TIMEOUT = "lock_timeout"
        const val KEY_NOTIF_ENABLED = "notif_enabled"
        const val KEY_NOTIF_DEFAULT_LEAD = "notif_lead_minutes"
        const val KEY_LAST_BACKUP = "last_backup"
        const val KEY_BACKUP_REMINDER = "backup_reminder"
        const val KEY_LAST_BG_TIME = "last_bg_time"
        const val KEY_ASKED_NOTIF_PERM = "asked_notif_perm"
        const val KEY_ASKED_PHONE_PERM = "asked_phone_perm"
        const val KEY_APP_INSTALLED_AT = "installed_at"
        const val KEY_COMPARE_IDS = "compare_ids"
    }

    private val sp: SharedPreferences = ctx.getSharedPreferences("estatedesk", Context.MODE_PRIVATE)

    fun int(key: String, def: Int): Int = sp.getInt(key, def)
    fun long(key: String, def: Long): Long = sp.getLong(key, def)
    fun bool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    fun set(key: String, value: Any) {
        sp.edit().apply {
            when (value) {
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }
}
