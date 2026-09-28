package com.dhikra.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Deep verse view: Arabic, transliteration, translation, reference + actions. */
class VerseDetailActivity : Activity() {

    private lateinit var repo: VerseRepository
    private lateinit var renderer: AyahRenderer
    private lateinit var pal: Palette
    private lateinit var body: LinearLayout
    private var appliedTheme: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val ui = UiPrefs(this)
        appliedTheme = ui.theme
        setTheme(UiPrefs.themeResId(ui.theme))
        super.onCreate(savedInstanceState)
        pal = Palettes.of(ui.theme)
        repo = VerseRepository(this)
        renderer = AyahRenderer(this)

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 64, 56, 64)
            setBackgroundColor(pal.background)
        }
        setContentView(ScrollView(this).apply { addView(body) })
        render()
    }

    override fun onResume() {
        super.onResume()
        // Theme may have changed in Settings while this activity was underneath.
        if (UiPrefs(this).theme != appliedTheme) {
            recreate()
        }
    }

    private fun render() {
        body.removeAllViews()
        val v = repo.current()
        val dp = resources.displayMetrics.density

        body.addView(TextView(this).apply {
            text = v.reference.uppercase()
            setTextColor(pal.gold)
            textSize = 15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        body.addView(spacer(18, dp))

        val amiri = try {
            resources.getFont(R.font.amiri_quran)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        body.addView(TextView(this).apply {
            text = v.arabic
            typeface = amiri
            setTextColor(pal.goldBright)
            textSize = 34f
            gravity = Gravity.CENTER
        })
        body.addView(spacer(14, dp))

        body.addView(TextView(this).apply {
            text = v.transliteration
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(pal.textDim)
            textSize = 16f
            gravity = Gravity.CENTER
        })
        body.addView(spacer(18, dp))

        body.addView(TextView(this).apply {
            text = "\u201C${v.translation}\u201D"
            setTextColor(pal.text)
            textSize = 19f
            gravity = Gravity.CENTER
        })
        body.addView(spacer(12, dp))

        body.addView(TextView(this).apply {
            text = v.theme.uppercase()
            setTextColor(pal.gold)
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        body.addView(spacer(30, dp))

        body.addView(actionButton("Next verse") {
            repo.next()
            render()
            AyahWidgetProvider.updateAll(this)
        })
        body.addView(actionButton("Set as wallpaper") { setWallpaper() })
        body.addView(actionButton("Open ${v.reference} on quran.com") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.quranComUrl)))
        })
        body.addView(spacer(20, dp))
        body.addView(TextView(this).apply {
            text = "Quran text: Tanzil.net (CC BY 3.0). English translation: Marmaduke Pickthall (1930), public domain."
            setTextColor(pal.textDim)
            textSize = 11f
            gravity = Gravity.CENTER
        })
    }

    private fun spacer(dpPx: Int, dp: Float) =
        TextView(this).apply { height = (dpPx * dp).toInt() }

    private fun actionButton(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            setTextColor(pal.background)
            setBackgroundColor(pal.gold)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
        }

    private fun setWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            val dm = resources.displayMetrics
            val bmp = renderer.renderWallpaper(repo.current(), dm.widthPixels, dm.heightPixels, pal)
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}
