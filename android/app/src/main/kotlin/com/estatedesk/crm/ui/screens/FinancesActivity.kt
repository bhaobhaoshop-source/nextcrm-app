package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Finance
import com.estatedesk.crm.ui.widgets.Dialogs
import com.estatedesk.crm.ui.widgets.PagedList

class FinancesActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var direction = ""
    private var category = ""
    private var queryId = 0
    private var emptyView: View? = null

    override fun build() {
        topBar(getString(R.string.finances_title))
        add(Ui.chipRow(this, listOf(
            getString(R.string.finance_all) to (direction.isBlank()),
            getString(R.string.finance_income) to (direction == Finance.INCOME),
            getString(R.string.finance_expense) to (direction == Finance.EXPENSE)
        )) { i ->
            direction = when (i) { 1 -> Finance.INCOME; 2 -> Finance.EXPENSE; else -> "" }
            refresh()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // totals strip
        Async.db({
            val s = Di.store.stats
            val (dayStart, _) = s.dayRange(0)
            val monthCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val (inc, exp, net) = Di.store.finances.totals(monthCal.timeInMillis)
            Triple(inc, exp, net)
        }) { t ->
            if (t == null || isFinishing) return@db
            val (inc, exp, net) = t
            val cur = Di.store.currency()
            val strip = Ui.card(this, emptyList(), padding = 14)
            val row = Ui.hbox(this)
            row.addView(stat(getString(R.string.finance_income), "+" + Fmt.moneyShort(inc, cur), p.success), Ui.weightLps(1f))
            row.addView(stat(getString(R.string.finance_expense), "-" + Fmt.moneyShort(exp, cur), p.danger), Ui.weightLps(1f))
            row.addView(stat(getString(R.string.finance_balance), Fmt.moneyShort(net, cur),
                if (net >= 0) p.primary else p.danger), Ui.weightLps(1f))
            strip.addView(row)
            add(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@FinancesActivity, 8)
            })
        }

        list = PagedList(this, 40)
        list.onLoadPage = { offset -> loadPage(offset) }
        add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addFab { showAdd() }

        // "add" intent (from Home quick add)
        if (intent.getStringExtra("add") != null) {
            Async.ui(300) { openForm(intent.getStringExtra("add") ?: "Expense") }
        }
        refresh()
    }

    private fun stat(label: String, value: String, color: Int): LinearLayout {
        val box = Ui.vbox(this)
        box.gravity = android.view.Gravity.CENTER
        val v = Ui.tv(this@FinancesActivity, value, 16, color, Ui.Font.BOLD)
        v.gravity = android.view.Gravity.CENTER
        box.addView(v)
        val l = Ui.tv(this@FinancesActivity, label, 11, p.textTertiary)
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
            val entries = Di.store.finances.query(direction, category, 0, offset, 40)
            entries.map { f ->
                val row = Ui.hbox(this@FinancesActivity)
                row.setPadding(Ui.dp(this@FinancesActivity, 4), Ui.dp(this@FinancesActivity, 11),
                    Ui.dp(this@FinancesActivity, 4), Ui.dp(this@FinancesActivity, 11))
                val iconRes = if (f.direction == Finance.INCOME) R.drawable.ic_upload else R.drawable.ic_download
                val tint = if (f.direction == Finance.INCOME) p.success else p.danger
                row.addView(Ui.icon(this@FinancesActivity, iconRes, 20, tint))
                row.addView(Ui.hspacer(this@FinancesActivity, 12))
                val mid = Ui.vbox(this@FinancesActivity)
                mid.addView(Ui.tv(this@FinancesActivity, f.category.ifBlank { f.direction }, 14, p.textPrimary, Ui.Font.MEDIUM, 1))
                mid.addView(Ui.tv(this@FinancesActivity,
                    listOf(Fmt.date(f.date), f.notes).filter { it.isNotBlank() }.joinToString(" · "),
                    12, p.textTertiary, maxLines = 1))
                row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val amount = (if (f.direction == Finance.INCOME) "+" else "-") +
                        Fmt.money(f.amount, Di.store.currency())
                row.addView(Ui.tv(this@FinancesActivity, amount, 14, tint, Ui.Font.BOLD))
                row.setOnClickListener { editEntry(f) }
                row
            }
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true)) {
                val empty = Ui.emptyState(this, R.drawable.ic_money, getString(R.string.finance_no_entries),
                    getString(R.string.finance_no_entries_hint), getString(R.string.qa_add_expense)) { showAdd() }
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

    private fun showAdd() {
        Ui.pick(this, getString(R.string.finance_direction),
            listOf(getString(R.string.finance_income), getString(R.string.finance_expense))) { i ->
            openForm(if (i == 0) Finance.INCOME else Finance.EXPENSE)
        }
    }

    private fun openForm(initialDirection: String) {
        val box = Ui.vbox(this)
        var dir = initialDirection
        var cat = ""
        var date = Fmt.startOfDay(System.currentTimeMillis())
        var dealId = 0L
        var dealName = ""

        val dirField = Ui.pickField(this, getString(R.string.finance_direction), dir) {
            Ui.pick(this, getString(R.string.finance_direction),
                listOf(getString(R.string.finance_income), getString(R.string.finance_expense))) { i ->
                dir = if (i == 0) Finance.INCOME else Finance.EXPENSE
            }
        }
        box.addView(dirField)
        val cats = if (dir == Finance.INCOME) Di.store.incomeCategories() else Di.store.expenseCategories()
        val catField = Ui.pickField(this, getString(R.string.finance_category),
            cat.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.finance_category), listOf(getString(R.string.flt_any)) + cats) { i ->
                cat = if (i == 0) "" else cats[i - 1]
            }
        }
        box.addView(catField)
        val amount = Ui.input(this@FinancesActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        box.addView(Ui.field(this, getString(R.string.finance_amount), amount))
        val dateField = Ui.pickField(this, getString(R.string.finance_date), Fmt.friendlyDate(date)) {
            Ui.datePick(this, date) { d -> date = d }
        }
        box.addView(dateField)
        val dealField = Ui.pickField(this, getString(R.string.finance_related_deal),
            dealName.ifBlank { getString(R.string.flt_any) }) {
            Dialogs.pickDeal(this, getString(R.string.finance_related_deal)) { d ->
                dealId = d.id
                dealName = d.displayTitle()
            }
        }
        box.addView(dealField)
        val notes = Ui.input(this@FinancesActivity, getString(R.string.finance_notes))
        box.addView(Ui.field(this, getString(R.string.finance_notes), notes))

        Ui.sheet(this, if (dir == Finance.INCOME) getString(R.string.finance_new_income)
        else getString(R.string.finance_new_expense), box, listOf(
            getString(R.string.cancel) to Ui.Btn.SECONDARY,
            getString(R.string.save) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx != 1) return@sheet
            val amt = amount.text.toString().toLongOrNull() ?: 0
            if (amt <= 0) {
                snack(getString(R.string.error_required)); return@sheet
            }
            Async.write({
                Di.store.finances.save(Finance(
                    direction = dir, category = cat, amount = amt,
                    currency = Di.store.currency().code, date = date, dealId = dealId,
                    notes = notes.text.toString().trim()
                ))
            }) {
                snack(getString(R.string.saved))
                refresh()
            }
        }.show()
    }

    private fun editEntry(f: Finance) {
        Ui.alert(this, f.category.ifBlank { f.direction },
            Fmt.money(f.amount, Di.store.currency()) + "\n" + Fmt.date(f.date),
            getString(R.string.delete), getString(R.string.close),
            onPositive = {
                Async.write({ Di.store.markDeleted("finances", f.id) }) {
                    snack(getString(R.string.moved_to_bin))
                    refresh()
                }
            })
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }
}
