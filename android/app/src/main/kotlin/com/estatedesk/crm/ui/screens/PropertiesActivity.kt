package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.ui.widgets.BottomNav
import com.estatedesk.crm.ui.widgets.PagedList
import com.estatedesk.crm.ui.widgets.Rows

class PropertiesActivity : BaseActivity() {

    private lateinit var list: PagedList
    private var q = ""
    private var saleRent = ""
    private var status = ""
    private var type = ""
    private var location = ""
    private var bedrooms = 0
    private var tags = ""
    private var sort = "newest"
    private var queryId = 0
    private var emptyView: View? = null
    private var chipsView: View? = null

    override fun build() {
        topBar(getString(R.string.properties_title), actions = listOf(
            R.drawable.ic_filter to { showFilters() },
            R.drawable.ic_sort to { showSort() }
        ))
        val search = Ui.input(this@PropertiesActivity, getString(R.string.search_hint), q)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                q = s?.toString()?.trim() ?: ""
                refresh()
            }
        })
        add(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@PropertiesActivity, 8)
        })
        addSaleRentChips()

        list = PagedList(this, 20)
        list.onLoadPage = { offset -> loadPage(offset) }
        add(list.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addFab { startActivity(Intent(this, PropertyFormActivity::class.java)) }
        BottomNav.attach(this, root, 2)
        refresh()
    }

    private fun addSaleRentChips() {
        val v = Ui.chipRow(this, listOf(
            getString(R.string.all) to (saleRent.isBlank()),
            getString(R.string.property_sale) to (saleRent == "Sale"),
            getString(R.string.property_rent) to (saleRent == "Rent")
        )) { i ->
            saleRent = when (i) { 1 -> "Sale"; 2 -> "Rent"; else -> "" }
            refreshChips()
            refresh()
        }
        chipsView = v
        add(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun refreshChips() {
        val idx = chipsView?.let { content.indexOfChild(it) } ?: return
        content.removeViewAt(idx)
        addSaleRentChips()
        val v = content.getChildAt(content.childCount - 1)
        content.removeViewAt(content.childCount - 1)
        content.addView(v, idx)
    }

    private fun refresh() {
        queryId++
        list.reset()
    }

    private fun loadPage(offset: Int) {
        val id = queryId
        Async.db({
            val props = Di.store.properties.query(
                q, saleRent, status, type, location, bedrooms, tags, sort, offset, 20
            )
            // pair cards into 2-column rows
            val cards = props.map { pr ->
                val photo = Di.store.properties.firstPhoto(pr.id)
                Rows.propertyCard(this@PropertiesActivity, pr, photo, {
                    startActivity(Intent(this@PropertiesActivity, PropertyDetailActivity::class.java)
                        .putExtra("id", pr.id))
                }, Ui.dp(this@PropertiesActivity, 164))
            }
            val rows = mutableListOf<View>()
            var i = 0
            while (i < cards.size) {
                if (i + 1 < cards.size) {
                    val row = Ui.hbox(this@PropertiesActivity)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.rightMargin = Ui.dp(this@PropertiesActivity, 10)
                    row.addView(cards[i], lp)
                    row.addView(cards[i + 1], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    rowLp.bottomMargin = Ui.dp(this@PropertiesActivity, 10)
                    rows.add(row.apply { layoutParams = rowLp })
                } else {
                    val row = Ui.hbox(this@PropertiesActivity)
                    row.addView(cards[i], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    rowLp.bottomMargin = Ui.dp(this@PropertiesActivity, 10)
                    rows.add(row.apply { layoutParams = rowLp })
                }
                i += 2
            }
            rows
        }) { views ->
            if (id != queryId || isFinishing) return@db
            if (offset == 0 && (views?.isEmpty() ?: true) && q.isBlank() && saleRent.isBlank()
                && status.isBlank() && type.isBlank() && location.isBlank() && bedrooms == 0
            ) {
                val empty = Ui.emptyState(this, R.drawable.ic_building, getString(R.string.property_no_props),
                    getString(R.string.property_no_props_hint), getString(R.string.qa_add_property)) {
                    startActivity(Intent(this, PropertyFormActivity::class.java))
                }
                list.markExhausted()
                list.view.visibility = View.GONE
                emptyView = empty
                content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                return@db
            }
            emptyView?.let { content.removeView(it); emptyView = null }
            list.view.visibility = View.VISIBLE
            list.addPage(views ?: emptyList())
        }
    }

    private fun showFilters() {
        val box = Ui.vbox(this)
        val typeOptions = listOf(getString(R.string.flt_any)) + Di.store.propertyTypes()
        val statusOptions = listOf(getString(R.string.flt_any)) + listOf(
            "Available", "Reserved", "Sold", "Rented", "Off Market", "Pending")
        box.addView(Ui.pickField(this, getString(R.string.flt_status),
            status.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_status), statusOptions) { i ->
                status = if (i == 0) "" else statusOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_type),
            type.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.flt_type), typeOptions) { i ->
                type = if (i == 0) "" else typeOptions[i]
            }
        })
        box.addView(Ui.pickField(this, getString(R.string.flt_bedrooms), bedroomsLabel()) {
            Ui.pick(this, getString(R.string.flt_bedrooms),
                listOf(getString(R.string.flt_any), "1+", "2+", "3+", "4+", "5+", "6+")) { i ->
                bedrooms = if (i == 0) 0 else i
            }
        })
        Ui.sheet(this, getString(R.string.filter), box, listOf(
            getString(R.string.clear) to Ui.Btn.DANGER,
            getString(R.string.apply_hint) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx == 0) {
                status = ""; type = ""; bedrooms = 0
            }
            refresh()
        }.show()
    }

    private fun bedroomsLabel(): String = if (bedrooms == 0) getString(R.string.flt_any) else "$bedrooms+"

    private fun showSort() {
        val options = listOf(
            getString(R.string.sort_newest), getString(R.string.sort_oldest),
            getString(R.string.sort_price_low), getString(R.string.sort_price_high)
        )
        val keys = listOf("newest", "oldest", "price_low", "price_high")
        Ui.pick(this, getString(R.string.sort), options, keys.indexOf(sort)) { i ->
            sort = keys[i]
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }
}
