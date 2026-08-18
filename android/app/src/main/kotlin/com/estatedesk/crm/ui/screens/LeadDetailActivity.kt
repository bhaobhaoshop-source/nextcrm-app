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
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.domain.LeadScorer
import com.estatedesk.crm.domain.MatchingEngine
import com.estatedesk.crm.domain.MessageTemplates
import com.estatedesk.crm.ui.widgets.Dialogs
import com.estatedesk.crm.ui.widgets.Rows

class LeadDetailActivity : BaseActivity() {

    private var leadId = 0L
    private var tab = 0
    private lateinit var lead: Lead
    private var contact: Contact? = null
    private var matches: List<MatchingEngine.MatchResult> = emptyList()
    private var stages: List<String> = emptyList()

    override fun build() {
        leadId = intent.getLongExtra("id", 0)
        if (leadId == 0L) {
            finish(); return
        }
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val l = st.leads.byId(leadId) ?: return@db null
            val c = st.contacts.byId(l.contactId)
            val phoneCount = c?.let { st.contacts.phones(it.id).size } ?: 0
            val stages = st.leadStages().map { it.name }
            val (score, _) = LeadScorer.score(l, c, phoneCount, stages)
            if (score != l.score) st.leads.setScore(leadId, score)
            data class D(
                val l: Lead, val c: Contact?, val matches: List<MatchingEngine.MatchResult>,
                val stages: List<String>, val phone: String
            )
            val allProps = st.properties.query("", "", "Available", "", "", 0, "", "newest", 0, 500)
            D(l.copy(score = score), c, MatchingEngine().match(l, allProps), stages,
                c?.let { st.contacts.phones(it.id).firstOrNull()?.number } ?: "")
        }) { d ->
            if (d == null || isFinishing) return@db
            lead = d.l
            contact = d.c
            matches = d.matches
            stages = d.stages
            content.removeAllViews()
            renderHeader(d.phone)
            renderTabs()
            when (tab) {
                0 -> renderOverview()
                1 -> renderMatches()
                2 -> renderTimeline()
            }
        }
    }

    private fun renderHeader(phone: String) {
        val head = Ui.hbox(this)
        val mid = Ui.vbox(this)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        mid.addView(Ui.tv(this@LeadDetailActivity, lead.displayTitle(), 18, p.textPrimary, Ui.Font.BOLD, 2))
        val meta = listOf(
            contact?.displayName() ?: "", lead.location, lead.propertyType, lead.intent
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) mid.addView(Ui.tv(this@LeadDetailActivity, meta, 13, p.textSecondary, maxLines = 2))
        val badges = Ui.hbox(this)
        badges.addView(Ui.statusBadge(this, lead.stage))
        badges.addView(Ui.hspacer(this, 6))
        badges.addView(Ui.priorityBadge(this, lead.priority))
        val lpb = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpb.topMargin = Ui.dp(this@LeadDetailActivity, 4)
        mid.addView(badges, lpb)
        head.addView(mid, lpMid)
        // score dial
        val scoreBox = Ui.vbox(this)
        scoreBox.gravity = Gravity.CENTER
        scoreBox.addView(Ui.tv(this@LeadDetailActivity, lead.score.toString(), 26,
            when {
                lead.score >= 75 -> p.success
                lead.score >= 50 -> p.primary
                else -> p.textSecondary
            }, Ui.Font.BOLD))
        scoreBox.addView(Ui.tv(this@LeadDetailActivity, getString(R.string.lead_score), 10, p.textTertiary))
        head.addView(scoreBox)
        add(head)
        add(Ui.spacer(this, 10))

        // quick actions
        val actionsCard = Ui.card(this, emptyList(), padding = 10)
        fun actionRow(items: List<Triple<String, Int, () -> Unit>>): LinearLayout {
            val row = Ui.hbox(this)
            items.forEach { (label, icon, fn) ->
                val col = Ui.vbox(this)
                col.gravity = Gravity.CENTER
                col.setPadding(Ui.dp(this@LeadDetailActivity, 4), Ui.dp(this@LeadDetailActivity, 6), Ui.dp(this@LeadDetailActivity, 4), Ui.dp(this@LeadDetailActivity, 6))
                val box = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(this@LeadDetailActivity, 10), Ui.dp(this@LeadDetailActivity, 10), Ui.dp(this@LeadDetailActivity, 10), Ui.dp(this@LeadDetailActivity, 10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(p.primarySoft)
                    }
                }
                box.addView(Ui.icon(this@LeadDetailActivity, icon, 18, p.primary))
                col.addView(box)
                val t = Ui.tv(this@LeadDetailActivity, label, 11, p.textSecondary, Ui.Font.MEDIUM, 1)
                t.gravity = Gravity.CENTER
                col.addView(t)
                col.setOnClickListener { fn() }
                row.addView(col, Ui.weightLps(1f))
            }
            return row
        }
        val c = contact
        actionsCard.addView(actionRow(listOf(
            Triple(getString(R.string.contact_call), R.drawable.ic_call, { callLead(phone) }),
            Triple(getString(R.string.contact_whatsapp), R.drawable.ic_whatsapp, { msgLead(phone) }),
            Triple(getString(R.string.contact_add_note), R.drawable.ic_note, {
                Dialogs.addNote(this, contact = c, lead = lead, onDone = { load() })
            }),
            Triple(getString(R.string.contact_schedule_followup), R.drawable.ic_history, {
                Dialogs.scheduleFollowUp(this, contact = c, lead = lead, onDone = { load() })
            })
        )))
        actionsCard.addView(actionRow(listOf(
            Triple(getString(R.string.lead_schedule_viewing), R.drawable.ic_eye, { scheduleViewing() }),
            Triple(getString(R.string.lead_matches), R.drawable.ic_building, { tab = 1; load() }),
            Triple(getString(R.string.lead_convert_deal), R.drawable.ic_money, { convertToDeal() }),
            Triple(getString(R.string.edit), R.drawable.ic_edit, {
                startActivity(Intent(this, LeadFormActivity::class.java).putExtra("id", leadId))
            })
        )))
        add(actionsCard)
        add(Ui.spacer(this, 8))

        // pipeline stepper
        val stepCard = Ui.card(this, emptyList(), padding = 14)
        stepCard.addView(Ui.tv(this@LeadDetailActivity, getString(R.string.lead_stage), 13, p.textTertiary, Ui.Font.MEDIUM))
        stepCard.addView(Ui.spacer(this, 10))
        val stepRow = Ui.hbox(this)
        stepRow.gravity = Gravity.CENTER_VERTICAL
        val idx = stages.indexOf(lead.stage)
        stages.forEachIndexed { i, s ->
            val dot = LinearLayout(this).apply {
                setPadding(Ui.dp(this@LeadDetailActivity, 2), 0, Ui.dp(this@LeadDetailActivity, 2), 0)
            }
            val inner = View(this).apply {
                val size = Ui.dp(this@LeadDetailActivity, if (i == idx) 14 else 10)
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(when {
                        i == idx -> p.primary
                        i < idx -> p.success
                        else -> p.chipBg
                    })
                }
            }
            dot.addView(inner)
            dot.setOnClickListener { changeStage(s) }
            stepRow.addView(dot)
            if (i < stages.size - 1) {
                val line = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@LeadDetailActivity, 8), Ui.dp(this@LeadDetailActivity, 2))
                    setBackgroundColor(if (i < idx) p.success else p.chipBg)
                }
                stepRow.addView(line)
            }
        }
        stepCard.addView(stepRow)
        stepCard.addView(Ui.spacer(this, 8))
        stepCard.addView(Ui.tv(this@LeadDetailActivity, lead.stage, 15, p.textPrimary, Ui.Font.MEDIUM))
        val prob = lead.probability
        stepCard.addView(Ui.tv(this@LeadDetailActivity, "${getString(R.string.lead_probability)}: $prob%", 12, p.textTertiary))
        add(stepCard)
        add(Ui.spacer(this, 8))
    }

    private fun changeStage(s: String) {
        if (s == lead.stage) return
        Async.write({
            val st = Di.store
            st.leads.setStage(leadId, s)
            st.activities.addStatusChange("Lead", lead.displayTitle(), lead.stage, s,
                contactId = lead.contactId, leadId = leadId)
            if (s == "Closed Won" && lead.dealId == 0L) {
                // prompt happens on UI side
            }
        }, { load() })
    }

    private fun callLead(phone: String) {
        if (phone.isBlank()) {
            snack(getString(R.string.contact_no_phones)); return
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 7)
            return
        }
        val c = contact
        try {
            startActivity(Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:${phone.trim()}")))
        } catch (t: Throwable) {
            startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${phone.trim()}")))
        }
        if (c != null) {
            Async.ui(600) { Dialogs.logCall(this, c, phone, lead = lead, onDone = { load() }) }
        } else {
            Async.ui(600) {
                Ui.prompt(this, getString(R.string.activity_log_note_title),
                    getString(R.string.activity_log_note_hint), multiline = true) { text ->
                    if (text.isNotBlank()) {
                        Async.write({
                            Di.store.activities.add(com.estatedesk.crm.data.Activity.T_CALL,
                                "Call", text, leadId = leadId)
                        })
                        snack(getString(R.string.saved))
                        load()
                    }
                }
            }
        }
    }

    private fun msgLead(phone: String) {
        val c = contact
        if (c != null && phone.isNotBlank()) {
            val text = MessageTemplates.followUp(c, lead, Di.store.profileName(), Di.store.profileCompany())
            Dialogs.messageSheet(this, c, text, phone, c.email)
        } else {
            snack(getString(R.string.contact_no_phones))
        }
    }

    private fun scheduleViewing() {
        val c = contact
        if (c == null) {
            snack(getString(R.string.lead_contact)); return
        }
        if (matches.isEmpty()) {
            Dialogs.pickProperty(this, getString(R.string.lead_schedule_viewing)) { prop ->
                Dialogs.scheduleViewing(this, c, prop, lead = lead, onDone = { load() })
            }
        } else {
            val options = matches.map { m ->
                "${m.property.displayTitle()} · ${Fmt.money(m.property.price, Di.store.currency())}"
            }
            Ui.pick(this, getString(R.string.viewing_property), options) { i ->
                Dialogs.scheduleViewing(this, c, matches[i].property, lead = lead, onDone = { load() })
            }
        }
    }

    private fun convertToDeal() {
        startActivity(Intent(this, DealFormActivity::class.java)
            .putExtra("lead_id", leadId)
            .putExtra("contact_id", lead.contactId)
            .putExtra("property_id", matches.firstOrNull()?.property?.id ?: 0)
            .putExtra("title", lead.displayTitle()))
    }

    private fun renderTabs() {
        add(Ui.chipRow(this, listOf(
            getString(R.string.contact_overview) to (tab == 0),
            getString(R.string.lead_matches) to (tab == 1),
            getString(R.string.contact_timeline) to (tab == 2)
        )) { i ->
            tab = i
            load()
        })
        add(Ui.spacer(this, 8))
    }

    private fun renderOverview() {
        // requirement
        val req = mutableListOf<Pair<String, String>>()
        if (lead.requirement.isNotBlank()) req.add(getString(R.string.lead_requirement) to lead.requirement)
        if (lead.budgetMax > 0) req.add(getString(R.string.lead_budget) to Fmt.money(lead.budgetMax, Di.store.currency()))
        if (lead.location.isNotBlank()) req.add(getString(R.string.lead_location) to lead.location)
        if (lead.propertyType.isNotBlank()) req.add(getString(R.string.lead_property_type) to lead.propertyType)
        if (lead.source.isNotBlank()) req.add(getString(R.string.lead_source) to lead.source)
        if (lead.nextFollowUp > 0) req.add(getString(R.string.lead_next_followup) to Fmt.friendlyDateTime(lead.nextFollowUp))
        if (lead.nextAction.isNotBlank()) req.add(getString(R.string.lead_next_action) to lead.nextAction)
        req.add(getString(R.string.contact_created) to Fmt.date(lead.createdAt))
        if (req.isNotEmpty()) {
            add(Rows.kvCard(this, getString(R.string.lead_requirement), req))
            add(Ui.spacer(this, 8))
        }

        // suggestions (rule-based, from real data)
        val sug = MessageTemplates.suggestions(Di.store, lead, contact)
        if (sug.isNotEmpty()) {
            val card = Ui.card(this, emptyList())
            card.addView(Ui.tv(this@LeadDetailActivity, getString(R.string.lead_suggestions), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 8))
            sug.take(4).forEach { s ->
                val row = Ui.hbox(this)
                row.addView(Ui.icon(this@LeadDetailActivity, R.drawable.ic_star, 14, p.accent))
                row.addView(Ui.hspacer(this, 8))
                row.addView(Ui.tv(this@LeadDetailActivity, s, 13, p.textSecondary))
                Ui.margin(row, 0, 0, 0, 6)
                card.addView(row)
            }
            add(card)
            add(Ui.spacer(this, 8))
        }

        // message templates
        if (contact != null) {
            val card = Ui.card(this, emptyList(), padding = 12) { openMessages() }
            val row = Ui.hbox(this)
            row.addView(Ui.icon(this@LeadDetailActivity, R.drawable.ic_whatsapp, 20, p.success))
            row.addView(Ui.hspacer(this, 10))
            row.addView(Ui.tv(this@LeadDetailActivity, getString(R.string.lead_msg_template), 14, p.textPrimary, Ui.Font.MEDIUM),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.icon(this@LeadDetailActivity, R.drawable.ic_chevron, 16, p.textTertiary))
            card.addView(row)
            add(card)
            add(Ui.spacer(this, 8))
        }

        // top matches preview
        if (matches.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.lead_matches), getString(R.string.show_more)) {
                tab = 1; load()
            })
            add(Rows.matchRow(this, matches.first().property, matches.first().score,
                matches.first().criteria.filter { it.verdict != MatchingEngine.Verdict.UNKNOWN }
                    .map { c2 ->
                        when (c2.verdict) {
                            MatchingEngine.Verdict.YES -> "✓ ${c2.label}"
                            MatchingEngine.Verdict.PARTIAL -> "△ ${c2.label}"
                            else -> "✗ ${c2.label}"
                        }
                    }) {
                startActivity(Intent(this, PropertyDetailActivity::class.java)
                    .putExtra("id", matches.first().property.id))
            })
            add(Ui.spacer(this, 8))
        }

        // notes
        if (lead.notes.isNotBlank()) {
            val card = Ui.card(this, emptyList())
            card.addView(Ui.tv(this@LeadDetailActivity, getString(R.string.lead_notes), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 6))
            card.addView(Ui.tv(this@LeadDetailActivity, lead.notes, 14, p.textSecondary))
            add(card)
        }
        add(Ui.spacer(this, 48))
    }

    private fun openMessages() {
        val c = contact ?: return
        val agent = Di.store.profileName()
        val company = Di.store.profileCompany()
        val phone = Di.store.contacts.phones(c.id).firstOrNull()?.number ?: ""
        val texts = listOf(
            MessageTemplates.followUp(c, lead, agent, company),
            if (matches.isNotEmpty()) MessageTemplates.propertyIntro(c, matches.first().property, agent, company, Di.store.currency()) else null,
            MessageTemplates.genericCheckIn(c, agent, company)
        ).filterNotNull()
        val box = Ui.vbox(this)
        texts.forEach { t ->
            val card = Ui.card(this, emptyList(), padding = 12) {
                Dialogs.messageSheet(this, c, t, phone, c.email)
            }
            card.addView(Ui.tv(this@LeadDetailActivity, t, 13, p.textSecondary, maxLines = 4))
            box.addView(card)
            box.addView(Ui.spacer(this, 8))
        }
        Ui.sheet(this, getString(R.string.lead_msg_template), box,
            listOf(getString(R.string.close) to Ui.Btn.SECONDARY)) { }.show()
    }

    private fun renderMatches() {
        if (matches.isEmpty()) {
            add(Ui.emptyState(this, R.drawable.ic_building, getString(R.string.lead_no_matches),
                getString(R.string.lead_no_matches_hint), getString(R.string.qa_add_property)) {
                startActivity(Intent(this, PropertyFormActivity::class.java))
            })
            add(Ui.spacer(this, 48))
            return
        }
        add(Ui.caption(this@LeadDetailActivity, "${matches.size} ${getString(R.string.lead_matches)}"))
        add(Ui.spacer(this, 8))
        matches.take(20).forEach { m ->
            val reasons = m.criteria.filter { it.verdict != MatchingEngine.Verdict.UNKNOWN }
                .map { c2 ->
                    val mark = when (c2.verdict) {
                        MatchingEngine.Verdict.YES -> "✓"
                        MatchingEngine.Verdict.PARTIAL -> "△"
                        else -> "✗"
                    }
                    "$mark ${c2.label}"
                }
            val card = Rows.matchRow(this, m.property, m.score, reasons) {
                openMatchActions(m.property)
            }
            add(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@LeadDetailActivity, 10)
            })
        }
        add(Ui.spacer(this, 40))
    }

    private fun openMatchActions(prop: Property) {
        val c = contact
        val box = Ui.vbox(this)
        fun act(label: String, icon: Int, fn: () -> Unit): LinearLayout {
            val row = Ui.hbox(this)
            row.setPadding(Ui.dp(this@LeadDetailActivity, 4), Ui.dp(this@LeadDetailActivity, 13), Ui.dp(this@LeadDetailActivity, 4), Ui.dp(this@LeadDetailActivity, 13))
            row.addView(Ui.icon(this@LeadDetailActivity, icon, 20, p.primary))
            row.addView(Ui.hspacer(this, 14))
            row.addView(Ui.tv(this@LeadDetailActivity, label, 15, p.textPrimary, Ui.Font.MEDIUM))
            row.setOnClickListener { fn() }
            return row
        }
        box.addView(act(getString(R.string.view), R.drawable.ic_eye) {
            startActivity(Intent(this, PropertyDetailActivity::class.java).putExtra("id", prop.id))
        })
        if (c != null) {
            box.addView(act(getString(R.string.lead_schedule_viewing), R.drawable.ic_calendar) {
                Dialogs.scheduleViewing(this, c, prop, lead = lead, onDone = { load() })
            })
            box.addView(act(getString(R.string.msg_property_intro), R.drawable.ic_whatsapp) {
                val phone = Di.store.contacts.phones(c.id).firstOrNull()?.number ?: ""
                val text = MessageTemplates.propertyIntro(c, prop, Di.store.profileName(),
                    Di.store.profileCompany(), Di.store.currency())
                Dialogs.messageSheet(this, c, text, phone, c.email)
            })
        }
        Ui.sheet(this, prop.displayTitle(), box,
            listOf(getString(R.string.close) to Ui.Btn.SECONDARY)) { }.show()
    }

    private fun renderTimeline() {
        Async.db({
            Di.store.activities.timelineFor(leadId = leadId, offset = 0, limit = 60)
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
        if (::lead.isInitialized) load()
    }
}
