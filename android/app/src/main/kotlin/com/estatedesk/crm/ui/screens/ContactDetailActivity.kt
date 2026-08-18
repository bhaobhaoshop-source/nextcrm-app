package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.estatedesk.crm.data.Document
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Phone
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.ui.widgets.Dialogs
import com.estatedesk.crm.ui.widgets.Rows

class ContactDetailActivity : BaseActivity() {

    private var contactId = 0L
    private var tab = 0 // 0 overview, 1 timeline, 2 related
    private lateinit var c: Contact
    private lateinit var phones: List<Phone>
    private var customValues: Map<String, String> = emptyMap()

    override fun build() {
        contactId = intent.getLongExtra("id", 0)
        if (contactId == 0L) {
            finish(); return
        }
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val c = st.contacts.byId(contactId) ?: return@db null
            data class D(
                val c: Contact, val phones: List<Phone>, val tags: List<String>,
                val leads: List<Lead>, val props: List<Property>,
                val custom: Map<String, String>
            )
            D(c, st.contacts.phones(contactId), st.contacts.tags(contactId),
                st.leads.byContact(contactId),
                st.properties.byOwnerPhone(st.contacts.phones(contactId).firstOrNull()?.number ?: ""),
                st.custom.values("contact", contactId))
        }) { d ->
            if (d == null || isFinishing) return@db
            c = d.c
            phones = d.phones
            customValues = d.custom
            content.removeAllViews()
            renderHeader()
            renderTabs()
            renderTab(d.leads, d.props, d.tags)
        }
    }

    private fun renderHeader() {
        val head = Ui.hbox(this)
        head.addView(Ui.avatar(this, c.displayName(), 52))
        val mid = Ui.vbox(this)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpMid.leftMargin = Ui.dp(this@ContactDetailActivity, 12)
        mid.addView(Ui.tv(this@ContactDetailActivity, c.displayName(), 19, p.textPrimary, Ui.Font.BOLD))
        val meta = listOf(c.classification, c.city, c.area).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) mid.addView(Ui.tv(this@ContactDetailActivity, meta, 13, p.textSecondary, maxLines = 1))
        val badges = Ui.hbox(this)
        badges.addView(Ui.statusBadge(this, c.leadStatus))
        badges.addView(Ui.hspacer(this, 6))
        badges.addView(Ui.badge(this@ContactDetailActivity, c.temperature, when (c.temperature.lowercase()) {
            "hot" -> p.danger; "warm" -> p.warning; else -> p.cold
        }, when (c.temperature.lowercase()) {
            "hot" -> p.dangerSoft; "warm" -> p.warningSoft; else -> p.infoSoft
        }))
        badges.addView(Ui.hspacer(this, 6))
        badges.addView(Ui.priorityBadge(this, c.priority))
        val lpb = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpb.topMargin = Ui.dp(this@ContactDetailActivity, 4)
        mid.addView(badges, lpb)
        head.addView(mid, lpMid)
        val edit = Ui.icon(this@ContactDetailActivity, R.drawable.ic_edit, 20, p.primary)
        Ui.pad(edit, 10, 10, 4, 10)
        edit.setOnClickListener {
            startActivity(Intent(this, ContactFormActivity::class.java).putExtra("id", contactId))
        }
        head.addView(edit)
        add(head)

        // Quick actions — two rows of 4
        add(Ui.spacer(this, 12))
        val actionsCard = Ui.card(this, emptyList(), padding = 10)
        fun actionRow(items: List<Triple<String, Int, () -> Unit>>): LinearLayout {
            val row = Ui.hbox(this)
            items.forEach { (label, icon, fn) ->
                val col = Ui.vbox(this)
                col.gravity = Gravity.CENTER
                col.setPadding(Ui.dp(this@ContactDetailActivity, 4), Ui.dp(this@ContactDetailActivity, 6), Ui.dp(this@ContactDetailActivity, 4), Ui.dp(this@ContactDetailActivity, 6))
                val box = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(p.primarySoft)
                    }
                }
                box.addView(Ui.icon(this@ContactDetailActivity, icon, 18, p.primary))
                col.addView(box)
                val t = Ui.tv(this@ContactDetailActivity, label, 11, p.textSecondary, Ui.Font.MEDIUM, 1)
                t.gravity = Gravity.CENTER
                col.addView(t)
                col.setOnClickListener { fn() }
                row.addView(col, Ui.weightLps(1f))
            }
            return row
        }
        val mainPhone = phones.firstOrNull()
        val phoneNumber = mainPhone?.number ?: ""
        actionsCard.addView(actionRow(listOf(
            Triple(getString(R.string.contact_call), R.drawable.ic_call, { call() }),
            Triple(getString(R.string.contact_whatsapp), R.drawable.ic_whatsapp, { whatsApp() }),
            Triple(getString(R.string.contact_sms), R.drawable.ic_sms, { sms() }),
            Triple(getString(R.string.contact_email_action), R.drawable.ic_email, { email() })
        )))
        actionsCard.addView(actionRow(listOf(
            Triple(getString(R.string.contact_add_note), R.drawable.ic_note, {
                Dialogs.addNote(this, contact = c, onDone = { load() })
            }),
            Triple(getString(R.string.contact_schedule_followup), R.drawable.ic_history, {
                Dialogs.scheduleFollowUp(this, contact = c, onDone = { load() })
            }),
            Triple(getString(R.string.contact_create_task), R.drawable.ic_check, {
                startActivity(Intent(this, TaskFormActivity::class.java).putExtra("contact_id", contactId))
            }),
            Triple(getString(R.string.contact_create_lead), R.drawable.ic_flag, {
                startActivity(Intent(this, LeadFormActivity::class.java).putExtra("contact_id", contactId))
            })
        )))
        add(actionsCard)
        add(Ui.spacer(this, 8))
    }

    private fun call() {
        val phone = phones.firstOrNull()?.number ?: return
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 7)
            return
        }
        Async.write({ Di.store.contacts.touchContact(contactId) })
        try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:${phone.trim()}"))
            startActivity(i)
        } catch (t: Throwable) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
        }
    }

    private fun whatsApp() {
        val phone = phones.firstOrNull { it.isWhatsapp } ?: phones.firstOrNull() ?: return
        val agent = Di.store.profileName()
        val company = Di.store.profileCompany()
        val text = com.estatedesk.crm.domain.MessageTemplates.genericCheckIn(c, agent, company)
        Dialogs.messageSheet(this, c, text, phone.number, c.email)
    }

    private fun sms() {
        val phone = phones.firstOrNull()?.number ?: return
        val agent = Di.store.profileName()
        val text = com.estatedesk.crm.domain.MessageTemplates.genericCheckIn(c, agent, Di.store.profileCompany())
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}"))
        i.putExtra("sms_body", text)
        startActivity(i)
    }

    private fun email() {
        if (c.email.isBlank()) {
            snack(getString(R.string.contact_no_phones)); return
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email}"))
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        startActivity(i)
    }

    private fun renderTabs() {
        val tabs = Ui.chipRow(this, listOf(
            getString(R.string.contact_overview) to (tab == 0),
            getString(R.string.contact_timeline) to (tab == 1),
            getString(R.string.contact_related) to (tab == 2)
        )) { i ->
            tab = i
            load()
        }
        add(tabs)
        add(Ui.spacer(this, 8))
    }

    private fun renderTab(leads: List<Lead>, props: List<Property>, tags: List<String>) {
        when (tab) {
            0 -> renderOverview(leads, props, tags)
            1 -> renderTimeline()
            2 -> renderRelated(leads, props)
        }
    }

    private fun renderOverview(leads: List<Lead>, props: List<Property>, tags: List<String>) {
        // contact details
        val details = mutableListOf<Pair<String, String>>()
        phones.forEach { details.add(("${it.label}${if (it.isWhatsapp) " · WhatsApp" else ""}") to it.number) }
        if (c.email.isNotBlank()) details.add("Email" to c.email)
        if (c.address.isNotBlank()) details.add(getString(R.string.contact_address) to c.address)
        if (c.leadSource.isNotBlank()) details.add(getString(R.string.contact_lead_source) to c.leadSource)
        if (c.lastContacted > 0) details.add(getString(R.string.contact_last_contacted) to Fmt.dateTime(c.lastContacted))
        if (c.nextFollowUp > 0) details.add(getString(R.string.contact_next_followup) to Fmt.friendlyDateTime(c.nextFollowUp))
        details.add(getString(R.string.contact_created) to Fmt.date(c.createdAt))
        add(Rows.kvCard(this, getString(R.string.contact_full_name), details))
        add(Ui.spacer(this, 8))

        // requirements
        if (c.budgetMax > 0 || c.preferredLocation.isNotBlank() || c.preferredType.isNotBlank()) {
            val req = mutableListOf<Pair<String, String>>()
            if (c.budgetMax > 0) req.add(getString(R.string.contact_budget) to Fmt.money(c.budgetMax, Di.store.currency()))
            if (c.preferredLocation.isNotBlank()) req.add(getString(R.string.contact_preferred_location) to c.preferredLocation)
            if (c.preferredType.isNotBlank()) req.add(getString(R.string.contact_preferred_type) to c.preferredType)
            add(Rows.kvCard(this, getString(R.string.contact_requirements), req))
            add(Ui.spacer(this, 8))
        }

        // tags
        if (tags.isNotEmpty()) {
            val card = Ui.card(this, emptyList(), padding = 12)
            card.addView(Ui.tv(this@ContactDetailActivity, getString(R.string.contact_tags), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 8))
            val row = Ui.hbox(this)
            tags.take(6).forEach { t ->
                row.addView(Ui.badge(this@ContactDetailActivity, t, p.primary, p.primarySoft))
                row.addView(Ui.hspacer(this, 6))
            }
            card.addView(row)
            add(card)
            add(Ui.spacer(this, 8))
        }

        // custom fields
        val defs = Di.store.custom.defsFor("contact")
        if (defs.isNotEmpty()) {
            val pairs = defs.mapNotNull { d ->
                val v = customValues[d.key] ?: return@mapNotNull null
                d.label to v
            }
            if (pairs.isNotEmpty()) {
                add(Rows.kvCard(this, getString(R.string.contact_custom_fields), pairs))
                add(Ui.spacer(this, 8))
            }
        }

        // notes
        if (c.notes.isNotBlank()) {
            val card = Ui.card(this, emptyList())
            card.addView(Ui.tv(this@ContactDetailActivity, getString(R.string.contact_notes), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 6))
            card.addView(Ui.tv(this@ContactDetailActivity, c.notes, 14, p.textSecondary))
            add(card)
            add(Ui.spacer(this, 8))
        }

        // documents
        val docs = Di.store.docs.forEntity("contact", contactId)
        if (docs.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.contact_documents)))
            val card = Ui.card(this, emptyList(), padding = 4)
            docs.forEach { d ->
                card.addView(docRow(d))
                if (d != docs.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
        add(Ui.spacer(this, 48))
    }

    private fun docRow(d: Document): LinearLayout {
        val row = Ui.hbox(this)
        row.setPadding(Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 12), Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 12))
        row.addView(Ui.icon(this@ContactDetailActivity, R.drawable.ic_doc, 20, p.primary))
        row.addView(Ui.hspacer(this, 12))
        row.addView(Ui.tv(this@ContactDetailActivity, d.name, 14, p.textPrimary, Ui.Font.MEDIUM, 1),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.tv(this@ContactDetailActivity, Fmt.date(d.createdAt), 11, p.textTertiary))
        row.setOnClickListener { openDoc(d) }
        return row
    }

    private fun openDoc(d: Document) {
        val uri = com.estatedesk.crm.core.FilesProvider.uriFor("documents/${java.io.File(d.path).name}")
        try {
            startActivity(com.estatedesk.crm.core.Files.openExternal(this, uri))
        } catch (t: Throwable) {
            snack(getString(R.string.docs_open_failed))
        }
    }

    private fun renderTimeline() {
        Async.db({
            Di.store.activities.timelineFor(contactId = contactId, offset = 0, limit = 60)
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

    private fun renderRelated(leads: List<Lead>, props: List<Property>) {
        // leads
        add(Ui.sectionTitle(this, getString(R.string.contact_leads), getString(R.string.qa_add_lead)) {
            startActivity(Intent(this, LeadFormActivity::class.java).putExtra("contact_id", contactId))
        })
        if (leads.isEmpty()) {
            add(Ui.tv(this@ContactDetailActivity, getString(R.string.lead_no_leads), 13, p.textTertiary))
        } else {
            val card = Ui.card(this, emptyList(), padding = 4)
            leads.take(8).forEach { l ->
                card.addView(Rows.leadRow(this, l, c) {
                    startActivity(Intent(this, LeadDetailActivity::class.java).putExtra("id", l.id))
                })
                if (l != leads.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
        add(Ui.spacer(this, 12))
        // properties owned
        add(Ui.sectionTitle(this, getString(R.string.contact_properties)))
        if (props.isEmpty()) {
            add(Ui.tv(this@ContactDetailActivity, getString(R.string.property_no_props), 13, p.textTertiary))
        } else {
            val card = Ui.card(this, emptyList(), padding = 4)
            props.take(8).forEach { pr ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 12), Ui.dp(this@ContactDetailActivity, 10), Ui.dp(this@ContactDetailActivity, 12))
                row.addView(Ui.tv(this@ContactDetailActivity, pr.displayTitle(), 14, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.statusBadge(this, pr.status))
                row.setOnClickListener {
                    startActivity(Intent(this, PropertyDetailActivity::class.java).putExtra("id", pr.id))
                }
                card.addView(row)
                if (pr != props.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
        add(Ui.spacer(this, 48))
    }

    override fun onResume() {
        super.onResume()
        if (::c.isInitialized) load()
    }
}
