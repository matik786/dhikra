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
