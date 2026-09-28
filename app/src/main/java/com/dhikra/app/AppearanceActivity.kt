package com.dhikra.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView

/** Theme selector. Applies immediately. */
class AppearanceActivity : Activity() {

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

        root.addView(SettingsUi.sectionTitle(this, pal, "Appearance"))
        val themeCard = SettingsUi.card(this, pal)
        val themeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val burgundy = RadioButton(this).apply {
            text = "Burgundy Gold"
            setTextColor(pal.text)
            tag = UiPrefs.THEME_BURGUNDY
            id = View.generateViewId()
        }
        val emerald = RadioButton(this).apply {
            text = "Emerald Night"
            setTextColor(pal.text)
            tag = UiPrefs.THEME_EMERALD
            id = View.generateViewId()
        }
        themeGroup.addView(burgundy)
        themeGroup.addView(emerald)
        themeGroup.check(if (ui.theme == UiPrefs.THEME_EMERALD) emerald.id else burgundy.id)
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val rb = findViewById<RadioButton>(checkedId)
            val chosen = rb.tag as String
            if (chosen != UiPrefs(this).theme) {
                UiPrefs(this).theme = chosen
                recreate()
            }
        }
        themeCard.addView(themeGroup)
        root.addView(themeCard)
    }

    override fun onResume() {
        super.onResume()
        if (UiPrefs(this).theme != appliedTheme) recreate()
    }
}
