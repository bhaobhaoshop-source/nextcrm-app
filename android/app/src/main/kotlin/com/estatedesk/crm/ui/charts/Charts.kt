package com.estatedesk.crm.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.estatedesk.crm.core.Ui

/** Hand-drawn, palette-aware charts. No chart library — full design control. */

private fun ctx(v: View): Context = v.context

/** Vertical bars with X labels — e.g. leads per day. */
class BarChartView(context: Context) : View(context) {
    private val labels = mutableListOf<String>()
    private val values = mutableListOf<Float>()
    private var maxV = 1f
    private val p = Ui.pal()

    fun setData(data: List<Pair<String, Float>>) {
        labels.clear(); values.clear()
        for ((l, v) in data) {
            labels.add(l); values.add(v)
        }
        maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (values.isEmpty()) return
        val pad = Ui.dp(context, 4)
        val labelH = Ui.dp(context, 18)
        val chartBottom = height - labelH.toFloat()
        val chartTop = pad * 2f
        val n = values.size
        val slot = width / n.toFloat()
        val barW = (slot * 0.55f).coerceAtMost(Ui.dp(context, 18).toFloat())

        val grid = Paint().apply {
            color = p.chartGrid; strokeWidth = 1f
        }
        c.drawLine(0f, chartBottom, width.toFloat(), chartBottom, grid)

        val bar = Paint().apply { color = p.primary; isAntiAlias = true }
        val text = Paint().apply {
            color = p.textTertiary; textSize = Ui.sp(context, 9).toFloat()
            textAlign = Paint.Align.CENTER
        }
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val h = (values[i] / maxV) * (chartBottom - chartTop)
            val top = chartBottom - h
            val r = RectF(cx - barW / 2, top, cx + barW / 2, chartBottom - 2f)
            if (values[i] > 0) c.drawRoundRect(r, Ui.dp(context, 3).toFloat(), Ui.dp(context, 3).toFloat(), bar)
            else c.drawRoundRect(r, 0f, 0f, Paint().apply {
                color = p.surface2; style = Paint.Style.FILL
            })
            if (n <= 16 || i % 2 == 0) {
                c.drawText(labels[i], cx, chartBottom + Ui.dp(context, 13).toFloat(), text)
            }
            if (values[i] > 0 && n <= 10) {
                val v = Paint().apply {
                    color = p.textSecondary; textSize = Ui.sp(context, 10).toFloat()
                    textAlign = Paint.Align.CENTER
                }
                c.drawText(if (values[i] == values[i].toLong().toFloat()) values[i].toLong().toString()
                else values[i].toString(), cx, top - Ui.dp(context, 3).toFloat(), v)
            }
        }
    }
}

/** Donut chart with center label. */
class DonutChartView(context: Context) : View(context) {
    private val items = mutableListOf<Pair<String, Float>>()
    private val colors = mutableListOf<Int>()
    private var centerText = ""
    private var centerSub = ""
    private val p = Ui.pal()

    fun setData(data: List<Pair<String, Float>>, center: String, sub: String) {
        items.clear(); colors.clear()
        val palette = listOf(
            0xFF175CD3.toInt(), 0xFF12B76A.toInt(), 0xFFE8A33D.toInt(), 0xFF7A5AF8.toInt(),
            0xFF0E9384.toInt(), 0xFFE04F16.toInt(), 0xFFC11574.toInt(), 0xFF2E90FA.toInt()
        )
        data.forEachIndexed { i, (l, v) ->
            items.add(l to v); colors.add(palette[i % palette.size])
        }
        centerText = center; centerSub = sub
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (items.isEmpty()) return
        val total = items.sumOf { it.second.toDouble() }.toFloat()
        if (total <= 0f) return
        val stroke = Ui.dp(context, 22).toFloat()
        val r = RectF(
            Ui.dp(context, 12).toFloat() + stroke / 2,
            Ui.dp(context, 8).toFloat() + stroke / 2,
            height - Ui.dp(context, 12).toFloat() - stroke / 2,
            height - Ui.dp(context, 8).toFloat() - stroke / 2
        )
        var start = -90f
        val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = stroke }
        for (i in items.indices) {
            val sweep = items[i].second / total * 360f
            paint.color = colors[i]
            c.drawArc(r, start, sweep, false, paint)
            start += sweep
        }
        val t1 = Paint().apply {
            color = p.textPrimary; textSize = Ui.sp(context, 20).toFloat()
            textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        val t2 = Paint().apply {
            color = p.textTertiary; textSize = Ui.sp(context, 11).toFloat()
            textAlign = Paint.Align.CENTER
        }
        c.drawText(centerText, width / 2f, height / 2f - Ui.dp(context, 2).toFloat(), t1)
        c.drawText(centerSub, width / 2f, height / 2f + Ui.dp(context, 16).toFloat(), t2)
    }
}

/** Line chart with soft area fill. */
class LineChartView(context: Context) : View(context) {
    private val labels = mutableListOf<String>()
    private val values = mutableListOf<Float>()
    private var maxV = 1f
    private val p = Ui.pal()

    fun setData(data: List<Pair<String, Float>>) {
        labels.clear(); values.clear()
        for ((l, v) in data) {
            labels.add(l); values.add(v)
        }
        maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (values.size < 2) return
        val pad = Ui.dp(context, 6)
        val labelH = Ui.dp(context, 18)
        val chartBottom = height - labelH.toFloat()
        val chartTop = pad * 3f
        val n = values.size
        val slot = width / (n - 1).toFloat()

        val path = Path()
        for (i in 0 until n) {
            val x = slot * i
            val y = chartBottom - (values[i] / maxV) * (chartBottom - chartTop)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path(path)
        fill.lineTo(width.toFloat(), chartBottom)
        fill.lineTo(0f, chartBottom)
        fill.close()
        c.drawPath(fill, Paint().apply {
            color = p.primary; alpha = 30; style = Paint.Style.FILL; isAntiAlias = true
        })
        c.drawPath(path, Paint().apply {
            color = p.primary; strokeWidth = (Ui.dp(context, 5) / 2f).toFloat()
            style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        val text = Paint().apply {
            color = p.textTertiary; textSize = Ui.sp(context, 9).toFloat(); textAlign = Paint.Align.CENTER
        }
        for (i in 0 until n) {
            if (n <= 8 || i % 2 == 0) {
                c.drawText(labels[i], slot * i, chartBottom + Ui.dp(context, 13).toFloat(), text)
            }
        }
        // dots
        val dot = Paint().apply { color = p.primary; isAntiAlias = true }
        for (i in 0 until n) {
            val x = slot * i
            val y = chartBottom - (values[i] / maxV) * (chartBottom - chartTop)
            c.drawCircle(x, y, Ui.dp(context, 3).toFloat(), dot)
        }
    }
}

/** Horizontal labeled bars — e.g. pipeline distribution. */
class HBarChartView(context: Context) : View(context) {
    private val labels = mutableListOf<String>()
    private val values = mutableListOf<Float>()
    private var maxV = 1f
    private var barColor = 0xFF175CD3.toInt()
    private val p = Ui.pal()

    fun setData(data: List<Pair<String, Float>>, color: Int) {
        labels.clear(); values.clear()
        for ((l, v) in data) {
            labels.add(l); values.add(v)
        }
        maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        barColor = color
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (values.isEmpty()) return
        val rowH = (height / values.size).toFloat().coerceAtMost(Ui.dp(context, 34).toFloat())
        val labelW = Ui.dp(context, 110).toFloat()
        val y0 = (height - rowH * values.size) / 2f
        for (i in values.indices) {
            val y = y0 + rowH * i
            val label = Paint().apply {
                color = p.textSecondary; textSize = Ui.sp(context, 11).toFloat()
                textAlign = Paint.Align.RIGHT
            }
            c.drawText(labels[i].take(16), labelW - Ui.dp(context, 8).toFloat(),
                y + rowH / 2 + Ui.dp(context, 4).toFloat(), label)
            val avail = width - labelW - Ui.dp(context, 36).toFloat()
            val w = (values[i] / maxV) * avail
            c.drawRoundRect(
                RectF(labelW, y + rowH * 0.2f, labelW + w.coerceAtLeast(Ui.dp(context, 2).toFloat()), y + rowH * 0.8f),
                Ui.dp(context, 4).toFloat(), Ui.dp(context, 4).toFloat(),
                Paint().apply { color = barColor; isAntiAlias = true }
            )
            val valPaint = Paint().apply {
                color = p.textPrimary; textSize = Ui.sp(context, 11).toFloat()
                textAlign = Paint.Align.LEFT; isFakeBoldText = true
            }
            c.drawText(values[i].toLong().toString(), labelW + w + Ui.dp(context, 6).toFloat(),
                y + rowH / 2 + Ui.dp(context, 4).toFloat(), valPaint)
        }
    }
}
