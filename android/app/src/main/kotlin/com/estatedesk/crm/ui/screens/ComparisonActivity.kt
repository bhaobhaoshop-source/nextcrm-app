package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Property

class ComparisonActivity : BaseActivity() {

    override fun build() {
        topBar(getString(R.string.comparison_title), actions = listOf(
            R.drawable.ic_delete to { clearAll() }
        ))
        val ids = Prefs(this).str(Prefs.KEY_COMPARE_IDS, "").split(",")
            .filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) {
            add(Ui.emptyState(this, R.drawable.ic_swap, getString(R.string.comparison_empty),
                getString(R.string.comparison_empty_hint)))
            return
        }
        Async.db({
            ids.mapNotNull { Di.store.properties.byId(it) }
        }) { props ->
            if (props == null || isFinishing) return@db
            render(props)
        }
    }

    private fun render(props: List<Property>) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add(Ui.caption(this@ComparisonActivity, getString(R.string.comparison_remove_hint)))
        add(Ui.spacer(this, 8))

        // label column
        val labelCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ComparisonActivity, 0), Ui.dp(this@ComparisonActivity, 8), 0, 0)
        }
        val labels = listOf(
            getString(R.string.comparison_price), getString(R.string.property_type),
            getString(R.string.property_sale_rent), getString(R.string.property_location),
            getString(R.string.property_size), getString(R.string.property_bedrooms),
            getString(R.string.property_bathrooms), getString(R.string.property_floors),
            getString(R.string.property_condition), getString(R.string.property_furnished),
            getString(R.string.property_status), getString(R.string.property_date_added)
        )
        val spacer = Ui.tv(this@ComparisonActivity, getString(R.string.property_title), 15, p.textPrimary, Ui.Font.BOLD)
        spacer.setPadding(0, Ui.dp(this@ComparisonActivity, 8), 0, Ui.dp(this@ComparisonActivity, 8))
        labelCol.addView(spacer, LinearLayout.LayoutParams(Ui.dp(this@ComparisonActivity, 150), Ui.dp(this@ComparisonActivity, 60)))
        labels.forEach { l ->
            labelCol.addView(Ui.tv(this@ComparisonActivity, l, 13, p.textTertiary),
                LinearLayout.LayoutParams(Ui.dp(this@ComparisonActivity, 150), Ui.dp(this@ComparisonActivity, 44)))
        }
        row.addView(labelCol)

        props.forEachIndexed { i, prop ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = Ui.tv(this@ComparisonActivity, prop.displayTitle(), 14, p.textPrimary, Ui.Font.BOLD, 2)
            title.setPadding(Ui.dp(this@ComparisonActivity, 8), Ui.dp(this@ComparisonActivity, 8), Ui.dp(this@ComparisonActivity, 8), Ui.dp(this@ComparisonActivity, 8))
            col.addView(title, LinearLayout.LayoutParams(Ui.dp(this@ComparisonActivity, 170), Ui.dp(this@ComparisonActivity, 60)))
            val values = listOf(
                Fmt.money(prop.price, Di.store.currency()),
                prop.type,
                prop.saleRent,
                prop.areaName.ifBlank { prop.location },
                Fmt.sizeText(prop.sizeValue, prop.sizeUnit),
                prop.bedrooms.toString(),
                prop.bathrooms.toString(),
                prop.floors.toString(),
                prop.condition,
                if (prop.furnished) getString(R.string.property_furnished) else getString(R.string.property_unfurnished),
                prop.status,
                Fmt.date(prop.dateAdded)
            )
            values.forEachIndexed { vi, v ->
                val cell = Ui.tv(this@ComparisonActivity, v.ifBlank { "—" }, 13, p.textPrimary, Ui.Font.MEDIUM)
                cell.gravity = Gravity.CENTER_VERTICAL
                cell.setPadding(Ui.dp(this@ComparisonActivity, 8), 0, Ui.dp(this@ComparisonActivity, 8), 0)
                val bg = if (vi % 2 == 0) p.surface else p.surface2
                cell.setBackgroundColor(bg)
                col.addView(cell, LinearLayout.LayoutParams(Ui.dp(this@ComparisonActivity, 170), Ui.dp(this@ComparisonActivity, 44)))
            }
            col.setOnClickListener {
                Ui.alert(this, getString(R.string.comparison_remove_hint),
                    prop.displayTitle(), getString(R.string.remove)) {
                    val prefs = Prefs(this)
                    val cur = prefs.str(Prefs.KEY_COMPARE_IDS, "").split(",")
                        .filter { it.isNotBlank() && it != prop.id.toString() }
                    prefs.set(Prefs.KEY_COMPARE_IDS, cur.joinToString(","))
                    content.removeAllViews()
                    topBar(getString(R.string.comparison_title), actions = listOf(
                        R.drawable.ic_delete to { clearAll() }
                    ))
                    if (cur.isEmpty()) {
                        add(Ui.emptyState(this, R.drawable.ic_swap, getString(R.string.comparison_empty),
                            getString(R.string.comparison_empty_hint)))
                    } else {
                        Async.db({ cur.mapNotNull { Di.store.properties.byId(it.toLongOrNull() ?: 0) } }) { p2 ->
                            if (p2 != null && isFinishing.not()) render(p2)
                        }
                    }
                }
            }
            row.addView(col)
        }
        scroll.addView(row)
        add(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(Ui.spacer(this, 40))
    }

    private fun clearAll() {
        Ui.alert(this, getString(R.string.comparison_clear),
            getString(R.string.comparison_empty), getString(R.string.confirm)) {
            Prefs(this).set(Prefs.KEY_COMPARE_IDS, "")
            content.removeAllViews()
            topBar(getString(R.string.comparison_title))
            add(Ui.emptyState(this, R.drawable.ic_swap, getString(R.string.comparison_empty),
                getString(R.string.comparison_empty_hint)))
        }
    }
}
