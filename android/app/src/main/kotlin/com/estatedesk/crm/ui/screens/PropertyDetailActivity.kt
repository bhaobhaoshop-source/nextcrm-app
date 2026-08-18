package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Files
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.PropertyPhoto
import com.estatedesk.crm.domain.MatchingEngine
import com.estatedesk.crm.ui.widgets.Dialogs
import com.estatedesk.crm.ui.widgets.Rows

class PropertyDetailActivity : BaseActivity() {

    private var propertyId = 0L
    private var tab = 0
    private lateinit var prop: Property
    private var photos = mutableListOf<PropertyPhoto>()

    override fun build() {
        propertyId = intent.getLongExtra("id", 0)
        if (propertyId == 0L) {
            finish(); return
        }
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val p = st.properties.byId(propertyId) ?: return@db null
            val ph = st.properties.photos(propertyId)
            val engine = MatchingEngine()
            val matched = st.leads.recent(500).filter { l ->
                engine.match(l, listOf(p)).isNotEmpty()
            }
            data class D(val p: Property, val ph: List<PropertyPhoto>, val matched: List<com.estatedesk.crm.data.Lead>)
            D(p, ph, matched)
        }) { d ->
            if (d == null || isFinishing) return@db
            prop = d.p
            photos = d.ph.toMutableList()
            content.removeAllViews()
            renderHeader()
            renderTabs()
            when (tab) {
                0 -> renderInfo(d.matched)
                1 -> renderLeads(d.matched)
                2 -> renderTimeline()
            }
        }
    }

    private fun renderHeader() {
        // gallery
        if (photos.isNotEmpty()) {
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            photos.forEach { ph ->
                val img = ImageView(this)
                img.scaleType = ImageView.ScaleType.CENTER_CROP
                img.setBackgroundColor(p.surface2)
                img.layoutParams = LinearLayout.LayoutParams(
                    Ui.dp(this@PropertyDetailActivity, 220), Ui.dp(this@PropertyDetailActivity, 150))
                Async.io({
                    try {
                        val o = BitmapFactory.Options().apply { inSampleSize = 3 }
                        BitmapFactory.decodeFile(ph.path, o)
                    } catch (t: Throwable) { null }
                }) { bmp -> if (bmp != null) img.setImageBitmap(bmp) }
                val lp = LinearLayout.LayoutParams(
                    Ui.dp(this@PropertyDetailActivity, 220), Ui.dp(this@PropertyDetailActivity, 150))
                lp.rightMargin = Ui.dp(this@PropertyDetailActivity, 8)
                row.addView(img, lp)
            }
            scroll.addView(row)
            add(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@PropertyDetailActivity, 150)).apply {
                bottomMargin = Ui.dp(this@PropertyDetailActivity, 12)
            })
        }

        val head = Ui.hbox(this)
        val mid = Ui.vbox(this)
        mid.addView(Ui.tv(this@PropertyDetailActivity, prop.displayTitle(), 18, p.textPrimary, Ui.Font.BOLD, 2))
        val priceColor = if (prop.saleRent == "Rent") p.accent else p.primary
        mid.addView(Ui.tv(this@PropertyDetailActivity, "${Fmt.money(prop.price, Di.store.currency())} · ${prop.saleRent}",
            15, priceColor, Ui.Font.BOLD, 1))
        mid.addView(Ui.tv(this@PropertyDetailActivity, prop.areaName.ifBlank { prop.location }, 13, p.textSecondary, maxLines = 1))
        head.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.statusBadge(this, prop.status))
        add(head)
        add(Ui.spacer(this, 10))

        // quick actions
        val actions = Ui.card(this, emptyList(), padding = 10)
        fun actionRow(items: List<Triple<String, Int, () -> Unit>>): LinearLayout {
            val row = Ui.hbox(this)
            items.forEach { (label, icon, fn) ->
                val col = Ui.vbox(this)
                col.gravity = Gravity.CENTER
                col.setPadding(Ui.dp(this@PropertyDetailActivity, 4), Ui.dp(this@PropertyDetailActivity, 6), Ui.dp(this@PropertyDetailActivity, 4), Ui.dp(this@PropertyDetailActivity, 6))
                val box = LinearLayout(this).apply {
                    gravity = Gravity.CENTER
                    setPadding(Ui.dp(this@PropertyDetailActivity, 10), Ui.dp(this@PropertyDetailActivity, 10), Ui.dp(this@PropertyDetailActivity, 10), Ui.dp(this@PropertyDetailActivity, 10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(p.primarySoft)
                    }
                }
                box.addView(Ui.icon(this@PropertyDetailActivity, icon, 18, p.primary))
                col.addView(box)
                val t = Ui.tv(this@PropertyDetailActivity, label, 11, p.textSecondary, Ui.Font.MEDIUM, 1)
                t.gravity = Gravity.CENTER
                col.addView(t)
                col.setOnClickListener { fn() }
                row.addView(col, Ui.weightLps(1f))
            }
            return row
        }
        actions.addView(actionRow(listOf(
            Triple(getString(R.string.property_associate), R.drawable.ic_people, { associateLead() }),
            Triple(getString(R.string.lead_schedule_viewing), R.drawable.ic_eye, { scheduleViewing() }),
            Triple(getString(R.string.contact_add_note), R.drawable.ic_note, {
                Dialogs.addNote(this, property = prop, onDone = { load() })
            }),
            Triple(getString(R.string.property_compare), R.drawable.ic_swap, { addToCompare() })
        )))
        actions.addView(actionRow(listOf(
            Triple(getString(R.string.property_owner), R.drawable.ic_call, { callOwner() }),
            Triple(getString(R.string.property_owner_contact), R.drawable.ic_whatsapp, { whatsAppOwner() }),
            Triple(getString(R.string.docs_attach), R.drawable.ic_doc, { attachDoc() }),
            Triple(getString(R.string.edit), R.drawable.ic_edit, {
                startActivity(Intent(this, PropertyFormActivity::class.java).putExtra("id", propertyId))
            })
        )))
        add(actions)
        add(Ui.spacer(this, 8))
    }

    private fun associateLead() {
        Dialogs.pickLead(this, getString(R.string.property_associate)) { l ->
            Async.write({
                Di.store.activities.add(
                    com.estatedesk.crm.data.Activity.T_NOTE,
                    "Property recommended",
                    "${prop.displayTitle()} recommended for ${l.displayTitle()}",
                    leadId = l.id, propertyId = propertyId
                )
            }) { snack(getString(R.string.saved)) }
        }
    }

    private fun scheduleViewing() {
        Dialogs.pickContact(this, getString(R.string.viewing_client)) { c ->
            Dialogs.scheduleViewing(this, c, prop, onDone = { load() })
        }
    }

    private fun addToCompare() {
        val prefs = Prefs(this)
        val cur = prefs.str(Prefs.KEY_COMPARE_IDS, "").split(",")
            .filter { it.isNotBlank() }.toMutableSet()
        cur.add(propertyId.toString())
        prefs.set(Prefs.KEY_COMPARE_IDS, cur.take(4).joinToString(","))
        snack(getString(R.string.property_compare_added))
    }

    private fun callOwner() {
        if (prop.ownerPhone.isBlank()) {
            snack(getString(R.string.contact_no_phones)); return
        }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${prop.ownerPhone.trim()}")))
        } catch (t: Throwable) {
            snack(getString(R.string.error_generic))
        }
    }

    private fun whatsAppOwner() {
        if (prop.ownerPhone.isBlank()) {
            snack(getString(R.string.contact_no_phones)); return
        }
        val text = "Hello, I found your listing “${prop.displayTitle()}” on ${getString(R.string.app_name)}. " +
                "A client is interested — can we discuss?"
        Dialogs.openWhatsApp(this, prop.ownerPhone, text)
    }

    private fun attachDoc() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 21)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            Async.io({
                val f = Files.copyIn(this, uri, Files.documentsDir(this), "doc")
                val size = f?.length() ?: 0
                val name = try {
                    var n = "document"
                    contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) n = c.getString(idx) ?: n
                        }
                    }
                    n
                } catch (t: Throwable) { "document" }
                Triple(f, name, size)
            }) { t ->
                val (f, name, size) = t ?: return@io
                if (f == null) {
                    snack(getString(R.string.error_generic)); return@io
                }
                Async.write({
                    Di.store.docs.add("property", propertyId, name, f.absolutePath,
                        contentResolver.getType(uri) ?: "", size)
                }) {
                    snack(getString(R.string.docs_added))
                    load()
                }
            }
        }
    }

    private fun renderTabs() {
        add(Ui.chipRow(this, listOf(
            getString(R.string.property_info) to (tab == 0),
            getString(R.string.property_related_leads) to (tab == 1),
            getString(R.string.contact_timeline) to (tab == 2)
        )) { i ->
            tab = i
            load()
        })
        add(Ui.spacer(this, 8))
    }

    private fun renderInfo(matched: List<com.estatedesk.crm.data.Lead>) {
        val spec = mutableListOf<Pair<String, String>>()
        if (prop.type.isNotBlank()) spec.add(getString(R.string.property_type) to prop.type)
        spec.add(getString(R.string.property_price) to Fmt.money(prop.price, Di.store.currency()))
        if (prop.sizeValue > 0) spec.add(getString(R.string.property_size) to Fmt.sizeText(prop.sizeValue, prop.sizeUnit))
        if (prop.bedrooms > 0) spec.add(getString(R.string.property_bedrooms) to prop.bedrooms.toString())
        if (prop.bathrooms > 0) spec.add(getString(R.string.property_bathrooms) to prop.bathrooms.toString())
        if (prop.floors > 0) spec.add(getString(R.string.property_floors) to prop.floors.toString())
        if (prop.condition.isNotBlank()) spec.add(getString(R.string.property_condition) to prop.condition)
        spec.add(getString(R.string.property_furnished) to
            if (prop.furnished) getString(R.string.property_furnished) else getString(R.string.property_unfurnished))
        spec.add(getString(R.string.property_date_added) to Fmt.date(prop.dateAdded))
        spec.add(getString(R.string.property_last_updated) to Fmt.date(prop.updatedAt))
        add(Rows.kvCard(this, getString(R.string.property_info), spec))
        add(Ui.spacer(this, 8))

        if (prop.ownerName.isNotBlank() || prop.ownerPhone.isNotBlank()) {
            val owner = mutableListOf<Pair<String, String>>()
            if (prop.ownerName.isNotBlank()) owner.add(getString(R.string.property_owner) to prop.ownerName)
            if (prop.ownerPhone.isNotBlank()) owner.add(getString(R.string.property_owner_contact) to prop.ownerPhone)
            add(Rows.kvCard(this, getString(R.string.property_owner), owner))
            add(Ui.spacer(this, 8))
        }

        if (prop.description.isNotBlank()) {
            val card = Ui.card(this, emptyList())
            card.addView(Ui.tv(this@PropertyDetailActivity, getString(R.string.property_description), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 6))
            card.addView(Ui.tv(this@PropertyDetailActivity, prop.description, 14, p.textSecondary))
            add(card)
            add(Ui.spacer(this, 8))
        }

        if (prop.tagList().isNotEmpty()) {
            val card = Ui.card(this, emptyList(), padding = 12)
            card.addView(Ui.tv(this@PropertyDetailActivity, getString(R.string.property_tags), 13, p.textTertiary, Ui.Font.MEDIUM))
            card.addView(Ui.spacer(this, 8))
            val row = Ui.hbox(this)
            prop.tagList().take(6).forEach { t ->
                row.addView(Ui.badge(this@PropertyDetailActivity, t, p.primary, p.primarySoft))
                row.addView(Ui.hspacer(this, 6))
            }
            card.addView(row)
            add(card)
            add(Ui.spacer(this, 8))
        }

        // matched leads count summary
        if (matched.isNotEmpty()) {
            val card = Ui.card(this, emptyList(), padding = 14) { tab = 1; load() }
            val row = Ui.hbox(this)
            row.addView(Ui.icon(this@PropertyDetailActivity, R.drawable.ic_people, 20, p.primary))
            row.addView(Ui.hspacer(this, 10))
            row.addView(Ui.tv(this@PropertyDetailActivity, "${matched.size} " + getString(R.string.property_related_leads),
                14, p.textPrimary, Ui.Font.MEDIUM), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.icon(this@PropertyDetailActivity, R.drawable.ic_chevron, 16, p.textTertiary))
            card.addView(row)
            add(card)
            add(Ui.spacer(this, 8))
        }

        // documents
        val docs = Di.store.docs.forEntity("property", propertyId)
        if (docs.isNotEmpty()) {
            add(Ui.sectionTitle(this, getString(R.string.contact_documents)))
            val card = Ui.card(this, emptyList(), padding = 4)
            docs.forEach { d ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@PropertyDetailActivity, 10), Ui.dp(this@PropertyDetailActivity, 12), Ui.dp(this@PropertyDetailActivity, 10), Ui.dp(this@PropertyDetailActivity, 12))
                row.addView(Ui.icon(this@PropertyDetailActivity, R.drawable.ic_doc, 20, p.primary))
                row.addView(Ui.hspacer(this, 12))
                row.addView(Ui.tv(this@PropertyDetailActivity, d.name, 14, p.textPrimary, Ui.Font.MEDIUM, 1),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.setOnClickListener {
                    try {
                        startActivity(Files.openExternal(this,
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

    private fun renderLeads(matched: List<com.estatedesk.crm.data.Lead>) {
        if (matched.isEmpty()) {
            add(Ui.emptyState(this, R.drawable.ic_people, getString(R.string.property_no_leads),
                getString(R.string.property_no_leads), getString(R.string.property_associate)) { associateLead() })
            add(Ui.spacer(this, 48))
            return
        }
        val card = Ui.card(this, emptyList(), padding = 4)
        matched.take(15).forEach { l ->
            val score = MatchingEngine().match(l, listOf(prop)).firstOrNull()?.score ?: 0
            val c = Di.store.contacts.byId(l.contactId)
            card.addView(Rows.leadRow(this, l, c) {
                startActivity(Intent(this, LeadDetailActivity::class.java).putExtra("id", l.id))
            })
            if (l != matched.last()) card.addView(Ui.divider(this))
        }
        add(card)
        add(Ui.spacer(this, 48))
    }

    private fun renderTimeline() {
        Async.db({
            Di.store.activities.timelineFor(propertyId = propertyId, offset = 0, limit = 60)
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
        if (::prop.isInitialized) load()
    }
}
