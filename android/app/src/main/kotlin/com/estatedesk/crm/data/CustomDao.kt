package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class CustomDao(private val db: SQLiteDatabase) {

    fun defs(): List<CustomFieldDef> {
        db.rawQuery("SELECT * FROM custom_field_defs ORDER BY sort, key", null).use { c ->
            val out = mutableListOf<CustomFieldDef>()
            while (c.moveToNext()) {
                out.add(
                    CustomFieldDef(
                        c.getString(c.getColumnIndexOrThrow("key")),
                        c.getString(c.getColumnIndexOrThrow("label")),
                        c.getString(c.getColumnIndexOrThrow("type")),
                        c.getString(c.getColumnIndexOrThrow("options")),
                        c.getString(c.getColumnIndexOrThrow("entities")),
                        c.getInt(c.getColumnIndexOrThrow("sort"))
                    )
                )
            }
            return out
        }
    }

    fun defsFor(entityType: String): List<CustomFieldDef> =
        defs().filter { it.entities.split(",").map { e -> e.trim() }.contains(entityType) }

    fun saveDef(def: CustomFieldDef) {
        val cv = ContentValues().apply {
            put("key", def.key)
            put("label", def.label)
            put("type", def.type)
            put("options", def.options)
            put("entities", def.entities)
            put("sort", def.sort)
        }
        db.insertWithOnConflict("custom_field_defs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteDef(key: String) {
        db.delete("custom_field_defs", "key=?", arrayOf(key))
        db.delete("custom_field_values", "key=?", arrayOf(key))
    }

    fun values(entityType: String, entityId: Long): Map<String, String> {
        db.rawQuery(
            "SELECT key, value FROM custom_field_values WHERE entity_type=? AND entity_id=?",
            arrayOf(entityType, entityId.toString())
        ).use { c ->
            val out = mutableMapOf<String, String>()
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1) ?: ""
            return out
        }
    }

    fun saveValues(entityType: String, entityId: Long, values: Map<String, String>) {
        db.delete("custom_field_values", "entity_type=? AND entity_id=?",
            arrayOf(entityType, entityId.toString()))
        for ((k, v) in values) {
            if (v.isBlank()) continue
            val cv = ContentValues().apply {
                put("entity_type", entityType); put("entity_id", entityId)
                put("key", k); put("value", v)
            }
            db.insertWithOnConflict("custom_field_values", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
