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
            // Asset parsing + bitmap rendering are too heavy for the
            // broadcast thread; finish the pending result off-thread.
            val appContext = context.applicationContext
            val pending = goAsync()
            Thread {
                try {
                    VerseRepository(appContext).next()
                    AyahWidgetProvider.updateAll(appContext)
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
