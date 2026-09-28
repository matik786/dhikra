package com.dhikra.app

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PrayerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when {
            intent.action == PrayerScheduler.ACTION_RESCHEDULE -> {
                PrayerScheduler.reschedule(context)
            }
            intent.action?.startsWith(PrayerScheduler.ACTION_PRAYER) == true -> {
                val prayer = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER) ?: return
                val prayerMin = intent.getIntExtra(PrayerScheduler.EXTRA_TIME, -1)
                showNotification(context, prayer, prayerMin)
            }
        }
    }

    private fun showNotification(context: Context, prayer: String, prayerMin: Int) {
        val prefs = PrayerPrefs(context)
        val lead = prefs.minutesBefore
        val timeStr = if (prayerMin >= 0) PrayTimes.minutesToString(prayerMin) else ""
        val masjid = prefs.masjidName.trim()
        val text = when {
            masjid.isNotEmpty() && timeStr.isNotEmpty() ->
                "$prayer Iqamah at $masjid, $timeStr"
            timeStr.isNotEmpty() && lead > 0 ->
                "$prayer prayer at $timeStr ($lead min)"
            timeStr.isNotEmpty() ->
                "Time for $prayer prayer ($timeStr)"
            else ->
                "Time for $prayer prayer"
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notif = Notification.Builder(context, App.PRAYER_CHANNEL_ID)
            .setContentTitle("$prayer reminder")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .build()
        nm.notify(prayer.hashCode(), notif)
    }
}
