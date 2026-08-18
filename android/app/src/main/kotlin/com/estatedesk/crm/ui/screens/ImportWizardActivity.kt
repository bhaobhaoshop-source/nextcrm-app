package com.estatedesk.crm.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Csv
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui

class ImportWizardActivity : BaseActivity() {

    private var entity = "contacts"
    private var headers: List<String> = emptyList()
    private var rows: List<List<String>> = emptyList()
    private var mapping = mutableMapOf<String, Int>()

    override fun build() {
        topBar(getString(R.string.import_title))
        add(Ui.caption(this@ImportWizardActivity, "1 · 2 · 3 · 4"))
        add(Ui.spacer(this, 8))
        add(Ui.pickField(this, getString(R.string.import_entity), entityLabel()) {
            Ui.pick(this, getString(R.string.import_entity),
                listOf("Contacts", "Leads", "Properties")) { i ->
                entity = listOf("contacts", "leads", "properties")[i]
                mapping.clear()
                headers = emptyList()
                rows = emptyList()
                content.removeAllViews()
                topBar(getString(R.string.import_title))
                renderStep1()
            }
        })
        renderStep1()
    }

    private fun entityLabel(): String = when (entity) {
        "leads" -> "Leads"
        "properties" -> "Properties"
        else -> "Contacts"
    }

    private fun renderStep1() {
        add(Ui.btn(this@ImportWizardActivity, getString(R.string.import_pick_file), Ui.Btn.PRIMARY) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "text/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(i, 31)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(Ui.caption(this@ImportWizardActivity, getString(R.string.import_error_msg)))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 31 && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            Async.io({
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    text
                } catch (t: Throwable) {
                    null
                }
            }) { text ->
                if (text == null) {
                    Ui.alert(this, getString(R.string.import_error_title),
                        getString(R.string.import_error_msg), getString(R.string.close)) {}
                    return@io
                }
                val parsed = Csv.parse(text)
                if (parsed.isEmpty()) {
                    Ui.alert(this, getString(R.string.import_error_title),
                        getString(R.string.import_error_msg), getString(R.string.close)) {}
                    return@io
                }
                val first = parsed.first()
                val firstLooksHeader = first.any { f ->
                    f.lowercase().contains("name") || f.lowercase().contains("phone") ||
                            f.lowercase().contains("title") || f.lowercase().contains("price")
                }
                headers = if (firstLooksHeader) first else List(first.size) { "Column $it" }
                rows = if (firstLooksHeader) parsed.drop(1) else parsed
                content.removeAllViews()
                topBar(getString(R.string.import_title))
                renderStep2()
            }
        }
    }

    private fun fieldsForEntity(): List<Pair<String, String>> = when (entity) {
        "leads" -> listOf(
            "title" to "Title", "contact_phone" to "Contact phone", "source" to "Source",
            "requirement" to "Requirement", "budget_min" to "Budget min",
            "budget_max" to "Budget max", "location" to "Location",
            "property_type" to "Property type", "intent" to "Intent (Buy/Rent)",
            "stage" to "Stage", "priority" to "Priority", "next_action" to "Next action",
            "notes" to "Notes"
        )
        "properties" -> listOf(
            "title" to "Title", "type" to "Type", "sale_rent" to "Sale/Rent",
            "price" to "Price", "location" to "Location", "area_name" to "Area",
            "size_value" to "Size value", "size_unit" to "Size unit",
            "bedrooms" to "Bedrooms", "bathrooms" to "Bathrooms", "floors" to "Floors",
            "condition" to "Condition", "owner_name" to "Owner", "owner_phone" to "Owner phone",
            "status" to "Status", "description" to "Description", "tags" to "Tags"
        )
        else -> listOf(
            "first_name" to "First name", "last_name" to "Last name", "phone" to "Phone",
            "email" to "Email", "city" to "City", "area" to "Area",
            "classification" to "Classification", "lead_status" to "Status",
            "temperature" to "Temperature", "priority" to "Priority", "budget" to "Budget",
            "preferred_location" to "Preferred location", "preferred_type" to "Preferred type",
            "lead_source" to "Source", "notes" to "Notes"
        )
    }

    private fun renderStep2() {
        add(Ui.caption(this@ImportWizardActivity, getString(R.string.import_map_hint)))
        add(Ui.spacer(this, 8))
        val card = Ui.card(this, emptyList(), padding = 10)
        fieldsForEntity().forEach { (field, label) ->
            val row = Ui.hbox(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(Ui.tv(this@ImportWizardActivity, label, 14, p.textSecondary),
                LinearLayout.LayoutParams(Ui.dp(this@ImportWizardActivity, 120), ViewGroup.LayoutParams.WRAP_CONTENT))
            val current = mapping[field]?.let { headers.getOrNull(it) ?: "" } ?: ""
            val picker = Ui.chip(this@ImportWizardActivity, current.ifBlank { getString(R.string.import_skip) },
                current.isNotBlank()) {
                Ui.pick(this, label, listOf(getString(R.string.import_skip)) + headers) { i ->
                    if (i == 0) mapping.remove(field) else mapping[field] = i - 1
                    content.removeAllViews()
                    topBar(getString(R.string.import_title))
                    renderStep2()
                }
            }
            row.addView(picker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            Ui.margin(row, 0, 0, 0, 8)
            card.addView(row)
        }
        add(card)
        add(Ui.spacer(this, 10))
        add(Ui.caption(this@ImportWizardActivity, getString(R.string.import_rows, rows.size)))
        add(Ui.btn(this@ImportWizardActivity, getString(R.string.import_step_preview), Ui.Btn.PRIMARY) {
            content.removeAllViews()
            topBar(getString(R.string.import_title))
            renderStep3()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun renderStep3() {
        val issues = Di.store.importer.validationIssues(entity, mapping, rows)
        val dupes = Di.store.importer.countDuplicates(entity, mapping, rows)
        add(Ui.caption(this@ImportWizardActivity, getString(R.string.import_valid_rows,
            rows.size - issues.size, issues.size)))
        if (dupes > 0) {
            add(Ui.caption(this@ImportWizardActivity, getString(R.string.import_duplicates, dupes), p.warning))
        }
        add(Ui.spacer(this, 8))
        // preview
        val card = Ui.card(this, emptyList(), padding = 10)
        rows.take(4).forEach { r ->
            val line = headers.zip(r).take(4).joinToString(" · ") { (h, v) -> "$h: ${v.take(16)}" }
            card.addView(Ui.tv(this@ImportWizardActivity, line, 12, p.textSecondary, maxLines = 2))
            card.addView(Ui.spacer(this, 4))
        }
        add(card)
        add(Ui.spacer(this, 10))
        issues.take(5).forEach { add(Ui.caption(this@ImportWizardActivity, it, p.danger)) }
        add(Ui.btn(this@ImportWizardActivity, getString(R.string.import_run, rows.size), Ui.Btn.PRIMARY) {
            Async.db({
                Di.store.importer.importRows(entity, mapping, rows, skipDuplicates = true)
            }) { result ->
                if (result == null || isFinishing) return@db
                content.removeAllViews()
                topBar(getString(R.string.import_title))
                renderStep4(result)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun renderStep4(result: com.estatedesk.crm.domain.ImportExport.ImportResult) {
        val box = Ui.vbox(this)
        box.gravity = Gravity.CENTER
        val ok = Ui.icon(this@ImportWizardActivity, R.drawable.ic_check, 44, p.success)
        ok.layoutParams = LinearLayout.LayoutParams(Ui.dp(this@ImportWizardActivity, 44), Ui.dp(this@ImportWizardActivity, 44))
        box.addView(ok)
        add(box)
        add(Ui.spacer(this, 10))
        add(Ui.tv(this@ImportWizardActivity, getString(R.string.import_done), 18, p.textPrimary, Ui.Font.BOLD))
        add(Ui.tv(this@ImportWizardActivity, getString(R.string.import_result, result.imported, result.skipped), 14, p.textSecondary))
        if (result.duplicates > 0) {
            add(Ui.tv(this@ImportWizardActivity, getString(R.string.import_duplicates, result.duplicates), 13, p.warning))
        }
        result.errors.take(5).forEach { add(Ui.caption(this@ImportWizardActivity, it, p.danger)) }
        add(Ui.btn(this@ImportWizardActivity, getString(R.string.done), Ui.Btn.PRIMARY) { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
