package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.services.ReminderScheduler
import com.estatedesk.crm.ui.widgets.Dialogs

class TaskFormActivity : BaseActivity() {

    private var taskId = 0L
    private var contactId = 0L
    private var propertyId = 0L
    private var leadId = 0L
    private var dealId = 0L
    private lateinit var title: EditText
    private lateinit var notes: EditText
    private var kind = Task.KIND_TASK
    private var dueDate = 0L
    private var minutes = -1
    private var priority = 1
    private var reminder = true
    private var recurrence = ""
    private var contactName = ""
    private var propertyName = ""
    private var leadName = ""
    private var dealName = ""

    override fun build() {
        taskId = intent.getLongExtra("id", 0)
        contactId = intent.getLongExtra("contact_id", 0)
        propertyId = intent.getLongExtra("property_id", 0)
        leadId = intent.getLongExtra("lead_id", 0)
        dealId = intent.getLongExtra("deal_id", 0)
        val presetKind = intent.getStringExtra("kind") ?: ""
        topBar(if (taskId == 0L) getString(R.string.task_new) else getString(R.string.task_edit))
        if (taskId > 0) {
            Async.db({ Di.store.tasks.byId(taskId) }) { t ->
                if (t == null || isFinishing) return@db
                contactId = t.contactId; propertyId = t.propertyId
                leadId = t.leadId; dealId = t.dealId
                contactName = Di.store.contacts.byId(contactId)?.displayName() ?: ""
                propertyName = Di.store.properties.byId(propertyId)?.displayTitle() ?: ""
                leadName = Di.store.leads.byId(leadId)?.displayTitle() ?: ""
                dealName = Di.store.deals.byId(dealId)?.displayTitle() ?: ""
                render()
                title.setText(t.title); notes.setText(t.notes)
                kind = t.kind; dueDate = t.dueDate; minutes = t.dueTimeMinutes
                priority = t.priority; reminder = t.reminder; recurrence = t.recurrence
            }
        } else {
            if (presetKind.isNotBlank()) kind = presetKind
            if (contactId > 0) {
                Async.db({ Di.store.contacts.byId(contactId)?.displayName() ?: "" }) { n ->
                    contactName = n ?: ""
                    render()
                }
            } else render()
        }
    }

    private fun render() {
        title = Ui.input(this@TaskFormActivity, getString(R.string.task_title_field))
        add(Ui.field(this, getString(R.string.task_title_field), title))

        add(Ui.pickField(this, getString(R.string.task_kind), kind) {
            Ui.pick(this, getString(R.string.task_kind),
                listOf("Task", "Follow-up", "Call", "Meeting", "Viewing", "Email")) { i ->
                kind = listOf("Task", "Follow-up", "Call", "Meeting", "Viewing", "Email")[i]
            }
        })

        // related entity (single picker, one at a time)
        val relatedLabel = when {
            contactId > 0 -> contactName
            propertyId > 0 -> propertyName
            leadId > 0 -> leadName
            dealId > 0 -> dealName
            else -> ""
        }
        add(Ui.pickField(this, getString(R.string.task_related_contact),
            relatedLabel.ifBlank { getString(R.string.flt_any) }) {
            val options = listOf("Contact", "Property", "Lead", "Deal")
            Ui.pick(this, getString(R.string.task_related_contact), options) { i ->
                when (i) {
                    0 -> Dialogs.pickContact(this, getString(R.string.task_related_contact)) { c ->
                        contactId = c.id; propertyId = 0; leadId = 0; dealId = 0
                        contactName = c.displayName()
                        content.removeAllViews(); topBar(if (taskId == 0L) getString(R.string.task_new) else getString(R.string.task_edit)); render()
                    }
                    1 -> Dialogs.pickProperty(this, getString(R.string.task_related_property)) { pr ->
                        contactId = 0; propertyId = pr.id; leadId = 0; dealId = 0
                        propertyName = pr.displayTitle()
                        content.removeAllViews(); topBar(if (taskId == 0L) getString(R.string.task_new) else getString(R.string.task_edit)); render()
                    }
                    2 -> Dialogs.pickLead(this, getString(R.string.task_related_lead)) { l ->
                        contactId = 0; propertyId = 0; leadId = l.id; dealId = 0
                        leadName = l.displayTitle()
                        content.removeAllViews(); topBar(if (taskId == 0L) getString(R.string.task_new) else getString(R.string.task_edit)); render()
                    }
                    3 -> Dialogs.pickDeal(this, getString(R.string.task_related_deal)) { dl ->
                        contactId = 0; propertyId = 0; leadId = 0; dealId = dl.id
                        dealName = dl.displayTitle()
                        content.removeAllViews(); topBar(if (taskId == 0L) getString(R.string.task_new) else getString(R.string.task_edit)); render()
                    }
                }
            }
        })

        val dueRow = Ui.hbox(this)
        dueRow.addView(Ui.pickField(this, getString(R.string.task_due_date),
            Fmt.friendlyDate(dueDate)) {
            Ui.datePick(this, dueDate) { d -> dueDate = d }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@TaskFormActivity, 8) })
        dueRow.addView(Ui.pickField(this, getString(R.string.task_due_time),
            if (minutes >= 0) Fmt.minutesToTime(minutes) else "") {
            Ui.timePick(this, minutes) { m -> minutes = m }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(dueRow)

        add(Ui.pickField(this, getString(R.string.task_priority), priorityText()) {
            Ui.pick(this, getString(R.string.task_priority),
                listOf(getString(R.string.pr_low), getString(R.string.pr_medium), getString(R.string.pr_high))) { i ->
                priority = i + 1
            }
        })

        add(Ui.pickField(this, getString(R.string.task_recurrence), recurrenceLabel()) {
            Ui.pick(this, getString(R.string.task_recurrence),
                listOf(getString(R.string.task_recurrence_none), getString(R.string.task_recurrence_daily),
                    getString(R.string.task_recurrence_weekly), getString(R.string.task_recurrence_monthly))) { i ->
                recurrence = listOf("", "Daily", "Weekly", "Monthly")[i]
            }
        })

        val rem = CheckBox(this).apply {
            text = getString(R.string.task_reminder)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = reminder
            setOnCheckedChangeListener { _, b -> reminder = b }
        }
        add(rem)

        notes = Ui.input(this@TaskFormActivity, getString(R.string.task_notes), multiline = true)
        add(Ui.field(this, getString(R.string.task_notes), notes))

        add(Ui.spacer(this, 8))
        add(Ui.btn(this@TaskFormActivity, getString(R.string.save), Ui.Btn.PRIMARY) { save() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@TaskFormActivity, 40)
            })
    }

    private fun priorityText(): String = when (priority) {
        3 -> getString(R.string.pr_high)
        2 -> getString(R.string.pr_medium)
        else -> getString(R.string.pr_low)
    }

    private fun recurrenceLabel(): String = when (recurrence) {
        "Daily" -> getString(R.string.task_recurrence_daily)
        "Weekly" -> getString(R.string.task_recurrence_weekly)
        "Monthly" -> getString(R.string.task_recurrence_monthly)
        else -> getString(R.string.task_recurrence_none)
    }

    private fun save() {
        if (title.text.isNullOrBlank()) {
            snack(getString(R.string.error_required)); return
        }
        val existing = if (taskId > 0) Di.store.tasks.byId(taskId) else null
        val t = Task(
            id = taskId,
            title = title.text.toString().trim(),
            kind = kind,
            contactId = contactId, propertyId = propertyId, leadId = leadId, dealId = dealId,
            dueDate = dueDate, dueTimeMinutes = minutes,
            priority = priority,
            status = existing?.status ?: Task.STATUS_OPEN,
            reminder = reminder, recurrence = recurrence,
            notes = notes.text.toString().trim(),
            completedAt = existing?.completedAt
        )
        Async.write({
            val id = Di.store.tasks.save(t)
            val saved = Di.store.tasks.byId(id)
            if (saved != null) ReminderScheduler.rescheduleTask(this, saved)
            else ReminderScheduler.cancel(this, id)
        }, {
            snack(getString(R.string.saved))
            setResult(android.app.Activity.RESULT_OK)
            finish()
        }, { snack(getString(R.string.error_save)) })
    }
}
