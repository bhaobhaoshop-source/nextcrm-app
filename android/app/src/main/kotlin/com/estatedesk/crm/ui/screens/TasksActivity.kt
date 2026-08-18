package com.estatedesk.crm.ui.screens

import android.content.Intent
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
import com.estatedesk.crm.ui.widgets.PagedList
import com.estatedesk.crm.ui.widgets.Rows

class TasksActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var tab = "all"
    private var kind = ""
    private var queryId = 0
    private var emptyView: View? = null

    override fun build() {
        topBar(getString(R.string.tasks_title))
        add(Ui.chipRow(this, listOf(
            getString(R.string.task_tab_all) to (tab == "all"),
            getString(R.string.task_tab_today) to (tab == "today"),
            getString(R.string.task_tab_upcoming) to (tab == "upcoming"),
            getString(R.string.task_tab_overdue) to (tab == "overdue")
        )) { i ->
            tab = listOf("all", "today", "upcoming", "overdue")[i]
            refresh()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(Ui.spacer(this, 4))
        val kindRow = Ui.chipRow(this, listOf(
            getString(R.string.all) to (kind.isBlank()),
            getString(R.string.task_kind_followup) to (kind == Task.KIND_FOLLOW_UP),
            getString(R.string.task_kind_viewing) to (kind == Task.KIND_VIEWING),
            getString(R.string.task_kind_meeting) to (kind == Task.KIND_MEETING),
            getString(R.string.task_kind_call) to (kind == Task.KIND_CALL)
        )) { i ->
            kind = when (i) {
                1 -> Task.KIND_FOLLOW_UP; 2 -> Task.KIND_VIEWING
                3 -> Task.KIND_MEETING; 4 -> Task.KIND_CALL; else -> ""
            }
            refresh()
        }
        add(kindRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list = PagedList(this, 30)
        list.onLoadPage = { offset -> loadPage(offset) }
        add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addFab { startActivity(Intent(this, TaskFormActivity::class.java)) }
        refresh()
    }

    private fun refresh() {
        queryId++
        list.reset()
    }

    private fun loadPage(offset: Int) {
        val id = queryId
        Async.db({
            val st = Di.store
            val (dayStart, dayEnd) = st.stats.dayRange(0)
            val tasks = st.tasks.query(tab, kind, "", dayStart, dayEnd, offset, 30)
            tasks.map { t ->
                val related = when {
                    t.contactId > 0 -> st.contacts.byId(t.contactId)?.displayName() ?: ""
                    t.propertyId > 0 -> st.properties.byId(t.propertyId)?.displayTitle() ?: ""
                    else -> ""
                }
                val trailing: View = if (t.status == Task.STATUS_OPEN) {
                    Ui.icon(this@TasksActivity, R.drawable.ic_check, 22, p.success).apply {
                        setOnClickListener { complete(t) }
                    }
                } else {
                    Ui.icon(this@TasksActivity, R.drawable.ic_refresh, 18, p.textTertiary).apply {
                        setOnClickListener { reopen(t) }
                    }
                }
                Rows.taskRow(this@TasksActivity, t, related, {
                    startActivity(Intent(this@TasksActivity, TaskFormActivity::class.java).putExtra("id", t.id))
                }, trailing)
            }
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true)) {
                val empty = Ui.emptyState(this, R.drawable.ic_check, getString(R.string.task_no_tasks),
                    getString(R.string.task_no_tasks_hint), getString(R.string.qa_add_task)) {
                    startActivity(Intent(this, TaskFormActivity::class.java))
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

    private fun complete(t: Task) {
        Async.db({
            val next = Di.store.tasks.complete(t.id)
            Di.store.activities.add(
                com.estatedesk.crm.data.Activity.T_TASK, "Task completed", t.title,
                contactId = t.contactId, leadId = t.leadId, dealId = t.dealId, taskId = t.id
            )
            ReminderScheduler.cancel(this, t.id)
            next
        }) { next ->
            snack(getString(R.string.task_mark_done))
            if (next != null) ReminderScheduler.rescheduleTask(this, next)
            refresh()
        }
    }

    private fun reopen(t: Task) {
        Async.write({
            Di.store.tasks.reopen(t.id)
            val cur = Di.store.tasks.byId(t.id)
            if (cur != null) ReminderScheduler.rescheduleTask(this, cur)
        }) { refresh() }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }
}
