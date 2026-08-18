package com.estatedesk.crm.core

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import java.util.Calendar

/** Design-system primitives. Every screen is composed from these so the app
 *  keeps one visual language: 16dp cards, 12dp inputs, one palette, one
 *  typography scale. */
object Ui {

    fun dp(ctx: Context, v: Int): Int =
        (ctx.resources.displayMetrics.density * v).toInt()

    fun sp(ctx: Context, v: Int): Int =
        (ctx.resources.displayMetrics.scaledDensity * v).toInt()

    fun pal(): Palette = Di.palette

    // ------------------------------------------------------------- typography

    private fun medium(): Typeface? = try {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    } catch (t: Throwable) {
        Typeface.DEFAULT
    }

    private fun font(weight: Int): Typeface? = when (weight) {
        Font.BOLD -> Typeface.DEFAULT_BOLD
        Font.MEDIUM -> medium()
        else -> Typeface.DEFAULT
    }

    object Font {
        const val REGULAR = 0
        const val MEDIUM = 1
        const val BOLD = 2
    }

    fun tv(ctx: Context, text: String, size: Int, color: Int,
           weight: Int = Font.REGULAR, maxLines: Int = Int.MAX_VALUE): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.toFloat())
            setTextColor(color)
            typeface = font(weight)
            setMaxLines(maxLines)
            if (maxLines > 1) ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }

    fun title(ctx: Context, text: String): TextView = tv(ctx, text, 20, pal().textPrimary, Font.BOLD)
    fun h1(ctx: Context, text: String): TextView = tv(ctx, text, 24, pal().textPrimary, Font.BOLD)
    fun h2(ctx: Context, text: String): TextView = tv(ctx, text, 17, pal().textPrimary, Font.BOLD)
    fun body(ctx: Context, text: String, color: Int = pal().textSecondary): TextView =
        tv(ctx, text, 14, color)
    fun caption(ctx: Context, text: String, color: Int = pal().textTertiary): TextView =
        tv(ctx, text, 12, color)
    fun label(ctx: Context, text: String): TextView = tv(ctx, text, 12, pal().textTertiary, Font.MEDIUM)

    // ------------------------------------------------------------- layouts

    fun vbox(ctx: Context, vararg children: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            for (c in children) addView(c)
        }

    fun hbox(ctx: Context, vararg children: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            for (c in children) addView(c)
        }

    fun spacer(ctx: Context, h: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h))
    }

    fun hspacer(ctx: Context, w: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(ctx, w), 1)
    }

    fun lps(w: Int, h: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(Di.ctx, w), dp(Di.ctx, h))

    fun weightLps(weight: Float, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(Di.ctx, h), weight)

    fun margin(v: View, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
        val ctx = v.context
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        lp.setMargins(dp(ctx, l), dp(ctx, t), dp(ctx, r), dp(ctx, b))
        v.layoutParams = lp
    }

    fun pad(v: View, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
        val ctx = v.context
        v.setPadding(dp(ctx, l), dp(ctx, t), dp(ctx, r), dp(ctx, b))
    }

    // ------------------------------------------------------------- cards & surfaces

    fun card(ctx: Context, children: List<View>, padding: Int = 16, click: (() -> Unit)? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.estatedesk.crm.R.drawable.bg_card)
            setPadding(dp(ctx, padding), dp(ctx, padding), dp(ctx, padding), dp(ctx, padding))
            for (c in children) addView(c)
            if (click != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
            }
        }

    fun sectionTitle(ctx: Context, text: String, actionText: String? = null,
                     onAction: (() -> Unit)? = null): LinearLayout {
        val row = hbox(ctx)
        row.addView(tv(ctx, text, 13, pal().textTertiary, Font.MEDIUM), weightLps(1f))
        if (actionText != null) {
            val a = tv(ctx, actionText, 13, pal().primary, Font.MEDIUM)
            pad(a, 8, 8, 0, 8)
            a.setOnClickListener { onAction?.invoke() }
            row.addView(a)
        }
        margin(row, 4, 0, 4, 4)
        return row
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(pal().divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }

    // ------------------------------------------------------------- badges & chips

    fun badge(ctx: Context, text: String, textColor: Int, bgColor: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(textColor)
            typeface = if (bold) Typeface.DEFAULT_BOLD else font(Font.MEDIUM)
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 8).toFloat()
                setColor(bgColor)
            }
            setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3))
            gravity = Gravity.CENTER
        }

    fun chip(ctx: Context, text: String, selected: Boolean = false, onClick: (() -> Unit)? = null): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (selected) pal().primary else pal().textSecondary)
            typeface = font(Font.MEDIUM)
            setBackgroundResource(com.estatedesk.crm.R.drawable.bg_chip)
            isSelected = selected
            setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
            if (onClick != null) setOnClickListener { onClick() }
        }

    fun statusBadge(ctx: Context, status: String): TextView {
        val (tc, bg) = when (status.lowercase()) {
            "available", "closed won", "qualified", "done", "completed", "customer" ->
                pal().success to pal().successSoft
            "hot", "overdue", "closed lost", "no show", "off market" ->
                pal().danger to pal().dangerSoft
            "reserved", "pending", "viewing scheduled", "viewing completed", "negotiation",
            "documentation", "warm", "contacted", "scheduled" ->
                pal().warning to pal().warningSoft
            "sold", "rented" -> pal().primary to pal().primarySoft
            else -> pal().textSecondary to pal().chipBg
        }
        return badge(ctx, status, tc, bg)
    }

    fun priorityBadge(ctx: Context, priority: Int): TextView {
        val label = when (priority) {
            3 -> "High"
            2 -> "Medium"
            else -> "Low"
        }
        val (tc, bg) = when (priority) {
            3 -> pal().danger to pal().dangerSoft
            2 -> pal().warning to pal().warningSoft
            else -> pal().textSecondary to pal().chipBg
        }
        return badge(ctx, label, tc, bg)
    }

    // ------------------------------------------------------------- icon

    fun icon(ctx: Context, resId: Int, size: Int = 20, tint: Int? = null): ImageView =
        ImageView(ctx).apply {
            setImageResource(resId)
            val d = dp(ctx, size)
            layoutParams = LinearLayout.LayoutParams(d, d)
            if (tint != null) setColorFilter(tint)
            else setColorFilter(pal().textSecondary)
        }

    // ------------------------------------------------------------- buttons

    fun btn(ctx: Context, text: String, kind: Int, onClick: (() -> Unit)? = null): Button =
        Button(ctx).apply {
            this.text = text
            when (kind) {
                Btn.PRIMARY -> {
                    setTextColor(pal().white)
                    setBackgroundResource(com.estatedesk.crm.R.drawable.bg_btn_primary)
                    typeface = font(Font.MEDIUM)
                }
                Btn.SECONDARY -> {
                    setTextColor(pal().textPrimary)
                    setBackgroundResource(com.estatedesk.crm.R.drawable.bg_btn_secondary)
                    typeface = font(Font.MEDIUM)
                }
                Btn.DANGER -> {
                    setTextColor(pal().danger)
                    setBackgroundResource(com.estatedesk.crm.R.drawable.bg_btn_danger)
                    typeface = font(Font.MEDIUM)
                }
                Btn.TEXT -> {
                    setTextColor(pal().primary)
                    background = null
                    typeface = font(Font.MEDIUM)
                    minHeight = dp(ctx, 40)
                }
            }
            stateListAnimator = null
            if (onClick != null) setOnClickListener { onClick() }
        }

    object Btn {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val DANGER = 2
        const val TEXT = 3
    }

    // ------------------------------------------------------------- inputs

    fun input(ctx: Context, hint: String, prefill: String = "",
              inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
              multiline: Boolean = false): EditText =
        EditText(ctx).apply {
            this.hint = hint
            setText(prefill)
            setSingleLine(!multiline)
            setInputType(inputType)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(pal().textPrimary)
            setHintTextColor(pal().textTertiary)
            setBackgroundResource(com.estatedesk.crm.R.drawable.bg_input)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            minHeight = dp(ctx, 48)
            if (multiline) {
                gravity = Gravity.TOP or Gravity.START
                minLines = 3
                setHorizontallyScrolling(false)
            }
        }

    fun field(ctx: Context, labelText: String, child: View, required: Boolean = false): LinearLayout =
        vbox(ctx).apply {
            val l = label(ctx, if (required) "$labelText *" else labelText)
            addView(l)
            margin(l, 2, 0, 2, 0)
            addView(child)
            margin(this, 0, 0, 0, 14)
        }

    /** Non-editable field that opens a picker — looks like an input. */
    fun pickField(ctx: Context, labelText: String, value: String, onClick: (() -> Unit)? = null): LinearLayout {
        val fake = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(com.estatedesk.crm.R.drawable.bg_input)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 10), dp(ctx, 12))
            minimumHeight = dp(ctx, 48)
            if (onClick != null) setOnClickListener { onClick() }
        }
        val txt = tv(ctx, value.ifBlank { "Select" }, 15,
            if (value.isBlank()) pal().textTertiary else pal().textPrimary)
        fake.addView(txt, weightLps(1f))
        fake.addView(icon(ctx, com.estatedesk.crm.R.drawable.ic_chevron, 18, pal().textTertiary))
        return field(ctx, labelText, fake)
    }

    // ------------------------------------------------------------- top bars

    class TopBar(val root: LinearLayout, val titleView: TextView, val actionRow: LinearLayout)

    fun topBar(act: Activity, titleText: String, subtitle: String? = null,
               back: Boolean = false, actions: List<Pair<Int, () -> Unit>> = emptyList()): TopBar {
        val ctx = act
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        }
        if (back) {
            val b = icon(ctx, com.estatedesk.crm.R.drawable.ic_back, 22)
            pad(b, 10, 10, 10, 10)
            b.setOnClickListener { act.finish() }
            root.addView(b)
        }
        val texts = vbox(ctx)
        val t = if (subtitle.isNullOrBlank()) title(ctx, titleText)
        else tv(ctx, titleText, 18, pal().textPrimary, Font.BOLD)
        texts.addView(t)
        if (!subtitle.isNullOrBlank()) texts.addView(caption(ctx, subtitle))
        root.addView(texts, weightLps(1f))
        val actionRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(actionRow)
        for ((res, fn) in actions) {
            val iv = icon(ctx, res, 20, pal().textPrimary)
            pad(iv, 10, 10, 6, 10)
            iv.setOnClickListener { fn() }
            actionRow.addView(iv)
        }
        return TopBar(root, t, actionRow)
    }

    // ------------------------------------------------------------- FAB

    fun fab(act: Activity, onAdd: () -> Unit): ImageView {
        val p = pal()
        val size = dp(act, 56)
        val fab = ImageView(act).apply {
            setImageResource(com.estatedesk.crm.R.drawable.ic_add)
            setColorFilter(p.white)
            setBackgroundResource(com.estatedesk.crm.R.drawable.bg_fab)
            elevation = dp(act, 6).toFloat()
            setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16))
            setOnClickListener { onAdd() }
        }
        val lp = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, dp(act, 18), dp(act, 18))
        fab.layoutParams = lp
        return fab
    }

    // ------------------------------------------------------------- dialogs

    /** Bottom sheet with a title, content and optional buttons. */
    fun sheet(act: Activity, titleText: String, content: View,
              actions: List<Pair<String, Int>>, onAction: (Int) -> Unit): Dialog {
        val p = pal()
        val d = Dialog(act)
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.surface)
            setPadding(dp(act, 20), dp(act, 20), dp(act, 20), dp(act, 24))
        }
        root.addView(tv(act, titleText, 18, p.textPrimary, Font.BOLD))
        margin(root.getChildAt(0), 0, 0, 0, 12)
        root.addView(content)
        val btnRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        root.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(act, 16)
        })
        actions.forEachIndexed { i, (text, kind) ->
            val b = btn(act, text, kind) { d.dismiss(); onAction(i) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dp(act, 8)
            btnRow.addView(b, lp)
        }
        d.setContentView(root)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes = attributes.apply { dimAmount = 0.45f }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        return d
    }

    fun alert(act: Activity, titleText: String, message: String,
              positive: String,
              negative: String? = null, onNegative: (() -> Unit)? = null,
              onPositive: () -> Unit = {}) {
        val content = vbox(act, body(act, message))
        val actions = mutableListOf<Pair<String, Int>>()
        if (negative != null) actions.add(negative to Btn.SECONDARY)
        actions.add(positive to Btn.PRIMARY)
        sheet(act, titleText, content, actions) { idx ->
            if (negative != null && idx == 0) onNegative?.invoke() else onPositive()
        }.show()
    }

    fun pick(act: Activity, titleText: String, options: List<String>,
             selected: Int = -1, onPick: (Int) -> Unit) {
        val p = pal()
        val d = Dialog(act)
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.surface)
            setPadding(dp(act, 20), dp(act, 20), dp(act, 20), dp(act, 24))
        }
        root.addView(tv(act, titleText, 18, p.textPrimary, Font.BOLD))
        val list = ListView(act).apply {
            adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = options.size
                override fun getItem(position: Int): Any = options[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(act, 4), dp(act, 14), dp(act, 4), dp(act, 14))
                    }
                    val t = tv(act, options[position], 16, p.textPrimary)
                    row.addView(t, weightLps(1f))
                    if (position == selected) {
                        row.addView(icon(act, com.estatedesk.crm.R.drawable.ic_check, 18, p.primary))
                    }
                    return row
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                d.dismiss(); onPick(position)
            }
            dividerHeight = 0
            setBackgroundColor(p.surface)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 320))
        lp.topMargin = dp(act, 8)
        root.addView(list, lp)
        d.setContentView(root)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes = attributes.apply { dimAmount = 0.45f }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        d.show()
    }

    fun prompt(act: Activity, titleText: String, hint: String, prefill: String = "",
               multiline: Boolean = false, okText: String = "Save", onOk: (String) -> Unit) {
        val edit = input(act, hint, prefill, multiline = multiline)
        sheet(act, titleText, edit, listOf("Cancel" to Btn.SECONDARY, okText to Btn.PRIMARY)) { idx ->
            if (idx == 1) onOk(edit.text.toString().trim())
        }.show()
    }

    fun datePick(act: Activity, current: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        if (current > 0) cal.timeInMillis = current
        DatePickerDialog(
            act,
            { _: DatePicker, y: Int, m: Int, day: Int ->
                val c = Calendar.getInstance().apply {
                    set(y, m, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPick(c.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun timePick(act: Activity, currentMinutes: Int, onPick: (Int) -> Unit) {
        val h = if (currentMinutes in 0..(24 * 60)) currentMinutes / 60 else 9
        val m = if (currentMinutes in 0..(24 * 60)) currentMinutes % 60 else 0
        TimePickerDialog(act, { _: TimePicker, hh: Int, mm: Int ->
            onPick(hh * 60 + mm)
        }, h, m, false).show()
    }

    // ------------------------------------------------------------- snackbar / toast

    fun snack(act: Activity, text: String) {
        try {
            val root = act.findViewById<FrameLayout>(com.estatedesk.crm.R.id.screen_root) ?: return
            root.removeView(root.findViewWithTag<View>("snack"))
            val p = pal()
            val t = tv(act, text, 14, p.white, Font.MEDIUM).apply {
                tag = "snack"
                setBackgroundColor(if (p.isDark) 0xFF2A3242.toInt() else 0xFF1D2939.toInt())
                setPadding(dp(act, 16), dp(act, 12), dp(act, 16), dp(act, 12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(act, 12).toFloat()
                    setColor(if (p.isDark) 0xFF2A3242.toInt() else 0xFF1D2939.toInt())
                }
                elevation = dp(act, 6).toFloat()
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            lp.setMargins(dp(act, 16), 0, dp(act, 16), dp(act, 24))
            root.addView(t, lp)
            t.postDelayed({ root.removeView(t) }, 2600)
        } catch (t: Throwable) {
        }
    }

    fun toast(act: Activity, text: String) = snack(act, text)

    // ------------------------------------------------------------- empty state

    fun emptyState(ctx: Context, iconRes: Int, titleText: String, hint: String,
                   actionText: String? = null, onAction: (() -> Unit)? = null): LinearLayout {
        val box = vbox(ctx)
        box.gravity = Gravity.CENTER_HORIZONTAL
        val ic = icon(ctx, iconRes, 40, pal().textTertiary)
        val lp = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40))
        lp.topMargin = dp(ctx, 40)
        box.addView(ic, lp)
        val t = tv(ctx, titleText, 17, pal().textPrimary, Font.BOLD)
        t.gravity = Gravity.CENTER
        val lpT = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpT.topMargin = dp(ctx, 16)
        box.addView(t, lpT)
        val h = tv(ctx, hint, 14, pal().textSecondary)
        h.gravity = Gravity.CENTER
        val lpH = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpH.topMargin = dp(ctx, 6)
        lpH.leftMargin = dp(ctx, 32)
        lpH.rightMargin = dp(ctx, 32)
        box.addView(h, lpH)
        if (actionText != null && onAction != null) {
            val b = btn(ctx, actionText, Btn.PRIMARY) { onAction() }
            val lpb = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lpb.topMargin = dp(ctx, 20)
            box.addView(b, lpb)
        }
        return box
    }

    // ------------------------------------------------------------- misc

    fun hideKeyboard(v: View) {
        try {
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        } catch (t: Throwable) {
        }
    }

    fun avatar(ctx: Context, name: String, size: Int = 44): TextView {
        val p = pal()
        val colors = listOf(
            0xFF175CD3.toInt(), 0xFF0E9384.toInt(), 0xFF7A5AF8.toInt(),
            0xFFB54708.toInt(), 0xFFC11574.toInt(), 0xFF067647.toInt(), 0xFFE04F16.toInt()
        )
        val hash = (name.hashCode() and 0x7FFFFFFF) % colors.size
        val bg = colors[hash]
        val av = TextView(ctx).apply {
            text = Fmt.initials(name)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, (size / 2.6f))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = font(Font.MEDIUM)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bg)
            }
        }
        val d = dp(ctx, size)
        av.layoutParams = LinearLayout.LayoutParams(d, d)
        return av
    }

    /** Horizontal scrollable chip row. */
    fun chipRow(ctx: Context, items: List<Pair<String, Boolean>>,
                onTap: (Int) -> Unit): HorizontalScrollView {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 4))
        }
        items.forEachIndexed { i, (text, selected) ->
            val c = chip(ctx, text, selected) { onTap(i) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(ctx, 8)
            row.addView(c, lp)
        }
        return HorizontalScrollView(ctx).apply {
            addView(row)
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(pal().bg)
        }
    }

    /** Detail row: label left, value right. */
    fun kv(ctx: Context, key: String, value: String, valueColor: Int? = null): LinearLayout {
        val row = hbox(ctx)
        row.addView(tv(ctx, key, 13, pal().textTertiary), weightLps(1f))
        val v = tv(ctx, value.ifBlank { "—" }, 13, valueColor ?: pal().textPrimary, Font.MEDIUM)
        v.gravity = Gravity.END
        row.addView(v)
        margin(row, 0, 6, 0, 6)
        return row
    }
}
