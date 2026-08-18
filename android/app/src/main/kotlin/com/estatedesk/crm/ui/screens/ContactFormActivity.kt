package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Phone

class ContactFormActivity : BaseActivity() {

    private var contactId = 0L
    private lateinit var first: EditText
    private lateinit var last: EditText
    private lateinit var email: EditText
    private lateinit var city: EditText
    private lateinit var area: EditText
    private lateinit var address: EditText
    private lateinit var budgetMin: EditText
    private lateinit var budgetMax: EditText
    private lateinit var prefLocation: EditText
    private lateinit var prefType: EditText
    private lateinit var notes: EditText
    private var classification = "Buyer"
    private var status = "New"
    private var temperature = "Warm"
    private var priority = 1
    private var source = ""
    private var assignedTo = ""
    private val phones = mutableListOf<Phone>()
    private var selectedTags = mutableListOf<String>()
    private val phoneRows = LinearLayout(this)

    override fun build() {
        contactId = intent.getLongExtra("id", 0)
        topBar(if (contactId == 0L) getString(R.string.contact_new) else getString(R.string.contact_edit))

        if (contactId > 0) {
            loadExisting()
        } else {
            render()
        }
    }

    private fun loadExisting() {
        Async.db({
            val c = Di.store.contacts.byId(contactId) ?: return@db null
            val p = Di.store.contacts.phones(contactId)
            val t = Di.store.contacts.tags(contactId)
            Triple(c, p, t)
        }) { t ->
            if (t == null || isFinishing) return@db
            val (c, p, tags) = t
            phones.clear(); phones.addAll(p)
            selectedTags = tags.toMutableList()
            render()
            first.setText(c.firstName); last.setText(c.lastName)
            email.setText(c.email); city.setText(c.city); area.setText(c.area); address.setText(c.address)
            budgetMin.setText(if (c.budgetMin > 0) c.budgetMin.toString() else "")
            budgetMax.setText(if (c.budgetMax > 0) c.budgetMax.toString() else "")
            prefLocation.setText(c.preferredLocation); prefType.setText(c.preferredType)
            notes.setText(c.notes)
            classification = c.classification; status = c.leadStatus
            temperature = c.temperature; priority = c.priority; source = c.leadSource
            assignedTo = c.assignedTo
            rebuildPhoneRows()
        }
    }

    private fun render() {
        first = Ui.input(this@ContactFormActivity, getString(R.string.contact_first_name))
        last = Ui.input(this@ContactFormActivity, getString(R.string.contact_last_name))
        val nameRow = Ui.hbox(this)
        nameRow.addView(Ui.field(this, getString(R.string.contact_first_name), first),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@ContactFormActivity, 8) })
        nameRow.addView(Ui.field(this, getString(R.string.contact_last_name), last),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(nameRow)

        // phones
        add(Ui.label(this@ContactFormActivity, getString(R.string.contact_phone)))
        add(phoneRows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val addPhone = Ui.btn(this@ContactFormActivity, "+ " + getString(R.string.contact_add_phone), Ui.Btn.SECONDARY) {
            phones.add(Phone(0, 0, "Mobile", "", false))
            rebuildPhoneRows()
        }
        add(addPhone, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@ContactFormActivity, 14)
        })
        if (phones.isEmpty()) phones.add(Phone(0, 0, "Mobile", "", true))
        rebuildPhoneRows()

        email = Ui.input(this@ContactFormActivity, getString(R.string.contact_email),
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        add(Ui.field(this, getString(R.string.contact_email), email))

        val cityRow = Ui.hbox(this)
        city = Ui.input(this@ContactFormActivity, getString(R.string.contact_city))
        area = Ui.input(this@ContactFormActivity, getString(R.string.contact_area))
        cityRow.addView(Ui.field(this, getString(R.string.contact_city), city),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@ContactFormActivity, 8) })
        cityRow.addView(Ui.field(this, getString(R.string.contact_area), area),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(cityRow)

        address = Ui.input(this@ContactFormActivity, getString(R.string.contact_address))
        add(Ui.field(this, getString(R.string.contact_address), address))

        // classification + status + temperature + priority
        add(Ui.pickField(this, getString(R.string.contact_classification), classification) {
            Ui.pick(this, getString(R.string.contact_classification),
                listOf("Buyer", "Seller", "Investor", "Renter", "Landlord")) { i ->
                classification = listOf("Buyer", "Seller", "Investor", "Renter", "Landlord")[i]
            }
        })
        add(Ui.pickField(this, getString(R.string.contact_lead_status), status) {
            Ui.pick(this, getString(R.string.contact_lead_status),
                listOf("New", "Contacted", "Qualified", "Unqualified", "Customer")) { i ->
                status = listOf("New", "Contacted", "Qualified", "Unqualified", "Customer")[i]
            }
        })
        add(Ui.pickField(this, getString(R.string.contact_temperature), temperature) {
            Ui.pick(this, getString(R.string.contact_temperature), listOf("Hot", "Warm", "Cold")) { i ->
                temperature = listOf("Hot", "Warm", "Cold")[i]
            }
        })
        add(Ui.pickField(this, getString(R.string.contact_priority), priorityText()) {
            Ui.pick(this, getString(R.string.contact_priority),
                listOf(getString(R.string.pr_low), getString(R.string.pr_medium), getString(R.string.pr_high))) { i ->
                priority = i + 1
            }
        })

        // budget
        budgetMin = Ui.input(this@ContactFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        budgetMax = Ui.input(this@ContactFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        val bRow = Ui.hbox(this)
        bRow.addView(Ui.field(this, "Min", budgetMin),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@ContactFormActivity, 8) })
        bRow.addView(Ui.field(this, "Max", budgetMax),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(bRow)

        prefLocation = Ui.input(this@ContactFormActivity, getString(R.string.contact_preferred_location))
        add(Ui.field(this, getString(R.string.contact_preferred_location), prefLocation))
        prefType = Ui.input(this@ContactFormActivity, getString(R.string.contact_preferred_type))
        add(Ui.field(this, getString(R.string.contact_preferred_type), prefType))

        add(Ui.pickField(this, getString(R.string.contact_lead_source), source.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.contact_lead_source),
                listOf(getString(R.string.flt_any)) + Di.store.leadSources()) { i ->
                source = if (i == 0) "" else Di.store.leadSources()[i - 1]
            }
        })

        // tags
        add(Ui.label(this@ContactFormActivity, getString(R.string.contact_tags)))
        val allTags = Di.store.tags()
        if (allTags.isEmpty()) {
            add(Ui.caption(this@ContactFormActivity, getString(R.string.flt_any)))
        } else {
            val tagWrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, Ui.dp(this@ContactFormActivity, 4), 0, Ui.dp(this@ContactFormActivity, 10))
            }
            var row = Ui.hbox(this)
            tagWrap.addView(row)
            allTags.forEachIndexed { i, t ->
                lateinit var c: TextView
                c = Ui.chip(this@ContactFormActivity, t, selectedTags.contains(t)) {
                    if (selectedTags.contains(t)) selectedTags.remove(t) else selectedTags.add(t)
                    c.isSelected = selectedTags.contains(t)
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = Ui.dp(this@ContactFormActivity, 8); lp.bottomMargin = Ui.dp(this@ContactFormActivity, 8)
                if (i % 3 == 0 && i > 0) {
                    row = Ui.hbox(this)
                    tagWrap.addView(row)
                }
                row.addView(c, lp)
            }
            add(tagWrap)
        }

        notes = Ui.input(this@ContactFormActivity, getString(R.string.contact_notes), multiline = true)
        add(Ui.field(this, getString(R.string.contact_notes), notes))

        add(Ui.spacer(this, 8))
        val save = Ui.btn(this@ContactFormActivity, getString(R.string.save), Ui.Btn.PRIMARY) { saveContact() }
        add(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@ContactFormActivity, 40)
        })
    }

    private fun priorityText(): String = when (priority) {
        3 -> getString(R.string.pr_high)
        2 -> getString(R.string.pr_medium)
        else -> getString(R.string.pr_low)
    }

    private fun rebuildPhoneRows() {
        phoneRows.removeAllViews()
        phones.forEachIndexed { i, ph ->
            val row = Ui.hbox(this)
            val num = Ui.input(this@ContactFormActivity, getString(R.string.contact_number), ph.number,
                inputType = InputType.TYPE_CLASS_PHONE)
            row.addView(num, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.hspacer(this, 8))
            val wa = CheckBox(this).apply {
                text = getString(R.string.contact_whatsapp_yes)
                setTextColor(p.textSecondary)
                buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
                isChecked = ph.isWhatsapp
                setOnCheckedChangeListener { _, b -> phones[i] = ph.copy(isWhatsapp = b) }
            }
            row.addView(wa)
            val del = Ui.icon(this@ContactFormActivity, R.drawable.ic_close, 18, p.textTertiary)
            Ui.pad(del, 8, 8, 0, 8)
            del.setOnClickListener {
                phones.removeAt(i)
                rebuildPhoneRows()
            }
            row.addView(del)
            num.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    phones[i] = ph.copy(number = s?.toString() ?: "")
                }
            })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Ui.dp(this@ContactFormActivity, 8)
            phoneRows.addView(row, lp)
        }
    }

    private fun saveContact() {
        val f = first.text.toString().trim()
        val l = last.text.toString().trim()
        if (f.isBlank() && l.isBlank() && phones.all { it.number.isBlank() }) {
            snack(getString(R.string.error_required)); return
        }
        val c = Contact(
            id = contactId,
            firstName = f, lastName = l, fullName = "$f $l".trim(),
            email = email.text.toString().trim(), city = city.text.toString().trim(),
            area = area.text.toString().trim(), address = address.text.toString().trim(),
            classification = classification, leadStatus = status, temperature = temperature,
            priority = priority,
            budgetMin = budgetMin.text.toString().toLongOrNull() ?: 0,
            budgetMax = budgetMax.text.toString().toLongOrNull() ?: 0,
            preferredLocation = prefLocation.text.toString().trim(),
            preferredType = prefType.text.toString().trim(),
            leadSource = source, notes = notes.text.toString().trim(),
            assignedTo = assignedTo
        )
        val validPhones = phones.filter { it.number.isNotBlank() }

        Async.db({
            val dupes = Di.store.contacts.findDuplicates(f, l, c.email, validPhones.map { it.number })
                .filter { it.id != contactId }
            Triple(Di.store.contacts.byId(contactId), validPhones, dupes)
        }) { t ->
            if (t == null || isFinishing) return@db
            val (existing, validPhones2, dupes) = t
            if (dupes.isNotEmpty()) {
                val d = dupes.first()
                Ui.alert(this, getString(R.string.contact_duplicate_title),
                    getString(R.string.contact_duplicate_msg, d.displayName(),
                        Di.store.contacts.phones(d.id).firstOrNull()?.number ?: d.email),
                    getString(R.string.contact_duplicate_keep), getString(R.string.contact_duplicate_view),
                    onPositive = { persist(c, validPhones2, existing) },
                    onNegative = {
                        startActivity(Intent(this, ContactDetailActivity::class.java).putExtra("id", d.id))
                    })
            } else {
                persist(c, validPhones2, existing)
            }
        }
    }

    private fun persist(c: Contact, phoneList: List<Phone>, existing: Contact?) {
        Async.write({
            val saved = Di.store.contacts.save(
                c.copy(
                    avatarPath = existing?.avatarPath ?: "",
                    lastContacted = existing?.lastContacted ?: 0,
                    nextFollowUp = existing?.nextFollowUp ?: 0
                ),
                phoneList, selectedTags
            )
        }, {
            snack(getString(R.string.saved))
            if (contactId == 0L) finish()
            else {
                setResult(android.app.Activity.RESULT_OK)
                finish()
            }
        }, {
            snack(getString(R.string.error_save))
        })
    }
}
