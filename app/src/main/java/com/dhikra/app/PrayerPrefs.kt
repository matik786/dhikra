package com.dhikra.app

import android.content.Context
import android.content.SharedPreferences

/** All prayer-reminder settings, persisted in SharedPreferences. */
class PrayerPrefs(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)

    var masterEnabled: Boolean
        get() = p.getBoolean("master", true)
        set(v) = p.edit().putBoolean("master", v).apply()

    var minutesBefore: Int
        get() = p.getInt("minutes_before", 15)
        set(v) = p.edit().putInt("minutes_before", v).apply()

    /** Index into PrayTimes.METHODS. */
    var methodIndex: Int
        get() = p.getInt("method", 0)
        set(v) = p.edit().putInt("method", v).apply()

    var hanafiAsr: Boolean
        get() = p.getBoolean("hanafi_asr", true)
        set(v) = p.edit().putBoolean("hanafi_asr", v).apply()

    /** 0 = device location, 1 = manual coordinates. */
    var locationMode: Int
        get() = p.getInt("loc_mode", 0)
        set(v) = p.edit().putInt("loc_mode", v).apply()

    var manualLat: String
        get() = p.getString("lat", "") ?: ""
        set(v) = p.edit().putString("lat", v).apply()

    var manualLng: String
        get() = p.getString("lng", "") ?: ""
        set(v) = p.edit().putString("lng", v).apply()

    var masjidName: String
        get() = p.getString("masjid", "") ?: ""
        set(v) = p.edit().putString("masjid", v).apply()

    // ---- Mosque auto-detect selection ----
    /** Display name of the selected mosque, "" = none. */
    var selectedMosqueName: String
        get() = p.getString("mosque_name", "") ?: ""
        set(v) = p.edit().putString("mosque_name", v).apply()

    /** Provider id that detected it, "manual", or "" = none. */
    var selectedMosqueSource: String
        get() = p.getString("mosque_source", "") ?: ""
        set(v) = p.edit().putString("mosque_source", v).apply()

    /** Human-readable distance, e.g. "0.8 mi", or "". */
    var selectedMosqueDistance: String
        get() = p.getString("mosque_distance", "") ?: ""
        set(v) = p.edit().putString("mosque_distance", v).apply()

    fun clearMosqueSelection() {
        p.edit().remove("mosque_name").remove("mosque_source").remove("mosque_distance")
            .remove("sched_times").remove("sched_source").remove("sched_updated").apply()
    }

    // ---- Cached mosque schedule ("Fajr=375,Dhuhr=820,..." + source + epoch ms) ----
    fun setCachedSchedule(times: Map<String, Int>, sourceLabel: String, updatedAt: Long) {
        val flat = times.entries.joinToString(",") { "${it.key}=${it.value}" }
        p.edit().putString("sched_times", flat)
            .putString("sched_source", sourceLabel)
            .putLong("sched_updated", updatedAt).apply()
    }

    fun cachedScheduleTimes(): Map<String, Int> {
        val flat = p.getString("sched_times", "") ?: ""
        if (flat.isBlank()) return emptyMap()
        return flat.split(",").mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to (kv[1].toIntOrNull() ?: return@mapNotNull null)
            else null
        }.toMap()
    }

    fun cachedScheduleSource(): String = p.getString("sched_source", "") ?: ""
    fun cachedScheduleUpdated(): Long = p.getLong("sched_updated", 0L)

    // ---- Notification tone & vibration ----
    /** Ringtone URI string; "" = system default notification sound. */
    var toneUri: String
        get() = p.getString("tone_uri", "") ?: ""
        set(v) = p.edit().putString("tone_uri", v).apply()

    var vibrationEnabled: Boolean
        get() = p.getBoolean("vibration", true)
        set(v) = p.edit().putBoolean("vibration", v).apply()

    fun prayerEnabled(name: String): Boolean =
        p.getBoolean("enabled_$name", true)

    fun setPrayerEnabled(name: String, v: Boolean) =
        p.edit().putBoolean("enabled_$name", v).apply()

    /** Manual Iqamah override as "HH:MM", or "" for none. */
    fun overrideTime(name: String): String =
        p.getString("override_$name", "") ?: ""

    fun setOverrideTime(name: String, v: String) =
        p.edit().putString("override_$name", v).apply()
}
