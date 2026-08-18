package com.estatedesk.crm.core

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.estatedesk.crm.App
import com.estatedesk.crm.ui.screens.LockActivity
import com.estatedesk.crm.ui.screens.LockActivity.Companion.KEY_LAUNCH_INTENT

/**
 * Base screen. Applies the palette (light/dark), manages status-bar styling,
 * provides the FAB container and integrates app lock.
 */
abstract class BaseActivity : Activity() {

    protected val p: Palette get() = App.palette()
    protected lateinit var root: FrameLayout
    protected lateinit var content: LinearLayout
    private var scroll: ScrollView? = null
    private var lockedOut = false

    abstract fun build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeState()
        root = FrameLayout(this).apply {
            id = com.estatedesk.crm.R.id.screen_root
            setBackgroundColor(p.bg)
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setFillViewport(true)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@BaseActivity, 16), Ui.dp(this@BaseActivity, 8),
                Ui.dp(this@BaseActivity, 16), Ui.dp(this@BaseActivity, 24))
        }
        scroll?.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        build()
        root.post { scroll?.scrollTo(0, 0) }
    }

    override fun onStart() {
        super.onStart()
        if (!lockedOut) maybeLock()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !(this is LockActivity)) {
            Prefs(this).set(Prefs.KEY_LAST_BG_TIME, System.currentTimeMillis())
        }
    }

    private fun maybeLock() {
        val prefs = Prefs(this)
        if (!prefs.bool(Prefs.KEY_LOCK_ENABLED, false)) return
        if (this is LockActivity) return
        val timeoutKey = prefs.int(Prefs.KEY_LOCK_TIMEOUT, 0)
        val lastBg = prefs.long(Prefs.KEY_LAST_BG_TIME, 0)
        val now = System.currentTimeMillis()
        val grace = when (timeoutKey) {
            1 -> 30_000L
            2 -> 60_000L
            3 -> 300_000L
            else -> 0L
        }
        if (lastBg > 0 && now - lastBg > grace) {
            lockedOut = true
            val launch = Intent(this, LockActivity::class.java)
            launch.putExtra(KEY_LAUNCH_INTENT, intent)
            startActivity(launch)
            overridePendingTransition(0, 0)
        }
    }

    /** Called after successful unlock — re-runs so the screen refreshes. */
    fun onUnlocked() {
        lockedOut = false
        Prefs(this).set(Prefs.KEY_LAST_BG_TIME, 0L)
    }

    fun applyThemeState() {
        p.apply(this)
        val window: Window = window
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility =
                if (p.isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    /** Add to the scrollable content column. */
    fun add(view: View) = content.addView(view)

    fun add(view: View, lp: LinearLayout.LayoutParams) = content.addView(view, lp)

    /** Add a fixed bottom bar (outside the scroll area). */
    fun setBottomBar(view: View) {
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(view, lp)
    }

    /** Convenience: themed back arrow at the top of the content. */
    fun topBar(title: String, subtitle: String? = null,
               actions: List<Pair<Int, () -> Unit>> = emptyList()): Ui.TopBar {
        val tb = Ui.topBar(this, title, subtitle, back = true, actions = actions)
        add(tb.root)
        return tb
    }

    fun fab(): android.widget.ImageView = Ui.fab(this) {}.also { root.addView(it) }

    fun addFab(onClick: () -> Unit): android.widget.ImageView {
        val f = Ui.fab(this, onClick)
        root.addView(f)
        return f
    }

    fun snack(text: String) = Ui.snack(this, text)

    protected fun statusBarInset(): Int {
        var h = 0
        try {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) h = resources.getDimensionPixelSize(id)
        } catch (t: Throwable) {
        }
        return h
    }
}
