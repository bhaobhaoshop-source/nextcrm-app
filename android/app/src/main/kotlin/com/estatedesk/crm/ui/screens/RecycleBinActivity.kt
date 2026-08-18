package com.estatedesk.crm.ui.screens

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui

class RecycleBinActivity : BaseActivity() {

    private data class Item(val table: String, val id: Long, val label: String, val deletedAt: Long)

    override fun build() {
        topBar(getString(R.string.bin_title), actions = listOf(
            R.drawable.ic_delete to { purgeAll() }
        ))
        load()
    }

    private fun load() {
        Async.db({
            val st = Di.store
            val items = mutableListOf<Item>()
            st.contacts.allDeleted().forEach { (c, at) ->
                items.add(Item("contacts", c.id, c.displayName(), at))
            }
            st.leads.allDeleted().forEach { (l, at) ->
                items.add(Item("leads", l.id, l.displayTitle(), at))
            }
            st.properties.allDeleted().forEach { (p, at) ->
                items.add(Item("properties", p.id, p.displayTitle(), at))
            }
            st.deals.allDeleted().forEach { (d, at) ->
                items.add(Item("deals", d.id, d.displayTitle(), at))
            }
            st.tasks.allDeleted().forEach { (t, at) ->
                items.add(Item("tasks", t.id, t.title, at))
            }
            st.finances.allDeleted().forEach { (f, at) ->
                items.add(Item("finances", f.id, "${f.direction} · ${f.category}", at))
            }
            items.sortedByDescending { it.deletedAt }
        }) { items ->
            if (items == null || isFinishing) return@db
            content.removeAllViews()
            topBar(getString(R.string.bin_title), actions = listOf(
                R.drawable.ic_delete to { purgeAll() }
            ))
            if (items.isEmpty()) {
                add(Ui.emptyState(this, R.drawable.ic_refresh, getString(R.string.bin_empty),
                    getString(R.string.bin_empty_hint)))
                return@db
            }
            val card = Ui.card(this, emptyList(), padding = 4)
            items.forEach { it ->
                val row = Ui.hbox(this)
                row.setPadding(Ui.dp(this@RecycleBinActivity, 10), Ui.dp(this@RecycleBinActivity, 12), Ui.dp(this@RecycleBinActivity, 10), Ui.dp(this@RecycleBinActivity, 12))
                row.addView(Ui.icon(this@RecycleBinActivity, R.drawable.ic_doc, 16, p.textTertiary))
                row.addView(Ui.hspacer(this, 10))
                val mid = Ui.vbox(this)
                mid.addView(Ui.tv(this@RecycleBinActivity, it.label, 14, p.textPrimary, Ui.Font.MEDIUM, 1))
                mid.addView(Ui.tv(this@RecycleBinActivity, "${it.table} · ${getString(R.string.bin_item_deleted, Fmt.relative(it.deletedAt))}",
                    11, p.textTertiary))
                row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.btn(this@RecycleBinActivity, getString(R.string.restore), Ui.Btn.TEXT) {
                    restore(it)
                })
                card.addView(row)
                if (it != items.last()) card.addView(Ui.divider(this))
            }
            add(card)
        }
    }

    private fun restore(item: Item) {
        Async.write({ Di.store.restoreRecord(item.table, item.id) }) {
            snack(getString(R.string.restored))
            load()
        }
    }

    private fun purgeAll() {
        Ui.alert(this, getString(R.string.bin_purge_all), getString(R.string.bin_purge_confirm),
            getString(R.string.confirm)) {
            Async.write({ Di.store.purgeOld(0) }) {
                snack(getString(R.string.deleted_forever))
                load()
            }
        }
    }
}
