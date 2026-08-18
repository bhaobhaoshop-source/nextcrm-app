package com.estatedesk.crm.core

import android.content.Context
import android.content.res.Configuration

/**
 * Theme engine. Two full palettes (light/dark); the active one is chosen by
 * the user preference (System / Light / Dark). All UiKit views read colors
 * from here so every screen shares one design language.
 */
class Palette {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }

    var themePref: Int = THEME_SYSTEM
    var isDark: Boolean = false
        private set

    // resolved colors
    var primary = 0xFF175CD3.toInt()
    var primaryDark = 0xFF12419E.toInt()
    var primarySoft = 0xFFEAF1FE.toInt()
    var accent = 0xFFE8A33D.toInt()
    var accentSoft = 0xFFFCF3E3.toInt()
    var bg = 0xFFF4F6FA.toInt()
    var surface = 0xFFFFFFFF.toInt()
    var surface2 = 0xFFEEF2F8.toInt()
    var textPrimary = 0xFF101828.toInt()
    var textSecondary = 0xFF475467.toInt()
    var textTertiary = 0xFF98A2B3.toInt()
    var border = 0xFFE4E7EC.toInt()
    var divider = 0xFFEAECF0.toInt()
    var success = 0xFF12B76A.toInt()
    var successSoft = 0xFFE6F7EF.toInt()
    var warning = 0xFFF79009.toInt()
    var warningSoft = 0xFFFEF3E2.toInt()
    var danger = 0xFFF04438.toInt()
    var dangerSoft = 0xFFFEEAE9.toInt()
    var info = 0xFF2E90FA.toInt()
    var infoSoft = 0xFFE9F2FE.toInt()
    var hot = 0xFFF04438.toInt()
    var warm = 0xFFF79009.toInt()
    var cold = 0xFF2E90FA.toInt()
    var navy = 0xFF101828.toInt()
    var scrim = 0x80101828.toInt()
    var white = 0xFFFFFFFF.toInt()
    var chartGrid = 0xFFEAECF0.toInt()
    var chipBg = 0xFFEEF2F8.toInt()

    fun init(ctx: Context) {
        themePref = Prefs(ctx).int(Prefs.KEY_THEME, THEME_SYSTEM)
        apply(ctx)
    }

    fun apply(ctx: Context) {
        val systemDark = (ctx.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        isDark = when (themePref) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> systemDark
        }
        if (isDark) {
            primary = 0xFF3E7BF6.toInt(); primaryDark = 0xFF6B9BF8.toInt(); primarySoft = 0xFF142A4D.toInt()
            accent = 0xFFE8A33D.toInt(); accentSoft = 0xFF3A2E16.toInt()
            bg = 0xFF0E1116.toInt(); surface = 0xFF171B23.toInt(); surface2 = 0xFF202634.toInt()
            textPrimary = 0xFFF2F4F7.toInt(); textSecondary = 0xFFA9B2C2.toInt(); textTertiary = 0xFF6B7385.toInt()
            border = 0xFF29303E.toInt(); divider = 0xFF242B37.toInt()
            success = 0xFF2BD98F.toInt(); successSoft = 0xFF12352A.toInt()
            warning = 0xFFFDB022.toInt(); warningSoft = 0xFF3A2E10.toInt()
            danger = 0xFFF97066.toInt(); dangerSoft = 0xFF43231F.toInt()
            info = 0xFF53B1FD.toInt(); infoSoft = 0xFF132B47.toInt()
            hot = 0xFFF97066.toInt(); warm = 0xFFFDB022.toInt(); cold = 0xFF53B1FD.toInt()
            navy = 0xFF101828.toInt(); scrim = 0xB0000000.toInt()
            chartGrid = 0xFF242B37.toInt(); chipBg = 0xFF202634.toInt()
        } else {
            primary = 0xFF175CD3.toInt(); primaryDark = 0xFF12419E.toInt(); primarySoft = 0xFFEAF1FE.toInt()
            accent = 0xFFE8A33D.toInt(); accentSoft = 0xFFFCF3E3.toInt()
            bg = 0xFFF4F6FA.toInt(); surface = 0xFFFFFFFF.toInt(); surface2 = 0xFFEEF2F8.toInt()
            textPrimary = 0xFF101828.toInt(); textSecondary = 0xFF475467.toInt(); textTertiary = 0xFF98A2B3.toInt()
            border = 0xFFE4E7EC.toInt(); divider = 0xFFEAECF0.toInt()
            success = 0xFF12B76A.toInt(); successSoft = 0xFFE6F7EF.toInt()
            warning = 0xFFF79009.toInt(); warningSoft = 0xFFFEF3E2.toInt()
            danger = 0xFFF04438.toInt(); dangerSoft = 0xFFFEEAE9.toInt()
            info = 0xFF2E90FA.toInt(); infoSoft = 0xFFE9F2FE.toInt()
            hot = 0xFFF04438.toInt(); warm = 0xFFF79009.toInt(); cold = 0xFF2E90FA.toInt()
            navy = 0xFF101828.toInt(); scrim = 0x80101828.toInt()
            chartGrid = 0xFFEAECF0.toInt(); chipBg = 0xFFEEF2F8.toInt()
        }
    }
}
