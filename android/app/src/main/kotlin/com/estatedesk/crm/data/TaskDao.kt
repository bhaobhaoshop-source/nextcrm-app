package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class TaskDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Task = Task(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        kind = c.getString(c.getColumnIndexOrThrow("kind")),
        contactId = c.getLong(c.getColumnIndexOrThrow("contact_id")),
        propertyId = c.getLong(c.getColumnIndexOrThrow("property_id")),
        leadId = c.getLong(c.getColumnIndexOrThrow("lead_id")),
        dealId = c.getLong(c.getColumnIndexOrThrow("deal_id")),
        dueDate = c.getLong(c.getColumnIndexOrThrow("due_date")),
        dueTimeMinutes = c.getInt(c.getColumnIndexOrThrow("due_time")),
        priority = c.getInt(c.getColumnIndexOrThrow("priority")),
        status = c.getString(c.getColumnIndexOrThrow("status")),
        reminder = c.getInt(c.getColumnIndexOrThrow("reminder")) == 1,
        recurrence = c.getString(c.getColumnIndexOrThrow("recurrence")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        completedAt = if (c.isNull(c.getColumnIndexOrThrow("completed_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("completed_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun byId(id: Long): Task? {
        db.rawQuery("SELECT * FROM tasks WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun save(task: Task): Long {
        val now = s.now()
        val cv = ContentValues().apply {
            put("title", task.title)
            put("kind", task.kind)
            put("contact_id", task.contactId)
            put("property_id", task.propertyId)
            put("lead_id", task.leadId)
            put("deal_id", task.dealId)
            put("due_date", task.dueDate)
            put("due_time", task.dueTimeMinutes)
            put("priority", task.priority)
            put("status", task.status)
            put("reminder", if (task.reminder) 1 else 0)
            put("recurrence", task.recurrence)
            put("notes", task.notes)
            put("updated_at", now)
        }
        val id: Long
        if (task.id == 0L) {
            cv.put("created_at", now)
            id = db.insertOrThrow("tasks", null, cv)
        } else {
            id = task.id
            db.update("tasks", cv, "id=?", arrayOf(id.toString()))
        }
        val contactName = task.contactId.takeIf { it > 0 }?.let { cid ->
            db.rawQuery("SELECT full_name FROM contacts WHERE id=?", arrayOf(cid.toString())).use { c ->
                if (c.moveToFirst()) c.getString(0) else ""
            }
        } ?: ""
        s.indexSearch("task", id, task.title, contactName + " " + task.notes)
        return id
    }

    /** Mark done; if recurring, clone the next occurrence as a new open task. */
    fun complete(id: Long): Task? {
        val t = byId(id) ?: return null
        val cv = ContentValues().apply {
            put("status", Task.STATUS_DONE)
            put("completed_at", s.now())
            put("updated_at", s.now())
        }
        db.update("tasks", cv, "id=?", arrayOf(id.toString()))
        val next = cloneNext(t)
        s.indexSearch("task", id, t.title, "")
        return next
    }

    fun reopen(id: Long) {
        val cv = ContentValues().apply {
            put("status", Task.STATUS_OPEN)
            putNull("completed_at")
            put("updated_at", s.now())
        }
        db.update("tasks", cv, "id=?", arrayOf(id.toString()))
    }

    private fun cloneNext(t: Task): Task? {
        if (t.recurrence.isBlank()) return null
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = t.dueDate
        when (t.recurrence) {
            Task.REC_DAILY -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            Task.REC_WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            Task.REC_MONTHLY -> cal.add(java.util.Calendar.MONTH, 1)
            else -> return null
        }
        val copy = t.copy(
            id = 0,
            dueDate = cal.timeInMillis,
            status = Task.STATUS_OPEN,
            completedAt = null,
            createdAt = 0,
            updatedAt = 0,
            deletedAt = null
        )
        return byId(save(copy))
    }

    fun snooze(id: Long, deltaMillis: Long) {
        val t = byId(id) ?: return
        val cv = ContentValues().apply {
            put("due_date", t.dueDate + deltaMillis)
            put("updated_at", s.now())
        }
        db.update("tasks", cv, "id=?", arrayOf(id.toString()))
    }

    fun setDue(id: Long, dueDate: Long, minutes: Int) {
        val cv = ContentValues().apply {
            put("due_date", dueDate)
            put("due_time", minutes)
            put("updated_at", s.now())
        }
        db.update("tasks", cv, "id=?", arrayOf(id.toString()))
    }

    /** Open tasks due before [endOfRange], for scheduling reminders. */
    fun upcomingWithReminders(beforeMillis: Long): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND status='Open' AND reminder=1 AND due_date>0 AND due_date<=? ORDER BY due_date",
            arrayOf(beforeMillis.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun query(
        tab: String, kind: String, q: String, startOfDay: Long, endOfDay: Long,
        offset: Int, limit: Int
    ): List<Task> {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        when (tab) {
            "today" -> {
                where.append(" AND status='Open' AND due_date>=? AND due_date<=?")
                args.add(startOfDay.toString()); args.add(endOfDay.toString())
            }
            "upcoming" -> {
                where.append(" AND status='Open' AND due_date>?")
                args.add(endOfDay.toString())
            }
            "overdue" -> {
                where.append(" AND status='Open' AND due_date>0 AND due_date<?")
                args.add(startOfDay.toString())
            }
        }
        if (kind.isNotBlank()) { where.append(" AND kind=?"); args.add(kind) }
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR notes LIKE ?)")
            val like = "%$q%"; args.add(like); args.add(like)
        }
        val order = if (tab == "all") "CASE WHEN status='Open' THEN 0 ELSE 1 END, due_date ASC"
        else "priority DESC, due_date ASC"
        val sql = "SELECT * FROM tasks WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(sql, args.toTypedArray()).use { c -> return s.map(c) { row(it) } }
    }

    fun count(tab: String, kind: String, q: String, startOfDay: Long, endOfDay: Long): Int {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        when (tab) {
            "today" -> {
                where.append(" AND status='Open' AND due_date>=? AND due_date<=?")
                args.add(startOfDay.toString()); args.add(endOfDay.toString())
            }
            "upcoming" -> {
                where.append(" AND status='Open' AND due_date>?")
                args.add(endOfDay.toString())
            }
            "overdue" -> {
                where.append(" AND status='Open' AND due_date>0 AND due_date<?")
                args.add(startOfDay.toString())
            }
        }
        if (kind.isNotBlank()) { where.append(" AND kind=?"); args.add(kind) }
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR notes LIKE ?)")
            val like = "%$q%"; args.add(like); args.add(like)
        }
        db.rawQuery("SELECT COUNT(*) FROM tasks WHERE $where", args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** All open follow-ups ordered by due date (for the Follow Up Today screen). */
    fun followUps(): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND kind='Follow-up' AND status='Open' ORDER BY CASE WHEN due_date=0 THEN 1 ELSE 0 END, due_date ASC",
            null
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byContact(contactId: Long): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND contact_id=? ORDER BY due_date DESC",
            arrayOf(contactId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byLead(leadId: Long): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND lead_id=? ORDER BY due_date DESC",
            arrayOf(leadId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byDeal(dealId: Long): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND deal_id=? ORDER BY due_date DESC",
            arrayOf(dealId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byProperty(propertyId: Long): List<Task> {
        db.rawQuery(
            "SELECT * FROM tasks WHERE deleted_at IS NULL AND property_id=? ORDER BY due_date DESC",
            arrayOf(propertyId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun allDeleted(): List<Pair<Task, Long>> {
        db.rawQuery("SELECT * FROM tasks WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { row(it) to it.getLong(it.getColumnIndexOrThrow("deleted_at")) }
        }
    }
}
