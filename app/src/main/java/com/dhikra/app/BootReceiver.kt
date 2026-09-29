package com.dhikra.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Re-arm alarms after boot, and after the clock or time zone changes
        // (e.g. travel): the daily scheduler and prayer alarms are set against
        // absolute RTC times, so a zone change would otherwise leave them
        // firing at the wrong local times until something re-armed them.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                App.scheduleRotation(context)
                PrayerScheduler.reschedule(context)
            }
        }
    }
}
