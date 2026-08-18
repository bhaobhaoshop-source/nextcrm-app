package com.estatedesk.crm.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.estatedesk.crm.domain.PipelineConfig
import org.json.JSONArray
import org.json.JSONObject

/** All persistence goes through this class. One instance per process; every
 *  call must run on Async.db (see core/Async.kt). */
class Store(db: SQLiteDatabase) {

    /** Raw handle for the DAOs (package-private by convention; all access
     *  still flows through Async.db's single thread). */
    internal val db: SQLiteDatabase = db

    // DAOs — one instance per process
    val contacts = ContactDao(db, this)
    val leads = LeadDao(db, this)
    val properties = PropertyDao(db, this)
    val deals = DealDao(db, this)
    val tasks = TaskDao(db, this)
    val activities = ActivityDao(db, this)
    val finances = FinanceDao(db, this)
    val docs = DocumentDao(db, this)
    val custom = CustomDao(db)
    val search = SearchDao(db, this)
    val stats = StatsDao(db)
    val importer = com.estatedesk.crm.domain.ImportExport(
        this, contacts, leads, properties, deals, tasks, finances
    )
    val sampleData = com.estatedesk.crm.domain.SampleData(
        this, contacts, leads, properties, deals, tasks, finances
    )

    fun now(): Long = System.currentTimeMillis()

    // ------------------------------------------------------------ settings

    fun getSetting(key: String, def: String = ""): String {
        db.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) ?: def else def
        }
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key); put("value", value)
        }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSettingLong(key: String, def: Long = 0): Long =
        getSetting(key, def.toString()).toLongOrNull() ?: def

    fun getSettingInt(key: String, def: Int = 0): Int =
        getSetting(key, def.toString()).toIntOrNull() ?: def

    fun getSettingBool(key: String, def: Boolean = false): Boolean =
        getSetting(key, if (def) "1" else "0") == "1"

    // ------------------------------------------------------------ currency

    fun currency(): CurrencyCfg {
        val code = getSetting("currency_code", "PKR")
        val sym = getSetting("currency_symbol", "Rs")
        val dec = getSettingInt("currency_decimals", 0)
        val prefix = getSettingBool("currency_prefix", true)
        return CurrencyCfg(code, sym, dec, prefix)
    }

    fun saveCurrency(c: CurrencyCfg) {
        setSetting("currency_code", c.code)
        setSetting("currency_symbol", c.symbol)
        setSetting("currency_decimals", c.decimals.toString())
        setSetting("currency_prefix", if (c.prefix) "1" else "0")
    }

    // ------------------------------------------------------------ pipeline config

    fun leadStages(): List<StageDef> {
        val raw = getSetting("lead_stages")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_LEAD_STAGES
        return parseStages(raw).ifEmpty { PipelineConfig.DEFAULT_LEAD_STAGES }
    }

    fun dealStages(): List<StageDef> {
        val raw = getSetting("deal_stages")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_DEAL_STAGES
        return parseStages(raw).ifEmpty { PipelineConfig.DEFAULT_DEAL_STAGES }
    }

    fun saveLeadStages(stages: List<StageDef>) = setSetting("lead_stages", serializeStages(stages))
    fun saveDealStages(stages: List<StageDef>) = setSetting("deal_stages", serializeStages(stages))

    fun leadStageNames(): List<String> = leadStages().map { it.name }
    fun dealStageNames(): List<String> = dealStages().map { it.name }

    fun stageProbability(stage: String, deal: Boolean = false): Int {
        val list = if (deal) dealStages() else leadStages()
        return list.firstOrNull { it.name == stage }?.probability ?: 0
    }

    private fun parseStages(raw: String): List<StageDef> {
        val out = mutableListOf<StageDef>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    StageDef(
                        o.optString("name"),
                        o.optInt("probability", 10),
                        o.optString("color")
                    )
                )
            }
        } catch (t: Throwable) {
        }
        return out
    }

    private fun serializeStages(stages: List<StageDef>): String {
        val arr = JSONArray()
        for (s in stages) {
            arr.put(
                JSONObject()
                    .put("name", s.name)
                    .put("probability", s.probability)
                    .put("color", s.color)
            )
        }
        return arr.toString()
    }

    // ------------------------------------------------------------ picklists

    fun leadSources(): List<String> {
        val raw = getSetting("pick_sources")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_SOURCES
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun propertyTypes(): List<String> {
        val raw = getSetting("pick_types")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_PROPERTY_TYPES
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun tags(): List<String> {
        val raw = getSetting("pick_tags")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_TAGS
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun expenseCategories(): List<String> {
        val raw = getSetting("pick_expense_cats")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_EXPENSE_CATS
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun incomeCategories(): List<String> {
        val raw = getSetting("pick_income_cats")
        if (raw.isBlank()) return PipelineConfig.DEFAULT_INCOME_CATS
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun savePicklist(key: String, values: List<String>) {
        setSetting(key, values.joinToString("\n"))
    }

    fun addPicklistValue(key: String, value: String): List<String> {
        val cur = when (key) {
            "pick_sources" -> leadSources()
            "pick_types" -> propertyTypes()
            "pick_tags" -> tags()
            "pick_expense_cats" -> expenseCategories()
            "pick_income_cats" -> incomeCategories()
            else -> emptyList()
        }.toMutableList()
        if (value.isNotBlank() && cur.none { it.equals(value, true) }) cur.add(value)
        savePicklist(key, cur)
        return cur
    }

    fun removePicklistValue(key: String, value: String): List<String> {
        val cur = when (key) {
            "pick_sources" -> leadSources()
            "pick_types" -> propertyTypes()
            "pick_tags" -> tags()
            "pick_expense_cats" -> expenseCategories()
            "pick_income_cats" -> incomeCategories()
            else -> emptyList()
        }.filter { it != value }
        savePicklist(key, cur)
        return cur
    }

    // ------------------------------------------------------------ profile

    fun profileName(): String = getSetting("profile_name")
    fun profileCompany(): String = getSetting("profile_company", "EstateDesk")
    fun saveProfile(name: String, phone: String, email: String, company: String) {
        setSetting("profile_name", name)
        setSetting("profile_phone", phone)
        setSetting("profile_email", email)
        setSetting("profile_company", company)
    }

    // ------------------------------------------------------------ misc helpers

    fun rowCount(table: String, includeDeleted: Boolean = false): Long {
        val q = if (includeDeleted) "SELECT COUNT(*) FROM $table"
        else "SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL"
        db.rawQuery(q, null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    fun distinctValues(column: String, table: String, where: String = ""): List<String> {
        val q = "SELECT DISTINCT $column FROM $table WHERE $column != '' $where ORDER BY $column"
        val out = mutableListOf<String>()
        db.rawQuery(q, null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) ?: "")
        }
        return out
    }

    fun clearSearchEntry(type: String, entityId: Long) {
        db.execSQL("DELETE FROM search_index WHERE type=? AND entity_id=?", arrayOf(type, entityId.toString()))
    }

    fun indexSearch(type: String, entityId: Long, name: String, detail: String) {
        clearSearchEntry(type, entityId)
        val cv = ContentValues().apply {
            put("type", type); put("entity_id", entityId)
            put("name", name); put("detail", detail)
        }
        db.insert("search_index", null, cv)
    }

    fun markDeleted(table: String, id: Long): Boolean {
        val cv = ContentValues().apply { put("deleted_at", now()) }
        val n = db.update(table, cv, "id=?", arrayOf(id.toString()))
        return n > 0
    }

    fun restoreRecord(table: String, id: Long): Boolean {
        val cv = ContentValues().apply { putNull("deleted_at") }
        return db.update(table, cv, "id=?", arrayOf(id.toString())) > 0
    }

    fun purgeRecord(table: String, id: Long) {
        db.delete(table, "id=?", arrayOf(id.toString()))
    }

    fun purgeOld(olderThanMillis: Long) {
        val cutoff = now() - olderThanMillis
        for (t in listOf("contacts", "leads", "properties", "deals", "tasks", "finances", "documents")) {
            db.delete(t, "deleted_at IS NOT NULL AND deleted_at < ?", arrayOf(cutoff.toString()))
        }
    }

    fun <T> map(c: Cursor, fn: (Cursor) -> T): List<T> {
        val out = mutableListOf<T>()
        while (c.moveToNext()) out.add(fn(c))
        return out
    }
}
