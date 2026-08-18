package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.services.Notifier

class NotificationSettingsActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.settings_notifications))
        val prefs = Prefs(this)
        val enabled = CheckBox(this).apply {
            text = getString(R.string.notif_enable)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = prefs.bool(Prefs.KEY_NOTIF_ENABLED, true)
            setOnCheckedChangeListener { _, b -> prefs.set(Prefs.KEY_NOTIF_ENABLED, b) }
        }
        add(enabled)
        add(Ui.caption(this@NotificationSettingsActivity, getString(R.string.notif_enable_desc)))
        add(Ui.spacer(this, 12))
        add(Ui.btn(this@NotificationSettingsActivity, getString(R.string.settings_test_notification), Ui.Btn.SECONDARY) {
            if (!Notifier.hasPermission(this)) {
                Ui.alert(this, getString(R.string.perm_notif_title),
                    getString(R.string.notif_perm_denied), getString(R.string.notif_open_settings)) {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(i)
                }
            } else {
                Notifier.showTest(this)
                snack(getString(R.string.settings_test_sent))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
