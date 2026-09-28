package com.dhikra.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetAdvanceReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_NEXT = "com.dhikra.app.ACTION_NEXT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            VerseRepository(context).next()
            AyahWidgetProvider.updateAll(context)
        }
    }
}
