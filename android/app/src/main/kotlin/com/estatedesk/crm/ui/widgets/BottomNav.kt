package com.estatedesk.crm.ui.widgets

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.ui.screens.ContactsActivity
import com.estatedesk.crm.ui.screens.DealsActivity
import com.estatedesk.crm.ui.screens.HomeActivity
import com.estatedesk.crm.ui.screens.LeadsActivity
import com.estatedesk.crm.ui.screens.PropertiesActivity

/** Shared bottom navigation. Each tab is its own Activity; switching uses
 *  REORDER_TO_FRONT so state (scroll, filters) survives. */
object BottomNav {

    data class Tab(val label: Int, val icon: Int, val cls: Class<out Activity>)

    val TABS = listOf(
        Tab(com.estatedesk.crm.R.string.nav_home, com.estatedesk.crm.R.drawable.ic_home, HomeActivity::class.java),
        Tab(com.estatedesk.crm.R.string.nav_leads, com.estatedesk.crm.R.drawable.ic_flag, LeadsActivity::class.java),
        Tab(com.estatedesk.crm.R.string.nav_properties, com.estatedesk.crm.R.drawable.ic_building, PropertiesActivity::class.java),
        Tab(com.estatedesk.crm.R.string.nav_contacts, com.estatedesk.crm.R.drawable.ic_people, ContactsActivity::class.java),
        Tab(com.estatedesk.crm.R.string.nav_deals, com.estatedesk.crm.R.drawable.ic_money, DealsActivity::class.java)
    )

    fun attach(act: Activity, root: FrameLayout, selectedIndex: Int) {
        val p = Ui.pal()
        val bar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 8))
            background = GradientDrawable().apply {
                setColor(p.surface)
                // top hairline
                shape = GradientDrawable.RECTANGLE
            }
            elevation = Ui.dp(act, 12).toFloat()
        }
        TABS.forEachIndexed { i, tab ->
            val item = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 2))
                setOnClickListener {
                    if (i == selectedIndex) return@setOnClickListener
                    val intent = Intent(act, tab.cls)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    act.startActivity(intent)
                    act.overridePendingTransition(0, 0)
                }
            }
            val selected = i == selectedIndex
            item.addView(Ui.icon(act, tab.icon, 22,
                if (selected) p.primary else p.textTertiary))
            val label = Ui.tv(act, act.getString(tab.label), 10,
                if (selected) p.primary else p.textTertiary,
                if (selected) Ui.Font.MEDIUM else Ui.Font.REGULAR)
            label.gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(act, 2)
            item.addView(label, lp)
            bar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
    }
}
