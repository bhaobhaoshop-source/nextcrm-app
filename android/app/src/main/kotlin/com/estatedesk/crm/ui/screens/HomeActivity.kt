package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.App
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.ui.widgets.BottomNav
import com.estatedesk.crm.ui.widgets.Rows
import java.util.Calendar

/** The dashboard — what needs attention today, plus the business at a glance. */
class HomeActivity : BaseActivity() {

    data class Dashboard(
        val followUps: Long = 0, val overdue: Long = 0, val meetings: Long = 0, val viewings: Long = 0,
        val newLeadsWeek: Long = 0, val totalLeads: Long = 0, val qualified: Long = 0,
        val activeListings: Long = 0, val activeDeals: Long = 0, val closed: Long = 0,
        val revenue: Long = 0, val pendingComm: Long = 0, val conversion: Int = 0,
        val stageCounts: List<Pair<String, Int>> = emptyList(),
        val recent: List<com.estatedesk.crm.data.Lead> = emptyList(),
        val currency: com.estatedesk.crm.data.CurrencyCfg = com.estatedesk.crm.data.CurrencyCfg()
    )

    private var loaded = false

    override fun build() {
        val tb = Ui.topBar(this, greeting(), subtitle = Di.store.profileName().ifBlank { getString(R.string.app_tagline) },
            back = false, actions = listOf(
                R.drawable.ic_search to { startActivity(Intent(this, SearchActivity::class.java)) },
                R.drawable.ic_settings to { startActivity(Intent(this, SettingsActivity::class.java)) }
            ))
        add(tb.root)
        add(Ui.spacer(this, 8))
        renderSkeleton()
        load()
        addFab { showAddMenu() }
        BottomNav.attach(this, root, 0)
    }

    private fun showAddMenu() {
        val items = listOf(
            Triple(R.string.qa_add_lead, R.drawable.ic_flag, { startActivity(Intent(this, LeadFormActivity::class.java)) }),
            Triple(R.string.qa_add_contact, R.drawable.ic_person, { startActivity(Intent(this, ContactFormActivity::class.java)) }),
            Triple(R.string.qa_add_property, R.drawable.ic_building, { startActivity(Intent(this, PropertyFormActivity::class.java)) }),
            Triple(R.string.qa_add_task, R.drawable.ic_check, { startActivity(Intent(this, TaskFormActivity::class.java)) }),
            Triple(R.string.qa_add_deal, R.drawable.ic_money, { startActivity(Intent(this, DealFormActivity::class.java)) }),
            Triple(R.string.qa_add_expense, R.drawable.ic_download, { startActivity(Intent(this, FinancesActivity::class.java).putExtra("add", "Expense")) })
        )
        val box = Ui.vbox(this)
        items.forEach { (label, icon, fn) ->
            val row = Ui.hbox(this)
            row.setPadding(Ui.dp(this@HomeActivity, 4), Ui.dp(this@HomeActivity, 13), Ui.dp(this@HomeActivity, 4), Ui.dp(this@HomeActivity, 13))
            row.addView(Ui.icon(this@HomeActivity, icon, 20, p.primary))
            row.addView(Ui.hspacer(this, 14))
            row.addView(Ui.tv(this@HomeActivity, getString(label), 15, p.textPrimary, Ui.Font.MEDIUM))
            row.setOnClickListener { fn() }
            box.addView(row)
        }
        Ui.sheet(this, getString(R.string.qa_title), box,
            listOf(getString(R.string.close) to Ui.Btn.SECONDARY)) { }.show()
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> getString(R.string.home_greeting_morning)
            h < 17 -> getString(R.string.home_greeting_afternoon)
            else -> getString(R.string.home_greeting_evening)
        }
    }

    private fun renderSkeleton() {
        for (i in 0 until 3) {
            val c = Ui.card(this, emptyList())
            val h = Ui.hbox(this)
            h.addView(placeholder(120, 16))
            h.addView(View(this), Ui.weightLps(1f))
            h.addView(placeholder(60, 16))
            c.addView(h)
            c.addView(Ui.spacer(this, 8))
            c.addView(placeholder(220, 12))
            add(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@HomeActivity, 12)
            })
        }
    }

    private fun placeholder(w: Int, h: Int): View = View(this).apply {
        setBackgroundColor(p.surface2)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(this@HomeActivity, w), Ui.dp(this@HomeActivity, h))
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val s = st.stats
            val (dayStart, dayEnd) = s.dayRange(0)
            Dashboard(
                s.followUpsDueToday(dayStart, dayEnd),
                s.overdueFollowUps(dayStart),
                s.meetingsToday(dayStart, dayEnd),
                s.viewingsToday(dayStart, dayEnd),
                s.newLeadsSince(dayStart - 6 * 86_400_000L),
                s.totalLeads(), s.qualifiedLeads(), s.activeListings(),
                s.activeDeals(), s.closedWon(), s.totalRevenue(), s.pendingCommission(),
                s.conversionRate(),
                s.pipelineDistribution().sortedByDescending { it.second }.take(4).map { it.first to it.second.toInt() },
                st.leads.recent(5),
                st.currency()
            )
        }) { d ->
            if (d == null || isFinishing) return@db
            content.removeAllViews()
            val tb = Ui.topBar(this, greeting(), subtitle = Di.store.profileName().ifBlank { getString(R.string.app_tagline) },
                back = false, actions = listOf(
                    R.drawable.ic_search to { startActivity(Intent(this, SearchActivity::class.java)) },
                    R.drawable.ic_settings to { startActivity(Intent(this, SettingsActivity::class.java)) }
                ))
            add(tb.root)
            renderLoaded(d)
        }
    }

    private fun renderLoaded(data: Dashboard) {

        // ---- Today
        val todayCard = Ui.card(this, emptyList())
        todayCard.addView(Ui.tv(this@HomeActivity, getString(R.string.home_today), 13, p.textTertiary, Ui.Font.MEDIUM))
        todayCard.addView(Ui.spacer(this, 10))
        val todayRow = Ui.hbox(this)
        todayRow.addView(stat(getString(R.string.home_followups_today), data.followUps.toString(), if (data.followUps > 0) p.primary else p.textPrimary), Ui.weightLps(1f))
        todayRow.addView(stat(getString(R.string.home_overdue), data.overdue.toString(), if (data.overdue > 0) p.danger else p.textPrimary), Ui.weightLps(1f))
        todayRow.addView(stat(getString(R.string.home_meetings_today), data.meetings.toString(), p.textPrimary), Ui.weightLps(1f))
        todayRow.addView(stat(getString(R.string.home_viewings_today), data.viewings.toString(), p.textPrimary), Ui.weightLps(1f))
        todayCard.addView(todayRow)
        if (data.followUps > 0 || data.overdue > 0) {
            todayCard.addView(Ui.spacer(this, 12))
            todayCard.addView(Ui.btn(this@HomeActivity, getString(R.string.home_followup_today), Ui.Btn.PRIMARY) {
                startActivity(Intent(this, FollowUpTodayActivity::class.java))
            })
        } else {
            todayCard.addView(Ui.spacer(this, 8))
            todayCard.addView(Ui.tv(this@HomeActivity, getString(R.string.home_no_events_hint), 12, p.textTertiary))
        }
        add(todayCard, lpm())
        add(Ui.spacer(this, 4))

        // ---- Overview
        add(Ui.sectionTitle(this, getString(R.string.home_overview)))
        val overview = Ui.card(this, emptyList(), padding = 14)
        val grid1 = Ui.hbox(this)
        grid1.addView(stat(getString(R.string.home_total_leads), data.totalLeads.toString(), p.textPrimary), Ui.weightLps(1f))
        grid1.addView(stat(getString(R.string.home_qualified_leads), data.qualified.toString(), p.success), Ui.weightLps(1f))
        grid1.addView(stat(getString(R.string.home_active_listings), data.activeListings.toString(), p.textPrimary), Ui.weightLps(1f))
        overview.addView(grid1)
        overview.addView(Ui.divider(this))
        Ui.margin(overview.getChildAt(overview.childCount - 1), 0, 12, 0, 12)
        val grid2 = Ui.hbox(this)
        grid2.addView(stat(getString(R.string.home_active_deals), data.activeDeals.toString(), p.textPrimary), Ui.weightLps(1f))
        grid2.addView(stat(getString(R.string.home_revenue), Fmt.moneyShort(data.revenue, data.currency), p.primary), Ui.weightLps(1f))
        grid2.addView(stat(getString(R.string.home_conversion), data.conversion.toString() + "%", p.textPrimary), Ui.weightLps(1f))
        overview.addView(grid2)
        overview.addView(Ui.divider(this))
        Ui.margin(overview.getChildAt(overview.childCount - 1), 0, 12, 0, 12)
        val grid3 = Ui.hbox(this)
        grid3.addView(stat(getString(R.string.home_pending_commission), Fmt.moneyShort(data.pendingComm, data.currency), if (data.pendingComm > 0) p.warning else p.textPrimary), Ui.weightLps(1f))
        grid3.addView(stat(getString(R.string.home_week_leads), data.newLeadsWeek.toString(), p.textPrimary), Ui.weightLps(1f))
        grid3.addView(stat(getString(R.string.home_closed_deals), data.closed.toString(), p.success), Ui.weightLps(1f))
        overview.addView(grid3)
        add(overview, lpm())
        add(Ui.spacer(this, 4))

        // ---- Pipeline snapshot
        if (data.stageCounts.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.home_pipeline), getString(R.string.more)) {
                startActivity(Intent(this, LeadsActivity::class.java).putExtra("view", "pipeline"))
            })
            val pipe = Ui.card(this, emptyList(), padding = 14)
            val max = (data.stageCounts.maxOfOrNull { it.second } ?: 1).toFloat()
            for ((stage, count) in data.stageCounts) {
                val row = Ui.hbox(this)
                row.addView(Ui.tv(this@HomeActivity, stage, 13, p.textSecondary, maxLines = 1),
                    LinearLayout.LayoutParams(Ui.dp(this@HomeActivity, 130), ViewGroup.LayoutParams.WRAP_CONTENT))
                val track = LinearLayout(this).apply {
                    setBackgroundColor(p.surface2)
                }
                val fill = View(this).apply {
                    setBackgroundColor(p.primary)
                }
                track.addView(fill, LinearLayout.LayoutParams(
                    (Ui.dp(this@HomeActivity, 90) * count / max).toInt(), Ui.dp(this@HomeActivity, 8)))
                row.addView(track, Ui.weightLps(1f))
                row.addView(Ui.hspacer(this, 10))
                row.addView(Ui.tv(this@HomeActivity, count.toString(), 13, p.textPrimary, Ui.Font.BOLD))
                Ui.margin(row, 0, 0, 0, 8)
                pipe.addView(row)
            }
            add(pipe, lpm())
            add(Ui.spacer(this, 4))
        }

        // ---- Recent leads
        add(Ui.sectionTitle(this, getString(R.string.home_recent_leads), getString(R.string.nav_leads)) {
            startActivity(Intent(this, LeadsActivity::class.java))
        })
        if (data.recent.isEmpty()) {
            val e = Ui.emptyState(this, R.drawable.ic_flag, getString(R.string.lead_no_leads),
                getString(R.string.lead_no_leads_hint), getString(R.string.qa_add_lead)) {
                startActivity(Intent(this, LeadFormActivity::class.java))
            }
            add(e)
        } else {
            val card = Ui.card(this, emptyList(), padding = 4)
            data.recent.forEach { l ->
                val c = Di.store.contacts.byId(l.contactId)
                card.addView(Rows.leadRow(this, l, c) {
                    startActivity(Intent(this, LeadDetailActivity::class.java).putExtra("id", l.id))
                })
                if (l != data.recent.last()) card.addView(Ui.divider(this))
            }
            add(card, lpm())
        }

        // ---- Quick access
        add(Ui.sectionTitle(this, getString(R.string.home_quick_links)))
        val quick = Ui.card(this, emptyList(), padding = 6)
        quick.addView(quickLink(getString(R.string.home_all_tasks), R.drawable.ic_check) {
            startActivity(Intent(this, TasksActivity::class.java))
        })
        quick.addView(quickLink(getString(R.string.home_analytics), R.drawable.ic_chart) {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        })
        quick.addView(quickLink(getString(R.string.home_finances), R.drawable.ic_money) {
            startActivity(Intent(this, FinancesActivity::class.java))
        })
        quick.addView(quickLink(getString(R.string.data_backup), R.drawable.ic_lock) {
            startActivity(Intent(this, BackupActivity::class.java))
        })
        add(quick, lpm())

        add(Ui.spacer(this, 76))
    }

    private fun lpm(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@HomeActivity, 12)
        }

    private fun quickLink(label: String, res: Int, fn: () -> Unit): LinearLayout {
        val row = Ui.hbox(this)
        row.setPadding(Ui.dp(this@HomeActivity, 10), Ui.dp(this@HomeActivity, 12), Ui.dp(this@HomeActivity, 10), Ui.dp(this@HomeActivity, 12))
        row.addView(Ui.icon(this@HomeActivity, res, 20, p.primary))
        row.addView(Ui.hspacer(this, 14))
        row.addView(Ui.tv(this@HomeActivity, label, 15, p.textPrimary, Ui.Font.MEDIUM))
        row.setOnClickListener { fn() }
        return row
    }

    private fun stat(label: String, value: String, color: Int): LinearLayout {
        val box = Ui.vbox(this)
        box.gravity = Gravity.CENTER
        val v = Ui.tv(this@HomeActivity, value, 19, color, Ui.Font.BOLD)
        v.gravity = Gravity.CENTER
        box.addView(v)
        val l = Ui.tv(this@HomeActivity, label, 11, p.textTertiary)
        l.gravity = Gravity.CENTER
        box.addView(l)
        return box
    }

    override fun onResume() {
        super.onResume()
        if (loaded) load()
        loaded = true
        // one-time permission prompts
        val prefs = Prefs(this)
        if (!prefs.bool(Prefs.KEY_ASKED_NOTIF_PERM, false)) {
            prefs.set(Prefs.KEY_ASKED_NOTIF_PERM, true)
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
            }
        }
    }
}
