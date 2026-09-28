package com.dhikra.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Settings hub: Appearance, Prayer reminders, About. */
class SettingsActivity : Activity() {

    private lateinit var pal: Palette
    private var appliedTheme: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val ui = UiPrefs(this)
        appliedTheme = ui.theme
        setTheme(UiPrefs.themeResId(ui.theme))
        super.onCreate(savedInstanceState)
        pal = Palettes.of(ui.theme)

        val root = SettingsUi.screenRoot(this, pal)
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(SettingsUi.sectionTitle(this, pal, "Settings"))
        root.addView(hubRow("Appearance", "Theme", AppearanceActivity::class.java))
        root.addView(hubRow("Prayer reminders", "Times, mosque, notifications", PrayerSettingsActivity::class.java))
        root.addView(hubRow("About", "Sources and credits", AboutActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (UiPrefs(this).theme != appliedTheme) recreate()
    }

    private fun hubRow(title: String, subtitle: String, target: Class<*>): View {
        val d = SettingsUi.dp(this)
        val card = SettingsUi.card(this, pal)
        card.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(pal.text)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(pal.textDim)
            setPadding(0, (4 * d).toInt(), 0, 0)
        })
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (10 * d).toInt() }
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { startActivity(Intent(this, target)) }
        return card
    }
}
