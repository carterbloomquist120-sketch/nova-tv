package com.novatv.launcher

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared look for every screen. All sizes are written in "design pixels"
 * from the 1920x1080 mockup and scaled to the real screen, so the layout
 * matches the mockup on any TV.
 */
object Ui {
    val BG = 0xFF070B14.toInt()
    val TEXT = 0xFFEAF0FA.toInt()
    val BODY = 0xFFDCE3EF.toInt()
    val SOFT = 0xFFC3CDDE.toInt()
    val MUTED = 0xFF9AA6BC.toInt()
    val FAINT = 0xFF6F7C93.toInt()
    val ON_ACCENT = 0xFF06101A.toInt()
    val PANEL = 0x9E0A101E.toInt()
    val TILE = 0xC70E1628.toInt()
    val HAIRLINE = 0x1AFFFFFF
    val CARD = 0x0AFFFFFF
    val CARD_LINE = 0x14FFFFFF
    val CARD_FOCUS = 0x14FFFFFF
    val ART = 0x0DFFFFFF
    val CHIP = 0x0FFFFFFF
    val CHIP_LINE = 0x1FFFFFFF

    /** Glow colors offered in Settings: cyan, periwinkle, amber, mint. */
    val ACCENTS = intArrayOf(
        0xFF5CE1E6.toInt(),
        0xFF8B9DFF.toInt(),
        0xFFFFB547.toInt(),
        0xFF7CF29A.toInt()
    )

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    var scale = 1f
        private set

    private val faces = HashMap<Int, Typeface>()

    fun init(context: Context) {
        val dm = context.resources.displayMetrics
        scale = max(dm.widthPixels, dm.heightPixels) / 1920f
    }

    /** Design px -> real px (int). */
    fun s(v: Number): Int {
        val f = v.toFloat() * scale
        val r = f.roundToInt()
        return if (f > 0f && r < 1) 1 else r
    }

    /** Design px -> real px (float). */
    fun sf(v: Number): Float = v.toFloat() * scale

    fun alpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((a * 255f).roundToInt().coerceIn(0, 255) shl 24)

    fun font(context: Context, weight: Int): Typeface = faces.getOrPut(weight) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                Typeface.Builder(context.assets, "fonts/Sora.ttf")
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            } else {
                Typeface.createFromAsset(context.assets, "fonts/Sora.ttf")
            }
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    fun text(
        context: Context,
        value: String,
        size: Number,
        color: Int = TEXT,
        weight: Int = 400
    ): TextView {
        val tv = TextView(context)
        tv.text = value
        tv.setTextColor(color)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sf(size))
        tv.typeface = font(context, weight)
        tv.includeFontPadding = false
        return tv
    }

    /** Small spaced-out uppercase label, like "QUICK ACTIONS". */
    fun label(context: Context, value: String, size: Number = 15, color: Int = MUTED): TextView {
        val tv = TextView(context)
        tv.text = value.uppercase()
        tv.setTextColor(color)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, sf(size))
        tv.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        tv.letterSpacing = 0.16f
        tv.includeFontPadding = false
        return tv
    }

    fun box(fill: Int, radius: Number, strokeW: Number = 0, strokeColor: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = sf(radius)
        d.setColor(fill)
        if (strokeW.toFloat() > 0f) d.setStroke(s(strokeW), strokeColor)
        return d
    }

    fun oval(fill: Int, strokeW: Number = 0, strokeColor: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(fill)
        if (strokeW.toFloat() > 0f) d.setStroke(s(strokeW), strokeColor)
        return d
    }

    fun panel(): GradientDrawable = box(PANEL, 28, 1, HAIRLINE)

    fun states(normal: Drawable, focused: Drawable): StateListDrawable {
        val d = StateListDrawable()
        d.addState(intArrayOf(android.R.attr.state_focused), focused)
        d.addState(intArrayOf(android.R.attr.state_pressed), focused)
        d.addState(intArrayOf(), normal)
        return d
    }

    /** Makes a view selectable with the remote and gives it a small grow on focus. */
    fun focusable(v: View, grow: Float = 1.04f, onFocus: ((Boolean) -> Unit)? = null) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnFocusChangeListener { view, has ->
            val target = if (has) grow else 1f
            view.animate().scaleX(target).scaleY(target).setDuration(140).start()
            onFocus?.invoke(has)
        }
    }

    fun row(context: Context): LinearLayout {
        val l = LinearLayout(context)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        l.clipChildren = false
        l.clipToPadding = false
        return l
    }

    fun col(context: Context): LinearLayout {
        val l = LinearLayout(context)
        l.orientation = LinearLayout.VERTICAL
        l.clipChildren = false
        l.clipToPadding = false
        return l
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    fun LinearLayout.LayoutParams.margins(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        setMargins(left, top, right, bottom)
        return this
    }
}
