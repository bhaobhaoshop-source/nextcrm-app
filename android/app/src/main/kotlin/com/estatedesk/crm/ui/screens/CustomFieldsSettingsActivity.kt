package com.estatedesk.crm.ui.screens

import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.CustomFieldDef

class CustomFieldsSettingsActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.settings_custom_fields))
        render()
    }

    private fun render() {
        content.removeAllViews()
        topBar(getString(R.string.settings_custom_fields))
        val defs = Di.store.custom.defs()
        if (defs.isEmpty()) {
            add(Ui.emptyState(this, R.drawable.ic_list, getString(R.string.custom_fields_empty),
                getString(R.string.custom_fields_empty_hint), getString(R.string.custom_fields_add)) { addField() })
        } else {
            defs.forEach { d ->
                val card = Ui.card(this, emptyList(), padding = 12)
                val row = Ui.hbox(this)
                row.addView(Ui.tv(this@CustomFieldsSettingsActivity, d.label, 15, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.badge(this@CustomFieldsSettingsActivity, d.type, p.textSecondary, p.chipBg))
                card.addView(row)
                card.addView(Ui.tv(this@CustomFieldsSettingsActivity, d.entities, 12, p.textTertiary, maxLines = 1))
                val del = Ui.btn(this@CustomFieldsSettingsActivity, getString(R.string.remove), Ui.Btn.TEXT).apply {
                    setTextColor(p.danger)
                    setOnClickListener {
                        Ui.alert(this@CustomFieldsSettingsActivity, getString(R.string.picklist_remove_title, d.label),
                            getString(R.string.confirm_delete_msg), getString(R.string.remove)) {
                            Async.write({ Di.store.custom.deleteDef(d.key) }) { render() }
                        }
                    }
                }
                card.addView(del)
                add(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@CustomFieldsSettingsActivity, 10)
                })
            }
        }
        add(Ui.btn(this@CustomFieldsSettingsActivity, "+ " + getString(R.string.custom_fields_add), Ui.Btn.SECONDARY) { addField() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addField() {
        var label = ""
        var type = "Text"
        var entities = mutableListOf("contact")
        var options = ""
        val box = Ui.vbox(this)
        val labelInput = Ui.input(this@CustomFieldsSettingsActivity, getString(R.string.custom_field_label))
        box.addView(Ui.field(this, getString(R.string.custom_field_label), labelInput))
        box.addView(Ui.pickField(this, getString(R.string.custom_field_type), type) {
            Ui.pick(this, getString(R.string.custom_field_type),
                listOf("Text", "Number", "Date", "Choice")) { i ->
                type = listOf("Text", "Number", "Date", "Choice")[i]
            }
        })
        // applies to
        val entityLabels = listOf("Contact", "Lead", "Property", "Deal")
        entityLabels.forEach { e ->
            val row = Ui.hbox(this)
            val cb = android.widget.CheckBox(this).apply {
                text = e
                setTextColor(p.textPrimary)
                buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
                isChecked = entities.contains(e.lowercase())
                setOnCheckedChangeListener { _, b ->
                    val key = e.lowercase()
                    if (b) entities.add(key) else entities.remove(key)
                }
            }
            row.addView(cb)
            box.addView(row)
        }
        val optionsInput = Ui.input(this@CustomFieldsSettingsActivity, getString(R.string.custom_field_options))
        box.addView(Ui.field(this, getString(R.string.custom_field_options), optionsInput))

        Ui.sheet(this, getString(R.string.custom_fields_add), box, listOf(
            getString(R.string.cancel) to Ui.Btn.SECONDARY,
            getString(R.string.save) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx != 1) return@sheet
            val l = labelInput.text.toString().trim()
            if (l.isBlank() || entities.isEmpty()) {
                snack(getString(R.string.error_required)); return@sheet
            }
            val key = "cf_" + System.currentTimeMillis()
            Async.write({
                Di.store.custom.saveDef(CustomFieldDef(
                    key = key, label = l, type = type,
                    options = optionsInput.text.toString().trim(),
                    entities = entities.joinToString(","), sort = 0
                ))
            }) { render() }
        }.show()
    }
}
