package com.dhikra.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews

class AyahWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Rendering the widget bitmap is too heavy for the broadcast
        // thread; finish the pending result off-thread.
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                updateAll(appContext)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        fun updateAll(context: Context) {
            val pal = Palettes.of(context)
            val repo = VerseRepository(context)
            val raw = AyahRenderer(context).renderCard(repo.current(), 1080, 1080, pal)
            val bmp = roundedCorners(raw, 64f)
            raw.recycle()

            val mgr = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_ayah)
            views.setImageViewBitmap(R.id.widget_image, bmp)

            // Tap the card -> verse detail screen. The round button advances the verse.
            val openPi = PendingIntent.getActivity(
                context, 1,
                Intent(context, VerseDetailActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, openPi)

            val nextPi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WidgetAdvanceReceiver::class.java)
                    .setAction(WidgetAdvanceReceiver.ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_next, nextPi)
            views.setInt(R.id.widget_next, "setColorFilter", AyahRenderer.GOLD)

            val cn = ComponentName(context, AyahWidgetProvider::class.java)
            for (id in mgr.getAppWidgetIds(cn)) {
                mgr.updateAppWidget(id, views)
            }
        }

        private fun roundedCorners(src: Bitmap, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawRoundRect(
                RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()),
                radius, radius, p
            )
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(src, 0f, 0f, p)
            return out
        }
    }
}
