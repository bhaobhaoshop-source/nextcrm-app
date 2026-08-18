package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Task
import com.estatedesk.crm.services.ReminderScheduler
import com.estatedesk.crm.ui.widgets.Rows

/** The dedicated follow-up workbench: overdue, today, upcoming, done-today. */
class FollowUpTodayActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.followup_title))
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val (dayStart, dayEnd) = st.stats.dayRange(0)
            val all = st.tasks.followUps()
            val doneToday = st.tasks.query("today", "Follow-up", "", dayStart, dayEnd, 0, 200)
                .filter { it.status == Task.STATUS_DONE } +
                    st.tasks.query("all", "Follow-up", "", 0, 0, 0, 500)
                        .filter { it.status == Task.STATUS_DONE && (it.completedAt ?: 0) >= dayStart }
            data class G(
                val overdue: List<Task>, val today: List<Task>, val upcoming: List<Task>,
                val done: List<Task>
            )
            G(
                all.filter { it.dueDate > 0 && it.dueDate < dayStart },
                all.filter { it.dueDate in dayStart until dayEnd },
                all.filter { it.dueDate >= dayEnd },
                doneToday.distinctBy { it.id }
            )
        }) { g ->
            if (g == null || isFinishing) return@db
            content.removeAllViews()
            topBar(getString(R.string.followup_title))
            if (g.overdue.isEmpty() && g.today.isEmpty() && g.upcoming.isEmpty()) {
                add(Ui.emptyState(this, R.drawable.ic_history,
                    getString(R.string.followup_none_today), getString(R.string.followup_all_clear),
                    getString(R.string.qa_add_followup)) {
                    startActivity(Intent(this, TaskFormActivity::class.java).putExtra("kind", "Follow-up"))
                })
            } else {
                if (g.overdue.isNotEmpty()) {
                    add(Ui.sectionTitle(this, getString(R.string.followup_overdue_section) +
                            " · ${g.overdue.size}"))
                    val card = Ui.card(this, emptyList(), padding = 4)
                    g.overdue.forEach { t ->
                        card.addView(followUpRow(t, overdue = true))
                        if (t != g.overdue.last()) card.addView(Ui.divider(this))
                    }
                    add(card)
                    add(Ui.spacer(this, 10))
                }
                if (g.today.isNotEmpty()) {
                    add(Ui.sectionTitle(this, getString(R.string.followup_today_section) + " · ${g.today.size}"))
                    val card = Ui.card(this, emptyList(), padding = 4)
                    g.today.forEach { t ->
                        card.addView(followUpRow(t, overdue = false))
                        if (t != g.today.last()) card.addView(Ui.divider(this))
                    }
                    add(card)
                    add(Ui.spacer(this, 10))
                }
                if (g.upcoming.isNotEmpty()) {
                    add(Ui.sectionTitle(this, getString(R.string.followup_upcoming_section)))
                    val card = Ui.card(this, emptyList(), padding = 4)
                    g.upcoming.take(15).forEach { t ->
                        card.addView(followUpRow(t, overdue = false))
                        if (t != g.upcoming.take(15).last()) card.addView(Ui.divider(this))
                    }
                    add(card)
                }
            }
            add(Ui.spacer(this, 48))
        }
    }

    private fun followUpRow(t: Task, overdue: Boolean): LinearLayout {
        val contactName = t.contactId.takeIf { it > 0 }
            ?.let { Di.store.contacts.byId(it)?.displayName() } ?: ""
        val box = Ui.vbox(this)
        val row = Ui.hbox(this)
        val mid = Ui.vbox(this)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mid.addView(Ui.tv(this@FollowUpTodayActivity, t.title, 14, p.textPrimary, Ui.Font.MEDIUM, 2))
        val sub = listOf(contactName, Fmt.friendlyDateTime(t.dueAt())).filter { it.isNotBlank() }
            .joinToString(" · ")
        mid.addView(Ui.tv(this@FollowUpTodayActivity, sub, 12, if (overdue) p.danger else p.textTertiary, maxLines = 1))
        row.addView(mid, lpMid)
        box.addView(row)
        // actions
        val actions = Ui.hbox(this)
        box.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(this@FollowUpTodayActivity, 8) })
        val done = Ui.btn(this@FollowUpTodayActivity, getString(R.string.task_mark_done), Ui.Btn.PRIMARY) { complete(t) }
        actions.addView(done, LinearLayout.LayoutParams(0, Ui.dp(this@FollowUpTodayActivity, 40), 1f).apply {
            rightMargin = Ui.dp(this@FollowUpTodayActivity, 6)
        })
        val snooze = Ui.btn(this@FollowUpTodayActivity, getString(R.string.followup_snooze), Ui.Btn.SECONDARY) {
            Ui.pick(this, getString(R.string.followup_snooze), listOf(
                getString(R.string.followup_snooze_hour),
                getString(R.string.followup_snooze_day),
                getString(R.string.followup_snooze_week)
            )) { i ->
                val delta = when (i) {
                    0 -> 60 * 60_000L
                    1 -> 86_400_000L
                    else -> 7 * 86_400_000L
                }
                Async.write({
                    Di.store.tasks.snooze(t.id, delta)
                    val cur = Di.store.tasks.byId(t.id)
                    if (cur != null) ReminderScheduler.rescheduleTask(this, cur)
                }) { load() }
            }
        }
        actions.addView(snooze, LinearLayout.LayoutParams(0, Ui.dp(this@FollowUpTodayActivity, 40), 1f).apply {
            rightMargin = Ui.dp(this@FollowUpTodayActivity, 6)
        })
        val call = Ui.btn(this@FollowUpTodayActivity, getString(R.string.contact_call), Ui.Btn.SECONDARY) { callContact(t) }
        actions.addView(call, LinearLayout.LayoutParams(0, Ui.dp(this@FollowUpTodayActivity, 40), 1f))
        box.setPadding(Ui.dp(this@FollowUpTodayActivity, 10), Ui.dp(this@FollowUpTodayActivity, 12), Ui.dp(this@FollowUpTodayActivity, 10), Ui.dp(this@FollowUpTodayActivity, 12))
        return box
    }

    private fun complete(t: Task) {
        Async.db({
            val next = Di.store.tasks.complete(t.id)
            Di.store.activities.add(
                com.estatedesk.crm.data.Activity.T_FOLLOW_UP, "Follow-up done", t.title,
                contactId = t.contactId, leadId = t.leadId, taskId = t.id
            )
            ReminderScheduler.cancel(this, t.id)
            next
        }) { next ->
            if (next != null) ReminderScheduler.rescheduleTask(this, next)
            snack(getString(R.string.task_mark_done))
            load()
        }
    }

    private fun callContact(t: Task) {
        if (t.contactId == 0L) return
        Async.db({
            val c = Di.store.contacts.byId(t.contactId)
            c?.let { Di.store.contacts.phones(it.id).firstOrNull()?.number } ?: ""
        }) { phone ->
            if (phone.isNullOrBlank()) {
                snack(getString(R.string.contact_no_phones)); return@db
            }
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
            } catch (t2: Throwable) {
                snack(getString(R.string.error_generic))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }
}
