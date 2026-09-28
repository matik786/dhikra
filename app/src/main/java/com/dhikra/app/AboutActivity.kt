package com.dhikra.app

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** About: version, sources, credits. */
class AboutActivity : Activity() {

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

        root.addView(SettingsUi.sectionTitle(this, pal, "About"))
        val card = SettingsUi.card(this, pal)
        card.addView(TextView(this).apply {
            text = "Dhikra"
            textSize = 20f
            setTextColor(pal.gold)
        })
        card.addView(SettingsUi.bodyText(this, pal, "Version ${versionName()}"))
        card.addView(SettingsUi.bodyText(this, pal, "Quran text: Tanzil.net (CC BY 3.0)."))
        card.addView(
            SettingsUi.bodyText(
                this, pal,
                "English translation: Marmaduke Pickthall (1930), public domain."
            )
        )
        card.addView(
            SettingsUi.bodyText(
                this, pal,
                "Prayer times are calculated estimates unless a mosque schedule is entered."
            )
        )
        root.addView(card)
    }

    override fun onResume() {
        super.onResume()
        if (UiPrefs(this).theme != appliedTheme) recreate()
    }

    private fun versionName(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (_: PackageManager.NameNotFoundException) {
        "1.0"
    }
}
