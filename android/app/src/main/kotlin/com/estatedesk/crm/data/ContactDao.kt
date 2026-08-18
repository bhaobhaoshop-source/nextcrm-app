package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class ContactDao(private val db: SQLiteDatabase, private val s: Store) {

    fun contactRow(c: Cursor): Contact = Contact(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        firstName = c.getString(c.getColumnIndexOrThrow("first_name")),
        lastName = c.getString(c.getColumnIndexOrThrow("last_name")),
        fullName = c.getString(c.getColumnIndexOrThrow("full_name")),
        avatarPath = c.getString(c.getColumnIndexOrThrow("avatar_path")),
        email = c.getString(c.getColumnIndexOrThrow("email")),
        address = c.getString(c.getColumnIndexOrThrow("address")),
        city = c.getString(c.getColumnIndexOrThrow("city")),
        area = c.getString(c.getColumnIndexOrThrow("area")),
        classification = c.getString(c.getColumnIndexOrThrow("classification")),
        leadStatus = c.getString(c.getColumnIndexOrThrow("lead_status")),
        temperature = c.getString(c.getColumnIndexOrThrow("temperature")),
        priority = c.getInt(c.getColumnIndexOrThrow("priority")),
        budgetMin = c.getLong(c.getColumnIndexOrThrow("budget_min")),
        budgetMax = c.getLong(c.getColumnIndexOrThrow("budget_max")),
        preferredLocation = c.getString(c.getColumnIndexOrThrow("preferred_location")),
        preferredType = c.getString(c.getColumnIndexOrThrow("preferred_type")),
        leadSource = c.getString(c.getColumnIndexOrThrow("lead_source")),
        assignedTo = c.getString(c.getColumnIndexOrThrow("assigned_to")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        lastContacted = c.getLong(c.getColumnIndexOrThrow("last_contacted")),
        nextFollowUp = c.getLong(c.getColumnIndexOrThrow("next_follow_up")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun byId(id: Long): Contact? {
        db.rawQuery("SELECT * FROM contacts WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) contactRow(c) else null
        }
    }

    fun phones(contactId: Long): List<Phone> {
        db.rawQuery("SELECT * FROM phones WHERE contact_id=? ORDER BY id", arrayOf(contactId.toString())).use { c ->
            return s.map(c) {
                Phone(
                    it.getLong(it.getColumnIndexOrThrow("id")),
                    it.getLong(it.getColumnIndexOrThrow("contact_id")),
                    it.getString(it.getColumnIndexOrThrow("label")),
                    it.getString(it.getColumnIndexOrThrow("number")),
                    it.getInt(it.getColumnIndexOrThrow("is_whatsapp")) == 1
                )
            }
        }
    }

    fun tags(contactId: Long): List<String> {
        db.rawQuery("SELECT tag FROM contact_tags WHERE contact_id=?", arrayOf(contactId.toString())).use { c ->
            return s.map(c) { it.getString(0) }
        }
    }

    fun save(contact: Contact, phoneList: List<Phone>, tagList: List<String>): Long {
        val now = s.now()
        val cv = ContentValues().apply {
            put("first_name", contact.firstName)
            put("last_name", contact.lastName)
            put("full_name", contact.fullName)
            put("avatar_path", contact.avatarPath)
            put("email", contact.email.trim())
            put("address", contact.address)
            put("city", contact.city)
            put("area", contact.area)
            put("classification", contact.classification)
            put("lead_status", contact.leadStatus)
            put("temperature", contact.temperature)
            put("priority", contact.priority)
            put("budget_min", contact.budgetMin)
            put("budget_max", contact.budgetMax)
            put("preferred_location", contact.preferredLocation)
            put("preferred_type", contact.preferredType)
            put("lead_source", contact.leadSource)
            put("assigned_to", contact.assignedTo)
            put("notes", contact.notes)
            put("last_contacted", contact.lastContacted)
            put("next_follow_up", contact.nextFollowUp)
            put("updated_at", now)
        }
        val id: Long
        if (contact.id == 0L) {
            cv.put("created_at", now)
            id = db.insertOrThrow("contacts", null, cv)
        } else {
            id = contact.id
            db.update("contacts", cv, "id=?", arrayOf(id.toString()))
        }
        // phones
        db.delete("phones", "contact_id=?", arrayOf(id.toString()))
        for (p in phoneList) {
            if (p.number.isBlank()) continue
            val pcv = ContentValues().apply {
                put("contact_id", id)
                put("label", p.label)
                put("number", p.number.trim())
                put("is_whatsapp", if (p.isWhatsapp) 1 else 0)
            }
            db.insertWithOnConflict("phones", null, pcv, SQLiteDatabase.CONFLICT_IGNORE)
        }
        // tags
        db.delete("contact_tags", "contact_id=?", arrayOf(id.toString()))
        for (t in tagList) {
            if (t.isBlank()) continue
            val tcv = ContentValues().apply {
                put("contact_id", id); put("tag", t.trim())
            }
            db.insertWithOnConflict("contact_tags", null, tcv, SQLiteDatabase.CONFLICT_IGNORE)
        }
        // search index
        val detail = listOf(
            phoneList.joinToString(" ") { it.number },
            contact.email, contact.city, contact.area, contact.preferredLocation, contact.notes
        ).joinToString(" ")
        s.indexSearch("contact", id, contact.displayName(), detail)
        return id
    }

    fun setAvatar(id: Long, path: String) {
        val cv = ContentValues().apply { put("avatar_path", path) }
        db.update("contacts", cv, "id=?", arrayOf(id.toString()))
    }

    fun touchContact(id: Long) {
        val cv = ContentValues().apply { put("last_contacted", s.now()) }
        db.update("contacts", cv, "id=?", arrayOf(id.toString()))
    }

    fun setNextFollowUp(id: Long, at: Long) {
        val cv = ContentValues().apply { put("next_follow_up", at) }
        db.update("contacts", cv, "id=?", arrayOf(id.toString()))
    }

    /** Paginated query with filters + sort. */
    fun query(
        q: String,
        classification: String,
        status: String,
        temperature: String,
        source: String,
        priority: Int,
        city: String,
        tag: String,
        sort: String,
        offset: Int,
        limit: Int
    ): List<Contact> {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (full_name LIKE ? OR email LIKE ? OR city LIKE ? OR area LIKE ? OR notes LIKE ?)")
            val like = "%$q%"
            repeat(5) { args.add(like) }
        }
        if (classification.isNotBlank()) {
            where.append(" AND classification=?"); args.add(classification)
        }
        if (status.isNotBlank()) {
            where.append(" AND lead_status=?"); args.add(status)
        }
        if (temperature.isNotBlank()) {
            where.append(" AND temperature=?"); args.add(temperature)
        }
        if (source.isNotBlank()) {
            where.append(" AND lead_source=?"); args.add(source)
        }
        if (priority > 0) {
            where.append(" AND priority=?"); args.add(priority.toString())
        }
        if (city.isNotBlank()) {
            where.append(" AND city=?"); args.add(city)
        }
        if (tag.isNotBlank()) {
            where.append(" AND id IN (SELECT contact_id FROM contact_tags WHERE tag=?)")
            args.add(tag)
        }
        val order = when (sort) {
            "oldest" -> "created_at ASC"
            "priority" -> "priority DESC, updated_at DESC"
            "followup" -> "CASE WHEN next_follow_up=0 THEN 1 ELSE 0 END, next_follow_up ASC"
            "last_contacted" -> "last_contacted DESC"
            "name" -> "full_name COLLATE NOCASE ASC"
            else -> "updated_at DESC"
        }
        val sql = "SELECT * FROM contacts WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(sql, args.toTypedArray()).use { c -> return s.map(c) { contactRow(it) } }
    }

    fun count(q: String, classification: String, status: String, temperature: String,
              source: String, priority: Int, city: String, tag: String): Int {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (full_name LIKE ? OR email LIKE ? OR city LIKE ? OR area LIKE ? OR notes LIKE ?)")
            val like = "%$q%"; repeat(5) { args.add(like) }
        }
        if (classification.isNotBlank()) { where.append(" AND classification=?"); args.add(classification) }
        if (status.isNotBlank()) { where.append(" AND lead_status=?"); args.add(status) }
        if (temperature.isNotBlank()) { where.append(" AND temperature=?"); args.add(temperature) }
        if (source.isNotBlank()) { where.append(" AND lead_source=?"); args.add(source) }
        if (priority > 0) { where.append(" AND priority=?"); args.add(priority.toString()) }
        if (city.isNotBlank()) { where.append(" AND city=?"); args.add(city) }
        if (tag.isNotBlank()) {
            where.append(" AND id IN (SELECT contact_id FROM contact_tags WHERE tag=?)"); args.add(tag)
        }
        db.rawQuery("SELECT COUNT(*) FROM contacts WHERE $where", args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** All active contacts with phones — for pickers. */
    fun allForPicker(): List<Contact> {
        db.rawQuery("SELECT * FROM contacts WHERE deleted_at IS NULL ORDER BY full_name COLLATE NOCASE", null).use { c ->
            return s.map(c) { contactRow(it) }
        }
    }

    fun allDeleted(): List<Pair<Contact, Long>> {
        db.rawQuery("SELECT * FROM contacts WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { contactRow(it) to (it.getLong(it.getColumnIndexOrThrow("deleted_at"))) }
        }
    }

    /** Possible duplicates by normalized phone, email or name. */
    fun findDuplicates(firstName: String, lastName: String, email: String, phones: List<String>): List<Contact> {
        val out = mutableListOf<Contact>()
        val seen = mutableSetOf<Long>()
        fun addAll(list: List<Contact>) {
            for (c2 in list) if (seen.add(c2.id)) out.add(c2)
        }
        if (email.isNotBlank()) {
            db.rawQuery(
                "SELECT * FROM contacts WHERE deleted_at IS NULL AND email=? COLLATE NOCASE",
                arrayOf(email.trim())
            ).use { c -> addAll(s.map(c) { contactRow(it) }) }
        }
        for (p in phones) {
            val digits = p.filter { it.isDigit() }
            if (digits.length < 6) continue
            db.rawQuery(
                "SELECT * FROM contacts WHERE deleted_at IS NULL AND id IN " +
                        "(SELECT contact_id FROM phones WHERE replace(replace(replace(number,' ',''),'-',''),'+','')=?)",
                arrayOf(digits)
            ).use { c -> addAll(s.map(c) { contactRow(it) }) }
        }
        val name = "$firstName $lastName".trim().lowercase()
        if (name.isNotBlank()) {
            db.rawQuery(
                "SELECT * FROM contacts WHERE deleted_at IS NULL AND lower(full_name)=?",
                arrayOf(name)
            ).use { c -> addAll(s.map(c) { contactRow(it) }) }
        }
        return out
    }
}
