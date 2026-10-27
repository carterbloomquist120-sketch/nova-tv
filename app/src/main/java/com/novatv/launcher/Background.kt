package com.novatv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import kotlin.math.sin

/** The background scenes you can pick, plus your own photo. */
object Backgrounds {
    val STYLES = listOf("mountains", "aurora", "sunset", "ocean", "city", "space", "plain", "photo")
    val LABELS = listOf("Mountains", "Aurora", "Sunset", "Ocean", "City", "Space", "Plain", "Photo")

    /** Turns "northern lights", "galaxy", "beach"... into one of STYLES. */
    fun normalize(value: String): String? {
        val k = value.lowercase().filter { it.isLetter() }
        return when {
            k.contains("aurora") || k.contains("northern") -> "aurora"
            k.contains("sunset") || k.contains("dusk") || k.contains("sunrise") -> "sunset"
            k.contains("ocean") || k.contains("sea") || k.contains("beach") || k.contains("water") || k.contains("lake") -> "ocean"
            k.contains("city") || k.contains("skyline") || k.contains("downtown") -> "city"
            k.contains("space") || k.contains("galaxy") || k.contains("nebula") || k.contains("cosmos") -> "space"
            k.contains("plain") || k.contains("solid") || k.contains("black") || k.contains("minimal") -> "plain"
            k.contains("photo") || k.contains("picture") || k.contains("image") -> "photo"
            k.contains("mountain") || k.contains("night") || k.contains("default") || k.contains("original") -> "mountains"
            else -> null
        }
    }

    fun photoFile(context: Context): File = File(context.filesDir, "background.jpg")

    fun hasPhoto(context: Context): Boolean = photoFile(context).length() > 0L

    /**
     * Downloads a picture link and saves it as the photo background.
     * Call from a background thread. Returns an error message, or null when it worked.
     */
    fun download(context: Context, url: String): String? {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return "That doesn't look like a link. Use one that starts with https://"
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(clean).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "NovaTV/1.0")
            val code = conn.responseCode
            if (code !in 200..299) return "Couldn't download that picture (error $code)."
            val bytes = conn.inputStream.use { readLimited(it, 20 * 1024 * 1024) }
                ?: return "That picture is too big (over 20 MB)."

            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return "That link isn't a picture. Use a direct link to a .jpg or .png."
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return "Couldn't read that picture."
            FileOutputStream(photoFile(context)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            null
        } catch (e: Exception) {
            "Couldn't download that picture. Check the link and the TV's internet."
        } finally {
            conn?.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

/**
 * Full-screen background. Each scene is drawn once into a bitmap on a
 * 1920x1080 grid (scaled to the real screen), so it costs nothing to show.
 */
class SkyView(context: Context) : View(context) {
    private val style: String
    private val accent: Int
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cache: Bitmap? = null

    init {
        val prefs = Prefs(context)
        style = prefs.background
        accent = prefs.accent
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cache = if (w > 0 && h > 0) {
            try {
                render(w, h)
            } catch (e: OutOfMemoryError) {
                null
            }
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = cache
        if (b == null) {
            canvas.drawColor(Ui.BG)
            return
        }
        canvas.drawBitmap(b, 0f, 0f, null)
    }

    private fun render(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (style == "photo" && photo(c, w, h)) return bmp
        c.scale(w / W, h / H)
        when (style) {
            "aurora" -> aurora(c)
            "sunset" -> sunset(c)
            "ocean" -> ocean(c)
            "city" -> city(c)
            "space" -> space(c)
            "plain" -> plain(c)
            else -> mountains(c)
        }
        return bmp
    }

    // ------------------------------------------------------------ helpers

    private fun fill(c: Canvas, colors: IntArray, stops: FloatArray, top: Float = 0f, bottom: Float = H) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, top, 0f, bottom, colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(0f, top, W, bottom, paint)
        paint.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            x, y, r,
            intArrayOf(Ui.alpha(color, a), Ui.alpha(color, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, paint)
        paint.shader = null
    }

    private fun ridge(c: Canvas, pts: FloatArray, color: Int) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        p.lineTo(W, H)
        p.lineTo(0f, H)
        p.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(p, paint)
    }

    private fun stars(c: Canvas, count: Int, seed: Long, maxY: Float, bright: Float = 1f) {
        val rnd = Random(seed)
        paint.style = Paint.Style.FILL
        for (i in 0 until count) {
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * maxY
            val r = 0.7f + rnd.nextFloat() * 1.3f
            paint.color = Ui.alpha(0xFFC9D6EE.toInt(), (0.15f + rnd.nextFloat() * 0.45f) * bright)
            c.drawCircle(x, y, r, paint)
        }
    }

    // ------------------------------------------------------------ scenes

    private fun mountains(c: Canvas) {
        fill(c, intArrayOf(0xFF111D36.toInt(), 0xFF0B1427.toInt(), 0xFF070B14.toInt()), floatArrayOf(0f, 0.45f, 1f))
        stars(c, 60, 11L, 520f)
        paint.color = Ui.alpha(0xFFC9D6EE.toInt(), 0.10f)
        c.drawCircle(1160f, 180f, 40f, paint)
        ridge(c, FAR, 0xE613203A.toInt())
        ridge(c, MID, 0xFF0E182C.toInt())
        ridge(c, NEAR, 0xFF0A1120.toInt())
    }

    private fun aurora(c: Canvas) {
        fill(c, intArrayOf(0xFF050E1A.toInt(), 0xFF071826.toInt(), 0xFF04080F.toInt()), floatArrayOf(0f, 0.55f, 1f))
        stars(c, 110, 23L, 760f)
        ribbon(c, 300f, 70f, 0.0042f, 0.4f, 200f, accent, 0.34f)
        ribbon(c, 420f, 55f, 0.0055f, 2.1f, 150f, accent, 0.22f)
        ribbon(c, 230f, 45f, 0.0034f, 4.0f, 120f, 0xFF8B9DFF.toInt(), 0.18f)
        ridge(c, NEAR, 0xFF03060C.toInt())
    }

    /** One curtain of light: brightest along its lower edge, fading upward. */
    private fun ribbon(c: Canvas, base: Float, amp: Float, freq: Float, phase: Float, height: Float, color: Int, a: Float) {
        val p = Path()
        var x = 0f
        p.moveTo(0f, base + amp * sin(phase) - height)
        while (x <= W) {
            p.lineTo(x, base + amp * sin(x * freq + phase) - height)
            x += 40f
        }
        x = W
        while (x >= 0f) {
            p.lineTo(x, base + amp * sin(x * freq + phase) + amp * 0.3f * sin(x * freq * 3f))
            x -= 40f
        }
        p.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, base - height - amp, 0f, base + amp,
            intArrayOf(Ui.alpha(color, 0f), Ui.alpha(color, a * 0.4f), Ui.alpha(color, a)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(p, paint)
        paint.shader = null
    }

    private fun sunset(c: Canvas) {
        fill(
            c,
            intArrayOf(0xFF120B26.toInt(), 0xFF2A1340.toInt(), 0xFF5E2246.toInt(), 0xFF9A3B3E.toInt(), 0xFFC8663E.toInt()),
            floatArrayOf(0f, 0.40f, 0.62f, 0.80f, 1f)
        )
        stars(c, 30, 31L, 300f, 0.7f)
        glow(c, 1240f, 760f, 460f, 0xFFFFB86B.toInt(), 0.35f)
        paint.style = Paint.Style.FILL
        paint.color = Ui.alpha(0xFFFFC98A.toInt(), 0.9f)
        c.drawCircle(1240f, 760f, 90f, paint)
        ridge(c, FAR, 0xFF3A1838.toInt())
        ridge(c, MID, 0xFF24102A.toInt())
        ridge(c, NEAR, 0xFF140A1A.toInt())
    }

    private fun ocean(c: Canvas) {
        val horizon = 620f
        fill(c, intArrayOf(0xFF06101F.toInt(), 0xFF0B1F38.toInt(), 0xFF173A57.toInt()), floatArrayOf(0f, 0.7f, 1f), 0f, horizon)
        fill(c, intArrayOf(0xFF0A2235.toInt(), 0xFF06131F.toInt()), floatArrayOf(0f, 1f), horizon, H)
        stars(c, 60, 41L, 540f)
        glow(c, 1300f, 230f, 220f, 0xFFE6EEF8.toInt(), 0.18f)
        paint.style = Paint.Style.FILL
        paint.color = Ui.alpha(0xFFE6EEF8.toInt(), 0.85f)
        c.drawCircle(1300f, 230f, 46f, paint)

        // Moonlight on the water, then gentle wave lines.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val rnd = Random(43L)
        var y = horizon + 14f
        while (y < H) {
            val t = (y - horizon) / (H - horizon)
            val half = 30f + t * 120f + rnd.nextFloat() * 40f
            paint.strokeWidth = 2f + t * 3f
            paint.color = Ui.alpha(0xFFE6EEF8.toInt(), 0.40f * (1f - t) + 0.05f)
            val jitter = (rnd.nextFloat() - 0.5f) * 40f
            c.drawLine(1300f - half + jitter, y, 1300f + half + jitter, y, paint)
            y += 16f + t * 22f
        }
        for (i in 0 until 70) {
            val wy = horizon + 20f + rnd.nextFloat() * (H - horizon - 20f)
            val wx = rnd.nextFloat() * W
            val len = 30f + rnd.nextFloat() * 90f
            paint.strokeWidth = 2f
            paint.color = Ui.alpha(0xFF9FC4E0.toInt(), 0.06f + rnd.nextFloat() * 0.08f)
            c.drawLine(wx, wy, wx + len, wy, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun city(c: Canvas) {
        fill(c, intArrayOf(0xFF0A0F24.toInt(), 0xFF131A36.toInt(), 0xFF1F2142.toInt()), floatArrayOf(0f, 0.6f, 1f))
        stars(c, 35, 51L, 380f, 0.8f)
        fill(c, intArrayOf(0x00FF9E5E, 0x1AFF9E5E), floatArrayOf(0f, 1f), 640f, H)

        val rnd = Random(7L)
        paint.style = Paint.Style.FILL
        // Back row of buildings
        var x = -20f
        while (x < W) {
            val bw = 70f + rnd.nextFloat() * 110f
            val bh = 260f + rnd.nextFloat() * 300f
            paint.color = 0xFF10163A.toInt()
            c.drawRect(x, H - bh, x + bw, H, paint)
            x += bw + 6f
        }
        // Front row with lit windows
        x = -10f
        while (x < W) {
            val bw = 90f + rnd.nextFloat() * 150f
            val bh = 150f + rnd.nextFloat() * 260f
            val top = H - bh
            paint.color = 0xFF080B18.toInt()
            c.drawRect(x, top, x + bw, H, paint)
            var wy = top + 22f
            while (wy < H - 20f) {
                var wx = x + 14f
                while (wx < x + bw - 22f) {
                    if (rnd.nextFloat() < 0.2f) {
                        paint.color = if (rnd.nextFloat() < 0.8f) {
                            Ui.alpha(0xFFFFD58A.toInt(), 0.35f + rnd.nextFloat() * 0.3f)
                        } else {
                            Ui.alpha(0xFF9FD8FF.toInt(), 0.35f)
                        }
                        c.drawRect(wx, wy, wx + 10f, wy + 14f, paint)
                    }
                    wx += 26f
                }
                wy += 34f
            }
            x += bw + 10f
        }
    }

    private fun space(c: Canvas) {
        fill(c, intArrayOf(0xFF02030A.toInt(), 0xFF070A1A.toInt(), 0xFF0B0F24.toInt()), floatArrayOf(0f, 0.6f, 1f))
        glow(c, 500f, 320f, 620f, 0xFF8B9DFF.toInt(), 0.18f)
        glow(c, 1420f, 720f, 700f, accent, 0.14f)
        glow(c, 1080f, 240f, 420f, 0xFFFF7AB6.toInt(), 0.08f)
        stars(c, 280, 61L, H)
        val rnd = Random(67L)
        paint.style = Paint.Style.FILL
        for (i in 0 until 14) {
            val sx = rnd.nextFloat() * W
            val sy = rnd.nextFloat() * H
            glow(c, sx, sy, 14f, 0xFFDCE6FF.toInt(), 0.5f)
            paint.color = 0xFFF4F7FF.toInt()
            c.drawCircle(sx, sy, 2.2f, paint)
        }
    }

    private fun plain(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Ui.BG
        c.drawRect(0f, 0f, W, H, paint)
        glow(c, 260f, 0f, 1000f, accent, 0.07f)
    }

    /** Your own picture, cropped to fill, with a dark wash so text stays readable. */
    private fun photo(c: Canvas, w: Int, h: Int): Boolean {
        val file = Backgrounds.photoFile(context)
        if (file.length() <= 0L) return false
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false

        val target = w.toFloat() / h
        val srcAspect = bmp.width.toFloat() / bmp.height
        val src = if (srcAspect > target) {
            val cw = (bmp.height * target).toInt()
            val left = (bmp.width - cw) / 2
            Rect(left, 0, left + cw, bmp.height)
        } else {
            val ch = (bmp.width / target).toInt()
            val top = (bmp.height - ch) / 2
            Rect(0, top, bmp.width, top + ch)
        }
        val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(bmp, src, RectF(0f, 0f, w.toFloat(), h.toFloat()), drawPaint)
        bmp.recycle()

        val wash = Paint()
        wash.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x99070B14.toInt(), 0x66070B14, 0xB3070B14.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), wash)
        return true
    }

    companion object {
        private const val W = 1920f
        private const val H = 1080f
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
    }
}
