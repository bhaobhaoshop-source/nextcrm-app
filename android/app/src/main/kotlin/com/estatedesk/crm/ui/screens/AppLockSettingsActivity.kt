package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Bio
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Security
import com.estatedesk.crm.core.Ui

class AppLockSettingsActivity : BaseActivity() {

    private var bioPendingEnable = false

    override fun build() {
        topBar(getString(R.string.settings_app_lock))
        val prefs = Prefs(this)
        val enabled = CheckBox(this).apply {
            text = getString(R.string.lock_enable)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = prefs.bool(Prefs.KEY_LOCK_ENABLED, false)
            setOnCheckedChangeListener { _, b ->
                if (b && !hasPin()) {
                    isChecked = false
                    setupPin()
                } else {
                    prefs.set(Prefs.KEY_LOCK_ENABLED, b)
                }
            }
        }
        add(enabled)

        add(Ui.pickField(this, getString(R.string.lock_timeout), timeoutLabel()) {
            Ui.pick(this, getString(R.string.lock_timeout), listOf(
                getString(R.string.lock_timeout_immediate), getString(R.string.lock_timeout_30s),
                getString(R.string.lock_timeout_1m), getString(R.string.lock_timeout_5m))) { i ->
                prefs.set(Prefs.KEY_LOCK_TIMEOUT, i)
            }
        })

        add(Ui.btn(this, if (hasPin()) getString(R.string.lock_change_pin) else getString(R.string.lock_set_pin),
            Ui.Btn.SECONDARY) { setupPin() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@AppLockSettingsActivity, 10)
            })

        val bio = CheckBox(this).apply {
            text = getString(R.string.lock_biometric)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = prefs.bool(Prefs.KEY_LOCK_BIOMETRIC, false)
            setOnCheckedChangeListener { _, b ->
                if (b) {
                    if (!Bio.confirm(this@AppLockSettingsActivity,
                            getString(R.string.lock_biometric_title),
                            getString(R.string.lock_biometric_subtitle))) {
                        isChecked = false
                        snack(getString(R.string.error_generic))
                    } else {
                        bioPendingEnable = true
                    }
                } else {
                    prefs.set(Prefs.KEY_LOCK_BIOMETRIC, false)
                }
            }
        }
        add(bio)
        add(Ui.caption(this, getString(R.string.settings_app_lock_desc)))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (bioPendingEnable) {
            bioPendingEnable = false
            if (Bio.isConfirmResult(requestCode, resultCode)) {
                Prefs(this).set(Prefs.KEY_LOCK_BIOMETRIC, true)
                snack(getString(R.string.saved))
            }
        }
    }

    private fun hasPin(): Boolean {
        val prefs = Prefs(this)
        return prefs.str(Prefs.KEY_LOCK_PIN_HASH, "").isNotBlank()
    }

    private fun timeoutLabel(): String = when (Prefs(this).int(Prefs.KEY_LOCK_TIMEOUT, 0)) {
        1 -> getString(R.string.lock_timeout_30s)
        2 -> getString(R.string.lock_timeout_1m)
        3 -> getString(R.string.lock_timeout_5m)
        else -> getString(R.string.lock_timeout_immediate)
    }

    private fun setupPin() {
        askPin(getString(R.string.lock_set_pin)) { pin1 ->
            if (pin1.length < 4) {
                snack(getString(R.string.lock_pin_short)); return@askPin
            }
            askPin(getString(R.string.lock_confirm_pin)) { pin2 ->
                if (pin1 != pin2) {
                    snack(getString(R.string.lock_pin_mismatch)); return@askPin
                }
                val salt = Security.randomSalt()
                val hash = Security.hashPin(pin1, salt)
                val prefs = Prefs(this)
                prefs.set(Prefs.KEY_LOCK_PIN_SALT, Security.saltToHex(salt))
                prefs.set(Prefs.KEY_LOCK_PIN_HASH, hash)
                prefs.set(Prefs.KEY_LOCK_ENABLED, true)
                snack(getString(R.string.saved))
            }
        }
    }

    private fun askPin(title: String, onOk: (String) -> Unit) {
        val edit = Ui.input(this, title,
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        Ui.sheet(this, title, edit, listOf(
            getString(R.string.cancel) to Ui.Btn.SECONDARY,
            getString(R.string.save) to Ui.Btn.PRIMARY
        )) { idx ->
            if (idx == 1) onOk(edit.text.toString())
        }.show()
    }
}
