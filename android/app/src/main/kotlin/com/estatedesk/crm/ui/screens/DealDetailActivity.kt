package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Deal
import com.estatedesk.crm.ui.widgets.Dialogs
import com.estatedesk.crm.ui.widgets.Rows

class DealDetailActivity : BaseActivity() {

    private var dealId = 0L
    private var tab = 0
    private lateinit var deal: Deal

    override fun build() {
        dealId = intent.getLongExtra("id", 0)
        if (dealId == 0L) {
            finish(); return
        }
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val d = st.deals.byId(dealId) ?: return@db null
            data class D(
                val d: Deal, val buyer: String, val seller: String, val prop: String,
                val fin: List<com.estatedesk.crm.data.Finance>, val tasks: List<com.estatedesk.crm.data.Task>,
                val docs: List<com.estatedesk.crm.data.Document>
            )
            D(
                d,
                st.contacts.byId(d.buyerContactId)?.displayName() ?: "",
                st.contacts.byId(d.sellerContactId)?.displayName() ?: "",
                st.properties.byId(d.propertyId)?.displayTitle() ?: "",
                st.finances.byDeal(dealId),
                st.tasks.byDeal(dealId),
                st.docs.forEntity("deal", dealId)
            )
        }) { t ->
            if (t == null || isFinishing) return@db
            deal = t.d
            content.removeAllViews()
            renderHeader(t.buyer, t.seller, t.prop)
            renderTabs()
            when (tab) {
                0 -> renderOverview(t.buyer, t.seller, t.prop, t.fin, t.tasks, t.docs)
                1 -> renderTimeline()
            }
        }
    }

    private fun renderHeader(buyer: String, seller: String, prop: String) {
        val head = Ui.hbox(this)
        val mid = Ui.vbox(this)
        mid.addView(Ui.tv(this@DealDetailActivity, deal.displayTitle(), 18, p.textPrimary, Ui.Font.BOLD, 2))
        val parties = listOf(buyer, seller, prop).filter { it.isNotBlank() }.joinToString(" · ")
        if (parties.isNotBlank()) mid.addView(Ui.tv(this@DealDetailActivity, parties, 13, p.textSecondary, maxLines = 2))
        val badges = Ui.hbox(this)
        badges.addView(Ui.statusBadge(this, deal.stage))
        val lpb = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpb.topMargin = Ui.dp(this@DealDetailActivity, 4)
        mid.addView(badges, lpb)
        head.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.tv(this@DealDetailActivity, Fmt.money(deal.value, Di.store.currency()), 20, p.primary, Ui.Font.BOLD, 1))
        add(head)
        add(Ui.spacer(this, 10))

        // commission summary card
        val comm = Ui.card(this, emptyList(), padding = 14)
        val total = Fmt.money(deal.commissionAmount, Di.store.currency())
        val recv = Fmt.money(deal.commissionReceived, Di.store.currency())
        val pend = Fmt.money(deal.pendingCommission(), Di.store.currency())
        val row = Ui.hbox(this)
        row.addView(miniStat(getString(R.string.deal_commission_amount), total, p.textPrimary), Ui.weightLps(1f))
        row.addView(miniStat(getString(R.string.deal_commission_received), recv, p.success), Ui.weightLps(1f))
        row.addView(miniStat(getString(R.string.deal_commission_pending), pend,
            if (deal.pendingCommission() > 0) p.warning else p.success), Ui.weightLps(1f))
        comm.addView(row)
        add(comm)
        add(Ui.spacer(this, 8))

        // actions
        val actions = Ui.card(this, emptyList(), padding = 10)
        val actionsRow = Ui.hbox(this)
        val closed = deal.stage == "Closed Won" || deal.stage == "Closed Lost"
        if (!closed) {
            val b1 = Ui.btn(this@DealDetailActivity, getString(R.string.deal_close), Ui.Btn.PRIMARY) { closeDeal(true) }
            row.addView(b1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@DealDetailActivity, 8)
            })
            val b2 = Ui.btn(this@DealDetailActivity, getString(R.string.deal_close_lost), Ui.Btn.DANGER) { closeDeal(false) }
            row.addView(b2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            val b1 = Ui.btn(this@DealDetailActivity, getString(R.string.deal_reopen), Ui.Btn.SECONDARY) { reopenDeal() }
            row.addView(b1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@DealDetailActivity, 8)
            })
            val b2 = Ui.btn(this@DealDetailActivity, getString(R.string.deal_record_commission), Ui.Btn.PRIMARY) { recordCommission() }
            row.addView(b2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        actions.addView(actionsRow)
        add(actions)
        add(Ui.spacer(this, 8))
    }

    private fun miniStat(label: String, value: String, color: Int): LinearLayout {
        val box = Ui.vbox(this)
        box.gravity = Gravity.CENTER
        val v = Ui.tv(this@DealDetailActivity, value, 15, color, Ui.Font.BOLD)
        v.gravity = Gravity.CENTER
        box.addView(v)
        val l = Ui.tv(this@DealDetailActivity, label, 11, p.textTertiary)
        l.gravity = Gravity.CENTER
        box.addView(l)
        return box
    }

    private fun closeDeal(won: Boolean) {
        Ui.alert(this,
            if (won) getString(R.string.deal_close) else getString(R.string.deal_close_lost),
            getString(R.string.confirm_delete_msg), getString(R.string.confirm)) {
            Async.write({
                val st = Di.store
                st.deals.close(dealId, won)
                st.activities.add(
                    com.estatedesk.crm.data.Activity.T_DEAL,
                    "Deal ${if (won) "won" else "lost"}",
                    deal.displayTitle(), contactId = deal.buyerContactId,
                    propertyId = deal.propertyId, leadId = deal.leadId, dealId = dealId
                )
            }) { load() }
        }
    }

    private fun reopenDeal() {
        Async.write({
            Di.store.deals.reopen(dealId)
        }) { load() }
    }

    private fun recordCommission() {
        Dialogs.askAmount(this, getString(R.string.deal_record_commission)) { amount ->
            if (amount <= 0) return@askAmount
            Async.write({
                val st = Di.store
                st.deals.addCommission(dealId, amount)
                st.finances.save(com.estatedesk.crm.data.Finance(
                    direction = com.estatedesk.crm.data.Finance.INCOME,
                    category = "Commission",
                    amount = amount,
                    date = st.now(),
                    dealId = dealId,
                    notes = "Commission received — ${deal.displayTitle()}"
                ))
            }) {
                snack(getString(R.string.saved))
                load()
            }
        }
    }

    private fun renderTabs() {
        add(Ui.chipRow(this, listOf(
            getString(R.string.contact_overview) to (tab == 0),
            getString(R.string.contact_timeline) to (tab == 1)
        )) { i ->
            tab = i
            load()
        })
        add(Ui.spacer(this, 8))
    }

    private fun renderOverview(
        buyer: String, seller: String, prop: String,
        fin: List<com.estatedesk.crm.data.Finance>,
        tasks: List<com.estatedesk.crm.data.Task>,
        docs: List<com.estatedesk.crm.data.Document>
    ) {
        val info = mutableListOf<Pair<String, String>>()
        if (buyer.isNotBlank()) info.add(getString(R.string.deal_buyer) to buyer)
        if (seller.isNotBlank()) info.add(getString(R.string.deal_seller) to seller)
        if (prop.isNotBlank()) info.add(getString(R.string.deal_property) to prop)
        info.add(getString(R.string.deal_value) to Fmt.money(deal.value, Di.store.currency()))
        info.add(getString(R.string.deal_commission_pct) to "${deal.commissionPct}%")
        if (deal.expectedClose > 0) info.add(getString(R.string.deal_expected_close) to Fmt.date(deal.expectedClose))
        deal.actualClose?.let { info.add(getString(R.string.deal_closed_on) to Fmt.date(it)) }
        info.add(getString(R.string.contact_created) to Fmt.date(deal.createdAt))
        add(Rows.kvCard(this, getString(R.string.deal_title), info))
        add(Ui.spacer(this, 8))

        if (deal.notes.isNotBlank()) {
            val card = Ui.card(this, emptyList())
            card.addView(Ui.tv(this@DealDetailActivity, getString(R.string.deal_notes), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 6))
            card.addView(Ui.tv(this@DealDetailActivity, deal.notes, 14, p.textSecondary))
            add(card)
            add(Ui.spacer(this, 8))
        }

        // finances for this deal
        if (fin.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.finances_title)))
            val card = Ui.card(this, emptyList(), padding = 4)
            fin.forEach { f ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@DealDetailActivity, 10), Ui.dp(this@DealDetailActivity, 12), Ui.dp(this@DealDetailActivity, 10), Ui.dp(this@DealDetailActivity, 12))
                row.addView(Ui.tv(this@DealDetailActivity, f.category, 14, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val color = if (f.direction == "Income") p.success else p.danger
                row.addView(Ui.tv(this@DealDetailActivity, (if (f.direction == "Income") "+" else "-") +
                        Fmt.money(f.amount, Di.store.currency()), 14, color, Ui.Font.BOLD))
                row.addView(Ui.hspacer(this, 10))
                row.addView(Ui.tv(this@DealDetailActivity, Fmt.dateShort(f.date), 11, p.textTertiary))
                card.addView(row)
                if (f != fin.last()) card.addView(Ui.divider(this))
            }
            add(card)
            add(Ui.spacer(this, 8))
        }

        // tasks
        if (tasks.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.nav_tasks), getString(R.string.qa_add_task)) {
                startActivity(Intent(this, TaskFormActivity::class.java).putExtra("deal_id", dealId))
            })
            val card = Ui.card(this, emptyList(), padding = 4)
            tasks.forEach { t ->
                card.addView(Rows.taskRow(this, t, "", onClick = {
                    startActivity(Intent(this, TaskFormActivity::class.java).putExtra("id", t.id))
                }))
                if (t != tasks.last()) card.addView(Ui.divider(this))
            }
            add(card)
            add(Ui.spacer(this, 8))
        }

        // documents
        if (docs.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.deal_documents)))
            val card = Ui.card(this, emptyList(), padding = 4)
            docs.forEach { d ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@DealDetailActivity, 10), Ui.dp(this@DealDetailActivity, 12), Ui.dp(this@DealDetailActivity, 10), Ui.dp(this@DealDetailActivity, 12))
                row.addView(Ui.icon(this@DealDetailActivity, R.drawable.ic_doc, 20, p.primary))
                row.addView(Ui.hspacer(this, 12))
                row.addView(Ui.tv(this@DealDetailActivity, d.name, 14, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.setOnClickListener {
                    try {
                        startActivity(com.estatedesk.crm.core.Files.openExternal(this,
                            com.estatedesk.crm.core.FilesProvider.uriFor("documents/${java.io.File(d.path).name}")))
                    } catch (t: Throwable) {
                        snack(getString(R.string.docs_open_failed))
                    }
                }
                card.addView(row)
                if (d != docs.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
        add(Ui.spacer(this, 48))
    }

    private fun renderTimeline() {
        Async.db({
            Di.store.activities.timelineFor(dealId = dealId, offset = 0, limit = 60)
        }) { acts ->
            if (isFinishing) return@db
            if (acts.isNullOrEmpty()) {
                add(Ui.emptyState(this, R.drawable.ic_history, getString(R.string.contact_timeline),
                    getString(R.string.contact_no_activity)))
            } else {
                val card = Ui.card(this, emptyList(), padding = 4)
                acts.forEach { a ->
                    card.addView(Rows.timelineRow(this, a))
                    if (a != acts.last()) card.addView(Ui.divider(this))
                }
                add(card)
            }
            add(Ui.spacer(this, 48))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::deal.isInitialized) load()
    }
}
