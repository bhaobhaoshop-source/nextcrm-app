package com.estatedesk.crm.ui.widgets

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Deal
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.services.ReminderScheduler
import java.util.Calendar

/** Shared cross-screen dialogs: call logging, notes, follow-ups, viewings,
 *  messages and entity pickers. */
object Dialogs {

    // ------------------------------------------------------------ log call

    fun logCall(act: Activity, contact: Contact, phone: String, lead: Lead? = null,
                property: Property? = null, onDone: () -> Unit = {}) {
        val p = Ui.pal()
        val box = Ui.vbox(act)
        val outcomes = listOf("Connected", "No answer", "Busy", "Wrong number")
        var outcome = "Connected"
        box.addView(Ui.tv(act, act.getString(R.string.activity_call_outcome), 12, p.textTertiary, Ui.Font.MEDIUM))
        val chipRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        outcomes.forEach { o ->
            val c = Ui.chip(act, o, outcome == o) { outcome = o }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = Ui.dp(act, 8)
            chipRow.addView(c, lp)
        }
        box.addView(chipRow)
        box.addView(Ui.spacer(act, 10))
        val notes = Ui.input(act, act.getString(R.string.activity_call_notes), multiline = true)
        box.addView(notes)
        box.addView(Ui.spacer(act, 10))
        val follow = CheckBox(act).apply {
            text = act.getString(R.string.activity_schedule_followup)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = true
        }
        box.addView(follow)

        Ui.sheet(act, act.getString(R.string.activity_log_call_title, contact.displayName()),
            box, listOf("Cancel" to Ui.Btn.SECONDARY, "Save" to Ui.Btn.PRIMARY)) { idx ->
            if (idx != 1) return@sheet
            val note = notes.text.toString().trim()
            val leadId = lead?.id ?: 0L
            val propId = property?.id ?: 0L
            Async.write({
                val st = Di.store
                st.activities.add(
                    com.estatedesk.crm.data.Activity.T_CALL,
                    "Call with ${contact.displayName()} — $outcome",
                    if (note.isBlank()) "Called ${phone}" else note,
                    contactId = contact.id, leadId = leadId, propertyId = propId
                )
                st.contacts.touchContact(contact.id)
                if (lead != null) {
                    st.leads.setScore(lead.id, lead.score.coerceAtLeast(25))
                    st.leads.save(lead.copy(updatedAt = st.now()))
                }
            }, {
                onDone()
                Ui.snack(act, act.getString(R.string.saved))
                if (follow.isChecked) scheduleFollowUp(act, contact, lead, property = property)
            })
        }.show()
    }

    // ------------------------------------------------------------ add note

    fun addNote(act: Activity, contact: Contact? = null, lead: Lead? = null,
                property: Property? = null, deal: Deal? = null, onDone: () -> Unit = {}) {
        Ui.prompt(act, act.getString(R.string.activity_log_note_title),
            act.getString(R.string.activity_log_note_hint), multiline = true) { text ->
            if (text.isBlank()) return@prompt
            Async.write({
                Di.store.activities.add(
                    com.estatedesk.crm.data.Activity.T_NOTE, "Note", text,
                    contactId = contact?.id ?: 0, leadId = lead?.id ?: 0,
                    propertyId = property?.id ?: 0, dealId = deal?.id ?: 0
                )
                contact?.let { Di.store.contacts.touchContact(it.id) }
            }) {
                Ui.snack(act, act.getString(R.string.saved))
                onDone()
            }
        }
    }

    // ------------------------------------------------------------ follow-up

    fun scheduleFollowUp(act: Activity, contact: Contact? = null, lead: Lead? = null,
                         deal: Deal? = null, property: Property? = null,
                         prefillDate: Long = 0, onDone: () -> Unit = {}) {
        val p = Ui.pal()
        val box = Ui.vbox(act)
        var due = if (prefillDate > 0) prefillDate else Fmt.startOfDay(System.currentTimeMillis()) + 86_400_000L
        var minutes = 10 * 60
        var remind = true
        var recurrence = ""

        val dateText = Ui.tv(act, Fmt.friendlyDate(due), 15, Ui.pal().textPrimary)
        val dateField = Ui.pickField(act, act.getString(R.string.task_due_date),
            Fmt.friendlyDate(due)) {
            Ui.datePick(act, due) { d ->
                due = d
                dateText.text = Fmt.friendlyDate(d)
            }
        }
        // replace the pick field's inner text with the updatable one
        (dateField.getChildAt(1) as? LinearLayout)?.let { inner ->
            inner.removeAllViews()
            inner.addView(dateText, Ui.weightLps(1f))
            inner.addView(Ui.icon(act, com.estatedesk.crm.R.drawable.ic_chevron, 18, Ui.pal().textTertiary))
        }
        box.addView(dateField)

        val timeText = Ui.tv(act, Fmt.minutesToTime(minutes), 15, Ui.pal().textPrimary)
        val timeRow = Ui.hbox(act)
        val timeField = Ui.pickField(act, act.getString(R.string.task_due_time),
            Fmt.minutesToTime(minutes)) {
            Ui.timePick(act, minutes) { m ->
                minutes = m
                timeText.text = Fmt.minutesToTime(m)
            }
        }
        (timeField.getChildAt(1) as? LinearLayout)?.let { inner ->
            inner.removeAllViews()
            inner.addView(timeText, Ui.weightLps(1f))
            inner.addView(Ui.icon(act, com.estatedesk.crm.R.drawable.ic_chevron, 18, Ui.pal().textTertiary))
        }
        timeRow.addView(timeField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(timeRow)

        val recurText = Ui.tv(act, "Never", 15, Ui.pal().textPrimary)
        val recurField = Ui.pickField(act, act.getString(R.string.task_recurrence), "Never") {
            Ui.pick(act, act.getString(R.string.task_recurrence), listOf("Never", "Daily", "Weekly", "Monthly")) { i ->
                recurrence = listOf("", "Daily", "Weekly", "Monthly")[i]
                recurText.text = listOf("Never", "Daily", "Weekly", "Monthly")[i]
            }
        }
        (recurField.getChildAt(1) as? LinearLayout)?.let { inner ->
            inner.removeAllViews()
            inner.addView(recurText, Ui.weightLps(1f))
            inner.addView(Ui.icon(act, com.estatedesk.crm.R.drawable.ic_chevron, 18, Ui.pal().textTertiary))
        }
        val recurRow = Ui.hbox(act)
        recurRow.addView(recurField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(recurRow)

        val rem = CheckBox(act).apply {
            text = act.getString(R.string.task_reminder)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = true
        }
        box.addView(rem)

        Ui.sheet(act, act.getString(R.string.activity_schedule_followup_short), box,
            listOf("Cancel" to Ui.Btn.SECONDARY, "Schedule" to Ui.Btn.PRIMARY)) { idx ->
            if (idx != 1) return@sheet
            Async.write({
                val st = Di.store
                val title = if (contact != null) "Follow up with ${contact.displayName()}"
                else "Follow up"
                val t = Task(
                    title = title, kind = Task.KIND_FOLLOW_UP,
                    contactId = contact?.id ?: 0, leadId = lead?.id ?: 0, dealId = deal?.id ?: 0,
                    propertyId = property?.id ?: 0,
                    dueDate = due, dueTimeMinutes = minutes, priority = 1,
                    reminder = rem.isChecked, recurrence = recurrence
                )
                val id = st.tasks.save(t)
                val saved = st.tasks.byId(id)
                // keep contact/lead next_follow_up in sync
                contact?.let { st.contacts.setNextFollowUp(it.id, due) }
                lead?.let { st.leads.setNextFollowUp(it.id, due) }
                if (saved != null) ReminderScheduler.rescheduleTask(act, saved)
            }) {
                Ui.snack(act, act.getString(R.string.saved))
                onDone()
            }
        }.show()
    }

    // ------------------------------------------------------------ viewing

    fun scheduleViewing(act: Activity, contact: Contact, property: Property,
                        lead: Lead? = null, onDone: () -> Unit = {}) {
        val p = Ui.pal()
        val box = Ui.vbox(act)
        var due = Fmt.startOfDay(System.currentTimeMillis()) + 86_400_000L
        var minutes = 16 * 60
        box.addView(Ui.tv(act, "${contact.displayName()} · ${property.displayTitle()}", 14, p.textSecondary))
        box.addView(Ui.spacer(act, 10))
        val dateField = Ui.pickField(act, act.getString(R.string.viewing_date),
            Fmt.friendlyDate(due)) { Ui.datePick(act, due) { d -> due = d } }
        box.addView(dateField)
        val timeField = Ui.pickField(act, act.getString(R.string.viewing_time),
            Fmt.minutesToTime(minutes)) { Ui.timePick(act, minutes) { m -> minutes = m } }
        box.addView(timeField)
        val rem = CheckBox(act).apply {
            text = act.getString(R.string.task_reminder)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = true
        }
        box.addView(rem)

        Ui.sheet(act, act.getString(R.string.viewing_title), box,
            listOf("Cancel" to Ui.Btn.SECONDARY, "Schedule" to Ui.Btn.PRIMARY)) { idx ->
            if (idx != 1) return@sheet
            Async.write({
                val st = Di.store
                val t = Task(
                    title = "Viewing: ${property.displayTitle()}",
                    kind = Task.KIND_VIEWING,
                    contactId = contact.id, propertyId = property.id, leadId = lead?.id ?: 0,
                    dueDate = due, dueTimeMinutes = minutes, priority = 2,
                    reminder = rem.isChecked
                )
                val id = st.tasks.save(t)
                val saved = st.tasks.byId(id)
                st.activities.add(
                    com.estatedesk.crm.data.Activity.T_VIEWING,
                    "Viewing scheduled", "${property.displayTitle()} — ${Fmt.friendlyDateTime(due + minutes * 60_000L)}",
                    contactId = contact.id, propertyId = property.id, leadId = lead?.id ?: 0
                )
                lead?.let { st.leads.setNextFollowUp(it.id, due) }
                if (saved != null) ReminderScheduler.rescheduleTask(act, saved)
            }) {
                Ui.snack(act, act.getString(R.string.saved))
                onDone()
            }
        }.show()
    }

    // ------------------------------------------------------------ messages

    fun messageSheet(act: Activity, contact: Contact, text: String,
                     phone: String, email: String) {
        val p = Ui.pal()
        val box = Ui.vbox(act)
        val preview = Ui.tv(act, text, 14, p.textSecondary)
        preview.setPadding(Ui.dp(act, 14), Ui.dp(act, 14), Ui.dp(act, 14), Ui.dp(act, 14))
        preview.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = Ui.dp(act, 12).toFloat()
            setColor(p.surface2)
        }
        box.addView(preview)
        box.addView(Ui.spacer(act, 14))

        fun row(label: String, res: Int, action: () -> Unit): LinearLayout {
            val r = Ui.hbox(act)
            r.setPadding(Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4), Ui.dp(act, 12))
            r.addView(Ui.icon(act, res, 20, p.primary))
            r.addView(Ui.hspacer(act, 14))
            r.addView(Ui.tv(act, label, 15, p.textPrimary, Ui.Font.MEDIUM))
            r.setOnClickListener { action() }
            return r
        }

        box.addView(row(act.getString(R.string.msg_copy), R.drawable.ic_doc) {
            val cm = act.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
            Ui.snack(act, act.getString(R.string.msg_copied))
        })
        if (phone.isNotBlank()) {
            box.addView(row(act.getString(R.string.msg_send_whatsapp), R.drawable.ic_whatsapp) {
                openWhatsApp(act, phone, text)
            })
            box.addView(row(act.getString(R.string.msg_send_sms), R.drawable.ic_sms) {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                i.putExtra("sms_body", text)
                act.startActivity(i)
            })
        }
        if (email.isNotBlank()) {
            box.addView(row(act.getString(R.string.msg_send_email), R.drawable.ic_email) {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                i.putExtra(Intent.EXTRA_SUBJECT, act.getString(R.string.app_name))
                i.putExtra(Intent.EXTRA_TEXT, text)
                act.startActivity(i)
            })
        }
        Ui.sheet(act, act.getString(R.string.lead_msg_template), box,
            listOf("Close" to Ui.Btn.SECONDARY)) { }.show()
    }

    fun openWhatsApp(act: Activity, phone: String, text: String) {
        val digits = phone.filter { it.isDigit() }.removePrefix("0")
        val full = if (digits.startsWith("92")) digits else "92$digits"
        try {
            val url = "https://wa.me/$full?text=" + android.net.Uri.encode(text)
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            act.startActivity(i)
        } catch (t: Throwable) {
            Ui.snack(act, act.getString(R.string.error_generic))
        }
    }

    // ------------------------------------------------------------ pickers

    fun pickContact(act: Activity, title: String, onPick: (Contact) -> Unit) {
        Async.db({ Di.store.contacts.allForPicker() }) { list ->
            if (list.isEmpty()) {
                Ui.alert(act, title, act.getString(R.string.contacts_empty), act.getString(R.string.close)) {}
                return@db
            }
            val names = list.map { c ->
                val phone = Di.store.contacts.phones(c.id).firstOrNull()?.number ?: ""
                "${c.displayName()}${if (phone.isNotBlank()) " · $phone" else ""}"
            }
            Ui.pick(act, title, names) { i -> onPick(list[i]) }
        }
    }

    fun pickProperty(act: Activity, title: String, onPick: (Property) -> Unit) {
        Async.db({ Di.store.properties.allForPicker() }) { list ->
            if (list.isEmpty()) {
                Ui.alert(act, title, act.getString(R.string.property_no_props), act.getString(R.string.close)) {}
                return@db
            }
            val names = list.map { "${it.displayTitle()} · ${Fmt.money(it.price, Di.store.currency())}" }
            Ui.pick(act, title, names) { i -> onPick(list[i]) }
        }
    }

    fun pickDeal(act: Activity, title: String, onPick: (Deal) -> Unit) {
        Async.db({ Di.store.deals.allForPicker() }) { list ->
            if (list.isEmpty()) {
                Ui.alert(act, title, act.getString(R.string.deal_no_deals), act.getString(R.string.close)) {}
                return@db
            }
            val names = list.map { it.displayTitle() }
            Ui.pick(act, title, names) { i -> onPick(list[i]) }
        }
    }

    fun pickLead(act: Activity, title: String, onPick: (Lead) -> Unit) {
        Async.db({ Di.store.leads.recent(500) }) { list ->
            if (list.isEmpty()) {
                Ui.alert(act, title, act.getString(R.string.lead_no_leads), act.getString(R.string.close)) {}
                return@db
            }
            val names = list.map { it.displayTitle() }
            Ui.pick(act, title, names) { i -> onPick(list[i]) }
        }
    }

    /** Ask amount (minor units) — returns value via callback. */
    fun askAmount(act: Activity, title: String, onOk: (Long) -> Unit) {
        val edit = Ui.input(act, "0", inputType = android.text.InputType.TYPE_CLASS_NUMBER)
        Ui.sheet(act, title, edit, listOf("Cancel" to Ui.Btn.SECONDARY, "OK" to Ui.Btn.PRIMARY)) { idx ->
            if (idx == 1) onOk(edit.text.toString().toLongOrNull() ?: 0L)
        }.show()
    }
}
