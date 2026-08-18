package com.estatedesk.crm.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite database — the single source of truth. WAL journaling for
 * durability + read concurrency; migrations are explicit and never wipe data.
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "estatedesk.db", null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(false) // soft references by design
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE contacts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                first_name TEXT NOT NULL DEFAULT '', last_name TEXT NOT NULL DEFAULT '',
                full_name TEXT NOT NULL DEFAULT '', avatar_path TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '',
                city TEXT NOT NULL DEFAULT '', area TEXT NOT NULL DEFAULT '',
                classification TEXT NOT NULL DEFAULT 'Buyer', lead_status TEXT NOT NULL DEFAULT 'New',
                temperature TEXT NOT NULL DEFAULT 'Warm', priority INTEGER NOT NULL DEFAULT 1,
                budget_min INTEGER NOT NULL DEFAULT 0, budget_max INTEGER NOT NULL DEFAULT 0,
                preferred_location TEXT NOT NULL DEFAULT '', preferred_type TEXT NOT NULL DEFAULT '',
                lead_source TEXT NOT NULL DEFAULT '', assigned_to TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '', last_contacted INTEGER NOT NULL DEFAULT 0,
                next_follow_up INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_contacts_name ON contacts(full_name)")
        db.execSQL("CREATE INDEX idx_contacts_deleted ON contacts(deleted_at)")

        db.execSQL(
            """CREATE TABLE phones(
                id INTEGER PRIMARY KEY AUTOINCREMENT, contact_id INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT 'Mobile', number TEXT NOT NULL DEFAULT '',
                is_whatsapp INTEGER NOT NULL DEFAULT 0,
                UNIQUE(contact_id, number))"""
        )
        db.execSQL("CREATE INDEX idx_phones_contact ON phones(contact_id)")

        db.execSQL(
            "CREATE TABLE contact_tags(tag TEXT NOT NULL, contact_id INTEGER NOT NULL, PRIMARY KEY(tag, contact_id))"
        )

        db.execSQL(
            """CREATE TABLE leads(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '', contact_id INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT '', requirement TEXT NOT NULL DEFAULT '',
                budget_min INTEGER NOT NULL DEFAULT 0, budget_max INTEGER NOT NULL DEFAULT 0,
                location TEXT NOT NULL DEFAULT '', property_type TEXT NOT NULL DEFAULT '',
                intent TEXT NOT NULL DEFAULT 'Buy', stage TEXT NOT NULL DEFAULT 'New Lead',
                score INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 1,
                probability INTEGER NOT NULL DEFAULT 10, next_action TEXT NOT NULL DEFAULT '',
                next_follow_up INTEGER NOT NULL DEFAULT 0, assigned_to TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER, deal_id INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_leads_stage ON leads(stage)")
        db.execSQL("CREATE INDEX idx_leads_follow ON leads(next_follow_up)")
        db.execSQL("CREATE INDEX idx_leads_deleted ON leads(deleted_at)")

        db.execSQL(
            """CREATE TABLE properties(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '', type TEXT NOT NULL DEFAULT '',
                sale_rent TEXT NOT NULL DEFAULT 'Sale', price INTEGER NOT NULL DEFAULT 0,
                currency TEXT NOT NULL DEFAULT '', location TEXT NOT NULL DEFAULT '',
                area_name TEXT NOT NULL DEFAULT '', size_value REAL NOT NULL DEFAULT 0,
                size_unit TEXT NOT NULL DEFAULT 'Marla', bedrooms INTEGER NOT NULL DEFAULT 0,
                bathrooms INTEGER NOT NULL DEFAULT 0, floors INTEGER NOT NULL DEFAULT 0,
                condition TEXT NOT NULL DEFAULT '', furnished INTEGER NOT NULL DEFAULT 0,
                owner_name TEXT NOT NULL DEFAULT '', owner_phone TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'Available', description TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '', assigned_to TEXT NOT NULL DEFAULT '',
                date_added INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_props_status ON properties(status)")
        db.execSQL("CREATE INDEX idx_props_deleted ON properties(deleted_at)")

        db.execSQL(
            """CREATE TABLE property_photos(
                id INTEGER PRIMARY KEY AUTOINCREMENT, property_id INTEGER NOT NULL,
                path TEXT NOT NULL DEFAULT '', caption TEXT NOT NULL DEFAULT '',
                sort INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_photos_prop ON property_photos(property_id)")

        db.execSQL(
            """CREATE TABLE deals(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '', buyer_contact_id INTEGER NOT NULL DEFAULT 0,
                seller_contact_id INTEGER NOT NULL DEFAULT 0, property_id INTEGER NOT NULL DEFAULT 0,
                lead_id INTEGER NOT NULL DEFAULT 0, value INTEGER NOT NULL DEFAULT 0,
                currency TEXT NOT NULL DEFAULT '', commission_pct REAL NOT NULL DEFAULT 0,
                commission_amount INTEGER NOT NULL DEFAULT 0, commission_received INTEGER NOT NULL DEFAULT 0,
                stage TEXT NOT NULL DEFAULT 'Negotiation', expected_close INTEGER NOT NULL DEFAULT 0,
                actual_close INTEGER, notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_deals_stage ON deals(stage)")
        db.execSQL("CREATE INDEX idx_deals_deleted ON deals(deleted_at)")

        db.execSQL(
            """CREATE TABLE tasks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL DEFAULT 'Task',
                contact_id INTEGER NOT NULL DEFAULT 0, property_id INTEGER NOT NULL DEFAULT 0,
                lead_id INTEGER NOT NULL DEFAULT 0, deal_id INTEGER NOT NULL DEFAULT 0,
                due_date INTEGER NOT NULL DEFAULT 0, due_time INTEGER NOT NULL DEFAULT -1,
                priority INTEGER NOT NULL DEFAULT 1, status TEXT NOT NULL DEFAULT 'Open',
                reminder INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER, deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_tasks_due ON tasks(due_date, status)")
        db.execSQL("CREATE INDEX idx_tasks_deleted ON tasks(deleted_at)")

        db.execSQL(
            """CREATE TABLE activities(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL DEFAULT '', title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '', at INTEGER NOT NULL DEFAULT 0,
                contact_id INTEGER NOT NULL DEFAULT 0, property_id INTEGER NOT NULL DEFAULT 0,
                lead_id INTEGER NOT NULL DEFAULT 0, deal_id INTEGER NOT NULL DEFAULT 0,
                task_id INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_act_time ON activities(at)")
        db.execSQL("CREATE INDEX idx_act_contact ON activities(contact_id)")
        db.execSQL("CREATE INDEX idx_act_prop ON activities(property_id)")
        db.execSQL("CREATE INDEX idx_act_lead ON activities(lead_id)")
        db.execSQL("CREATE INDEX idx_act_deal ON activities(deal_id)")

        db.execSQL(
            """CREATE TABLE finances(
                id INTEGER PRIMARY KEY AUTOINCREMENT, direction TEXT NOT NULL DEFAULT 'Expense',
                category TEXT NOT NULL DEFAULT '', amount INTEGER NOT NULL DEFAULT 0,
                currency TEXT NOT NULL DEFAULT '', date INTEGER NOT NULL DEFAULT 0,
                deal_id INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_fin_date ON finances(date)")

        db.execSQL(
            """CREATE TABLE documents(
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '',
                path TEXT NOT NULL DEFAULT '', mime TEXT NOT NULL DEFAULT '',
                size INTEGER NOT NULL DEFAULT 0, entity_type TEXT NOT NULL DEFAULT '',
                entity_id INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER)"""
        )
        db.execSQL("CREATE INDEX idx_docs_entity ON documents(entity_type, entity_id)")

        db.execSQL(
            """CREATE TABLE custom_field_defs(
                key TEXT PRIMARY KEY, label TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL DEFAULT 'Text', options TEXT NOT NULL DEFAULT '',
                entities TEXT NOT NULL DEFAULT '', sort INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE custom_field_values(
                entity_type TEXT NOT NULL, entity_id INTEGER NOT NULL, key TEXT NOT NULL,
                value TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(entity_type, entity_id, key))"""
        )

        db.execSQL(
            """CREATE VIRTUAL TABLE search_index USING fts4(
                type TEXT NOT NULL, entity_id INTEGER NOT NULL,
                name TEXT, detail TEXT)"""
        )

        db.execSQL(
            """CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations add ALTER TABLE / CREATE TABLE steps here, guarded
        // by version numbers. Never drop tables, never wipe data.
        var v = oldVersion
        if (v < 2) {
            // example for a future schema change:
            // db.execSQL("ALTER TABLE contacts ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
            v = 2
        }
    }

    companion object {
        const val DB_VERSION = 1

        @Volatile
        private var instance: Db? = null

        fun open(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }

        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        fun helper(ctx: Context): SQLiteDatabase = open(ctx).writableDatabase
    }
}
