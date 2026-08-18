package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class DocumentDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Document = Document(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        path = c.getString(c.getColumnIndexOrThrow("path")),
        mime = c.getString(c.getColumnIndexOrThrow("mime")),
        size = c.getLong(c.getColumnIndexOrThrow("size")),
        entityType = c.getString(c.getColumnIndexOrThrow("entity_type")),
        entityId = c.getLong(c.getColumnIndexOrThrow("entity_id")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun add(entityType: String, entityId: Long, name: String, path: String,
            mime: String, size: Long): Long {
        val cv = ContentValues().apply {
            put("name", name); put("path", path); put("mime", mime); put("size", size)
            put("entity_type", entityType); put("entity_id", entityId)
            put("created_at", s.now())
        }
        return db.insertOrThrow("documents", null, cv)
    }

    fun forEntity(entityType: String, entityId: Long): List<Document> {
        db.rawQuery(
            "SELECT * FROM documents WHERE entity_type=? AND entity_id=? AND deleted_at IS NULL ORDER BY created_at DESC",
            arrayOf(entityType, entityId.toString())
        ).use { c -> return s.map(c) { row(it) } }
    }

    fun byId(id: Long): Document? {
        db.rawQuery("SELECT * FROM documents WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun all(): List<Document> {
        db.rawQuery("SELECT * FROM documents WHERE deleted_at IS NULL ORDER BY created_at DESC", null).use { c ->
            return s.map(c) { row(it) }
        }
    }
}
