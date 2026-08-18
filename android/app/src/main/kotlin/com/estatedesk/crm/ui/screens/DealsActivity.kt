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
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.ui.widgets.BottomNav
import com.estatedesk.crm.ui.widgets.PagedList
import com.estatedesk.crm.ui.widgets.Rows

class DealsActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var q = ""
    private var stage = ""
    private var sort = "newest"
    private var queryId = 0
    private var emptyView: View? = null

    override fun build() {
        topBar(getString(R.string.deals_title), actions = listOf(
            R.drawable.ic_filter to { showFilters() },
            R.drawable.ic_sort to { showSort() }
        ))
        val search = Ui.input(this@DealsActivity, getString(R.string.search_hint), q)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString()?.trim() ?: ""
                refresh()
            }
        })
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@DealsActivity, 8)
        })
        add(Ui.chipRow(this, listOf(
            getString(R.string.all) to (stage.isBlank()),
            getString(R.string.stage_negotiation) to (stage == "Negotiation"),
            getString(R.string.stage_documentation) to (stage == "Documentation"),
            getString(R.string.stage_closed_won) to (stage == "Closed Won"),
            getString(R.string.stage_closed_lost) to (stage == "Closed Lost")
        )) { i ->
            stage = when (i) {
                1 -> "Negotiation"; 2 -> "Documentation"
                3 -> "Closed Won"; 4 -> "Closed Lost"; else -> ""
            }
            refresh()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // summary strip
        Async.db({
            val s = Di.store.stats
            Triple(s.activeDeals(), s.closedWon(), s.pendingCommission())
        }) { t ->
            if (t == null || isFinishing) return@db
            val (active, closed, pending) = t
            val strip = Ui.card(this, emptyList(), padding = 14)
            val row = Ui.hbox(this)
            row.addView(miniStat(getString(R.string.home_active_deals), active.toString(), p.textPrimary), Ui.weightLps(1f))
            row.addView(miniStat(getString(R.string.home_closed_deals), closed.toString(), p.success), Ui.weightLps(1f))
            row.addView(miniStat(getString(R.string.home_pending_commission),
                Fmt.moneyShort(pending, Di.store.currency()), if (pending > 0) p.warning else p.textPrimary), Ui.weightLps(1f))
            strip.addView(row)
            add(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@DealsActivity, 8)
            })
        }

        list = PagedList(this, 30)
        list.onLoadPage = { offset -> loadPage(offset) }
        add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addFab { startActivity(Intent(this, DealFormActivity::class.java)) }
        BottomNav.attach(this, root, 4)
        refresh()
    }

    private fun miniStat(label: String, value: String, color: Int): LinearLayout {
        val box = Ui.vbox(this)
        box.gravity = android.view.Gravity.CENTER
        val v = Ui.tv(this@DealsActivity, value, 17, color, Ui.Font.BOLD)
        v.gravity = android.view.Gravity.CENTER
        box.addView(v)
        val l = Ui.tv(this@DealsActivity, label, 11, p.textTertiary)
        l.gravity = android.view.Gravity.CENTER
        box.addView(l)
        return box
    }

    private fun refresh() {
        queryId++
        list.reset()
    }

    private fun loadPage(offset: Int) {
        val id = queryId
        Async.db({
            val deals = Di.store.deals.query(q, stage, sort, offset, 30)
            deals.map { d ->
                val buyer = Di.store.contacts.byId(d.buyerContactId)
                val prop = Di.store.properties.byId(d.propertyId)
                Rows.dealRow(this@DealsActivity, d, buyer, prop) {
                    startActivity(Intent(this@DealsActivity, DealDetailActivity::class.java)
                        .putExtra("id", d.id))
                }
            }
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true) && q.isBlank() && stage.isBlank()) {
                val empty = Ui.emptyState(this, R.drawable.ic_money, getString(R.string.deal_no_deals),
                    getString(R.string.deal_no_deals_hint), getString(R.string.qa_add_deal)) {
                    startActivity(Intent(this, DealFormActivity::class.java))
                }
                list.markExhausted()
                list.view.visibility = View.GONE
                emptyView = empty
                content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                return@db
            }
            emptyView?.let { content.removeView(it); emptyView = null }
            list.view.visibility = View.VISIBLE
            list.addPage(views ?: emptyList())
        }
    }

    private fun showFilters() {
        val box = Ui.vbox(this)
        val stageOptions = listOf(getString(R.string.flt_any)) + Di.store.dealStages().map { it.name }
        box.addView(Ui.pickField(this, getString(R.string.flt_stage),
            stage.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_stage), stageOptions) { i ->
                stage = if (i == 0) "" else stageOptions[i]
            }
        })
        Ui.sheet(this, getString(R.string.filter), box, listOf(
            getString(R.string.clear) to Ui.Btn.DANGER,
            getString(R.string.apply_hint) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx == 0) stage = ""
            refresh()
        }.show()
    }

    private fun showSort() {
        val options = listOf(getString(R.string.sort_newest), getString(R.string.sort_oldest), getString(R.string.sort_value))
        val keys = listOf("newest", "oldest", "value")
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
