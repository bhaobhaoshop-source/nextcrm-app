package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Store

class SettingsActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.settings_title))

        // profile header
        Async.db({
            val st = Di.store
            Triple(st.profileName(), st.profileCompany(), st.currency())
        }) { t ->
            if (t == null || isFinishing) return@db
            val (name, company, cur) = t
            val head = Ui.card(this, emptyList(), padding = 16) {
                startActivity(Intent(this, ProfileSettingsActivity::class.java))
            }
            val row = Ui.hbox(this)
            row.addView(Ui.avatar(this, name.ifBlank { getString(R.string.app_name) }, 52))
            val mid = Ui.vbox(this)
            val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lpMid.leftMargin = Ui.dp(this@SettingsActivity, 12)
            mid.addView(Ui.tv(this@SettingsActivity, name.ifBlank { getString(R.string.settings_profile) }, 16, p.textPrimary, Ui.Font.BOLD))
            mid.addView(Ui.tv(this@SettingsActivity, "${company} · ${cur.code}", 12, p.textTertiary))
            head.addView(row)
            head.addView(mid, lpMid)
            head.addView(Ui.icon(this@SettingsActivity, R.drawable.ic_chevron, 16, p.textTertiary))
            add(head, lpm())
        }

        add(Ui.sectionTitle(this, getString(R.string.settings_general)))
        add(Ui.card(this, listOf(
            item(R.string.settings_appearance, R.string.settings_appearance_desc, R.drawable.ic_eye) {
                startActivity(Intent(this, AppearanceSettingsActivity::class.java))
            },
            item(R.string.settings_notifications, R.string.settings_notifications_desc, R.drawable.ic_notifications) {
                startActivity(Intent(this, NotificationSettingsActivity::class.java))
            },
            item(R.string.settings_app_lock, R.string.settings_app_lock_desc, R.drawable.ic_lock) {
                startActivity(Intent(this, AppLockSettingsActivity::class.java))
            },
            item(R.string.settings_currency, R.string.settings_currency_desc, R.drawable.ic_money) {
                startActivity(Intent(this, CurrencySettingsActivity::class.java))
            }
        ), padding = 4), lpm())

        add(Ui.sectionTitle(this, getString(R.string.settings_personalization)))
        add(Ui.card(this, listOf(
            item(R.string.settings_pipeline, R.string.settings_pipeline_desc, R.drawable.ic_swap) {
                startActivity(Intent(this, PipelineStagesActivity::class.java).putExtra("deal", false))
            },
            item(R.string.settings_deal_stages, R.string.settings_deal_stages_desc, R.drawable.ic_swap) {
                startActivity(Intent(this, PipelineStagesActivity::class.java).putExtra("deal", true))
            },
            item(R.string.settings_sources, 0, R.drawable.ic_flag) {
                startActivity(Intent(this, PicklistSettingsActivity::class.java).putExtra("kind", "sources"))
            },
            item(R.string.settings_property_types, 0, R.drawable.ic_building) {
                startActivity(Intent(this, PicklistSettingsActivity::class.java).putExtra("kind", "types"))
            },
            item(R.string.settings_tags, 0, R.drawable.ic_star) {
                startActivity(Intent(this, PicklistSettingsActivity::class.java).putExtra("kind", "tags"))
            },
            item(R.string.settings_custom_fields, R.string.settings_custom_fields_desc, R.drawable.ic_list) {
                startActivity(Intent(this, CustomFieldsSettingsActivity::class.java))
            }
        ), padding = 4), lpm())

        add(Ui.sectionTitle(this, getString(R.string.settings_data_section)))
        add(Ui.card(this, listOf(
            item(R.string.data_import, 0, R.drawable.ic_upload) {
                startActivity(Intent(this, ImportWizardActivity::class.java))
            },
            item(R.string.data_export, 0, R.drawable.ic_download) {
                startActivity(Intent(this, ExportActivity::class.java))
            },
            item(R.string.data_backup, 0, R.drawable.ic_refresh) {
                startActivity(Intent(this, BackupActivity::class.java))
            },
            item(R.string.data_recycle_bin, 0, R.drawable.ic_delete) {
                startActivity(Intent(this, RecycleBinActivity::class.java))
            },
            item(R.string.data_sample, 0, R.drawable.ic_note) {
                sampleMenu()
            }
        ), padding = 4), lpm())

        add(Ui.card(this, listOf(
            item(R.string.settings_about, R.string.settings_about_desc, R.drawable.ic_person) {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        ), padding = 4), lpm())
        add(Ui.spacer(this, 40))
    }

    private fun item(titleRes: Int, descRes: Int, iconRes: Int, fn: () -> Unit): LinearLayout {
        val row = Ui.hbox(this)
        row.setPadding(Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 13), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 13))
        row.addView(Ui.icon(this@SettingsActivity, iconRes, 20, p.primary))
        row.addView(Ui.hspacer(this, 14))
        val mid = Ui.vbox(this)
        mid.addView(Ui.tv(this@SettingsActivity, getString(titleRes), 15, p.textPrimary, Ui.Font.MEDIUM))
        if (descRes != 0) {
            mid.addView(Ui.tv(this@SettingsActivity, getString(descRes), 12, p.textTertiary, maxLines = 1))
        }
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.icon(this@SettingsActivity, R.drawable.ic_chevron, 16, p.textTertiary))
        row.setOnClickListener { fn() }
        return row
    }

    private fun sampleMenu() {
        val hasSample = Di.store.sampleData.hasSample()
        val box = Ui.vbox(this)
        box.addView(Ui.tv(this@SettingsActivity, getString(R.string.sample_load_msg), 14, p.textSecondary))
        Ui.sheet(this, getString(R.string.data_sample), box, listOf(
            getString(R.string.close) to Ui.Btn.SECONDARY,
            (if (hasSample) getString(R.string.sample_clear) else getString(R.string.add)) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx != 1) return@sheet
            Async.write({
                if (Di.store.sampleData.hasSample()) Di.store.sampleData.clear()
                else Di.store.sampleData.load()
            }) {
                snack(if (Di.store.sampleData.hasSample())
                    getString(R.string.sample_loaded) else getString(R.string.sample_cleared))
            }
        }.show()
    }

    private fun lpm(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@SettingsActivity, 12)
        }
}
