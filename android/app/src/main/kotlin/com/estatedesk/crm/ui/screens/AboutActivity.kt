package com.estatedesk.crm.ui.screens

import com.estatedesk.crm.R
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Ui

class AboutActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.settings_about))
        val card = Ui.card(this, emptyList(), padding = 20)
        val icon = Ui.icon(this@AboutActivity, R.drawable.ic_home, 44, p.primary)
        card.addView(icon)
        card.addView(Ui.spacer(this, 10))
        card.addView(Ui.h1(this@AboutActivity, getString(R.string.app_name)))
        card.addView(Ui.tv(this@AboutActivity, getString(R.string.about_version, "1.0.0"), 13, p.textTertiary))
        card.addView(Ui.spacer(this, 12))
        card.addView(Ui.body(this@AboutActivity, getString(R.string.about_desc)))
        add(card)

        add(Ui.sectionTitle(this, getString(R.string.about_privacy_title)))
        add(Ui.card(this, listOf(Ui.body(this@AboutActivity, getString(R.string.about_privacy_body)))))
        add(Ui.spacer(this, 8))
        add(Ui.sectionTitle(this, getString(R.string.about_help_title)))
        add(Ui.card(this, listOf(Ui.body(this@AboutActivity, getString(R.string.about_help_body)))))
        add(Ui.spacer(this, 8))
        add(Ui.caption(this@AboutActivity, getString(R.string.about_license)))
        add(Ui.spacer(this, 24))
    }
}
