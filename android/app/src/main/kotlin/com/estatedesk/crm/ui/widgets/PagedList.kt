package com.estatedesk.crm.ui.widgets

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import com.estatedesk.crm.core.Ui

/** ListView with incremental pagination — designed for 10k+ rows.
 *  The screen assigns [onLoadPage] to fetch a page (typically via Async.db)
 *  and then calls [addPage] with the built views. */
class PagedList(private val ctx: Context, private val pageSize: Int) {

    private val rows = mutableListOf<View>()
    private var loading = false
    private var exhausted = false

    var onLoadPage: ((offset: Int) -> Unit)? = null

    val view: ListView = ListView(ctx).apply {
        divider = null
        dividerHeight = 0
        setBackgroundColor(Ui.pal().bg)
        clipToPadding = false
        setPadding(0, 0, 0, Ui.dp(ctx, 8))
        setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView, state: Int) {}
            override fun onScroll(v: AbsListView, first: Int, visible: Int, total: Int) {
                if (total > 0 && first + visible >= total - 3) requestMore()
            }
        })
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = rows.size + (if (!exhausted) 1 else 0)
            override fun getItem(position: Int): Any = rows.getOrNull(position) ?: Unit
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                if (position < rows.size) return rows[position]
                return LinearLayout(ctx).apply {
                    setPadding(0, Ui.dp(ctx, 18), 0, Ui.dp(ctx, 18))
                    gravity = Gravity.CENTER
                    addView(Ui.tv(ctx, "Loading…", 13, Ui.pal().textTertiary))
                }
            }
        }
    }

    fun reset() {
        rows.clear()
        exhausted = false
        loading = false
        notifyChanged()
        requestMore()
    }

    fun addPage(views: List<View>) {
        loading = false
        if (views.isEmpty()) exhausted = true
        rows.addAll(views)
        notifyChanged()
    }

    fun markExhausted() {
        exhausted = true
        loading = false
        notifyChanged()
    }

    fun requestMore() {
        if (loading || exhausted) return
        loading = true
        onLoadPage?.invoke(rows.size)
    }

    fun isEmpty(): Boolean = rows.isEmpty()

    private fun notifyChanged() = (view.adapter as BaseAdapter).notifyDataSetChanged()
}
