package com.estatedesk.crm.core

import com.estatedesk.crm.data.CurrencyCfg
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatting helpers — money, dates, relative time, phone numbers. */
object Fmt {

    fun money(minor: Long, c: CurrencyCfg): String {
        if (c.decimals == 0) return moneyNoDecimals(minor, c)
        val div = Math.pow(10.0, c.decimals.toDouble())
        val major = minor / div
        val pattern = "#,##0." + "0".repeat(c.decimals)
        val df = DecimalFormat(pattern)
        val s = df.format(major)
        return if (c.prefix) "${c.symbol} $s" else "$s ${c.symbol}"
    }

    private fun moneyNoDecimals(minor: Long, c: CurrencyCfg): String {
        val abs = Math.abs(minor)
        val neg = minor < 0
        val s = String.format(Locale.US, "%,d", abs)
        val body = if (c.prefix) "${c.symbol} $s" else "$s ${c.symbol}"
        return if (neg) "-$body" else body
    }

    /** Short money for tight spaces: 1.2Cr, 45L, 500K (for PKR-like amounts). */
    fun moneyShort(minor: Long, c: CurrencyCfg): String {
        val abs = Math.abs(minor)
        val (v, unit) = when {
            abs >= 100_000_00L -> (minor / 100_000_00.0) to "Cr"
            abs >= 100_000L -> (minor / 100_000.0) to "L"
            abs >= 1_000L -> (minor / 1_000.0) to "K"
            else -> minor.toDouble() to ""
        }
        val df = DecimalFormat(if (unit.isBlank()) "#,##0" else "#,##0.#")
        return "${c.symbol} ${df.format(v)}$unit"
    }

    fun pct(v: Double): String = "${Math.round(v)}%"

    fun date(at: Long): String {
        if (at <= 0) return "—"
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(at))
    }

    fun dateShort(at: Long): String {
        if (at <= 0) return "—"
        return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(at))
    }

    fun time(at: Long): String {
        if (at <= 0) return ""
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
    }

    fun dateTime(at: Long): String = "${date(at)} · ${time(at)}"

    fun monthYear(at: Long): String {
        if (at <= 0) return "—"
        return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(at))
    }

    fun weekday(at: Long): String =
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at))

    fun minutesToTime(minutes: Int): String {
        if (minutes < 0) return ""
        val h = minutes / 60
        val m = minutes % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(Locale.US, "%d:%02d %s", h12, m, ampm)
    }

    /** "Today", "Tomorrow", "Yesterday", "Mon 12 Aug", "12 Aug 2025" */
    fun friendlyDate(at: Long): String {
        if (at <= 0) return "—"
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val today = startOfDay(now.timeInMillis)
        val dayStart = startOfDay(at)
        val diffDays = ((today - dayStart) / 86_400_000L).toInt()
        return when (diffDays) {
            0 -> "Today"
            -1 -> "Tomorrow"
            1 -> "Yesterday"
            else -> {
                val yearNow = now.get(Calendar.YEAR)
                val yearAt = cal.get(Calendar.YEAR)
                val pattern = if (yearNow == yearAt) "EEE d MMM" else "d MMM yyyy"
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
            }
        }
    }

    fun friendlyDateTime(at: Long): String {
        if (at <= 0) return "—"
        val t = minutesToTime(minuteOfDay(at))
        return if (t.isBlank()) friendlyDate(at) else "${friendlyDate(at)} · $t"
    }

    fun relative(at: Long): String {
        if (at <= 0) return ""
        val diff = System.currentTimeMillis() - at
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} hr ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} days ago"
            else -> date(at)
        }
    }

    fun startOfDay(at: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(at: Long): Long = startOfDay(at) + 86_400_000L - 1

    fun minuteOfDay(at: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun phone(display: String): String {
        val s = display.trim()
        if (s.isBlank()) return ""
        // light formatting: keep as entered
        return s
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    fun sizeText(value: Double, unit: String): String {
        if (value <= 0) return ""
        val df = DecimalFormat("0.##")
        return "${df.format(value)} $unit"
    }

    fun plural(n: Long, one: String, many: String): String =
        if (n == 1L) one else many
}
