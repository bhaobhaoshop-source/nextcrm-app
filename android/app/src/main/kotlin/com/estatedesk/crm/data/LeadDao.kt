package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class LeadDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Lead = Lead(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        contactId = c.getLong(c.getColumnIndexOrThrow("contact_id")),
        source = c.getString(c.getColumnIndexOrThrow("source")),
        requirement = c.getString(c.getColumnIndexOrThrow("requirement")),
        budgetMin = c.getLong(c.getColumnIndexOrThrow("budget_min")),
        budgetMax = c.getLong(c.getColumnIndexOrThrow("budget_max")),
        location = c.getString(c.getColumnIndexOrThrow("location")),
        propertyType = c.getString(c.getColumnIndexOrThrow("property_type")),
        intent = c.getString(c.getColumnIndexOrThrow("intent")),
        stage = c.getString(c.getColumnIndexOrThrow("stage")),
        score = c.getInt(c.getColumnIndexOrThrow("score")),
        priority = c.getInt(c.getColumnIndexOrThrow("priority")),
        probability = c.getInt(c.getColumnIndexOrThrow("probability")),
        nextAction = c.getString(c.getColumnIndexOrThrow("next_action")),
        nextFollowUp = c.getLong(c.getColumnIndexOrThrow("next_follow_up")),
        assignedTo = c.getString(c.getColumnIndexOrThrow("assigned_to")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at")),
        dealId = c.getLong(c.getColumnIndexOrThrow("deal_id"))
    )

    fun byId(id: Long): Lead? {
        db.rawQuery("SELECT * FROM leads WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun save(lead: Lead): Long {
        val now = s.now()
        val cv = ContentValues().apply {
            put("title", lead.title)
            put("contact_id", lead.contactId)
            put("source", lead.source)
            put("requirement", lead.requirement)
            put("budget_min", lead.budgetMin)
            put("budget_max", lead.budgetMax)
            put("location", lead.location)
            put("property_type", lead.propertyType)
            put("intent", lead.intent)
            put("stage", lead.stage)
            put("score", lead.score)
            put("priority", lead.priority)
            put("probability", lead.probability)
            put("next_action", lead.nextAction)
            put("next_follow_up", lead.nextFollowUp)
            put("assigned_to", lead.assignedTo)
            put("notes", lead.notes)
            put("deal_id", lead.dealId)
            put("updated_at", now)
        }
        val id: Long
        if (lead.id == 0L) {
            cv.put("created_at", now)
            id = db.insertOrThrow("leads", null, cv)
        } else {
            id = lead.id
            db.update("leads", cv, "id=?", arrayOf(id.toString()))
        }
        val contactName = lead.contactId.takeIf { it > 0 }?.let { cid ->
            db.rawQuery("SELECT full_name FROM contacts WHERE id=?", arrayOf(cid.toString())).use { c ->
                if (c.moveToFirst()) c.getString(0) else ""
            }
        } ?: ""
        s.indexSearch(
            "lead", id, lead.displayTitle(),
            listOf(contactName, lead.requirement, lead.location, lead.propertyType, lead.notes).joinToString(" ")
        )
        return id
    }

    fun setStage(id: Long, stage: String) {
        val cv = ContentValues().apply {
            put("stage", stage)
            put("updated_at", s.now())
        }
        db.update("leads", cv, "id=?", arrayOf(id.toString()))
        s.indexSearch("lead", id, byId(id)?.displayTitle() ?: "", byId(id)?.requirement ?: "")
    }

    fun setScore(id: Long, score: Int) {
        db.execSQL("UPDATE leads SET score=? WHERE id=?", arrayOf(score.toString(), id.toString()))
    }

    fun setNextFollowUp(id: Long, at: Long) {
        val cv = ContentValues().apply { put("next_follow_up", at) }
        db.update("leads", cv, "id=?", arrayOf(id.toString()))
    }

    fun linkDeal(id: Long, dealId: Long) {
        db.execSQL("UPDATE leads SET deal_id=? WHERE id=?", arrayOf(dealId.toString(), id.toString()))
    }

    fun query(
        q: String, stage: String, status: String, temperature: String, source: String,
        priority: Int, location: String, intent: String, sort: String,
        offset: Int, limit: Int
    ): List<Lead> {
        val where = StringBuilder("l.deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (l.title LIKE ? OR l.requirement LIKE ? OR l.location LIKE ? OR l.notes LIKE ?)")
            val like = "%$q%"; repeat(4) { args.add(like) }
        }
        if (stage.isNotBlank()) { where.append(" AND l.stage=?"); args.add(stage) }
        if (source.isNotBlank()) { where.append(" AND l.source=?"); args.add(source) }
        if (priority > 0) { where.append(" AND l.priority=?"); args.add(priority.toString()) }
        if (location.isNotBlank()) { where.append(" AND l.location=?"); args.add(location) }
        if (intent.isNotBlank()) { where.append(" AND l.intent=?"); args.add(intent) }
        if (temperature.isNotBlank()) {
            where.append(" AND l.contact_id IN (SELECT id FROM contacts WHERE temperature=?)")
            args.add(temperature)
        }
        if (status.isNotBlank()) {
            where.append(" AND l.contact_id IN (SELECT id FROM contacts WHERE lead_status=?)")
            args.add(status)
        }
        val order = when (sort) {
            "oldest" -> "l.created_at ASC"
            "priority" -> "l.priority DESC, l.updated_at DESC"
            "followup" -> "CASE WHEN l.next_follow_up=0 THEN 1 ELSE 0 END, l.next_follow_up ASC"
            "value" -> "l.budget_max DESC"
            "score" -> "l.score DESC"
            else -> "l.updated_at DESC"
        }
        val sql = "SELECT l.* FROM leads l WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(sql, args.toTypedArray()).use { c -> return s.map(c) { row(it) } }
    }

    fun count(
        q: String, stage: String, status: String, temperature: String, source: String,
        priority: Int, location: String, intent: String
    ): Int {
        val where = StringBuilder("l.deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (l.title LIKE ? OR l.requirement LIKE ? OR l.location LIKE ? OR l.notes LIKE ?)")
            val like = "%$q%"; repeat(4) { args.add(like) }
        }
        if (stage.isNotBlank()) { where.append(" AND l.stage=?"); args.add(stage) }
        if (source.isNotBlank()) { where.append(" AND l.source=?"); args.add(source) }
        if (priority > 0) { where.append(" AND l.priority=?"); args.add(priority.toString()) }
        if (location.isNotBlank()) { where.append(" AND l.location=?"); args.add(location) }
        if (intent.isNotBlank()) { where.append(" AND l.intent=?"); args.add(intent) }
        if (temperature.isNotBlank()) {
            where.append(" AND l.contact_id IN (SELECT id FROM contacts WHERE temperature=?)")
            args.add(temperature)
        }
        if (status.isNotBlank()) {
            where.append(" AND l.contact_id IN (SELECT id FROM contacts WHERE lead_status=?)")
            args.add(status)
        }
        db.rawQuery("SELECT COUNT(*) FROM leads l WHERE $where", args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun byStage(stage: String): List<Lead> {
        db.rawQuery(
            "SELECT * FROM leads WHERE stage=? AND deleted_at IS NULL ORDER BY priority DESC, updated_at DESC",
            arrayOf(stage)
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun stageCounts(): List<Pair<String, Int>> {
        db.rawQuery(
            "SELECT stage, COUNT(*) FROM leads WHERE deleted_at IS NULL GROUP BY stage",
            null
        ).use { c -> return s.map(c) { c.getString(0) to c.getInt(1) } }
    }

    fun stageValues(): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT stage, COALESCE(SUM(budget_max),0) FROM leads WHERE deleted_at IS NULL GROUP BY stage",
            null
        ).use { c -> return s.map(c) { c.getString(0) to c.getLong(1) } }
    }

    fun recent(limit: Int): List<Lead> {
        db.rawQuery(
            "SELECT * FROM leads WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byContact(contactId: Long): List<Lead> {
        db.rawQuery(
            "SELECT * FROM leads WHERE contact_id=? AND deleted_at IS NULL ORDER BY updated_at DESC",
            arrayOf(contactId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun allDeleted(): List<Pair<Lead, Long>> {
        db.rawQuery("SELECT * FROM leads WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { row(it) to it.getLong(it.getColumnIndexOrThrow("deleted_at")) }
        }
    }

    fun countByStage(stage: String): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE stage=? AND deleted_at IS NULL",
            arrayOf(stage)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Reassign leads from a removed stage to the first stage. */
    fun reassignStage(from: String, to: String) {
        val cv = ContentValues().apply { put("stage", to) }
        db.update("leads", cv, "stage=? AND deleted_at IS NULL", arrayOf(from))
    }
}
