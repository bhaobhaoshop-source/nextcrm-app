package com.estatedesk.crm.ui.screens

import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui

class PicklistSettingsActivity : BaseActivity() {

    private var kind = "sources"
    private var values = mutableListOf<String>()

    override fun build() {
        kind = intent.getStringExtra("kind") ?: "sources"
        topBar(when (kind) {
            "types" -> getString(R.string.settings_property_types)
            "tags" -> getString(R.string.settings_tags)
            else -> getString(R.string.settings_sources)
        })
        values = current().toMutableList()
        render()
    }

    private fun current(): List<String> = when (kind) {
        "types" -> Di.store.propertyTypes()
        "tags" -> Di.store.tags()
        else -> Di.store.leadSources()
    }

    private fun render() {
        content.removeAllViews()
        topBar(when (kind) {
            "types" -> getString(R.string.settings_property_types)
            "tags" -> getString(R.string.settings_tags)
            else -> getString(R.string.settings_sources)
        })
        if (values.isEmpty()) {
            add(Ui.emptyState(this, R.drawable.ic_list, getString(R.string.settings_sources), ""))
        } else {
            val card = Ui.card(this, emptyList(), padding = 4)
            values.forEach { v ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@PicklistSettingsActivity, 12), Ui.dp(this@PicklistSettingsActivity, 13), Ui.dp(this@PicklistSettingsActivity, 12), Ui.dp(this@PicklistSettingsActivity, 13))
                row.addView(Ui.tv(this@PicklistSettingsActivity, v, 15, p.textPrimary, Ui.Font.MEDIUM, 2),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val del = Ui.icon(this@PicklistSettingsActivity, R.drawable.ic_close, 16, p.textTertiary)
                Ui.pad(del, 8, 8, 0, 8)
                del.setOnClickListener {
                    Ui.alert(this, getString(R.string.picklist_remove_title, v),
                        getString(R.string.picklist_remove_msg), getString(R.string.remove)) {
                        Async.write({
                            values = Di.store.removePicklistValue(key(), v).toMutableList()
                        }) { render() }
                    }
                }
                row.addView(del)
                card.addView(row)
                if (v != values.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
        add(Ui.spacer(this, 10))
        add(Ui.btn(this@PicklistSettingsActivity, "+ " + getString(R.string.picklist_add), Ui.Btn.SECONDARY) {
            Ui.prompt(this, getString(R.string.picklist_add), getString(R.string.picklist_hint)) { v ->
                Async.write({
                    values = Di.store.addPicklistValue(key(), v).toMutableList()
                }) { render() }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@PicklistSettingsActivity, 24)
        })
    }

    private fun key(): String = when (kind) {
        "types" -> "pick_types"
        "tags" -> "pick_tags"
        else -> "pick_sources"
    }
}
