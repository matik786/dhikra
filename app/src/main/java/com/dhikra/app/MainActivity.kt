package com.dhikra.app

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var repo: VerseRepository
    private lateinit var renderer: AyahRenderer
    private lateinit var pal: Palette
    private lateinit var cardView: ImageView
    private lateinit var actionList: LinearLayout
    private lateinit var nextPrayerLine: TextView
    private var quranRowLabel: TextView? = null
    private var appliedTheme: String = ""

    private val dp: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        val ui = UiPrefs(this)
        appliedTheme = ui.theme
        setTheme(UiPrefs.themeResId(ui.theme))
        super.onCreate(savedInstanceState)
        pal = Palettes.of(ui.theme)
        setContentView(R.layout.activity_main)

        repo = VerseRepository(this)
        renderer = AyahRenderer(this)
        cardView = findViewById(R.id.cardView)
        actionList = findViewById(R.id.actionList)
        nextPrayerLine = findViewById(R.id.nextPrayerLine)

        buildActions()
        showCurrent()
    }

    override fun onResume() {
        super.onResume()
        // Theme may have changed in Settings while this activity was underneath:
        // recreate so the new palette applies immediately.
        if (UiPrefs(this).theme != appliedTheme) {
            recreate()
            return
        }
        // Re-arm alarms: granting location in system Settings (or a day change)
        // never passes through Settings' Save. reschedule() is idempotent.
        if (PrayerPrefs(this).masterEnabled) {
            try {
                PrayerScheduler.reschedule(this)
            } catch (_: Exception) {
            }
        }
        showCurrent()
    }

    private fun buildActions() {
        addRow(android.R.drawable.ic_media_next, "Next verse") {
            repo.next()
            showCurrent()
            AyahWidgetProvider.updateAll(this)
        }
        addRow(android.R.drawable.ic_menu_gallery, "Set as wallpaper") { setWallpaper() }
        addRow(android.R.drawable.ic_menu_add, "Add widget to home screen") { requestPinWidget() }
        quranRowLabel = addRow(android.R.drawable.ic_menu_view, "Open on quran.com") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.current().quranComUrl)))
        }
        addRow(android.R.drawable.ic_lock_idle_alarm, "Prayer reminder settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /** Returns the label view so its text can be updated later. */
    private fun addRow(iconRes: Int, label: String, onClick: () -> Unit): TextView {
        val pad = (16 * dp).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.row_bg)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            foreground = getDrawable(tv.resourceId)
            setOnClickListener { onClick() }
        }

        val chipSize = (52 * dp).toInt()
        val chip = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.chip_bg)
            layoutParams = LinearLayout.LayoutParams(chipSize, chipSize)
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(pal.gold)
        }
        val iconPad = (13 * dp).toInt()
        chip.addView(iv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { setMargins(iconPad, iconPad, iconPad, iconPad) })

        val tv = TextView(this).apply {
            text = label
            setTextColor(pal.text)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = (16 * dp).toInt()
            }
        }
        val chev = TextView(this).apply {
            text = "\u203A"
            setTextColor(pal.gold)
            textSize = 26f
        }
        row.addView(chip)
        row.addView(tv)
        row.addView(chev)
        actionList.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, -2
        ).apply { bottomMargin = (10 * dp).toInt() })
        return tv
    }

    private fun showCurrent() {
        val v = repo.current()
        cardView.setImageBitmap(renderer.renderCard(v, 1080, 1350, pal))
        quranRowLabel?.text = "Open ${v.reference} on quran.com"
        val prayerText = nextPrayerText()
        if (prayerText.isEmpty() && PrayerPrefs(this).masterEnabled &&
            PrayerPrefs(this).locationMode == 0
        ) {
            nextPrayerLine.text = "Getting location for prayer times…"
            nextPrayerLine.visibility = View.VISIBLE
        } else {
            nextPrayerLine.text = prayerText
            nextPrayerLine.visibility = if (prayerText.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun nextPrayerText(): String {
        val next = try {
            PrayerInfo.next(this)
        } catch (_: Exception) {
            null
        } ?: return ""
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var diff = next.minutes - nowMin
        if (diff < 0) diff += 1440
        val h = diff / 60
        val m = diff % 60
        val countdown = when {
            h > 0 -> "in ${h}h ${m}m"
            else -> "in ${m}m"
        }
        val time = PrayerInfo.minutesToDisplay(next.minutes)
        return "${next.name} $countdown · $time"
    }

    private fun setWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)
            // Render at the exact screen size. Using desiredMinimumWidth (which is
            // typically 2x the screen width for parallax scrolling) produced a
            // zoomed, cropped wallpaper.
            val dm = resources.displayMetrics
            val bmp = renderer.renderWallpaper(repo.current(), dm.widthPixels, dm.heightPixels, pal)
            wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
            Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPinWidget() {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            val cn = ComponentName(this, AyahWidgetProvider::class.java)
            mgr.requestPinAppWidget(cn, null, null)
        } else {
            Toast.makeText(this, "Add the widget from your launcher", Toast.LENGTH_LONG).show()
        }
    }
}
