package com.dhikra.app

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

class AyahRenderer(private val context: Context) {

    private val amiri: Typeface =
        context.resources.getFont(R.font.amiri_quran) ?: Typeface.DEFAULT
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    companion object {
        val GOLD = Color.rgb(240, 216, 120)
        val GOLD_DIM = Color.rgb(201, 162, 39)
        val IVORY = Color.rgb(242, 236, 216)
        val SAGE = Color.rgb(183, 194, 176)
    }

    fun renderCard(v: Ayah, w: Int, h: Int, palette: Palette = Palettes.BURGUNDY): Bitmap =
        render(v, w, h, framed = false, palette)
    fun renderWallpaper(v: Ayah, w: Int, h: Int, palette: Palette = Palettes.BURGUNDY): Bitmap =
        render(v, w, h, framed = true, palette)

    private fun textPaint(tf: Typeface, size: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            this.color = color
        }

    private fun layout(
        text: String, paint: TextPaint, maxW: Int, rtl: Boolean, spacing: Float
    ): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true)
        if (rtl) b.setTextDirection(TextDirectionHeuristics.RTL)
        return b.build()
    }

    private fun drawLayout(c: Canvas, l: StaticLayout, cx: Float, y: Float) {
        c.save()
        c.translate(cx - l.width / 2f, y)
        l.draw(c)
        c.restore()
    }

    /** Draws letter-spaced text centered at (cx, yCenter). Returns total width. */
    private fun drawTracked(
        c: Canvas, text: String, cx: Float, yCenter: Float,
        size: Float, color: Int, tracking: Float
    ): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serif; textSize = size; this.color = color
        }
        val ws = text.map { p.measureText(it.toString()) }
        val total = ws.sum() + tracking * (text.length - 1)
        var x = cx - total / 2f
        val fm = p.fontMetrics
        val baseline = yCenter - (fm.ascent + fm.descent) / 2f
        text.forEachIndexed { i, ch ->
            c.drawText(ch.toString(), x, baseline, p)
            x += ws[i] + tracking
        }
        return total
    }

    private fun drawDiamond(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val path = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy)
            lineTo(cx, cy + r); lineTo(cx - r, cy); close()
        }
        c.drawPath(path, p)
    }

    private fun render(v: Ayah, w: Int, h: Int, framed: Boolean, palette: Palette): Bitmap {
        val s = w / 1080f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f

        // themed gradient background
        val grad = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            palette.cardGradient,
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = grad })
        // soft top glow
        val topGlow = RadialGradient(
            cx, -h * 0.25f, h * 0.75f,
            intArrayOf(palette.glowColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = topGlow })

        if (framed) {
            val inset = w * 0.045f
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (2f * s).coerceAtLeast(2f)
                color = palette.gold; alpha = 110
            }
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), 28f * s, 28f * s, fp)
        }

        val maxW = (w * 0.84f).toInt()
        val gaps = listOf(70f, 55f, 50f, 50f, 55f).map { it * s }
        val kickH = 70f * s
        val divH = 60f * s
        val refH = 60f * s

        // shrink Arabic until everything fits
        var arSize = 92f * s
        lateinit var arLayout: StaticLayout
        lateinit var arGlow: StaticLayout
        lateinit var trLayout: StaticLayout
        lateinit var enLayout: StaticLayout
        var total = Float.MAX_VALUE
        val maxTotal = h * 0.94f
        while (true) {
            arLayout = layout(v.arabic, textPaint(amiri, arSize, palette.goldBright), maxW, rtl = true, spacing = 1.7f)
            val glowPaint = textPaint(amiri, arSize, palette.goldBright).apply {
                alpha = 160
                maskFilter = BlurMaskFilter(28f * s, BlurMaskFilter.Blur.NORMAL)
            }
            arGlow = layout(v.arabic, glowPaint, maxW, rtl = true, spacing = 1.7f)
            trLayout = layout(
                v.transliteration, textPaint(serif, 30f * s, palette.textDim),
                maxW, rtl = false, spacing = 1.9f
            )
            enLayout = layout(
                "\u201C${v.translation}\u201D", textPaint(serif, 36f * s, palette.text),
                maxW, rtl = false, spacing = 1.8f
            )
            total = kickH + arLayout.height + trLayout.height + divH +
                    enLayout.height + refH + gaps.sum()
            if (total <= maxTotal || arSize <= 54f * s) break
            arSize -= 4f * s
        }

        var y = (h - total) / 2f

        // kicker with diamonds
        val kickerW = drawTracked(c, v.theme.uppercase(), cx, y + kickH / 2f, 30f * s, palette.gold, 10f * s)
        drawDiamond(c, cx - kickerW / 2f - 60f * s, y + kickH / 2f, 6f * s, palette.gold)
        drawDiamond(c, cx + kickerW / 2f + 60f * s, y + kickH / 2f, 6f * s, palette.gold)
        y += kickH + gaps[0]

        // arabic with glow
        drawLayout(c, arGlow, cx, y)
        drawLayout(c, arLayout, cx, y)
        y += arLayout.height + gaps[1]

        // transliteration
        drawLayout(c, trLayout, cx, y)
        y += trLayout.height + gaps[2]

        // divider
        val dw = 140f * s
        val dy = y + divH / 2f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.gold; alpha = 200; strokeWidth = (2f * s).coerceAtLeast(2f)
        }
        c.drawLine(cx - dw / 2f, dy, cx + dw / 2f, dy, lp)
        drawDiamond(c, cx, dy, 7f * s, palette.gold)
        y += divH + gaps[3]

        // translation
        drawLayout(c, enLayout, cx, y)
        y += enLayout.height + gaps[4]

        // reference
        drawTracked(c, v.reference.uppercase(), cx, y + refH / 2f, 28f * s, palette.gold, 6f * s)

        return bmp
    }
}
