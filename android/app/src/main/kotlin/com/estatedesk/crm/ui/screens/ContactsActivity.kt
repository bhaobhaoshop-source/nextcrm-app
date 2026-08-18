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
import com.estatedesk.crm.ui.widgets.BottomNav
import com.estatedesk.crm.ui.widgets.PagedList
import com.estatedesk.crm.ui.widgets.Rows

class ContactsActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var q = ""
    private var classification = ""
    private var status = ""
    private var temperature = ""
    private var source = ""
    private var priority = 0
    private var city = ""
    private var tag = ""
    private var sort = "newest"
    private var queryId = 0
    private var lastTotal = 0

    override fun build() {
        topBar(getString(R.string.contacts_title), actions = listOf(
            R.drawable.ic_filter to { showFilters() },
            R.drawable.ic_sort to { showSort() }
        ))
        val search = Ui.input(this@ContactsActivity, getString(R.string.search_hint), q)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString()?.trim() ?: ""
                refresh()
            }
        })
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@ContactsActivity, 8)
        })
        addFilterChips()
        list = PagedList(this, 30)
        list.onLoadPage = { offset -> loadPage(offset) }
        add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addFab { startActivity(Intent(this, ContactFormActivity::class.java)) }
        BottomNav.attach(this, root, 3)
        refresh()
    }

    private fun activeChipRow(): List<Pair<String, Boolean>> {
        val tabs = listOf(getString(R.string.all)) + listOf(
            "Buyer", "Seller", "Investor", "Renter", "Landlord"
        )
        return tabs.map { it to (it == (classification.ifBlank { getString(R.string.all) })) }
    }

    private fun addFilterChips() {
        chipsView = Ui.chipRow(this, activeChipRow()) { i ->
            classification = if (i == 0) "" else listOf("Buyer", "Seller", "Investor", "Renter", "Landlord")[i - 1]
            refreshChips()
            refresh()
        }
        add(chipsView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun refreshChips() {
        val idx = content.indexOfChild(chipsView)
        if (idx >= 0) content.removeViewAt(idx)
        addFilterChips()
        if (idx >= 0) {
            val v = content.getChildAt(content.childCount - 1)
            content.removeViewAt(content.childCount - 1)
            content.addView(v, idx)
        }
    }

    private lateinit var chipsView: View

    private fun refresh() {
        queryId++
        lastTotal = 0
        Async.db({ Di.store.contacts.count(q, classification, status, temperature, source, priority, city, tag) }) { n ->
            lastTotal = n ?: 0
            list.reset()
        }
    }

    private fun loadPage(offset: Int) {
        val id = queryId
        Async.db({
            val rows = Di.store.contacts.query(
                q, classification, status, temperature, source, priority, city, tag, sort, offset, 30
            )
            rows.map { c ->
                val phones = Di.store.contacts.phones(c.id)
                Rows.contactRow(this@ContactsActivity, c, phones) {
                    startActivity(Intent(this@ContactsActivity, ContactDetailActivity::class.java)
                        .putExtra("id", c.id))
                }
            }
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true) && q.isBlank() && classification.isBlank()
                && status.isBlank() && temperature.isBlank() && source.isBlank()
                && priority == 0 && city.isBlank() && tag.isBlank()
            ) {
                showEmptyState()
                return@db
            }
            hideEmptyState()
            list.addPage(views ?: emptyList())
        }
    }

    private var emptyView: View? = null

    private fun showEmptyState() {
        list.markExhausted()
        list.view.visibility = View.GONE
        if (emptyView == null) {
            emptyView = Ui.emptyState(this, R.drawable.ic_people, getString(R.string.contacts_empty),
                getString(R.string.lead_no_leads_hint), getString(R.string.qa_add_contact)) {
                startActivity(Intent(this, ContactFormActivity::class.java))
            }
        }
        if (emptyView?.parent == null) {
            content.addView(emptyView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun hideEmptyState() {
        emptyView?.let { content.removeView(it) }
        list.view.visibility = View.VISIBLE
    }

    private fun showFilters() {
        val box = Ui.vbox(this)
        val statusOptions = listOf(getString(R.string.flt_any)) + listOf("New", "Contacted", "Qualified", "Unqualified", "Customer")
        val tempOptions = listOf(getString(R.string.flt_any)) + listOf("Hot", "Warm", "Cold")
        val srcOptions = listOf(getString(R.string.flt_any)) + Di.store.leadSources()
        val priOptions = listOf(getString(R.string.flt_any), getString(R.string.pr_high), getString(R.string.pr_medium), getString(R.string.pr_low))

        box.addView(Ui.pickField(this, getString(R.string.flt_status),
            status.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_status), statusOptions) { i ->
                status = if (i == 0) "" else statusOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_temperature),
            temperature.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_temperature), tempOptions) { i ->
                temperature = if (i == 0) "" else tempOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_source),
            source.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_source), srcOptions) { i ->
                source = if (i == 0) "" else srcOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_priority),
            priorityLabel()) {
            Ui.pick(this, getString(R.string.flt_priority), priOptions) { i ->
                priority = when (i) {
                    1 -> 3; 2 -> 2; 3 -> 1; else -> 0
                }
            }
        })
        Ui.sheet(this, getString(R.string.filter), box, listOf(
            getString(R.string.clear) to Ui.Btn.DANGER,
            getString(R.string.apply_hint) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx == 0) {
                status = ""; temperature = ""; source = ""; priority = 0; city = ""; tag = ""
            }
            refresh()
        }.show()
    }

    private fun priorityLabel(): String = when (priority) {
        3 -> getString(R.string.pr_high)
        2 -> getString(R.string.pr_medium)
        1 -> getString(R.string.pr_low)
        else -> getString(R.string.flt_any)
    }

    private fun showSort() {
        val options = listOf(
            getString(R.string.sort_newest), getString(R.string.sort_oldest),
            getString(R.string.sort_name), getString(R.string.sort_priority),
            getString(R.string.sort_followup), getString(R.string.sort_last_contacted)
        )
        val keys = listOf("newest", "oldest", "name", "priority", "followup", "last_contacted")
        Ui.pick(this, getString(R.string.sort), options, keys.indexOf(sort)) { i ->
            sort = keys[i]
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }
}
