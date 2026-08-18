package com.estatedesk.crm.data

import android.database.sqlite.SQLiteDatabase

/** Global search across contacts, leads, properties, deals, tasks and notes
 *  using an FTS4 index with prefix matching, plus a LIKE fallback. */
class SearchDao(private val db: SQLiteDatabase, private val s: Store) {

    fun search(query: String, types: Set<String>, limit: Int): List<SearchHit> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val out = mutableListOf<SearchHit>()

        // FTS4 prefix search
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
            .map { "\"" + it.replace("\"", "\"\"") + "*\"" }
        val match = tokens.joinToString(" AND ")
        val typeFilter = if (types.isNotEmpty() && types.size < 6)
            " AND type IN (${types.joinToString(",") { "'$it'" }})" else ""
        try {
            db.rawQuery(
                "SELECT type, entity_id, name, detail FROM search_index WHERE search_index MATCH ?$typeFilter LIMIT ?",
                arrayOf(match, (limit * 3).toString())
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        SearchHit(
                            c.getString(0), c.getLong(1),
                            c.getString(2) ?: "", c.getString(3) ?: ""
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            // malformed query → fall through to LIKE
        }

        if (out.size < limit) {
            // LIKE fallback for fuzzy/partial matches (covers the 10k-row scale
            // via indexed LIKE on a narrow subset).
            val like = "%$q%"
            val seen = out.map { it.type + it.entityId }.toMutableSet()
            db.rawQuery(
                "SELECT type, entity_id, name, detail FROM search_index WHERE " +
                        "(name LIKE ? OR detail LIKE ?)$typeFilter LIMIT ?",
                arrayOf(like, like, (limit * 2).toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) + c.getLong(1)
                    if (seen.add(key)) {
                        out.add(SearchHit(c.getString(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: ""))
                    }
                }
            }
        }
        return out.take(limit)
    }

    fun rebuild() {
        db.execSQL("DELETE FROM search_index")
        db.execSQL(
            "INSERT INTO search_index(type, entity_id, name, detail) " +
                    "SELECT 'contact', id, full_name, email || ' ' || city || ' ' || area || ' ' || notes FROM contacts WHERE deleted_at IS NULL"
        )
        db.execSQL(
            "INSERT INTO search_index(type, entity_id, name, detail) " +
                    "SELECT 'lead', id, title, requirement || ' ' || location || ' ' || notes FROM leads WHERE deleted_at IS NULL"
        )
        db.execSQL(
            "INSERT INTO search_index(type, entity_id, name, detail) " +
                    "SELECT 'property', id, title, location || ' ' || area_name || ' ' || type || ' ' || description FROM properties WHERE deleted_at IS NULL"
        )
        db.execSQL(
            "INSERT INTO search_index(type, entity_id, name, detail) " +
                    "SELECT 'deal', id, title, notes FROM deals WHERE deleted_at IS NULL"
        )
        db.execSQL(
            "INSERT INTO search_index(type, entity_id, name, detail) " +
                    "SELECT 'task', id, title, notes FROM tasks WHERE deleted_at IS NULL"
        )
    }
}
