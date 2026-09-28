package com.dhikra.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Calendar

/**
 * Computes today's prayer times (calculated or masjid-override) and arms
 * one alarm per enabled prayer, plus a daily re-scheduler just after midnight.
 */
object PrayerScheduler {

    const val ACTION_PRAYER = "com.dhikra.app.ACTION_PRAYER"
    const val ACTION_RESCHEDULE = "com.dhikra.app.ACTION_RESCHEDULE"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_TIME = "time"

    val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class Reminder(val prayer: String, val prayerMin: Int, val remindMin: Int)

    fun reschedule(context: Context) {
        cancelAll(context)
        val prefs = PrayerPrefs(context)
        if (!prefs.masterEnabled) return

        val loc = resolveLocation(context, prefs)
        // loc may be null (no permission / no fix yet): manual overrides and
        // the mosque cache need no location, so keep going and let each
        // prayer resolve what it can.
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val method = PrayTimes.METHODS[prefs.methodIndex.coerceIn(PrayTimes.METHODS.indices)]

        for (name in PRAYERS) {
            if (!prefs.prayerEnabled(name)) continue
            val prayerMin = prayerTimeMinutes(now, loc, method, prefs, name) ?: continue
            val remindMin = prayerMin - prefs.minutesBefore
            if (remindMin <= nowMin) continue
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, remindMin / 60)
                set(Calendar.MINUTE, remindMin % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val am = context.getSystemService(AlarmManager::class.java) ?: continue
            val pi = prayerIntent(context, name, prayerMin)
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi
                )
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }

        // Recompute every day just after midnight.
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.timeInMillis,
            AlarmManager.INTERVAL_DAY, rescheduleIntent(context)
        )
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (name in PRAYERS) am.cancel(prayerIntent(context, name, 0))
        am.cancel(rescheduleIntent(context))
    }

    private fun prayerTimeMinutes(
        now: Calendar, loc: Pair<Double, Double>?,
        method: PrayTimes.CalcMethod, prefs: PrayerPrefs, name: String
    ): Int? {
        // 1. Manual HH:MM override wins (needs no location).
        PrayTimes.parseMinutes(prefs.overrideTime(name))?.let { return it }
        // 2. Same-day cached mosque schedule (only when a mosque is selected).
        if (prefs.selectedMosqueName.isNotBlank() && PrayerInfo.isCacheFresh(prefs)) {
            prefs.cachedScheduleTimes()[name]?.let { return it }
        }
        // 3. Calculated fallback — needs a location; otherwise this prayer
        // is skipped rather than blocking every other prayer.
        if (loc == null) return null
        val t = PrayTimes.getTimes(now, loc.first, loc.second, method, prefs.hanafiAsr)
        val hours = t.asMap()[name] ?: return null
        return ((hours * 60 + 0.5).toInt() % 1440 + 1440) % 1440
    }

    fun resolveLocation(context: Context, prefs: PrayerPrefs): Pair<Double, Double>? {
        if (prefs.locationMode == 1) {
            val lat = prefs.manualLat.toDoubleOrNull()
            val lng = prefs.manualLng.toDoubleOrNull()
            if (lat != null && lng != null) return lat to lng
            return null
        }
        val last = lastKnownLocation(context)
        if (last == null) {
            // Last-known cache is empty: kick off one fresh fix so prayer times
            // (and alarms) can be computed once the fix arrives.
            try {
                requestFreshLocation(context)
            } catch (_: Exception) {
            }
        }
        return last
    }

    /** Earliest time (ms) a fresh location request may be issued again. */
    @Volatile
    private var lastFreshRequestMs = 0L

    /**
     * Requests a single fresh location fix. Throttled to at most one request
     * per 2 minutes. When a fix arrives, alarms are re-armed via reschedule().
     */
    fun requestFreshLocation(context: Context) {
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!coarseGranted && !fineGranted) return

        val now = System.currentTimeMillis()
        if (now - lastFreshRequestMs < 120_000) return
        lastFreshRequestMs = now

        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val appContext = context.applicationContext
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {
                }
                try {
                    reschedule(appContext)
                } catch (_: Exception) {
                }
            }

            // Kept as an explicit override (rather than the interface default)
            // so the method exists on minSdk 26 runtimes too.
            @Deprecated("Framework method is deprecated; override retained for API 26 compatibility.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        var requested = false
        for (provider in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )) {
            try {
                requestSingleUpdateCompat(lm, provider, listener)
                requested = true
            } catch (_: SecurityException) {
                return
            } catch (_: IllegalArgumentException) {
                // Provider not present on this device; try the next one.
            }
        }
        if (requested) {
            // Safety timeout: stop listening after ~30s if no fix arrives.
            try {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        lm.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }, 30_000)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * requestSingleUpdate(String, LocationListener, Looper) is deprecated in
     * newer SDKs but remains the only single-update signature available back
     * to minSdk 26, so it is used here with the deprecation suppressed.
     */
    @Suppress("DEPRECATION")
    private fun requestSingleUpdateCompat(
        lm: LocationManager, provider: String, listener: LocationListener
    ) {
        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                lm.getLastKnownLocation(provider)?.let {
                    return it.latitude to it.longitude
                }
            } catch (_: SecurityException) {
                return null
            }
        }
        return null
    }

    private fun prayerIntent(context: Context, prayer: String, prayerMin: Int): PendingIntent {
        val intent = Intent(context, PrayerReceiver::class.java)
            .setAction("$ACTION_PRAYER.$prayer")
            .putExtra(EXTRA_PRAYER, prayer)
            .putExtra(EXTRA_TIME, prayerMin)
        return PendingIntent.getBroadcast(
            context, 1000 + PRAYERS.indexOf(prayer), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rescheduleIntent(context: Context): PendingIntent {
        val intent = Intent(context, PrayerReceiver::class.java).setAction(ACTION_RESCHEDULE)
        return PendingIntent.getBroadcast(
            context, 1999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
