package com.estatedesk.crm.ui.screens

import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Fmt
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.CurrencyCfg

class CurrencySettingsActivity : BaseActivity() {
    override fun build() {
        topBar(getString(R.string.settings_currency))
        val cur = Di.store.currency()
        val codes = listOf(
            "PKR" to "Rs", "USD" to "$", "EUR" to "€", "GBP" to "£",
            "AED" to "AED", "SAR" to "SAR", "INR" to "₹", "CAD" to "C$", "AUD" to "A$"
        )
        val names = codes.map { "${it.first} (${it.second})" }
        var code = cur.code
        var symbol = cur.symbol
        var decimals = cur.decimals
        var prefix = cur.prefix

        add(Ui.pickField(this, getString(R.string.currency_code), code) {
            Ui.pick(this, getString(R.string.currency_code), names) { i ->
                code = codes[i].first
                symbol = codes[i].second
                decimals = if (code == "PKR") 0 else 2
            }
        })
        val sym = Ui.input(this@CurrencySettingsActivity, getString(R.string.currency_symbol), symbol)
        add(Ui.field(this, getString(R.string.currency_symbol), sym))
        val dec = Ui.input(this@CurrencySettingsActivity, "0", decimals.toString(), InputType.TYPE_CLASS_NUMBER)
        add(Ui.field(this, getString(R.string.currency_decimals), dec))
        add(Ui.pickField(this, getString(R.string.currency_position),
            if (prefix) getString(R.string.currency_prefix) else getString(R.string.currency_suffix)) {
            Ui.pick(this, getString(R.string.currency_position),
                listOf(getString(R.string.currency_prefix), getString(R.string.currency_suffix))) { i ->
                prefix = i == 0
            }
        })
        add(Ui.caption(this@CurrencySettingsActivity, getString(R.string.currency_sample) + ": " +
                Fmt.money(12_345_678, CurrencyCfg(code, symbol, decimals, prefix))))
        add(Ui.btn(this@CurrencySettingsActivity, getString(R.string.save), Ui.Btn.PRIMARY) {
            Async.write({
                Di.store.saveCurrency(CurrencyCfg(code, sym.text.toString().ifBlank { "Rs" },
                    dec.text.toString().toIntOrNull() ?: 0, prefix))
            }) {
                snack(getString(R.string.saved))
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
