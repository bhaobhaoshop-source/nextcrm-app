package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class DealDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Deal = Deal(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        buyerContactId = c.getLong(c.getColumnIndexOrThrow("buyer_contact_id")),
        sellerContactId = c.getLong(c.getColumnIndexOrThrow("seller_contact_id")),
        propertyId = c.getLong(c.getColumnIndexOrThrow("property_id")),
        leadId = c.getLong(c.getColumnIndexOrThrow("lead_id")),
        value = c.getLong(c.getColumnIndexOrThrow("value")),
        currency = c.getString(c.getColumnIndexOrThrow("currency")),
        commissionPct = c.getDouble(c.getColumnIndexOrThrow("commission_pct")),
        commissionAmount = c.getLong(c.getColumnIndexOrThrow("commission_amount")),
        commissionReceived = c.getLong(c.getColumnIndexOrThrow("commission_received")),
        stage = c.getString(c.getColumnIndexOrThrow("stage")),
        expectedClose = c.getLong(c.getColumnIndexOrThrow("expected_close")),
        actualClose = if (c.isNull(c.getColumnIndexOrThrow("actual_close"))) null
        else c.getLong(c.getColumnIndexOrThrow("actual_close")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun byId(id: Long): Deal? {
        db.rawQuery("SELECT * FROM deals WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun save(deal: Deal): Long {
        val now = s.now()
        val cv = ContentValues().apply {
            put("title", deal.title)
            put("buyer_contact_id", deal.buyerContactId)
            put("seller_contact_id", deal.sellerContactId)
            put("property_id", deal.propertyId)
            put("lead_id", deal.leadId)
            put("value", deal.value)
            put("currency", deal.currency)
            put("commission_pct", deal.commissionPct)
            put("commission_amount", deal.commissionAmount)
            put("commission_received", deal.commissionReceived)
            put("stage", deal.stage)
            put("expected_close", deal.expectedClose)
            if (deal.actualClose != null) put("actual_close", deal.actualClose) else putNull("actual_close")
            put("notes", deal.notes)
            put("updated_at", now)
        }
        val id: Long
        if (deal.id == 0L) {
            cv.put("created_at", now)
            id = db.insertOrThrow("deals", null, cv)
        } else {
            id = deal.id
            db.update("deals", cv, "id=?", arrayOf(id.toString()))
        }
        val detail = mutableListOf<String>()
        deal.propertyId.takeIf { it > 0 }?.let {
            detail.add(db.rawQuery("SELECT title FROM properties WHERE id=?", arrayOf(it.toString()))
                .use { c -> if (c.moveToFirst()) c.getString(0) else "" })
        }
        deal.buyerContactId.takeIf { it > 0 }?.let {
            detail.add(db.rawQuery("SELECT full_name FROM contacts WHERE id=?", arrayOf(it.toString()))
                .use { c -> if (c.moveToFirst()) c.getString(0) else "" })
        }
        s.indexSearch("deal", id, deal.displayTitle(), detail.joinToString(" ") + " " + deal.notes)
        return id
    }

    fun setStage(id: Long, stage: String) {
        val cv = ContentValues().apply { put("stage", stage); put("updated_at", s.now()) }
        db.update("deals", cv, "id=?", arrayOf(id.toString()))
    }

    fun addCommission(id: Long, amount: Long) {
        db.execSQL(
            "UPDATE deals SET commission_received=commission_received+?, updated_at=? WHERE id=?",
            arrayOf(amount.toString(), s.now().toString(), id.toString())
        )
    }

    fun close(id: Long, won: Boolean) {
        val cv = ContentValues().apply {
            put("stage", if (won) "Closed Won" else "Closed Lost")
            put("actual_close", s.now())
            put("updated_at", s.now())
        }
        db.update("deals", cv, "id=?", arrayOf(id.toString()))
    }

    fun reopen(id: Long) {
        val cv = ContentValues().apply {
            put("stage", "Negotiation")
            putNull("actual_close")
            put("updated_at", s.now())
        }
        db.update("deals", cv, "id=?", arrayOf(id.toString()))
    }

    fun query(q: String, stage: String, sort: String, offset: Int, limit: Int): List<Deal> {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR notes LIKE ?)")
            val like = "%$q%"; args.add(like); args.add(like)
        }
        if (stage.isNotBlank()) { where.append(" AND stage=?"); args.add(stage) }
        val order = when (sort) {
            "oldest" -> "created_at ASC"
            "value" -> "value DESC"
            else -> "updated_at DESC"
        }
        val sql = "SELECT * FROM deals WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(sql, args.toTypedArray()).use { c -> return s.map(c) { row(it) } }
    }

    fun count(q: String, stage: String): Int {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR notes LIKE ?)")
            val like = "%$q%"; args.add(like); args.add(like)
        }
        if (stage.isNotBlank()) { where.append(" AND stage=?"); args.add(stage) }
        db.rawQuery("SELECT COUNT(*) FROM deals WHERE $where", args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun stageCounts(): List<Pair<String, Int>> {
        db.rawQuery(
            "SELECT stage, COUNT(*) FROM deals WHERE deleted_at IS NULL GROUP BY stage", null
        ).use { c -> return s.map(c) { c.getString(0) to c.getInt(1) } }
    }

    fun byContact(contactId: Long): List<Deal> {
        db.rawQuery(
            "SELECT * FROM deals WHERE deleted_at IS NULL AND (buyer_contact_id=? OR seller_contact_id=?) ORDER BY updated_at DESC",
            arrayOf(contactId.toString(), contactId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byProperty(propertyId: Long): List<Deal> {
        db.rawQuery(
            "SELECT * FROM deals WHERE deleted_at IS NULL AND property_id=? ORDER BY updated_at DESC",
            arrayOf(propertyId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun allDeleted(): List<Pair<Deal, Long>> {
        db.rawQuery("SELECT * FROM deals WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { row(it) to it.getLong(it.getColumnIndexOrThrow("deleted_at")) }
        }
    }

    /** Reassign deals from a removed stage to another stage. */
    fun reassignStage(from: String, to: String) {
        val cv = ContentValues().apply { put("stage", to) }
        db.update("deals", cv, "stage=? AND deleted_at IS NULL", arrayOf(from))
    }

    fun allForPicker(): List<Deal> {
        db.rawQuery("SELECT * FROM deals WHERE deleted_at IS NULL ORDER BY updated_at DESC", null).use { c ->
            return s.map(c) { row(it) }
        }
    }
}
