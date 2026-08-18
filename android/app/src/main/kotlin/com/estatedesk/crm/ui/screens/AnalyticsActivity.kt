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
import com.estatedesk.crm.ui.charts.BarChartView
import com.estatedesk.crm.ui.charts.DonutChartView
import com.estatedesk.crm.ui.charts.HBarChartView
import com.estatedesk.crm.ui.charts.LineChartView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnalyticsActivity : BaseActivity() {

    private var rangeDays = 30

    override fun build() {
        topBar(getString(R.string.analytics_title))
        val ranges = listOf(30, 90, 180, 365, -1)
        val labels = listOf(
            getString(R.string.analytics_range_30d), getString(R.string.analytics_range_90d),
            getString(R.string.analytics_range_6m), getString(R.string.analytics_range_12m),
            getString(R.string.analytics_range_all)
        )
        add(Ui.chipRow(this, labels.map { it to (rangeDays == ranges[labels.indexOf(it)]) }) { i ->
            rangeDays = ranges[i]
            content.removeAllViews()
            topBar(getString(R.string.analytics_title))
            add(Ui.chipRow(this, labels.map { it to (rangeDays == ranges[labels.indexOf(it)]) }) { i2 ->
                rangeDays = ranges[i2]
                content.removeAllViews()
                topBar(getString(R.string.analytics_title))
                load()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            load()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        load()
    }

    private fun since(): Long =
        if (rangeDays < 0) 0 else System.currentTimeMillis() - rangeDays * 86_400_000L

    private fun load() {
        Async.db({
            val s = Di.store.stats
            data class D(
                val leadsByDay: List<Pair<String, Float>>,
                val sources: List<Pair<String, Float>>,
                val pipeline: List<Pair<String, Float>>,
                val funnel: List<Pair<String, Long>>,
                val revenue: List<Pair<String, Float>>,
                val types: List<Pair<String, Float>>,
                val areas: List<Pair<String, Float>>,
                val acts: Map<String, Long>,
                val commissionEarned: Long, val pending: Long,
                val winRate: Int, val avgDeal: Long, val totalLeads: Long
            )
            val dayFmt = SimpleDateFormat("d", Locale.getDefault())
            val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())
            D(
                s.leadsByDay(since(), if (rangeDays in 1..31) rangeDays else 12).map {
                    dayFmt.format(Date(it.first)) to it.second.toFloat()
                },
                s.sources(since()).take(7).map { it.first to it.second.toFloat() },
                s.pipelineDistribution().take(8).map { it.first to it.second.toFloat() },
                s.funnel(),
                s.revenueByMonth(if (rangeDays in 1..31) 1 else 6).map {
                    monthFmt.format(Date(it.first)) to it.second.toFloat()
                },
                s.propertyTypes().take(6).map { it.first to it.second.toFloat() },
                s.topAreas(5).map { it.first to it.second.toFloat() },
                s.activityCounts(since()),
                s.totalCommission(), s.pendingCommission(), s.conversionRate(),
                0L, s.totalLeads()
            )
        }) { d ->
            if (d == null || isFinishing) return@db
            val hasData = d.totalLeads > 0 || d.commissionEarned > 0 || d.acts.isNotEmpty()
            if (!hasData) {
                add(Ui.emptyState(this, R.drawable.ic_chart, getString(R.string.analytics_no_data),
                    getString(R.string.analytics_no_data_hint)))
                return@db
            }

            // KPIs
            val kpi = Ui.card(this, emptyList(), padding = 14)
            val row1 = Ui.hbox(this)
            row1.addView(kpi(getString(R.string.home_revenue), Fmt.moneyShort(d.commissionEarned, Di.store.currency()), p.success), Ui.weightLps(1f))
            row1.addView(kpi(getString(R.string.analytics_commission_pending), Fmt.moneyShort(d.pending, Di.store.currency()), p.warning), Ui.weightLps(1f))
            row1.addView(kpi(getString(R.string.analytics_win_rate), "${d.winRate}%", p.primary), Ui.weightLps(1f))
            kpi.addView(row1)
            add(kpi, lpm())

            // leads over time
            add(chartCard(getString(R.string.analytics_leads_over_time), BarChartView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalyticsActivity, 150))
                setData(d.leadsByDay)
            }))

            // lead sources donut
            if (d.sources.isNotEmpty()) {
                add(chartCard(getString(R.string.analytics_lead_sources), DonutChartView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@AnalyticsActivity, 170), Ui.dp(this@AnalyticsActivity, 170))
                    setData(d.sources, d.sources.sumOf { it.second.toInt() }.toString(), getString(R.string.analytics_lead_sources))
                }, legend = d.sources))
            }

            // pipeline distribution
            if (d.pipeline.isNotEmpty()) {
                add(chartCard(getString(R.string.analytics_pipeline_distribution), HBarChartView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalyticsActivity, 150))
                    setData(d.pipeline, p.primary)
                }))
            }

            // funnel
            if (d.funnel.any { it.second > 0 }) {
                add(chartCard(getString(R.string.analytics_conversion_funnel), funnelView(d.funnel)))
            }

            // revenue
            if (d.revenue.any { it.second > 0 }) {
                add(chartCard(getString(R.string.analytics_revenue), LineChartView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalyticsActivity, 150))
                    setData(d.revenue)
                }))
            }

            // property types
            if (d.types.isNotEmpty()) {
                add(chartCard(getString(R.string.analytics_property_types), HBarChartView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalyticsActivity, 130))
                    setData(d.types, p.accent)
                }))
            }

            // top areas
            if (d.areas.isNotEmpty()) {
                add(chartCard(getString(R.string.analytics_top_areas), HBarChartView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalyticsActivity, 120))
                    setData(d.areas, p.success)
                }))
            }

            // activity
            if (d.acts.isNotEmpty()) {
                val actCard = Ui.card(this, emptyList(), padding = 14)
                actCard.addView(Ui.tv(this@AnalyticsActivity, getString(R.string.analytics_activity), 13, p.textTertiary, Ui.Font.MEDIUM))
                actCard.addView(Ui.spacer(this, 10))
                val row = Ui.hbox(this)
                val items = listOf(
                    "Call" to getString(R.string.analytics_calls),
                    "Follow-up" to getString(R.string.analytics_followups),
                    "Viewing" to getString(R.string.analytics_viewings),
                    "Meeting" to getString(R.string.analytics_meetings)
                )
                items.forEach { (type, label) ->
                    val v = d.acts[type] ?: 0
                    row.addView(kpi(label, v.toString(), p.textPrimary), Ui.weightLps(1f))
                }
                actCard.addView(row)
                add(actCard, lpm())
            }
            add(Ui.spacer(this, 40))
        }
    }

    private fun funnelView(data: List<Pair<String, Long>>): LinearLayout {
        val box = Ui.vbox(this)
        val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        data.forEachIndexed { i, (label, count) ->
            val row = Ui.hbox(this)
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.addView(Ui.tv(this@AnalyticsActivity, label, 12, p.textSecondary),
                LinearLayout.LayoutParams(Ui.dp(this@AnalyticsActivity, 110), ViewGroup.LayoutParams.WRAP_CONTENT))
            val frac = if (max == 0L) 0f else (count.toFloat() / max)
            val bar = View(this).apply {
                setBackgroundColor(p.primary)
                layoutParams = LinearLayout.LayoutParams(
                    (Ui.dp(this@AnalyticsActivity, 110) * frac).toInt().coerceAtLeast(Ui.dp(this@AnalyticsActivity, 2)),
                    Ui.dp(this@AnalyticsActivity, 16))
            }
            row.addView(bar)
            row.addView(Ui.hspacer(this, 8))
            row.addView(Ui.tv(this@AnalyticsActivity, count.toString(), 13, p.textPrimary, Ui.Font.BOLD))
            if (i > 0) {
                val prev = data[i - 1].second
                val conv = if (prev > 0) (count * 100 / prev).toInt() else 0
                row.addView(Ui.hspacer(this, 8))
                row.addView(Ui.tv(this@AnalyticsActivity, "$conv%", 11, p.textTertiary))
            }
            Ui.margin(row, 0, 0, 0, 8)
            box.addView(row)
        }
        return box
    }

    private fun kpi(label: String, value: String, color: Int): LinearLayout {
        val box = Ui.vbox(this)
        box.gravity = android.view.Gravity.CENTER
        val v = Ui.tv(this@AnalyticsActivity, value, 17, color, Ui.Font.BOLD)
        v.gravity = android.view.Gravity.CENTER
        box.addView(v)
        val l = Ui.tv(this@AnalyticsActivity, label, 11, p.textTertiary)
        l.gravity = android.view.Gravity.CENTER
        box.addView(l)
        return box
    }

    private fun chartCard(title: String, chart: android.view.View,
                          legend: List<Pair<String, Float>>? = null): LinearLayout {
        val card = Ui.card(this, emptyList(), padding = 14)
        card.addView(Ui.tv(this@AnalyticsActivity, title, 13, p.textTertiary, Ui.Font.MEDIUM))
        card.addView(Ui.spacer(this, 8))
        val chartLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (chart.layoutParams == null) chart.layoutParams = chartLp
        card.addView(chart)
        if (legend != null) {
            card.addView(Ui.spacer(this, 8))
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            legend.forEach { (label, v) ->
                val row = Ui.hbox(this)
                val dot = View(this).apply {
                    setBackgroundColor(p.primary)
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@AnalyticsActivity, 8), Ui.dp(this@AnalyticsActivity, 8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(p.primary)
                    }
                }
                row.addView(dot)
                row.addView(Ui.hspacer(this, 8))
                row.addView(Ui.tv(this@AnalyticsActivity, label, 12, p.textSecondary),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.tv(this@AnalyticsActivity, v.toInt().toString(), 12, p.textPrimary, Ui.Font.MEDIUM))
                Ui.margin(row, 0, 0, 0, 6)
                wrap.addView(row)
            }
            card.addView(wrap)
        }
        val out = LinearLayout(this)
        out.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(this@AnalyticsActivity, 12) })
        return card
    }

    private fun lpm(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(this@AnalyticsActivity, 12)
        }
}
