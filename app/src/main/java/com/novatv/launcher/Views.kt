package com.novatv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Night sky with layered mountain ridges, drawn to fill the screen. */
class SkyView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sky: LinearGradient? = null
    private val ridges = ArrayList<Path>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sky = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF111D36.toInt(), 0xFF0B1427.toInt(), 0xFF070B14.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val sx = w / 1920f
        val sy = h / 1080f
        ridges.clear()
        for (pts in listOf(FAR, MID, NEAR)) {
            val p = Path()
            p.moveTo(pts[0] * sx, pts[1] * sy)
            var i = 2
            while (i < pts.size) {
                p.lineTo(pts[i] * sx, pts[i + 1] * sy)
                i += 2
            }
            p.lineTo(1920f * sx, 1080f * sy)
            p.lineTo(0f, 1080f * sy)
            p.close()
            ridges.add(p)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val sx = w / 1920f
        val sy = h / 1080f

        paint.style = Paint.Style.FILL
        paint.shader = sky
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        paint.color = 0xFFC9D6EE.toInt()
        paint.alpha = 26
        canvas.drawCircle(1160f * sx, 180f * sy, 40f * sx, paint)
        var i = 0
        while (i < STARS.size) {
            paint.alpha = 100
            canvas.drawCircle(STARS[i] * sx, STARS[i + 1] * sy, STARS[i + 2] * sx, paint)
            i += 3
        }

        val colors = intArrayOf(0xFF13203A.toInt(), 0xFF0E182C.toInt(), 0xFF0A1120.toInt())
        val alphas = intArrayOf(230, 255, 255)
        for (r in ridges.indices) {
            paint.color = colors[r]
            paint.alpha = alphas[r]
            canvas.drawPath(ridges[r], paint)
        }
    }

    companion object {
        private val FAR = floatArrayOf(
            0f, 620f, 180f, 540f, 320f, 590f, 470f, 470f, 610f, 560f, 760f, 430f, 900f, 520f,
            1040f, 400f, 1190f, 500f, 1330f, 440f, 1480f, 530f, 1640f, 450f, 1780f, 520f, 1920f, 470f
        )
        private val MID = floatArrayOf(
            0f, 760f, 140f, 690f, 300f, 740f, 460f, 650f, 620f, 720f, 800f, 630f, 960f, 710f,
            1120f, 640f, 1300f, 730f, 1460f, 660f, 1620f, 720f, 1780f, 650f, 1920f, 700f
        )
        private val NEAR = floatArrayOf(
            0f, 880f, 220f, 820f, 420f, 860f, 640f, 800f, 880f, 870f, 1100f, 810f,
            1340f, 880f, 1560f, 820f, 1760f, 860f, 1920f, 830f
        )
        private val STARS = floatArrayOf(
            260f, 120f, 1.6f, 610f, 70f, 1.2f, 880f, 150f, 1.4f, 1390f, 90f, 1.2f, 1700f, 160f, 1.6f
        )
    }
}

/** The assistant's glowing ring-and-dot mark. */
class OrbView(context: Context, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var color: Int = color
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f

        paint.style = Paint.Style.FILL
        paint.color = Ui.alpha(color, 0.14f)
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = color
        canvas.drawCircle(cx, cy, r * 0.72f, paint)

        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.24f, paint)
    }
}

enum class Icon {
    PLAY, SMILE, SPARK, SHUFFLE, COMPASS, FILM, SEARCH, GRID, SLIDERS, CHEVRON,
    SEND, MOON, SUN, CLOUD, RAIN, TV
}

/** Simple line icons drawn on a 24x24 grid, so no image files are needed. */
class IconView(context: Context, private val icon: Icon, color: Int) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    var color: Int = color
        set(value) {
            field = value
            invalidate()
        }

    init {
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = 1.8f
        fill.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val k = size / 24f
        stroke.color = color
        fill.color = color
        canvas.save()
        canvas.translate((width - 24f * k) / 2f, (height - 24f * k) / 2f)
        canvas.scale(k, k)
        draw24(canvas)
        canvas.restore()
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) =
        c.drawLine(x1, y1, x2, y2, stroke)

    private fun poly(c: Canvas, vararg pts: Float, close: Boolean = false) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        if (close) p.close()
        c.drawPath(p, stroke)
    }

    private fun cloudPath(dy: Float): Path {
        val p = Path()
        p.moveTo(7f, 18f + dy)
        p.lineTo(17f, 18f + dy)
        p.cubicTo(19.8f, 18f + dy, 21.5f, 16f + dy, 21.5f, 13.8f + dy)
        p.cubicTo(21.5f, 11.6f + dy, 19.8f, 10f + dy, 17.7f, 10f + dy)
        p.cubicTo(17f, 7f + dy, 14.8f, 5f + dy, 12f, 5f + dy)
        p.cubicTo(8.9f, 5f + dy, 6.5f, 7.3f + dy, 6.2f, 10.2f + dy)
        p.cubicTo(4.1f, 10.6f + dy, 2.5f, 12.3f + dy, 2.5f, 14.3f + dy)
        p.cubicTo(2.5f, 16.3f + dy, 4.4f, 18f + dy, 7f, 18f + dy)
        p.close()
        return p
    }

    private fun draw24(c: Canvas) {
        when (icon) {
            Icon.PLAY -> {
                c.drawCircle(12f, 12f, 9f, stroke)
                poly(c, 10f, 8.5f, 15f, 12f, 10f, 15.5f, close = true)
            }
            Icon.SMILE -> {
                c.drawCircle(12f, 12f, 9f, stroke)
                val p = Path()
                p.moveTo(8.5f, 14f)
                p.quadTo(12f, 17.5f, 15.5f, 14f)
                c.drawPath(p, stroke)
                c.drawCircle(9f, 9.5f, 1f, fill)
                c.drawCircle(15f, 9.5f, 1f, fill)
            }
            Icon.SPARK -> {
                line(c, 12f, 3f, 12f, 7f)
                line(c, 12f, 17f, 12f, 21f)
                line(c, 3f, 12f, 7f, 12f)
                line(c, 17f, 12f, 21f, 12f)
                line(c, 6f, 6f, 8.5f, 8.5f)
                line(c, 15.5f, 15.5f, 18f, 18f)
                line(c, 18f, 6f, 15.5f, 8.5f)
                line(c, 8.5f, 15.5f, 6f, 18f)
            }
            Icon.SHUFFLE -> {
                val a = Path()
                a.moveTo(4f, 7f)
                a.lineTo(7f, 7f)
                a.cubicTo(11f, 7f, 13f, 17f, 17f, 17f)
                a.lineTo(20f, 17f)
                c.drawPath(a, stroke)
                val b = Path()
                b.moveTo(4f, 17f)
                b.lineTo(7f, 17f)
                b.cubicTo(8.5f, 17f, 9.7f, 15.6f, 10.8f, 13.8f)
                c.drawPath(b, stroke)
                val d = Path()
                d.moveTo(13.2f, 10.2f)
                d.cubicTo(14.3f, 8.4f, 15.5f, 7f, 17f, 7f)
                d.lineTo(20f, 7f)
                c.drawPath(d, stroke)
                poly(c, 18f, 4f, 21f, 7f, 18f, 10f)
                poly(c, 18f, 14f, 21f, 17f, 18f, 20f)
            }
            Icon.COMPASS -> {
                c.drawCircle(12f, 12f, 9f, stroke)
                poly(c, 15.5f, 8.5f, 13.5f, 13.5f, 8.5f, 15.5f, 10.5f, 10.5f, close = true)
            }
            Icon.FILM -> {
                c.drawRoundRect(RectF(4f, 4f, 20f, 20f), 2f, 2f, stroke)
                line(c, 8f, 4f, 8f, 20f)
                line(c, 16f, 4f, 16f, 20f)
                line(c, 4f, 9f, 8f, 9f)
                line(c, 4f, 15f, 8f, 15f)
                line(c, 16f, 9f, 20f, 9f)
                line(c, 16f, 15f, 20f, 15f)
            }
            Icon.SEARCH -> {
                c.drawCircle(11f, 11f, 7f, stroke)
                line(c, 20f, 20f, 16f, 16f)
            }
            Icon.GRID -> {
                c.drawRoundRect(RectF(4f, 4f, 10f, 10f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(14f, 4f, 20f, 10f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(4f, 14f, 10f, 20f), 1.5f, 1.5f, stroke)
                c.drawRoundRect(RectF(14f, 14f, 20f, 20f), 1.5f, 1.5f, stroke)
            }
            Icon.SLIDERS -> {
                line(c, 4f, 6f, 14f, 6f)
                line(c, 18f, 6f, 20f, 6f)
                line(c, 4f, 12f, 8f, 12f)
                line(c, 12f, 12f, 20f, 12f)
                line(c, 4f, 18f, 16f, 18f)
                c.drawCircle(16f, 6f, 2f, stroke)
                c.drawCircle(10f, 12f, 2f, stroke)
                c.drawCircle(18f, 18f, 2f, stroke)
            }
            Icon.CHEVRON -> poly(c, 9f, 6f, 15f, 12f, 9f, 18f)
            Icon.SEND -> {
                line(c, 12f, 19f, 12f, 5f)
                poly(c, 6f, 11f, 12f, 5f, 18f, 11f)
            }
            Icon.MOON -> {
                val a = Path()
                a.addCircle(11.5f, 12.5f, 8f, Path.Direction.CW)
                val b = Path()
                b.addCircle(16.5f, 8f, 6.5f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE)
                c.drawPath(a, stroke)
            }
            Icon.SUN -> {
                c.drawCircle(12f, 12f, 4f, stroke)
                for (i in 0 until 8) {
                    val ang = Math.PI * i / 4.0
                    val cx = cos(ang).toFloat()
                    val cy = sin(ang).toFloat()
                    line(c, 12f + cx * 7f, 12f + cy * 7f, 12f + cx * 9.5f, 12f + cy * 9.5f)
                }
            }
            Icon.CLOUD -> c.drawPath(cloudPath(0f), stroke)
            Icon.RAIN -> {
                c.drawPath(cloudPath(-3f), stroke)
                line(c, 8f, 18.5f, 7f, 21f)
                line(c, 12f, 18.5f, 11f, 21f)
                line(c, 16f, 18.5f, 15f, 21f)
            }
            Icon.TV -> {
                c.drawRoundRect(RectF(3f, 5f, 21f, 16f), 2f, 2f, stroke)
                line(c, 8f, 20f, 16f, 20f)
                line(c, 12f, 16f, 12f, 20f)
            }
        }
    }
}
