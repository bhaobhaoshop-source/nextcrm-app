package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Deal
import com.estatedesk.crm.ui.widgets.Dialogs

class DealFormActivity : BaseActivity() {

    private var dealId = 0L
    private var buyerId = 0L
    private var sellerId = 0L
    private var propertyId = 0L
    private var leadId = 0L
    private lateinit var title: EditText
    private lateinit var value: EditText
    private lateinit var pct: EditText
    private lateinit var amount: EditText
    private lateinit var received: EditText
    private lateinit var notes: EditText
    private var stage = "Negotiation"
    private var expectedClose = 0L
    private var buyerName = ""
    private var sellerName = ""
    private var propertyName = ""

    override fun build() {
        dealId = intent.getLongExtra("id", 0)
        buyerId = intent.getLongExtra("contact_id", 0)
        propertyId = intent.getLongExtra("property_id", 0)
        leadId = intent.getLongExtra("lead_id", 0)
        val presetTitle = intent.getStringExtra("title") ?: ""
        topBar(if (dealId == 0L) getString(R.string.deal_new) else getString(R.string.deal_edit))
        if (dealId > 0) {
            loadExisting()
        } else {
            Async.db({
                data class Names(val b: String, val p: String)
                Names(
                    if (buyerId > 0) Di.store.contacts.byId(buyerId)?.displayName() ?: "" else "",
                    if (propertyId > 0) Di.store.properties.byId(propertyId)?.displayTitle() ?: "" else ""
                )
            }) { n ->
                if (n == null || isFinishing) return@db
                buyerName = n.b
                propertyName = n.p
                title.setText(presetTitle)
                render()
            }
        }
    }

    private fun loadExisting() {
        Async.db({ Di.store.deals.byId(dealId) }) { d ->
            if (d == null || isFinishing) return@db
            buyerId = d.buyerContactId; sellerId = d.sellerContactId
            propertyId = d.propertyId; leadId = d.leadId
            buyerName = Di.store.contacts.byId(buyerId)?.displayName() ?: ""
            sellerName = Di.store.contacts.byId(sellerId)?.displayName() ?: ""
            propertyName = Di.store.properties.byId(propertyId)?.displayTitle() ?: ""
            render()
            title.setText(d.title); value.setText(if (d.value > 0) d.value.toString() else "")
            pct.setText(if (d.commissionPct > 0) d.commissionPct.toString() else "")
            amount.setText(if (d.commissionAmount > 0) d.commissionAmount.toString() else "")
            received.setText(if (d.commissionReceived > 0) d.commissionReceived.toString() else "")
            notes.setText(d.notes)
            stage = d.stage; expectedClose = d.expectedClose
        }
    }

    private fun render() {
        title = Ui.input(this@DealFormActivity, getString(R.string.deal_title))
        add(Ui.field(this, getString(R.string.deal_title), title))

        add(Ui.pickField(this, getString(R.string.deal_buyer),
            buyerName.ifBlank { getString(R.string.flt_any) }) {
            Dialogs.pickContact(this, getString(R.string.deal_buyer)) { c ->
                buyerId = c.id
                buyerName = c.displayName()
                content.removeAllViews()
                topBar(if (dealId == 0L) getString(R.string.deal_new) else getString(R.string.deal_edit))
                render()
            }
        })
        add(Ui.pickField(this, getString(R.string.deal_seller),
            sellerName.ifBlank { getString(R.string.flt_any) }) {
            Dialogs.pickContact(this, getString(R.string.deal_seller)) { c ->
                sellerId = c.id
                sellerName = c.displayName()
                content.removeAllViews()
                topBar(if (dealId == 0L) getString(R.string.deal_new) else getString(R.string.deal_edit))
                render()
            }
        })
        add(Ui.pickField(this, getString(R.string.deal_property),
            propertyName.ifBlank { getString(R.string.flt_any) }) {
            Dialogs.pickProperty(this, getString(R.string.deal_property)) { pr ->
                propertyId = pr.id
                propertyName = pr.displayTitle()
                content.removeAllViews()
                topBar(if (dealId == 0L) getString(R.string.deal_new) else getString(R.string.deal_edit))
                render()
            }
        })

        value = Ui.input(this@DealFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        add(Ui.field(this, getString(R.string.deal_value), value))

        pct = Ui.input(this@DealFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        amount = Ui.input(this@DealFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        val commRow = Ui.hbox(this)
        commRow.addView(Ui.field(this, getString(R.string.deal_commission_pct) + " %", pct),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@DealFormActivity, 8) })
        commRow.addView(Ui.field(this, getString(R.string.deal_commission_amount), amount),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(commRow)

        received = Ui.input(this@DealFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        add(Ui.field(this, getString(R.string.deal_commission_received), received))

        val stages = Di.store.dealStages().map { it.name }
        add(Ui.pickField(this, getString(R.string.deal_stage), stage.ifBlank { stages.first() }) {
            Ui.pick(this, getString(R.string.deal_stage), stages) { i -> stage = stages[i] }
        })

        add(Ui.pickField(this, getString(R.string.deal_expected_close),
            Fmt.friendlyDate(expectedClose)) {
            Ui.datePick(this, expectedClose) { d -> expectedClose = d }
        })

        notes = Ui.input(this@DealFormActivity, getString(R.string.deal_notes), multiline = true)
        add(Ui.field(this, getString(R.string.deal_notes), notes))

        add(Ui.spacer(this, 8))
        add(Ui.btn(this@DealFormActivity, getString(R.string.save), Ui.Btn.PRIMARY) { save() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@DealFormActivity, 40)
            })
    }

    private fun save() {
        val t = title.text.toString().trim()
        if (t.isBlank() && buyerName.isBlank()) {
            snack(getString(R.string.error_required)); return
        }
        val pctV = pct.text.toString().toDoubleOrNull() ?: 0.0
        val valueV = value.text.toString().toLongOrNull() ?: 0
        val amountV = amount.text.toString().toLongOrNull()?.takeIf { it > 0 }
            ?: (valueV * pctV / 100).toLong()
        val existing = if (dealId > 0) Di.store.deals.byId(dealId) else null
        val deal = Deal(
            id = dealId,
            title = t.ifBlank { "${buyerName.ifBlank { "Deal" }} — ${propertyName.ifBlank { "Property" }}" },
            buyerContactId = buyerId, sellerContactId = sellerId,
            propertyId = propertyId, leadId = leadId,
            value = valueV, currency = Di.store.currency().code,
            commissionPct = pctV, commissionAmount = amountV,
            commissionReceived = received.text.toString().toLongOrNull() ?: 0,
            stage = stage,
            expectedClose = expectedClose,
            actualClose = existing?.actualClose,
            notes = notes.text.toString().trim(),
            createdAt = existing?.createdAt ?: 0
        )
        Async.write({
            val id = Di.store.deals.save(deal)
            if (leadId > 0) Di.store.leads.linkDeal(leadId, id)
            Di.store.activities.add(
                com.estatedesk.crm.data.Activity.T_DEAL,
                "Deal ${if (dealId == 0L) "created" else "updated"}",
                "${deal.displayTitle()} · ${Fmt.money(valueV, Di.store.currency())}",
                contactId = buyerId, propertyId = propertyId, leadId = leadId, dealId = id
            )
        }, {
            snack(getString(R.string.saved))
            setResult(android.app.Activity.RESULT_OK)
            finish()
        }, { snack(getString(R.string.error_save)) })
    }
}
