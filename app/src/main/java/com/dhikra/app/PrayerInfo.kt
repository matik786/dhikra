package com.dhikra.app

import android.content.Context
import java.util.Calendar

/** Today's prayer times with honest per-prayer source labels. */
object PrayerInfo {

    data class PrayerTime(val name: String, val minutes: Int, val source: String)

    /**
     * Source precedence per prayer: manual HH:MM override > mosque schedule
     * cache (same day only) > calculated. Returns null when no location is
     * available and no manual coordinates are set.
     */
    fun today(context: Context): List<PrayerTime>? {
        val prefs = PrayerPrefs(context)
        val loc = PrayerScheduler.resolveLocation(context, prefs) ?: return null
        val now = Calendar.getInstance()
        val method = PrayTimes.METHODS[prefs.methodIndex.coerceIn(PrayTimes.METHODS.indices)]
        val calc = PrayTimes.getTimes(now, loc.first, loc.second, method, prefs.hanafiAsr).asMap()
        val cache = prefs.cachedScheduleTimes()
        val cacheFresh = isCacheFresh(prefs)
        val mosqueName = prefs.selectedMosqueName.trim()
        return PrayerScheduler.PRAYERS.map { name ->
            val manual = PrayTimes.parseMinutes(prefs.overrideTime(name))
            when {
                manual != null -> PrayerTime(name, manual, "Manual entry")
                cacheFresh && cache.containsKey(name) ->
                    PrayerTime(name, cache.getValue(name), mosqueName.ifBlank { "Mosque" })
                else -> {
                    val h = calc[name] ?: return null
                    val min = ((h * 60 + 0.5).toInt() % 1440 + 1440) % 1440
                    PrayerTime(name, min, "Calculated")
                }
            }
        }
    }

    /** Next upcoming prayer today (wraps to Fajr when all have passed). */
    fun next(context: Context): PrayerTime? {
        val list = today(context) ?: return null
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return list.firstOrNull { it.minutes > nowMin } ?: list.firstOrNull()
    }

    /** One-line summary for the UI, e.g. "Masjid Madeena · updated today 6:00 AM". */
    fun sourceSummary(context: Context): String {
        val prefs = PrayerPrefs(context)
        val mosqueName = prefs.selectedMosqueName.trim()
        val updated = prefs.cachedScheduleUpdated()
        return when {
            mosqueName.isNotEmpty() && updated > 0L ->
                "$mosqueName · updated ${formatUpdated(updated)}"
            mosqueName.isNotEmpty() -> "$mosqueName · manual times"
            else -> {
                val m = PrayTimes.METHODS[prefs.methodIndex.coerceIn(PrayTimes.METHODS.indices)]
                "Calculated · ${m.name}"
            }
        }
    }

    fun isCacheFresh(prefs: PrayerPrefs): Boolean {
        val updated = prefs.cachedScheduleUpdated()
        if (updated <= 0L) return false
        val a = Calendar.getInstance().apply { timeInMillis = updated }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatUpdated(epochMs: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return "today " + minutesToDisplay(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    fun minutesToDisplay(min: Int): String {
        val h = ((min / 60) % 24 + 24) % 24
        val m = ((min % 60) + 60) % 60
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
        return "%d:%02d %s".format(h12, m, ampm)
    }
}
