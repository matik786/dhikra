package com.dhikra.app

import android.content.Context
import android.graphics.Color

/** App-wide appearance settings: theme choice, persisted. */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var theme: String
        get() = p.getString("theme", THEME_BURGUNDY) ?: THEME_BURGUNDY
        set(v) = p.edit().putString("theme", v).apply()

    companion object {
        const val THEME_BURGUNDY = "burgundy"
        const val THEME_EMERALD = "emerald"

        fun themeResId(theme: String): Int =
            if (theme == THEME_EMERALD) R.style.Theme_Dhikra_Emerald
            else R.style.Theme_Dhikra

        fun themeName(theme: String): String =
            if (theme == THEME_EMERALD) "Emerald Night" else "Burgundy Gold"
    }
}

/** Concrete colors for programmatic UI and rendered bitmaps. */
data class Palette(
    val background: Int,
    val card: Int,
    val gold: Int,
    val goldBright: Int,
    val text: Int,
    val textDim: Int,
    /** Gradient stops for verse card / wallpaper backgrounds, top to bottom. */
    val cardGradient: IntArray,
    /** Soft top-glow tint drawn over the card background. */
    val glowColor: Int
)

object Palettes {
    val BURGUNDY = Palette(
        background = Color.rgb(0x14, 0x0B, 0x0B),
        card = Color.rgb(0x2A, 0x12, 0x14),
        gold = Color.rgb(0xC9, 0xA2, 0x27),
        goldBright = Color.rgb(0xF0, 0xD8, 0x78),
        text = Color.rgb(0xF5, 0xEF, 0xE2),
        textDim = Color.rgb(0xC9, 0xBB, 0xA6),
        cardGradient = intArrayOf(
            Color.rgb(0x4A, 0x1A, 0x1E),
            Color.rgb(0x2A, 0x12, 0x14),
            Color.rgb(0x14, 0x0B, 0x0B)
        ),
        glowColor = Color.argb(60, 0xC9, 0xA2, 0x27)
    )
    val EMERALD = Palette(
        background = Color.rgb(0x0B, 0x12, 0x10),
        card = Color.rgb(0x14, 0x20, 0x1C),
        gold = Color.rgb(0xC9, 0xA2, 0x27),
        goldBright = Color.rgb(0xF0, 0xD8, 0x78),
        text = Color.rgb(0xF2, 0xF5, 0xEF),
        textDim = Color.rgb(0xB7, 0xC2, 0xB0),
        cardGradient = intArrayOf(
            Color.rgb(0x1E, 0x5F, 0x4F),
            Color.rgb(0x0E, 0x3D, 0x33),
            Color.rgb(0x07, 0x21, 0x1C)
        ),
        glowColor = Color.argb(70, 64, 140, 115)
    )

    fun of(theme: String): Palette =
        if (theme == UiPrefs.THEME_EMERALD) EMERALD else BURGUNDY

    fun of(context: Context): Palette = of(UiPrefs(context).theme)
}
