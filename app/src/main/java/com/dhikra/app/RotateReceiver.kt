package com.dhikra.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RotateReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ROTATE = "com.dhikra.app.ACTION_ROTATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ROTATE) {
            VerseRepository(context).next()
            AyahWidgetProvider.updateAll(context)
        }
    }
}
