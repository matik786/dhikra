package com.dhikra.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Prayer reminder settings: master toggle, mosque auto-detect + nearby list,
 * calculation fallback, location, lead-time presets, per-prayer toggles,
 * notification tone, vibration, and manual overrides.
 */
class PrayerSettingsActivity : Activity() {

    private lateinit var prefs: PrayerPrefs
    private lateinit var pal: Palette
    private var appliedTheme: String = ""
    private val dp: Float by lazy { resources.displayMetrics.density }

    private lateinit var masterSwitch: Switch
    private lateinit var mosqueStatus: TextView
    private lateinit var detectStatus: TextView
    private lateinit var nearbyGroup: RadioGroup
    private lateinit var methodSpinner: Spinner
    private lateinit var asrGroup: RadioGroup
    private lateinit var locGroup: RadioGroup
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText
    private lateinit var coordRow: LinearLayout
    private lateinit var locPermLine: TextView
    private lateinit var leadChipRow: LinearLayout
    private lateinit var leadCustomInput: EditText
    private var leadMinutes: Int = 15
    private var leadCustom: Boolean = false
    private val prayerSwitches = mutableMapOf<String, Switch>()
    private lateinit var toneLabel: TextView
    private lateinit var vibrationSwitch: Switch
    private lateinit var masjidInput: EditText
    private val overrideInputs = mutableMapOf<String, EditText>()

    companion object {
        private const val REQ_TONE = 41
        private val LEAD_PRESETS = listOf(5, 10, 15, 20, 30)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val ui = UiPrefs(this)
        setTheme(UiPrefs.themeResId(ui.theme))
        appliedTheme = ui.theme
        super.onCreate(savedInstanceState)
        prefs = PrayerPrefs(this)
        pal = Palettes.of(ui.theme)
        leadMinutes = prefs.minutesBefore
        leadCustom = leadMinutes !in LEAD_PRESETS

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            setBackgroundColor(pal.background)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(SettingsUi.sectionTitle(this, pal, "Prayer reminders"))
        val masterCard = SettingsUi.card(this, pal)
        masterSwitch = Switch(this).apply {
            text = "Enable prayer reminders"
            setTextColor(pal.text)
            isChecked = prefs.masterEnabled
        }
        masterCard.addView(masterSwitch)
        root.addView(masterCard)

        // ---- Mosque ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Mosque"))
        val mosqueCard = SettingsUi.card(this, pal)
        mosqueStatus = TextView(this).apply {
            setTextColor(pal.text)
            textSize = 15f
        }
        mosqueCard.addView(mosqueStatus)
        val detectBtn = Button(this).apply {
            text = "Detect nearby mosques"
            setTextColor(pal.background)
            setBackgroundColor(pal.gold)
            setOnClickListener { detectMosques() }
        }
        mosqueCard.addView(detectBtn)
        detectStatus = TextView(this).apply {
            setTextColor(pal.textDim)
            textSize = 13f
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        mosqueCard.addView(detectStatus)
        nearbyGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        mosqueCard.addView(nearbyGroup)
        val refreshBtn = Button(this).apply {
            text = "Refresh mosque schedule"
            setTextColor(pal.gold)
            setBackgroundColor(pal.card)
            setOnClickListener { refreshSchedule() }
        }
        mosqueCard.addView(refreshBtn)
        root.addView(mosqueCard)
        refreshMosqueStatus()

        // ---- Calculation fallback ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Calculation (fallback)"))
        val calcCard = SettingsUi.card(this, pal)
        calcCard.addView(SettingsUi.fieldLabel(this, pal, "Method"))
        methodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PrayerSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                PrayTimes.METHODS.map { it.name }
            )
            setSelection(prefs.methodIndex.coerceIn(PrayTimes.METHODS.indices))
        }
        calcCard.addView(methodSpinner)
        calcCard.addView(SettingsUi.fieldLabel(this, pal, "Asr calculation"))
        asrGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val hanafi = RadioButton(this).apply { text = "Hanafi"; setTextColor(pal.text); id = View.generateViewId() }
        val shafii = RadioButton(this).apply { text = "Shafi'i"; setTextColor(pal.text); id = View.generateViewId() }
        asrGroup.addView(hanafi)
        asrGroup.addView(shafii)
        asrGroup.check(if (prefs.hanafiAsr) hanafi.id else shafii.id)
        calcCard.addView(asrGroup)
        root.addView(calcCard)

        // ---- Location ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Location"))
        val locCard = SettingsUi.card(this, pal)
        locGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val device = RadioButton(this).apply { text = "Device"; setTextColor(pal.text); id = View.generateViewId() }
        val manual = RadioButton(this).apply { text = "Manual"; setTextColor(pal.text); id = View.generateViewId() }
        locGroup.addView(device)
        locGroup.addView(manual)
        locGroup.check(if (prefs.locationMode == 1) manual.id else device.id)
        locCard.addView(locGroup)
        locPermLine = TextView(this).apply {
            setTextColor(pal.textDim)
            textSize = 13f
        }
        locCard.addView(locPermLine)
        coordRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        latInput = coordInput("Latitude", prefs.manualLat)
        lngInput = coordInput("Longitude", prefs.manualLng)
        coordRow.addView(latInput)
        coordRow.addView(lngInput)
        locCard.addView(coordRow)
        val showCoords = {
            coordRow.visibility = if (locGroup.checkedRadioButtonId == manual.id) View.VISIBLE else View.GONE
            updateLocPermLine()
        }
        showCoords()
        locGroup.setOnCheckedChangeListener { _, _ -> showCoords() }
        root.addView(locCard)

        // ---- Lead time ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Reminder lead time"))
        val leadCard = SettingsUi.card(this, pal)
        leadChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        leadCard.addView(leadChipRow)
        leadCustomInput = EditText(this).apply {
            hint = "Custom minutes"
            setHintTextColor(pal.textDim)
            setTextColor(pal.text)
            inputType = InputType.TYPE_CLASS_NUMBER
            visibility = View.GONE
        }
        leadCard.addView(leadCustomInput)
        root.addView(leadCard)
        renderLeadChips()

        // ---- Per-prayer toggles ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Per-prayer reminders"))
        val prayersCard = SettingsUi.card(this, pal)
        for (name in PrayerScheduler.PRAYERS) {
            val sw = Switch(this).apply {
                text = name
                setTextColor(pal.text)
                isChecked = prefs.prayerEnabled(name)
            }
            prayerSwitches[name] = sw
            prayersCard.addView(sw)
        }
        root.addView(prayersCard)

        // ---- Tone & vibration ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Notification"))
        val notifCard = SettingsUi.card(this, pal)
        val toneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { pickTone() }
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        toneRow.addView(TextView(this).apply {
            text = "Notification tone"
            setTextColor(pal.text)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        toneLabel = TextView(this).apply {
            setTextColor(pal.gold)
            textSize = 14f
        }
        toneRow.addView(toneLabel)
        notifCard.addView(toneRow)
        updateToneLabel()
        vibrationSwitch = Switch(this).apply {
            text = "Vibration"
            setTextColor(pal.text)
            isChecked = prefs.vibrationEnabled
        }
        notifCard.addView(vibrationSwitch)
        root.addView(notifCard)

        // ---- Manual overrides ----
        root.addView(SettingsUi.sectionTitle(this, pal, "Manual overrides"))
        val manualCard = SettingsUi.card(this, pal)
        manualCard.addView(TextView(this).apply {
            text = "Name your mosque and enter Iqamah times manually. " +
                    "Blank = use the detected or calculated time."
            setTextColor(pal.textDim)
            textSize = 13f
        })
        masjidInput = EditText(this).apply {
            hint = "Masjid name (optional)"
            setHintTextColor(pal.textDim)
            setTextColor(pal.text)
            setText(prefs.masjidName)
        }
        manualCard.addView(masjidInput)
        for (name in PrayerScheduler.PRAYERS) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = "$name Iqamah"
                setTextColor(pal.text)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            val et = EditText(this).apply {
                hint = "HH:MM"
                setHintTextColor(pal.textDim)
                setTextColor(pal.text)
                inputType = InputType.TYPE_CLASS_DATETIME
                setText(prefs.overrideTime(name))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            overrideInputs[name] = et
            row.addView(et)
            manualCard.addView(row)
        }
        root.addView(manualCard)



        root.addView(Button(this).apply {
            text = "Save"
            setTextColor(pal.background)
            setBackgroundColor(pal.gold)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (24 * dp).toInt() }
            setOnClickListener { saveAll(quiet = false) }
        })
    }

    override fun onResume() {
        super.onResume()
        if (UiPrefs(this).theme != appliedTheme) recreate()
    }

    // ---------- UI helpers ----------

    private fun sectionTitle(s: String) = TextView(this).apply {
        text = s
        textSize = 17f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        setTextColor(pal.gold)
        setPadding(0, (26 * dp).toInt(), 0, (8 * dp).toInt())
    }

    private fun fieldLabel(s: String) = TextView(this).apply {
        text = s
        textSize = 14f
        setTextColor(pal.textDim)
        setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
    }

    private fun card(): LinearLayout {
        val p = (16 * dp).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(pal.card)
                cornerRadius = 18 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), pal.gold)
            }
            setPadding(p, p, p, p)
        }
    }

    private fun coordInput(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(pal.textDim)
        setTextColor(pal.text)
        inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        setText(value)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    // ---------- Mosque ----------

    private fun refreshMosqueStatus() {
        val name = prefs.selectedMosqueName.trim()
        mosqueStatus.text = if (name.isEmpty()) {
            "No mosque selected — using calculated times.\n${PrayerInfo.sourceSummary(this)}"
        } else {
            val dist = prefs.selectedMosqueDistance.trim()
            val tag = if (prefs.selectedMosqueSource == "manual") "Manual" else "Auto-detected"
            "$name${if (dist.isNotEmpty()) " — $dist" else ""} [$tag]\n${PrayerInfo.sourceSummary(this)}"
        }
    }

    private fun detectMosques() {
        val loc = PrayerScheduler.resolveLocation(this, prefs)
        if (loc == null) {
            if (prefs.locationMode == 0) {
                ensureLocationPermission()
                Toast.makeText(this, "Grant location access, then detect again", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter manual coordinates first", Toast.LENGTH_SHORT).show()
            }
            return
        }
        detectStatus.text = "Searching…"
        nearbyGroup.removeAllViews()
        Thread {
            val results = MosqueSources.detectNearby(loc.first, loc.second)
            runOnUiThread { showDetectResults(results) }
        }.start()
    }

    private fun showDetectResults(results: List<DetectedMosque>) {
        nearbyGroup.removeAllViews()
        if (results.isEmpty()) {
            detectStatus.text = if (!MosqueSources.hasProviders()) {
                "No automatic mosque source is available in this version. " +
                        "Name your mosque and enter Iqamah times manually below — " +
                        "calculated times remain the fallback."
            } else {
                "No mosques found near this location."
            }
            return
        }
        detectStatus.text = "Select your mosque:"
        for (m in results) {
            nearbyGroup.addView(RadioButton(this).apply {
                text = "${m.name} — ${"%.1f".format(m.distanceMi)} mi"
                setTextColor(pal.text)
                tag = m
                id = View.generateViewId()
            })
        }
    }

    private fun refreshSchedule() {
        val name = prefs.selectedMosqueName.trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Select a mosque first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!MosqueSources.hasProviders()) {
            Toast.makeText(
                this,
                "No live mosque source in this version — using manual/calculated times",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(this, "No live mosque source in this version", Toast.LENGTH_SHORT).show()
    }

    // ---------- Lead time ----------

    private fun renderLeadChips() {
        leadChipRow.removeAllViews()
        val isCustom = leadCustom
        for (preset in LEAD_PRESETS) {
            leadChipRow.addView(leadChip("$preset", preset == leadMinutes && !isCustom) {
                leadMinutes = preset
                leadCustom = false
                leadCustomInput.visibility = View.GONE
                renderLeadChips()
            })
        }
        leadChipRow.addView(leadChip("Custom", isCustom) {
            leadCustom = true
            leadCustomInput.visibility = View.VISIBLE
            if (leadCustomInput.text.isEmpty()) leadCustomInput.setText(leadMinutes.toString())
            renderLeadChips()
        })
        if (isCustom) leadCustomInput.visibility = View.VISIBLE
    }

    private fun leadChip(label: String, selected: Boolean, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            if (selected) {
                setTextColor(pal.background)
                setBackgroundColor(pal.gold)
            } else {
                setTextColor(pal.gold)
                setBackgroundColor(pal.card)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = (8 * dp).toInt()
            }
        }

    // ---------- Tone ----------

    private fun pickTone() {
        val existing = prefs.toneUri.takeIf { it.isNotBlank() }?.let {
            android.net.Uri.parse(it)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Prayer reminder tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            if (existing != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_TONE)
    }

    @Deprecated("Use the tone picker result directly")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TONE && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<android.net.Uri>(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI
            )
            prefs.toneUri = uri?.toString() ?: ""
            updateToneLabel()
            App.ensurePrayerChannel(this)
            Toast.makeText(this, "Tone updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToneLabel() {
        toneLabel.text = try {
            val uri = prefs.toneUri.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }
            if (uri == null) "Default"
            else RingtoneManager.getRingtone(this, uri)?.getTitle(this) ?: "Default"
        } catch (_: Exception) {
            "Default"
        }
    }

    // ---------- Permissions ----------

    private fun updateLocPermLine() {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        locPermLine.text = if (prefs.locationMode == 0) {
            if (granted) "Location permission granted" else "Location permission not granted"
        } else {
            "Using manual coordinates"
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 2
            )
        }
    }

    // ---------- Save ----------

    private fun saveAll(quiet: Boolean) {
        // Validate every input BEFORE writing any pref, so a bad value
        // can't leave partially-saved state behind.
        for (name in PrayerScheduler.PRAYERS) {
            val raw = overrideInputs[name]?.text.toString()
            if (raw.isNotBlank() && PrayTimes.parseMinutes(raw) == null) {
                Toast.makeText(this, "Bad time for $name (use HH:MM)", Toast.LENGTH_SHORT).show()
                return
            }
        }

        prefs.masterEnabled = masterSwitch.isChecked

        // Mosque selection from the nearby list.
        val checkedId = nearbyGroup.checkedRadioButtonId
        if (checkedId != -1) {
            val sel = findViewById<RadioButton>(checkedId).tag as? DetectedMosque
            if (sel != null) {
                prefs.selectedMosqueName = sel.name
                prefs.selectedMosqueSource = sel.sourceId
                prefs.selectedMosqueDistance = "%.1f mi".format(sel.distanceMi)
                Thread {
                    val sched = MosqueSources.fetchSchedule(sel)
                    if (sched != null) {
                        prefs.setCachedSchedule(sched.times, sched.sourceLabel, sched.updatedAt)
                        runOnUiThread {
                            refreshMosqueStatus()
                            PrayerScheduler.reschedule(this)
                        }
                    }
                }.start()
            }
        }

        prefs.methodIndex = methodSpinner.selectedItemPosition
        val hanafiId = (asrGroup.getChildAt(0) as RadioButton).id
        prefs.hanafiAsr = asrGroup.checkedRadioButtonId == hanafiId
        val manualId = (locGroup.getChildAt(1) as RadioButton).id
        prefs.locationMode = if (locGroup.checkedRadioButtonId == manualId) 1 else 0
        prefs.manualLat = latInput.text.toString().trim()
        prefs.manualLng = lngInput.text.toString().trim()

        val customRaw = leadCustomInput.text.toString().toIntOrNull()
        leadMinutes = if (leadCustom && customRaw != null) {
            customRaw.coerceIn(0, 180)
        } else {
            leadMinutes.coerceIn(0, 180)
        }
        prefs.minutesBefore = leadMinutes

        for (name in PrayerScheduler.PRAYERS) {
            prefs.setPrayerEnabled(name, prayerSwitches[name]?.isChecked == true)
            val raw = overrideInputs[name]?.text.toString()
            prefs.setOverrideTime(name, raw.trim())
        }

        prefs.masjidName = masjidInput.text.toString().trim().ifBlank {
            prefs.selectedMosqueName.trim()
        }
        prefs.vibrationEnabled = vibrationSwitch.isChecked
        App.ensurePrayerChannel(this)

        if (prefs.masterEnabled) {
            ensureNotificationPermission()
            ensureLocationPermission()
            PrayerScheduler.reschedule(this)
        } else {
            PrayerScheduler.cancelAll(this)
        }
        refreshMosqueStatus()
        if (!quiet) {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
