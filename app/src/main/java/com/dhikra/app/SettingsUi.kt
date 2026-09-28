package com.dhikra.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView

/** Shared programmatic UI helpers for the settings screens. */
object SettingsUi {

    fun dp(context: Context): Float = context.resources.displayMetrics.density

    fun sectionTitle(context: Context, pal: Palette, s: String) = TextView(context).apply {
        text = s
        textSize = 17f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setTextColor(pal.gold)
        val d = dp(context)
        setPadding(0, (26 * d).toInt(), 0, (8 * d).toInt())
    }

    fun fieldLabel(context: Context, pal: Palette, s: String) = TextView(context).apply {
        text = s
        textSize = 14f
        setTextColor(pal.textDim)
        val d = dp(context)
        setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
    }

    fun card(context: Context, pal: Palette): LinearLayout {
        val d = dp(context)
        val p = (16 * d).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(pal.card)
                cornerRadius = 18 * d
                setStroke((1 * d).toInt().coerceAtLeast(1), pal.gold)
            }
            setPadding(p, p, p, p)
        }
    }

    fun screenRoot(context: Context, pal: Palette): LinearLayout {
        val d = dp(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
            setBackgroundColor(pal.background)
        }
    }

    fun bodyText(context: Context, pal: Palette, s: String) = TextView(context).apply {
        text = s
        textSize = 15f
        setTextColor(pal.text)
        setPadding(0, (6 * dp(context)).toInt(), 0, (6 * dp(context)).toInt())
    }
}
