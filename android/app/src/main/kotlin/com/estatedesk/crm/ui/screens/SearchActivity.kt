package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.SearchHit
import java.util.concurrent.atomic.AtomicInteger

class SearchActivity : BaseActivity() {

    private val seq = AtomicInteger(0)
    private var typeFilter = ""

    override fun build() {
        topBar(getString(R.string.search_title))
        val search = Ui.input(this@SearchActivity, getString(R.string.search_hint))
        search.requestFocus()
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(Ui.chipRow(this, listOf(
            getString(R.string.search_all_types) to (typeFilter.isBlank()),
            getString(R.string.search_type_contact) to (typeFilter == "contact"),
            getString(R.string.search_type_lead) to (typeFilter == "lead"),
            getString(R.string.search_type_property) to (typeFilter == "property"),
            getString(R.string.search_type_deal) to (typeFilter == "deal"),
            getString(R.string.search_type_task) to (typeFilter == "task")
        )) { i ->
            typeFilter = when (i) {
                1 -> "contact"; 2 -> "lead"; 3 -> "property"
                4 -> "deal"; 5 -> "task"; else -> ""
            }
            doSearch(search.text.toString())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                doSearch(s?.toString() ?: "")
            }
        })
        add(Ui.emptyState(this, R.drawable.ic_search, getString(R.string.search_title),
            getString(R.string.search_hint)))
        Async.ui(200) { search.requestFocus() }
    }

    private fun doSearch(q: String) {
        val id = seq.incrementAndGet()
        val query = q.trim()
        if (query.isBlank()) {
            content.removeAllViews()
            topBar(getString(R.string.search_title))
            add(Ui.emptyState(this, R.drawable.ic_search, getString(R.string.search_title),
                getString(R.string.search_hint)))
            return
        }
        Async.db({
            val types = if (typeFilter.isBlank())
                setOf("contact", "lead", "property", "deal", "task")
            else setOf(typeFilter)
            Di.store.search.search(query, types, 40)
        }) { hits ->
            if (id != seq.get() || isFinishing) return@db
            content.removeAllViews()
            topBar(getString(R.string.search_title))
            val h = hits ?: emptyList()
            add(Ui.caption(this@SearchActivity, getString(R.string.search_results, h.size)))
            add(Ui.spacer(this, 8))
            if (h.isEmpty()) {
                add(Ui.emptyState(this, R.drawable.ic_search,
                    getString(R.string.search_no_results, query),
                    getString(R.string.search_no_results_hint)))
                return@db
            }
            val card = Ui.card(this, emptyList(), padding = 4)
            h.forEach { hit ->
                card.addView(hitRow(hit))
                if (hit != h.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
    }

    private fun hitRow(hit: SearchHit): LinearLayout {
        val typeLabel = when (hit.type) {
            "contact" -> getString(R.string.search_type_contact)
            "lead" -> getString(R.string.search_type_lead)
            "property" -> getString(R.string.search_type_property)
            "deal" -> getString(R.string.search_type_deal)
            "task" -> getString(R.string.search_type_task)
            else -> hit.type
        }
        val iconRes = when (hit.type) {
            "contact" -> R.drawable.ic_people
            "lead" -> R.drawable.ic_flag
            "property" -> R.drawable.ic_building
            "deal" -> R.drawable.ic_money
            "task" -> R.drawable.ic_check
            else -> R.drawable.ic_doc
        }
        val row = Ui.hbox(this)
        row.setPadding(Ui.dp(this@SearchActivity, 10), Ui.dp(this@SearchActivity, 12), Ui.dp(this@SearchActivity, 10), Ui.dp(this@SearchActivity, 12))
        row.addView(Ui.icon(this@SearchActivity, iconRes, 20, p.primary))
        row.addView(Ui.hspacer(this, 12))
        val mid = Ui.vbox(this)
        mid.addView(Ui.tv(this@SearchActivity, hit.name.ifBlank { typeLabel }, 14, p.textPrimary, Ui.Font.MEDIUM, 1))
        if (hit.detail.isNotBlank()) {
            mid.addView(Ui.tv(this@SearchActivity, hit.detail, 12, p.textTertiary, maxLines = 1))
        }
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.badge(this@SearchActivity, typeLabel, p.textSecondary, p.chipBg))
        row.setOnClickListener { open(hit) }
        return row
    }

    private fun open(hit: SearchHit) {
        val intent = when (hit.type) {
            "contact" -> Intent(this, ContactDetailActivity::class.java).putExtra("id", hit.entityId)
            "lead" -> Intent(this, LeadDetailActivity::class.java).putExtra("id", hit.entityId)
            "property" -> Intent(this, PropertyDetailActivity::class.java).putExtra("id", hit.entityId)
            "deal" -> Intent(this, DealDetailActivity::class.java).putExtra("id", hit.entityId)
            "task" -> Intent(this, TaskFormActivity::class.java).putExtra("id", hit.entityId)
            else -> return
        }
        startActivity(intent)
    }
}
