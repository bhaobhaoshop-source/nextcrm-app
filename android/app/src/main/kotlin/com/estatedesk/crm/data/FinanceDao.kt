package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class FinanceDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Finance = Finance(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        direction = c.getString(c.getColumnIndexOrThrow("direction")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        amount = c.getLong(c.getColumnIndexOrThrow("amount")),
        currency = c.getString(c.getColumnIndexOrThrow("currency")),
        date = c.getLong(c.getColumnIndexOrThrow("date")),
        dealId = c.getLong(c.getColumnIndexOrThrow("deal_id")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun byId(id: Long): Finance? {
        db.rawQuery("SELECT * FROM finances WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun save(f: Finance): Long {
        val cv = ContentValues().apply {
            put("direction", f.direction)
            put("category", f.category)
            put("amount", f.amount)
            put("currency", f.currency)
            put("date", f.date)
            put("deal_id", f.dealId)
            put("notes", f.notes)
            put("created_at", if (f.id == 0L) s.now() else f.createdAt)
        }
        return if (f.id == 0L) db.insertOrThrow("finances", null, cv)
        else {
            db.update("finances", cv, "id=?", arrayOf(f.id.toString()))
            f.id
        }
    }

    fun query(direction: String, category: String, since: Long, offset: Int, limit: Int): List<Finance> {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (direction.isNotBlank()) { where.append(" AND direction=?"); args.add(direction) }
        if (category.isNotBlank()) { where.append(" AND category=?"); args.add(category) }
        if (since > 0) { where.append(" AND date>=?"); args.add(since.toString()) }
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(
            "SELECT * FROM finances WHERE $where ORDER BY date DESC, id DESC LIMIT ? OFFSET ?",
            args.toTypedArray()
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun totals(since: Long = 0): Triple<Long, Long, Long> {
        val where = if (since > 0) "WHERE deleted_at IS NULL AND date>=?" else "WHERE deleted_at IS NULL"
        val args = if (since > 0) arrayOf(since.toString()) else null
        db.rawQuery(
            "SELECT COALESCE(SUM(CASE WHEN direction='Income' THEN amount ELSE 0 END),0), " +
                    "COALESCE(SUM(CASE WHEN direction='Expense' THEN amount ELSE 0 END),0) FROM finances $where",
            args
        ).use { c ->
            return if (c.moveToFirst()) Triple(c.getLong(0), c.getLong(1), c.getLong(0) - c.getLong(1))
            else Triple(0, 0, 0)
        }
    }

    fun byCategory(direction: String, since: Long): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT category, SUM(amount) FROM finances WHERE deleted_at IS NULL AND direction=? AND date>=? GROUP BY category ORDER BY SUM(amount) DESC",
            arrayOf(direction, since.toString())
        ).use { c -> return s.map(c) { c.getString(0) to c.getLong(1) } }
    }

    fun byDeal(dealId: Long): List<Finance> {
        db.rawQuery(
            "SELECT * FROM finances WHERE deleted_at IS NULL AND deal_id=? ORDER BY date DESC",
            arrayOf(dealId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun allDeleted(): List<Pair<Finance, Long>> {
        db.rawQuery("SELECT * FROM finances WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { row(it) to it.getLong(it.getColumnIndexOrThrow("deleted_at")) }
        }
    }
}
