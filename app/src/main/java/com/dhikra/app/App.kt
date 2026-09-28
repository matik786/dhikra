package com.dhikra.app

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock

class App : Application() {

    companion object {
        const val PRAYER_CHANNEL_ID = "prayer_reminders"
        private const val THREE_HOURS_MS = 3L * 3600 * 1000

        fun scheduleRotation(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, RotateReceiver::class.java)
                    .setAction(RotateReceiver.ACTION_ROTATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + THREE_HOURS_MS,
                THREE_HOURS_MS,
                pi
            )
        }

        /** (Re)creates the prayer channel with the user's chosen tone + vibration. */
        fun ensurePrayerChannel(context: Context) {
            val prefs = PrayerPrefs(context)
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val sound: Uri = prefs.toneUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                PRAYER_CHANNEL_ID,
                "Prayer reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(sound, attrs)
                enableVibration(prefs.vibrationEnabled)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensurePrayerChannel(this)
        scheduleRotation(this)
        PrayerScheduler.reschedule(this)
    }
}
