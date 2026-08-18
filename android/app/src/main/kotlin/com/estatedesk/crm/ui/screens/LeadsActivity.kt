package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.ui.widgets.BottomNav
import com.estatedesk.crm.ui.widgets.PagedList
import com.estatedesk.crm.ui.widgets.Rows

class LeadsActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var q = ""
    private var stage = ""
    private var status = ""
    private var temperature = ""
    private var source = ""
    private var priority = 0
    private var location = ""
    private var intentFilter = ""
    private var sort = "newest"
    private var queryId = 0
    private var pipelineMode = false
    private var pipelineHost: LinearLayout? = null

    override fun build() {
        pipelineMode = intent.getStringExtra("view") == "pipeline"
        topBar(getString(R.string.leads_title), actions = listOf(
            R.drawable.ic_filter to { showFilters() },
            R.drawable.ic_sort to { showSort() },
            R.drawable.ic_swap to { togglePipeline() }
        ))
        val search = Ui.input(this@LeadsActivity, getString(R.string.search_hint), q)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString()?.trim() ?: ""
                if (!pipelineMode) refresh()
            }
        })
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@LeadsActivity, 8)
        })

        if (pipelineMode) {
            renderPipeline()
        } else {
            list = PagedList(this, 30)
            list.onLoadPage = { offset -> loadPage(offset) }
            add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            refresh()
        }
        addFab { startActivity(Intent(this, LeadFormActivity::class.java)) }
        BottomNav.attach(this, root, 1)
    }

    private fun togglePipeline() {
        pipelineMode = !pipelineMode
        content.removeAllViews()
        topBar(getString(R.string.leads_title), actions = listOf(
            R.drawable.ic_filter to { showFilters() },
            R.drawable.ic_sort to { showSort() },
            R.drawable.ic_swap to { togglePipeline() }
        ))
        val search = Ui.input(this@LeadsActivity, getString(R.string.search_hint), q)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString()?.trim() ?: ""
                if (!pipelineMode) refresh()
            }
        })
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@LeadsActivity, 8)
        })
        if (pipelineMode) renderPipeline() else {
            list = PagedList(this, 30)
            list.onLoadPage = { offset -> loadPage(offset) }
            add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            refresh()
        }
    }

    private fun renderPipeline() {
        pipelineHost = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(pipelineHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        add(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        Async.db({
            val st = Di.store
            val stages = st.leadStages()
            val counts = st.leads.stageCounts().toMap()
            stages.map { s ->
                Triple(s.name, counts[s.name] ?: 0, st.leads.byStage(s.name).take(6))
            }
        }) { t ->
            if (t == null || isFinishing) return@db
            t.forEach { (name, count, leads) ->
                pipelineHost?.addView(column(name, count, leads), LinearLayout.LayoutParams(
                    Ui.dp(this@LeadsActivity, 190), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    rightMargin = Ui.dp(this@LeadsActivity, 10)
                })
            }
        }
    }

    private fun column(stageName: String, count: Int, leads: List<Lead>): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.surface2)
            setPadding(Ui.dp(this@LeadsActivity, 10), Ui.dp(this@LeadsActivity, 10),
                Ui.dp(this@LeadsActivity, 10), Ui.dp(this@LeadsActivity, 10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(this@LeadsActivity, 14).toFloat()
                setColor(p.surface2)
            }
        }
        val head = Ui.hbox(this)
        head.addView(Ui.tv(this@LeadsActivity, stageName, 13, p.textPrimary, Ui.Font.BOLD, 2),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.badge(this@LeadsActivity, count.toString(), p.textSecondary, p.surface))
        col.addView(head)
        col.addView(Ui.spacer(this, 8))
        if (leads.isEmpty()) {
            val e = Ui.tv(this@LeadsActivity, getString(R.string.lead_no_leads), 12, p.textTertiary)
            e.setPadding(0, Ui.dp(this@LeadsActivity, 12), 0, 0)
            col.addView(e)
        } else {
            leads.forEach { l ->
                val card = Ui.card(this, emptyList(), padding = 10) {
                    startActivity(Intent(this, LeadDetailActivity::class.java).putExtra("id", l.id))
                }
                card.addView(Ui.tv(this@LeadsActivity, l.displayTitle(), 13, p.textPrimary, Ui.Font.MEDIUM, 2))
                val c = Di.store.contacts.byId(l.contactId)
                if (c != null) card.addView(Ui.tv(this@LeadsActivity, c.displayName(), 11, p.textTertiary, maxLines = 1))
                if (l.budgetMax > 0) card.addView(Ui.tv(this@LeadsActivity, Fmt.moneyShort(l.budgetMax, Di.store.currency()),
                    12, p.primary, Ui.Font.BOLD, 1))
                if (l.nextFollowUp > 0) {
                    val overdue = l.nextFollowUp < System.currentTimeMillis()
                    card.addView(Ui.tv(this@LeadsActivity, Fmt.friendlyDate(l.nextFollowUp), 11,
                        if (overdue) p.danger else p.textTertiary))
                }
                col.addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@LeadsActivity, 8)
                })
            }
            if (count > leads.size) {
                val more = Ui.tv(this@LeadsActivity, "+${count - leads.size} more", 12, p.primary, Ui.Font.MEDIUM)
                more.setPadding(0, Ui.dp(this@LeadsActivity, 4), 0, 0)
                more.setOnClickListener {
                    stage = stageName
                    pipelineMode = false
                    content.removeAllViews()
                    topBar(getString(R.string.leads_title), actions = listOf(
                        R.drawable.ic_filter to { showFilters() },
                        R.drawable.ic_sort to { showSort() },
                        R.drawable.ic_swap to { togglePipeline() }
                    ))
                    list = PagedList(this, 30)
                    list.onLoadPage = { offset -> loadPage(offset) }
                    add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    refresh()
                }
                col.addView(more)
            }
        }
        return col
    }

    private fun refresh() {
        queryId++
        list.reset()
    }

    private fun loadPage(offset: Int) {
        val id = queryId
        Async.db({
            val leads = Di.store.leads.query(
                q, stage, status, temperature, source, priority, location, intentFilter, sort, offset, 30
            )
            leads.map { l ->
                val c = Di.store.contacts.byId(l.contactId)
                Rows.leadRow(this@LeadsActivity, l, c) {
                    startActivity(Intent(this@LeadsActivity, LeadDetailActivity::class.java).putExtra("id", l.id))
                }
            }
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true) && q.isBlank() && stage.isBlank()) {
                val empty = Ui.emptyState(this, R.drawable.ic_flag, getString(R.string.lead_no_leads),
                    getString(R.string.lead_no_leads_hint), getString(R.string.qa_add_lead)) {
                    startActivity(Intent(this, LeadFormActivity::class.java))
                }
                list.markExhausted()
                list.view.visibility = View.GONE
                if (emptyView == null) {
                    emptyView = empty
                    content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                }
                return@db
            }
            emptyView?.let { content.removeView(it); emptyView = null }
            list.view.visibility = View.VISIBLE
            list.addPage(views ?: emptyList())
        }
    }

    private var emptyView: View? = null

    private fun showFilters() {
        val box = Ui.vbox(this)
        val stageOptions = listOf(getString(R.string.flt_any)) + Di.store.leadStages().map { it.name }
        val srcOptions = listOf(getString(R.string.flt_any)) + Di.store.leadSources()
        val locOptions = listOf(getString(R.string.flt_any)) + Di.store.distinctValues("location", "leads").take(30)
        box.addView(Ui.pickField(this, getString(R.string.flt_stage),
            stage.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_stage), stageOptions) { i ->
                stage = if (i == 0) "" else stageOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_source),
            source.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_source), srcOptions) { i ->
                source = if (i == 0) "" else srcOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_location),
            location.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_location), locOptions) { i ->
                location = if (i == 0) "" else locOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.lead_intent),
            intentFilter.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.lead_intent),
                listOf(getString(R.string.flt_any), "Buy", "Rent")) { i ->
                intentFilter = if (i == 0) "" else listOf("Buy", "Rent")[i - 1]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_priority), priorityLabel()) {
            Ui.pick(this, getString(R.string.flt_priority),
                listOf(getString(R.string.flt_any), getString(R.string.pr_high),
                    getString(R.string.pr_medium), getString(R.string.pr_low))) { i ->
                priority = when (i) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 }
            }
        })
        Ui.sheet(this, getString(R.string.filter), box, listOf(
            getString(R.string.clear) to Ui.Btn.DANGER,
            getString(R.string.apply_hint) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx == 0) {
                stage = ""; source = ""; location = ""; intentFilter = ""; priority = 0
            }
            if (pipelineMode) renderPipeline() else refresh()
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
            getString(R.string.sort_priority), getString(R.string.sort_value),
            getString(R.string.sort_followup)
        )
        val keys = listOf("newest", "oldest", "priority", "value", "followup")
        Ui.pick(this, getString(R.string.sort), options, keys.indexOf(sort)) { i ->
            sort = keys[i]
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized && !pipelineMode) refresh()
    }
}
