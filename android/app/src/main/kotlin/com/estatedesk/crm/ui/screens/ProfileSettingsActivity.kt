package com.estatedesk.crm.ui.screens

import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Ui

class ProfileSettingsActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.settings_profile))
        val name = Ui.input(this@ProfileSettingsActivity, getString(R.string.profile_name), Di.store.profileName())
        val phone = Ui.input(this@ProfileSettingsActivity, getString(R.string.profile_phone), Di.store.getSetting("profile_phone"),
            inputType = InputType.TYPE_CLASS_PHONE)
        val email = Ui.input(this@ProfileSettingsActivity, getString(R.string.profile_email), Di.store.getSetting("profile_email"),
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val company = Ui.input(this@ProfileSettingsActivity, getString(R.string.profile_company), Di.store.profileCompany())
        add(Ui.field(this, getString(R.string.profile_name), name))
        add(Ui.field(this, getString(R.string.profile_phone), phone))
        add(Ui.field(this, getString(R.string.profile_email), email))
        add(Ui.field(this, getString(R.string.profile_company), company))
        add(Ui.btn(this@ProfileSettingsActivity, getString(R.string.save), Ui.Btn.PRIMARY) {
            Async.write({
                Di.store.saveProfile(name.text.toString().trim(), phone.text.toString().trim(),
                    email.text.toString().trim(), company.text.toString().trim().ifBlank { "EstateDesk" })
            }) {
                snack(getString(R.string.profile_saved))
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
