package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class ActivityDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Activity = Activity(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        type = c.getString(c.getColumnIndexOrThrow("type")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        body = c.getString(c.getColumnIndexOrThrow("body")),
        at = c.getLong(c.getColumnIndexOrThrow("at")),
        contactId = c.getLong(c.getColumnIndexOrThrow("contact_id")),
        propertyId = c.getLong(c.getColumnIndexOrThrow("property_id")),
        leadId = c.getLong(c.getColumnIndexOrThrow("lead_id")),
        dealId = c.getLong(c.getColumnIndexOrThrow("deal_id")),
        taskId = c.getLong(c.getColumnIndexOrThrow("task_id")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
    )

    /** Insert one activity. Does NOT index FTS (activities are not search records). */
    fun add(
        type: String, title: String, body: String,
        contactId: Long = 0, propertyId: Long = 0, leadId: Long = 0,
        dealId: Long = 0, taskId: Long = 0, at: Long = s.now()
    ): Long {
        val cv = ContentValues().apply {
            put("type", type); put("title", title); put("body", body)
            put("at", at)
            put("contact_id", contactId); put("property_id", propertyId)
            put("lead_id", leadId); put("deal_id", dealId); put("task_id", taskId)
            put("created_at", s.now())
        }
        return db.insertOrThrow("activities", null, cv)
    }

    /** Chronological timeline across the whole business (paginated). */
    fun timeline(offset: Int, limit: Int): List<Activity> {
        db.rawQuery(
            "SELECT * FROM activities ORDER BY at DESC, id DESC LIMIT ? OFFSET ?",
            arrayOf(limit.toString(), offset.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun timelineFor(contactId: Long = 0, propertyId: Long = 0, leadId: Long = 0,
                    dealId: Long = 0, offset: Int, limit: Int): List<Activity> {
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (contactId > 0) { where.append(" AND contact_id=?"); args.add(contactId.toString()) }
        if (propertyId > 0) { where.append(" AND property_id=?"); args.add(propertyId.toString()) }
        if (leadId > 0) { where.append(" AND lead_id=?"); args.add(leadId.toString()) }
        if (dealId > 0) { where.append(" AND deal_id=?"); args.add(dealId.toString()) }
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(
            "SELECT * FROM activities WHERE $where ORDER BY at DESC, id DESC LIMIT ? OFFSET ?",
            args.toTypedArray()
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun countsByType(since: Long): Map<String, Int> {
        db.rawQuery(
            "SELECT type, COUNT(*) FROM activities WHERE at>=? GROUP BY type",
            arrayOf(since.toString())
        ).use { c ->
            val out = mutableMapOf<String, Int>()
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
            return out
        }
    }

    fun addStatusChange(entity: String, name: String, from: String, to: String,
                        contactId: Long = 0, leadId: Long = 0, propertyId: Long = 0, dealId: Long = 0) {
        add(
            Activity.T_STATUS,
            "$entity stage changed",
            "$name: “$from” → “$to”",
            contactId, propertyId, leadId, dealId
        )
    }
}
