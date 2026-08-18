package com.estatedesk.crm.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Activity
import com.estatedesk.crm.data.Contact
import com.estatedesk.crm.data.Deal
import com.estatedesk.crm.data.Lead
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.Task
import java.io.File

/** Shared list-row builders keep every list screen consistent. */
object Rows {

    // ------------------------------------------------------------ contact

    fun contactRow(ctx: Context, c: Contact, phones: List<com.estatedesk.crm.data.Phone>,
                   onClick: () -> Unit): LinearLayout {
        val p = Ui.pal()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10))
            setOnClickListener { onClick() }
        }
        row.addView(Ui.avatar(ctx, c.displayName(), 44))
        val mid = Ui.vbox(ctx)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpMid.leftMargin = Ui.dp(ctx, 12)
        val nameRow = Ui.hbox(ctx)
        nameRow.addView(Ui.tv(ctx, c.displayName(), 15, p.textPrimary, Ui.Font.MEDIUM, 1))
        if (c.priority >= 2) {
            nameRow.addView(Ui.hspacer(ctx, 6))
            nameRow.addView(Ui.badge(ctx, if (c.priority == 3) "High" else "Med",
                if (c.priority == 3) p.danger else p.warning,
                if (c.priority == 3) p.dangerSoft else p.warningSoft))
        }
        mid.addView(nameRow)
        val phone = phones.firstOrNull()?.number ?: ""
        val sub = listOf(phone, c.city, c.area).filter { it.isNotBlank() }.joinToString(" · ")
        mid.addView(Ui.tv(ctx, sub.ifBlank { c.classification }, 12, p.textTertiary, maxLines = 1))
        row.addView(mid, lpMid)
        row.addView(Ui.badge(ctx, c.temperature, when (c.temperature.lowercase()) {
            "hot" -> p.danger
            "warm" -> p.warning
            else -> p.cold
        }, when (c.temperature.lowercase()) {
            "hot" -> p.dangerSoft
            "warm" -> p.warningSoft
            else -> p.infoSoft
        }))
        return row
    }

    // ------------------------------------------------------------ lead

    fun leadRow(ctx: Context, l: Lead, contact: Contact?, onClick: () -> Unit): LinearLayout {
        val p = Ui.pal()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10))
            setOnClickListener { onClick() }
        }
        val mid = Ui.vbox(ctx)
        val titleRow = Ui.hbox(ctx)
        titleRow.addView(Ui.tv(ctx, l.displayTitle(), 15, p.textPrimary, Ui.Font.MEDIUM, 1),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (l.score >= 50) {
            titleRow.addView(Ui.hspacer(ctx, 6))
            titleRow.addView(Ui.badge(ctx, l.score.toString(), p.white, p.primary))
        }
        mid.addView(titleRow)
        val sub = listOf(
            contact?.displayName() ?: "",
            l.location, l.propertyType, l.intent
        ).filter { it.isNotBlank() }.joinToString(" · ")
        mid.addView(Ui.tv(ctx, sub, 12, p.textTertiary, maxLines = 1))
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val right = Ui.vbox(ctx)
        right.gravity = Gravity.END
        right.addView(Ui.badge(ctx, l.stage, stageColor(ctx, l.stage), stageBg(ctx, l.stage)))
        if (l.nextFollowUp > 0) {
            val overdue = l.nextFollowUp < System.currentTimeMillis()
            val t = Ui.tv(ctx, Fmt.friendlyDate(l.nextFollowUp), 11,
                if (overdue) p.danger else p.textTertiary, Ui.Font.MEDIUM)
            t.gravity = Gravity.END
            right.addView(t)
        }
        row.addView(right)
        return row
    }

    fun stageColor(ctx: Context, stage: String): Int {
        val p = Ui.pal()
        return when {
            stage.contains("Won", true) -> p.success
            stage.contains("Lost", true) -> p.danger
            stage.contains("New", true) -> p.info
            stage.contains("Qualified", true) || stage.contains("Requirement", true) ||
                    stage.contains("Matched", true) -> p.success
            stage.contains("Viewing", true) || stage.contains("Negotiation", true) -> p.warning
            stage.contains("Documentation", true) -> 0xFF7A5AF8.toInt()
            else -> p.textSecondary
        }
    }

    fun stageBg(ctx: Context, stage: String): Int {
        val p = Ui.pal()
        return when {
            stage.contains("Won", true) -> p.successSoft
            stage.contains("Lost", true) -> p.dangerSoft
            stage.contains("New", true) -> p.infoSoft
            stage.contains("Qualified", true) || stage.contains("Requirement", true) ||
                    stage.contains("Matched", true) -> p.successSoft
            stage.contains("Viewing", true) || stage.contains("Negotiation", true) -> p.warningSoft
            stage.contains("Documentation", true) -> p.primarySoft
            else -> p.chipBg
        }
    }

    // ------------------------------------------------------------ property

    fun propertyCard(ctx: Context, prop: Property, photoPath: String,
                     onClick: () -> Unit, width: Int): LinearLayout {
        val p = Ui.pal()
        val card = Ui.card(ctx, emptyList(), padding = 0) { onClick() }
        card.layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        card.isClickable = true

        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(p.surface2)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 130))
        }
        if (photoPath.isNotBlank()) loadPhoto(img, photoPath) else {
            img.setImageResource(com.estatedesk.crm.R.drawable.ic_building)
            img.setColorFilter(p.textTertiary)
            img.setPadding(Ui.dp(ctx, 30), Ui.dp(ctx, 30), Ui.dp(ctx, 30), Ui.dp(ctx, 30))
        }
        card.addView(img)

        val body = Ui.vbox(ctx)
        body.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        body.addView(Ui.tv(ctx, prop.displayTitle(), 14, p.textPrimary, Ui.Font.MEDIUM, 2))
        body.addView(Ui.spacer(ctx, 4))
        val price = Fmt.money(prop.price, Di.store.currency())
        body.addView(Ui.tv(ctx, price, 16, p.primary, Ui.Font.BOLD, 1))
        body.addView(Ui.tv(ctx, prop.areaName.ifBlank { prop.location }, 12, p.textTertiary, maxLines = 1))
        body.addView(Ui.spacer(ctx, 6))
        val spec = listOf(
            prop.bedrooms.takeIf { it > 0 }?.let { "$it bd" },
            prop.bathrooms.takeIf { it > 0 }?.let { "$it ba" },
            Fmt.sizeText(prop.sizeValue, prop.sizeUnit)
        ).filterNotNull().joinToString(" · ")
        if (spec.isNotBlank()) body.addView(Ui.tv(ctx, spec, 12, p.textSecondary, maxLines = 1))
        card.addView(body)
        return card
    }

    private fun loadPhoto(iv: ImageView, path: String) {
        Async.io({
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(path, o)
            } catch (t: Throwable) {
                null
            }
        }) { bmp: Bitmap? -> if (bmp != null) iv.setImageBitmap(bmp) }
    }

    // ------------------------------------------------------------ deal

    fun dealRow(ctx: Context, d: Deal, buyer: Contact?, prop: Property?, onClick: () -> Unit): LinearLayout {
        val p = Ui.pal()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10))
            setOnClickListener { onClick() }
        }
        val mid = Ui.vbox(ctx)
        mid.addView(Ui.tv(ctx, d.displayTitle(), 15, p.textPrimary, Ui.Font.MEDIUM, 1))
        val sub = listOf(buyer?.displayName() ?: "", prop?.displayTitle() ?: "")
            .filter { it.isNotBlank() }.joinToString(" · ")
        mid.addView(Ui.tv(ctx, sub, 12, p.textTertiary, maxLines = 1))
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val right = Ui.vbox(ctx)
        right.gravity = Gravity.END
        right.addView(Ui.tv(ctx, Fmt.money(d.value, Di.store.currency()), 15, p.textPrimary, Ui.Font.BOLD, 1))
        right.addView(Ui.badge(ctx, d.stage, Rows.stageColor(ctx, d.stage), Rows.stageBg(ctx, d.stage)))
        row.addView(right)
        return row
    }

    // ------------------------------------------------------------ task

    fun taskRow(ctx: Context, t: Task, related: String, onClick: () -> Unit,
                trailing: View? = null): LinearLayout {
        val p = Ui.pal()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10))
            setOnClickListener { onClick() }
        }
        val iconRes = when (t.kind) {
            Task.KIND_CALL -> com.estatedesk.crm.R.drawable.ic_call
            Task.KIND_FOLLOW_UP -> com.estatedesk.crm.R.drawable.ic_history
            Task.KIND_MEETING -> com.estatedesk.crm.R.drawable.ic_people
            Task.KIND_VIEWING -> com.estatedesk.crm.R.drawable.ic_eye
            Task.KIND_EMAIL -> com.estatedesk.crm.R.drawable.ic_email
            else -> com.estatedesk.crm.R.drawable.ic_check
        }
        val tint = when (t.kind) {
            Task.KIND_FOLLOW_UP -> p.primary
            Task.KIND_VIEWING -> p.warning
            Task.KIND_CALL -> p.success
            else -> p.textSecondary
        }
        val box = LinearLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(ctx, 10).toFloat()
                setColor(p.surface2)
            }
        }
        box.addView(Ui.icon(ctx, iconRes, 18, tint))
        row.addView(box)

        val mid = Ui.vbox(ctx)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpMid.leftMargin = Ui.dp(ctx, 12)
        val titleColor = if (t.status == Task.STATUS_DONE) p.textTertiary else p.textPrimary
        val tt = Ui.tv(ctx, t.title, 14, titleColor,
            if (t.status == Task.STATUS_DONE) Ui.Font.REGULAR else Ui.Font.MEDIUM, 2)
        if (t.status == Task.STATUS_DONE) {
            tt.paintFlags = tt.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }
        mid.addView(tt)
        val due = Fmt.friendlyDateTime(t.dueAt())
        val overdue = t.status == Task.STATUS_OPEN && t.dueAt() > 0 && t.dueAt() < System.currentTimeMillis()
        val sub = mutableListOf<String>()
        if (related.isNotBlank()) sub.add(related)
        if (due.isNotBlank()) sub.add(due)
        val subTv = Ui.tv(ctx, sub.joinToString(" · "), 12,
            if (overdue) p.danger else p.textTertiary, maxLines = 1)
        mid.addView(subTv)
        row.addView(mid, lpMid)
        if (trailing != null) row.addView(trailing)
        return row
    }

    // ------------------------------------------------------------ timeline

    fun timelineRow(ctx: Context, a: Activity, onClick: (() -> Unit)? = null): LinearLayout {
        val p = Ui.pal()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10))
            if (onClick != null) setOnClickListener { onClick() }
        }
        val iconRes = when (a.type) {
            Activity.T_CALL -> com.estatedesk.crm.R.drawable.ic_call
            Activity.T_WHATSAPP -> com.estatedesk.crm.R.drawable.ic_whatsapp
            Activity.T_SMS -> com.estatedesk.crm.R.drawable.ic_sms
            Activity.T_EMAIL -> com.estatedesk.crm.R.drawable.ic_email
            Activity.T_MEETING -> com.estatedesk.crm.R.drawable.ic_people
            Activity.T_VIEWING -> com.estatedesk.crm.R.drawable.ic_eye
            Activity.T_NOTE -> com.estatedesk.crm.R.drawable.ic_note
            Activity.T_FOLLOW_UP -> com.estatedesk.crm.R.drawable.ic_history
            Activity.T_STATUS -> com.estatedesk.crm.R.drawable.ic_swap
            Activity.T_DEAL -> com.estatedesk.crm.R.drawable.ic_money
            Activity.T_TASK -> com.estatedesk.crm.R.drawable.ic_check
            Activity.T_REMINDER -> com.estatedesk.crm.R.drawable.ic_alarm
            else -> com.estatedesk.crm.R.drawable.ic_doc
        }
        val tint = when (a.type) {
            Activity.T_CALL -> p.success
            Activity.T_NOTE -> p.info
            Activity.T_VIEWING -> p.warning
            Activity.T_STATUS, Activity.T_DEAL -> p.primary
            else -> p.textSecondary
        }
        val box = LinearLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(ctx, 10).toFloat()
                setColor(p.surface2)
            }
        }
        box.addView(Ui.icon(ctx, iconRes, 16, tint))
        row.addView(box)
        val mid = Ui.vbox(ctx)
        val lpMid = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lpMid.leftMargin = Ui.dp(ctx, 12)
        mid.addView(Ui.tv(ctx, a.title.ifBlank { a.type }, 13, p.textPrimary, Ui.Font.MEDIUM, 2))
        if (a.body.isNotBlank()) mid.addView(Ui.tv(ctx, a.body, 13, p.textSecondary, maxLines = 3))
        mid.addView(Ui.tv(ctx, Fmt.relative(a.at), 11, p.textTertiary))
        row.addView(mid, lpMid)
        return row
    }

    // ------------------------------------------------------------ property mini (for matching)

    fun matchRow(ctx: Context, prop: Property, score: Int, reasons: List<String>,
                 onClick: () -> Unit): LinearLayout {
        val p = Ui.pal()
        val card = Ui.card(ctx, emptyList(), padding = 14) { onClick() }
        val head = Ui.hbox(ctx)
        head.addView(Ui.tv(ctx, prop.displayTitle(), 14, p.textPrimary, Ui.Font.MEDIUM, 2),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val scoreColor = when {
            score >= 80 -> p.success
            score >= 55 -> p.warning
            else -> p.textSecondary
        }
        head.addView(Ui.tv(ctx, "$score%", 18, scoreColor, Ui.Font.BOLD))
        card.addView(head)
        card.addView(Ui.tv(ctx, Fmt.money(prop.price, Di.store.currency()), 15, p.primary, Ui.Font.BOLD, 1))
        card.addView(Ui.tv(ctx, "${prop.areaName.ifBlank { prop.location }} · ${prop.saleRent} · ${prop.type}",
            12, p.textTertiary, maxLines = 1))
        val spec = listOf(
            prop.bedrooms.takeIf { it > 0 }?.let { "$it bd" },
            prop.bathrooms.takeIf { it > 0 }?.let { "$it ba" },
            Fmt.sizeText(prop.sizeValue, prop.sizeUnit)
        ).filterNotNull().joinToString(" · ")
        if (spec.isNotBlank()) card.addView(Ui.tv(ctx, spec, 12, p.textSecondary, maxLines = 1))
        if (reasons.isNotEmpty()) {
            card.addView(Ui.spacer(ctx, 6))
            card.addView(Ui.tv(ctx, reasons.take(3).joinToString("  "), 12, p.textSecondary, maxLines = 2))
        }
        return card
    }

    fun kvCard(ctx: Context, title: String, pairs: List<Pair<String, String>>): LinearLayout {
        val card = Ui.card(ctx, emptyList())
        card.addView(Ui.tv(ctx, title, 13, Ui.pal().textTertiary, Ui.Font.MEDIUM))
        card.addView(Ui.spacer(ctx, 8))
        for ((k, v) in pairs) card.addView(Ui.kv(ctx, k, v))
        return card
    }
}
