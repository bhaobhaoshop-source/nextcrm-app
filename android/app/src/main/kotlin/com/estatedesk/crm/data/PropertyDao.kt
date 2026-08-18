package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class PropertyDao(private val db: SQLiteDatabase, private val s: Store) {

    private fun row(c: Cursor): Property = Property(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        type = c.getString(c.getColumnIndexOrThrow("type")),
        saleRent = c.getString(c.getColumnIndexOrThrow("sale_rent")),
        price = c.getLong(c.getColumnIndexOrThrow("price")),
        currency = c.getString(c.getColumnIndexOrThrow("currency")),
        location = c.getString(c.getColumnIndexOrThrow("location")),
        areaName = c.getString(c.getColumnIndexOrThrow("area_name")),
        sizeValue = c.getDouble(c.getColumnIndexOrThrow("size_value")),
        sizeUnit = c.getString(c.getColumnIndexOrThrow("size_unit")),
        bedrooms = c.getInt(c.getColumnIndexOrThrow("bedrooms")),
        bathrooms = c.getInt(c.getColumnIndexOrThrow("bathrooms")),
        floors = c.getInt(c.getColumnIndexOrThrow("floors")),
        condition = c.getString(c.getColumnIndexOrThrow("condition")),
        furnished = c.getInt(c.getColumnIndexOrThrow("furnished")) == 1,
        ownerName = c.getString(c.getColumnIndexOrThrow("owner_name")),
        ownerPhone = c.getString(c.getColumnIndexOrThrow("owner_phone")),
        status = c.getString(c.getColumnIndexOrThrow("status")),
        description = c.getString(c.getColumnIndexOrThrow("description")),
        tags = c.getString(c.getColumnIndexOrThrow("tags")),
        assignedTo = c.getString(c.getColumnIndexOrThrow("assigned_to")),
        dateAdded = c.getLong(c.getColumnIndexOrThrow("date_added")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        deletedAt = if (c.isNull(c.getColumnIndexOrThrow("deleted_at"))) null
        else c.getLong(c.getColumnIndexOrThrow("deleted_at"))
    )

    fun byId(id: Long): Property? {
        db.rawQuery("SELECT * FROM properties WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) row(c) else null
        }
    }

    fun save(p: Property): Long {
        val now = s.now()
        val cv = ContentValues().apply {
            put("title", p.title)
            put("type", p.type)
            put("sale_rent", p.saleRent)
            put("price", p.price)
            put("currency", p.currency)
            put("location", p.location)
            put("area_name", p.areaName)
            put("size_value", p.sizeValue)
            put("size_unit", p.sizeUnit)
            put("bedrooms", p.bedrooms)
            put("bathrooms", p.bathrooms)
            put("floors", p.floors)
            put("condition", p.condition)
            put("furnished", if (p.furnished) 1 else 0)
            put("owner_name", p.ownerName)
            put("owner_phone", p.ownerPhone)
            put("status", p.status)
            put("description", p.description)
            put("tags", p.tags)
            put("assigned_to", p.assignedTo)
            put("updated_at", now)
        }
        val id: Long
        if (p.id == 0L) {
            cv.put("date_added", now)
            id = db.insertOrThrow("properties", null, cv)
        } else {
            id = p.id
            db.update("properties", cv, "id=?", arrayOf(id.toString()))
        }
        s.indexSearch(
            "property", id, p.displayTitle(),
            listOf(p.location, p.areaName, p.type, p.status, p.description, p.tags).joinToString(" ")
        )
        return id
    }

    fun setStatus(id: Long, status: String) {
        val cv = ContentValues().apply { put("status", status); put("updated_at", s.now()) }
        db.update("properties", cv, "id=?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------ photos

    fun photos(propertyId: Long): List<PropertyPhoto> {
        db.rawQuery(
            "SELECT * FROM property_photos WHERE property_id=? ORDER BY sort, id",
            arrayOf(propertyId.toString())
        ).use { c ->
            return s.map(c) {
                PropertyPhoto(
                    it.getLong(it.getColumnIndexOrThrow("id")),
                    it.getLong(it.getColumnIndexOrThrow("property_id")),
                    it.getString(it.getColumnIndexOrThrow("path")),
                    it.getString(it.getColumnIndexOrThrow("caption")),
                    it.getInt(it.getColumnIndexOrThrow("sort"))
                )
            }
        }
    }

    fun addPhoto(propertyId: Long, path: String): Long {
        val next = db.rawQuery(
            "SELECT COALESCE(MAX(sort), -1) + 1 FROM property_photos WHERE property_id=?",
            arrayOf(propertyId.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val cv = ContentValues().apply {
            put("property_id", propertyId); put("path", path); put("sort", next)
        }
        return db.insertOrThrow("property_photos", null, cv)
    }

    fun deletePhoto(id: Long) {
        db.delete("property_photos", "id=?", arrayOf(id.toString()))
    }

    fun firstPhoto(propertyId: Long): String {
        db.rawQuery(
            "SELECT path FROM property_photos WHERE property_id=? ORDER BY sort, id LIMIT 1",
            arrayOf(propertyId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getString(0) else "" }
    }

    // ------------------------------------------------------------ queries

    fun query(
        q: String, saleRent: String, status: String, type: String, location: String,
        bedrooms: Int, tags: String, sort: String, offset: Int, limit: Int
    ): List<Property> {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR location LIKE ? OR area_name LIKE ? OR description LIKE ?)")
            val like = "%$q%"; repeat(4) { args.add(like) }
        }
        if (saleRent.isNotBlank()) { where.append(" AND sale_rent=?"); args.add(saleRent) }
        if (status.isNotBlank()) { where.append(" AND status=?"); args.add(status) }
        if (type.isNotBlank()) { where.append(" AND type=?"); args.add(type) }
        if (location.isNotBlank()) { where.append(" AND (location=? OR area_name=?)"); args.add(location); args.add(location) }
        if (bedrooms > 0) { where.append(" AND bedrooms>=?"); args.add(bedrooms.toString()) }
        if (tags.isNotBlank()) { where.append(" AND tags LIKE ?"); args.add("%$tags%") }
        val order = when (sort) {
            "oldest" -> "date_added ASC"
            "price_low" -> "price ASC"
            "price_high" -> "price DESC"
            else -> "updated_at DESC"
        }
        val sql = "SELECT * FROM properties WHERE $where ORDER BY $order LIMIT ? OFFSET ?"
        args.add(limit.toString()); args.add(offset.toString())
        db.rawQuery(sql, args.toTypedArray()).use { c -> return s.map(c) { row(it) } }
    }

    fun count(
        q: String, saleRent: String, status: String, type: String, location: String,
        bedrooms: Int, tags: String
    ): Int {
        val where = StringBuilder("deleted_at IS NULL")
        val args = mutableListOf<String>()
        if (q.isNotBlank()) {
            where.append(" AND (title LIKE ? OR location LIKE ? OR area_name LIKE ? OR description LIKE ?)")
            val like = "%$q%"; repeat(4) { args.add(like) }
        }
        if (saleRent.isNotBlank()) { where.append(" AND sale_rent=?"); args.add(saleRent) }
        if (status.isNotBlank()) { where.append(" AND status=?"); args.add(status) }
        if (type.isNotBlank()) { where.append(" AND type=?"); args.add(type) }
        if (location.isNotBlank()) { where.append(" AND (location=? OR area_name=?)"); args.add(location); args.add(location) }
        if (bedrooms > 0) { where.append(" AND bedrooms>=?"); args.add(bedrooms.toString()) }
        if (tags.isNotBlank()) { where.append(" AND tags LIKE ?"); args.add("%$tags%") }
        db.rawQuery("SELECT COUNT(*) FROM properties WHERE $where", args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun allForPicker(): List<Property> {
        db.rawQuery("SELECT * FROM properties WHERE deleted_at IS NULL ORDER BY updated_at DESC", null).use { c ->
            return s.map(c) { row(it) }
        }
    }

    fun allDeleted(): List<Pair<Property, Long>> {
        db.rawQuery("SELECT * FROM properties WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", null).use { c ->
            return s.map(c) { row(it) to it.getLong(it.getColumnIndexOrThrow("deleted_at")) }
        }
    }

    fun byOwnerPhone(phone: String): List<Property> {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 6) return emptyList()
        db.rawQuery(
            "SELECT * FROM properties WHERE deleted_at IS NULL AND replace(replace(replace(owner_phone,' ',''),'-',''),'+','')=?",
            arrayOf(digits)
        ).use { c -> return s.map(c) { row(it) } }
    }
}
