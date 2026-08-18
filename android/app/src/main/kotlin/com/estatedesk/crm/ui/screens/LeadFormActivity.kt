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
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.domain.LeadScorer
import com.estatedesk.crm.ui.widgets.Dialogs

class LeadFormActivity : BaseActivity() {

    private var leadId = 0L
    private var contactId = 0L
    private lateinit var title: EditText
    private lateinit var requirement: EditText
    private lateinit var budgetMin: EditText
    private lateinit var budgetMax: EditText
    private lateinit var location: EditText
    private lateinit var nextAction: EditText
    private lateinit var notes: EditText
    private var propertyType = ""
    private var intentType = "Buy"
    private var stage = ""
    private var source = ""
    private var priority = 1
    private var contactName = ""

    override fun build() {
        leadId = intent.getLongExtra("id", 0L)
        contactId = intent.getLongExtra("contact_id", 0L)
        topBar(if (leadId == 0L) getString(R.string.lead_new) else getString(R.string.lead_edit))
        if (leadId > 0) loadExisting() else {
            if (contactId > 0) {
                Async.db({ Di.store.contacts.byId(contactId) }) { c ->
                    contactName = c?.displayName() ?: ""
                    render()
                }
            } else render()
        }
    }

    private fun loadExisting() {
        Async.db({ Di.store.leads.byId(leadId) }) { l ->
            if (l == null || isFinishing) return@db
            contactId = l.contactId
            contactName = Di.store.contacts.byId(l.contactId)?.displayName() ?: ""
            render()
            title.setText(l.title); requirement.setText(l.requirement)
            budgetMin.setText(if (l.budgetMin > 0) l.budgetMin.toString() else "")
            budgetMax.setText(if (l.budgetMax > 0) l.budgetMax.toString() else "")
            location.setText(l.location); nextAction.setText(l.nextAction); notes.setText(l.notes)
            propertyType = l.propertyType; intentType = l.intent; stage = l.stage
            source = l.source; priority = l.priority
        }
    }

    private fun render() {
        title = Ui.input(this@LeadFormActivity, getString(R.string.lead_title_field))
        add(Ui.field(this, getString(R.string.lead_title_field), title))

        add(Ui.pickField(this, getString(R.string.lead_contact), contactName.ifBlank { getString(R.string.flt_any) }) {
            Dialogs.pickContact(this, getString(R.string.lead_contact)) { c ->
                contactId = c.id
                contactName = c.displayName()
                if (title.text.isNullOrBlank()) title.setText(c.displayName())
                content.removeAllViews()
                topBar(if (leadId == 0L) getString(R.string.lead_new) else getString(R.string.lead_edit))
                render()
            }
        })

        requirement = Ui.input(this@LeadFormActivity, getString(R.string.lead_requirement_hint), multiline = true)
        add(Ui.field(this, getString(R.string.lead_requirement), requirement))

        budgetMin = Ui.input(this@LeadFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        budgetMax = Ui.input(this@LeadFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        val bRow = Ui.hbox(this)
        bRow.addView(Ui.field(this, "Min", budgetMin),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@LeadFormActivity, 8) })
        bRow.addView(Ui.field(this, "Max", budgetMax),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(bRow)

        location = Ui.input(this@LeadFormActivity, getString(R.string.lead_location))
        add(Ui.field(this, getString(R.string.lead_location), location))

        val typeRow = Ui.hbox(this)
        typeRow.addView(Ui.pickField(this, getString(R.string.lead_property_type),
            propertyType.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.lead_property_type),
                listOf(getString(R.string.flt_any)) + Di.store.propertyTypes()) { i ->
                propertyType = if (i == 0) "" else Di.store.propertyTypes()[i - 1]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@LeadFormActivity, 8) })
        typeRow.addView(Ui.pickField(this, getString(R.string.lead_intent), intentType) {
            Ui.pick(this, getString(R.string.lead_intent),
                listOf(getString(R.string.lead_intent_buy), getString(R.string.lead_intent_rent))) { i ->
                intentType = listOf("Buy", "Rent")[i]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(typeRow)

        val stages = Di.store.leadStages().map { it.name }
        add(Ui.pickField(this, getString(R.string.lead_stage), stage.ifBlank { stages.first() }) {
            Ui.pick(this, getString(R.string.lead_stage), stages) { i -> stage = stages[i] }
        })

        add(Ui.pickField(this, getString(R.string.lead_source),
            source.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.lead_source),
                listOf(getString(R.string.flt_any)) + Di.store.leadSources()) { i ->
                source = if (i == 0) "" else Di.store.leadSources()[i - 1]
            }
        })
        add(Ui.pickField(this, getString(R.string.lead_priority), priorityText()) {
            Ui.pick(this, getString(R.string.lead_priority),
                listOf(getString(R.string.pr_low), getString(R.string.pr_medium), getString(R.string.pr_high))) { i ->
                priority = i + 1
            }
        })

        nextAction = Ui.input(this@LeadFormActivity, getString(R.string.lead_next_action))
        add(Ui.field(this, getString(R.string.lead_next_action), nextAction))

        notes = Ui.input(this@LeadFormActivity, getString(R.string.lead_notes), multiline = true)
        add(Ui.field(this, getString(R.string.lead_notes), notes))

        add(Ui.spacer(this, 8))
        add(Ui.btn(this@LeadFormActivity, getString(R.string.save), Ui.Btn.PRIMARY) { save() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@LeadFormActivity, 40)
            })
    }

    private fun priorityText(): String = when (priority) {
        3 -> getString(R.string.pr_high)
        2 -> getString(R.string.pr_medium)
        else -> getString(R.string.pr_low)
    }

    private fun save() {
        val req = requirement.text.toString().trim()
        if (title.text.isNullOrBlank() && req.isBlank()) {
            snack(getString(R.string.error_required)); return
        }
        Async.db({
            val st = Di.store
            val stages = st.leadStages().map { it.name }
            val existing = st.leads.byId(leadId)
            val contact = st.contacts.byId(contactId)
            val phoneCount = if (contactId > 0) st.contacts.phones(contactId).size else 0
            val lead = Lead(
                id = leadId,
                title = title.text.toString().trim().ifBlank { req.take(48) },
                contactId = contactId,
                source = source,
                requirement = req,
                budgetMin = budgetMin.text.toString().toLongOrNull() ?: 0,
                budgetMax = budgetMax.text.toString().toLongOrNull() ?: 0,
                location = location.text.toString().trim(),
                propertyType = propertyType,
                intent = intentType,
                stage = if (stage.isBlank()) stages.first() else stage,
                score = existing?.score ?: 0,
                priority = priority,
                probability = existing?.probability ?: st.stageProbability(
                    if (stage.isBlank()) stages.first() else stage),
                nextAction = nextAction.text.toString().trim(),
                nextFollowUp = existing?.nextFollowUp ?: 0,
                assignedTo = existing?.assignedTo ?: "",
                notes = notes.text.toString().trim(),
                createdAt = existing?.createdAt ?: 0,
                dealId = existing?.dealId ?: 0
            )
            val (score, _) = LeadScorer.score(lead, contact, phoneCount, stages)
            val finalLead = lead.copy(score = score, probability = if (stage.isBlank()) score / 2 else st.stageProbability(lead.stage))
            val id = st.leads.save(finalLead)
            id
        }) { id ->
            if (id == null || isFinishing) return@db
            snack(getString(R.string.saved))
            setResult(android.app.Activity.RESULT_OK)
            finish()
        }
    }
}
