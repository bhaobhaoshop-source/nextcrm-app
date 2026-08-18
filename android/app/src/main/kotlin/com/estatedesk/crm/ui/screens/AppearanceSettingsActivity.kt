package com.estatedesk.crm.ui.screens

import com.estatedesk.crm.R
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Palette
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Ui

class AppearanceSettingsActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.settings_appearance))
        add(Ui.pickField(this, getString(R.string.appearance_theme), themeLabel()) {
            Ui.pick(this, getString(R.string.appearance_theme),
                listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))) { i ->
                Prefs(this).set(Prefs.KEY_THEME, i)
                p.themePref = i
                p.apply(this)
                applyThemeState()
                // recreate to repaint all views with the new palette
                recreate()
            }
        })
        add(Ui.caption(this@AppearanceSettingsActivity, getString(R.string.settings_appearance_desc)))
    }

    private fun themeLabel(): String = when (Prefs(this).int(Prefs.KEY_THEME, Palette.THEME_SYSTEM)) {
        Palette.THEME_LIGHT -> getString(R.string.theme_light)
        Palette.THEME_DARK -> getString(R.string.theme_dark)
        else -> getString(R.string.theme_system)
    }
}
