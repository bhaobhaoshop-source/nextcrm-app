package com.estatedesk.crm.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Bio
import com.estatedesk.crm.core.Prefs
import com.estatedesk.crm.core.Security
import com.estatedesk.crm.core.Ui

/** App lock gate: PIN with optional biometrics. */
class LockActivity : BaseActivity() {

    private var launchIntent: Intent? = null

    companion object {
        const val KEY_LAUNCH_INTENT = "launch_intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntent = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(KEY_LAUNCH_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(KEY_LAUNCH_INTENT)
    }

    override fun build() {
        val box = Ui.vbox(this)
        box.gravity = Gravity.CENTER
        val icon = Ui.icon(this@LockActivity, R.drawable.ic_lock, 44, p.primary)
        icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(this@LockActivity, 44), Ui.dp(this@LockActivity, 44))
        box.addView(icon)
        add(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        add(Ui.tv(this@LockActivity, getString(R.string.lock_enter_pin), 16, p.textPrimary, Ui.Font.MEDIUM))
        val edit = Ui.input(this@LockActivity, getString(R.string.lock_enter_pin),
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        add(edit)
        add(Ui.btn(this@LockActivity, getString(R.string.lock_enter_pin), Ui.Btn.PRIMARY) {
            checkPin(edit.text.toString())
        })
        if (Prefs(this).bool(Prefs.KEY_LOCK_BIOMETRIC, false)) {
            Bio.confirm(this, getString(R.string.lock_biometric_title),
                getString(R.string.lock_biometric_subtitle))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Bio.isConfirmResult(requestCode, resultCode)) {
            unlock()
        }
    }

    private fun checkPin(pin: String) {
        val prefs = Prefs(this)
        if (Security.verifyPin(pin, prefs.str(Prefs.KEY_LOCK_PIN_SALT, ""),
                prefs.str(Prefs.KEY_LOCK_PIN_HASH, ""))) {
            unlock()
        } else {
            snack(getString(R.string.lock_wrong_pin))
        }
    }

    private fun unlock() {
        Prefs(this).set(Prefs.KEY_LAST_BG_TIME, 0L)
        val target = launchIntent
        if (target != null && target.component != null) {
            // relaunch the target screen
            val i = Intent(target)
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } else {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() {
        // keep the app locked: go home instead of revealing the previous screen
        moveTaskToBack(true)
    }
}
