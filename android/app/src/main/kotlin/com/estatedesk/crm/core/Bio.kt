package com.estatedesk.crm.core

import android.app.Activity
import android.app.KeyguardManager

/** Device-credential (PIN / pattern / biometric) confirmation.
 *  Uses the framework KeyguardManager API — works on any Activity with the
 *  user's enrolled biometrics or device PIN, no androidx required. */
object Bio {

    const val REQ_CONFIRM = 1201

    fun available(activity: Activity): Boolean {
        val km = activity.getSystemService(Activity.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure == true
    }

    fun confirm(activity: Activity, title: String, subtitle: String): Boolean {
        val km = activity.getSystemService(Activity.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return false
        if (!km.isDeviceSecure) return false
        val intent = km.createConfirmDeviceCredentialIntent(title, subtitle)
        activity.startActivityForResult(intent, REQ_CONFIRM)
        return true
    }

    fun isConfirmResult(requestCode: Int, resultCode: Int): Boolean =
        requestCode == REQ_CONFIRM && resultCode == Activity.RESULT_OK
}
