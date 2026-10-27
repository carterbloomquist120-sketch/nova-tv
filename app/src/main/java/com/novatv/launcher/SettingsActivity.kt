package com.novatv.launcher

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.novatv.launcher.Ui.margins
import com.novatv.launcher.Ui.s

/** Name, AI key, weather location and glow color. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private var accent = Ui.ACCENTS[0]
    private var fahrenheit = true
    private val swatches = ArrayList<Pair<Int, FrameLayout>>()
    private lateinit var fBtn: TextView
    private lateinit var cBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Ui.init(this)
        window.decorView.setBackgroundColor(Ui.BG)
        accent = prefs.accent
        fahrenheit = prefs.fahrenheit

        val root = FrameLayout(this)
        root.addView(SkyView(this), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        root.addView(scroll, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val outer = Ui.col(this)
        outer.gravity = Gravity.CENTER_HORIZONTAL
        outer.setPadding(s(96), s(60), s(96), s(80))
        scroll.addView(outer, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        val form = Ui.col(this)
        form.background = Ui.panel()
        form.setPadding(s(48), s(44), s(48), s(48))
        outer.addView(form, Ui.lp(s(1240), Ui.WRAP))

        form.addView(Ui.text(this, "Launcher settings", 40, Ui.TEXT, 600))

        val name = field(form, "Your name", prefs.userName, "Shown in the greeting")
        val assistant = field(form, "Assistant name", prefs.assistantName, "Nova")

        val key = field(
            form, "Claude API key", "", if (prefs.apiKey.isEmpty()) "Not set" else "Saved key ending in ${prefs.apiKey.takeLast(4)}. Leave blank to keep it.",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        form.addView(
            Ui.text(
                this,
                "Get a key at console.anthropic.com. Tip: the Amazon Fire TV phone app has a keyboard, so you can paste the key from your phone instead of typing it with the remote.",
                18, Ui.MUTED
            ).apply { setLineSpacing(0f, 1.3f) },
            Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(12))
        )

        val model = field(form, "AI model", prefs.model, Prefs.DEFAULT_MODEL)

        // Weather location
        form.addView(Ui.label(this, "Weather location"), Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = s(36)))
        val coords = Ui.row(this)
        val signedDecimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val lat = input(prefs.latitude.toString(), "Latitude", signedDecimal)
        val lon = input(prefs.longitude.toString(), "Longitude", signedDecimal)
        coords.addView(lat, Ui.lp(0, s(72), 1f))
        coords.addView(lon, Ui.lp(0, s(72), 1f).margins(left = s(20)))
        fBtn = pill("°F") { fahrenheit = true; paintUnits() }
        cBtn = pill("°C") { fahrenheit = false; paintUnits() }
        coords.addView(fBtn, Ui.lp(s(96), s(72)).margins(left = s(20)))
        coords.addView(cBtn, Ui.lp(s(96), s(72)).margins(left = s(12)))
        form.addView(coords, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(12)))
        paintUnits()

        // Glow color
        form.addView(Ui.label(this, "Glow color"), Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = s(36)))
        val colors = Ui.row(this)
        for (c in Ui.ACCENTS) {
            val sw = FrameLayout(this)
            Ui.focusable(sw, 1.12f)
            sw.contentDescription = "Glow color"
            sw.setOnClickListener {
                accent = c
                paintSwatches()
            }
            swatches.add(Pair(c, sw))
            colors.addView(sw, Ui.lp(s(64), s(64)).margins(right = s(20)))
        }
        form.addView(colors, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(14)))
        paintSwatches()

        // Save / cancel
        val buttons = Ui.row(this)
        val save = button("Save", primary = true) {
            prefs.userName = name.text.toString()
            prefs.assistantName = assistant.text.toString()
            val newKey = key.text.toString().trim()
            if (newKey.isNotEmpty()) prefs.apiKey = newKey
            prefs.model = model.text.toString()
            lat.text.toString().toFloatOrNull()?.let { prefs.latitude = it }
            lon.text.toString().toFloatOrNull()?.let { prefs.longitude = it }
            prefs.fahrenheit = fahrenheit
            prefs.accent = accent
            prefs.bumpRevision()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        buttons.addView(save, Ui.lp(s(220), s(72)))
        buttons.addView(button("Cancel", primary = false) { finish() }, Ui.lp(s(220), s(72)).margins(left = s(20)))
        form.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(44)))

        setContentView(root)
        name.requestFocus()
    }

    private fun field(
        parent: LinearLayout,
        title: String,
        value: String,
        hint: String,
        type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    ): EditText {
        parent.addView(Ui.label(this, title), Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = s(36)))
        val e = input(value, hint, type)
        parent.addView(e, Ui.lp(Ui.MATCH, s(72)).margins(top = s(12)))
        return e
    }

    private fun input(value: String, hint: String, type: Int): EditText {
        val e = EditText(this)
        e.setText(value)
        e.hint = hint
        e.inputType = type
        e.setSingleLine(true)
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.FAINT)
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sf(24))
        e.typeface = Ui.font(this, 400)
        e.setPadding(s(22), 0, s(22), 0)
        e.background = Ui.states(
            Ui.box(Ui.CHIP, 16, 1, Ui.CHIP_LINE),
            Ui.box(Ui.alpha(accent, 0.10f), 16, 2, accent)
        )
        return e
    }

    private fun pill(textValue: String, onClick: () -> Unit): TextView {
        val t = Ui.text(this, textValue, 24, Ui.TEXT, 600)
        t.gravity = Gravity.CENTER
        Ui.focusable(t, 1.06f)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun paintUnits() {
        val on = Ui.states(Ui.box(Ui.alpha(accent, 0.20f), 16, 2, accent), Ui.box(Ui.alpha(accent, 0.30f), 16, 3, accent))
        val off = Ui.states(Ui.box(Ui.CHIP, 16, 1, Ui.CHIP_LINE), Ui.box(Ui.alpha(accent, 0.10f), 16, 2, accent))
        fBtn.background = if (fahrenheit) on else off
        cBtn.background = if (fahrenheit) off else on
    }

    private fun paintSwatches() {
        for ((c, sw) in swatches) {
            val selected = c == accent
            val normal = if (selected) Ui.oval(c, 4, Ui.TEXT) else Ui.oval(c)
            sw.background = Ui.states(normal, Ui.oval(c, 4, Ui.TEXT))
        }
        paintUnits()
    }

    private fun button(textValue: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = Ui.text(this, textValue, 24, if (primary) Ui.ON_ACCENT else Ui.TEXT, 600)
        t.gravity = Gravity.CENTER
        t.background = if (primary) {
            Ui.states(Ui.box(accent, 36), Ui.box(accent, 36, 3, Ui.TEXT))
        } else {
            Ui.states(Ui.box(Ui.CHIP, 36, 1, Ui.CHIP_LINE), Ui.box(Ui.alpha(accent, 0.10f), 36, 2, accent))
        }
        Ui.focusable(t, 1.05f)
        t.setOnClickListener { onClick() }
        return t
    }
}
