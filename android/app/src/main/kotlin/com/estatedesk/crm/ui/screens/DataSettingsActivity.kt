package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui

/** Data management hub (kept as its own screen for clarity). */
class DataSettingsActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.data_title))
        Async.db({
            val st = Di.store
            arrayOf(
                st.rowCount("contacts"), st.rowCount("leads"),
                st.rowCount("properties"), st.rowCount("deals")
            ).joinToString(" · ") { it.toString() }
        }) { stats ->
            if (stats != null && !isFinishing) {
                add(Ui.tv(this@DataSettingsActivity, getString(R.string.data_stats,
                    Di.store.rowCount("contacts"), Di.store.rowCount("leads"),
                    Di.store.rowCount("properties"), Di.store.rowCount("deals")),
                    13, p.textSecondary))
                add(Ui.spacer(this, 10))
            }
        }
        add(Ui.btn(this@DataSettingsActivity, getString(R.string.data_import), Ui.Btn.SECONDARY) {
            startActivity(Intent(this, ImportWizardActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@DataSettingsActivity, 10)
        })
        add(Ui.btn(this@DataSettingsActivity, getString(R.string.data_export), Ui.Btn.SECONDARY) {
            startActivity(Intent(this, ExportActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@DataSettingsActivity, 10)
        })
        add(Ui.btn(this@DataSettingsActivity, getString(R.string.data_backup), Ui.Btn.SECONDARY) {
            startActivity(Intent(this, BackupActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@DataSettingsActivity, 10)
        })
        add(Ui.btn(this@DataSettingsActivity, getString(R.string.data_recycle_bin), Ui.Btn.SECONDARY) {
            startActivity(Intent(this, RecycleBinActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
