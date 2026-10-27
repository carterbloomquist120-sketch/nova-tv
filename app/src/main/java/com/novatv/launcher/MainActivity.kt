package com.novatv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.novatv.launcher.Ui.margins
import com.novatv.launcher.Ui.s
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var builtRevision = -1
    private var accent = Ui.ACCENTS[0]
    private var apps: List<AppEntry> = emptyList()
    private val history = ArrayList<AiClient.Msg>()
    private var busy = false
    private var lastWeather: Weather? = null
    private var lastWeatherAt = 0L

    private lateinit var greetingView: TextView
    private lateinit var dateView: TextView
    private lateinit var timeView: TextView
    private lateinit var weatherIcon: IconView
    private lateinit var weatherText: TextView
    private lateinit var stateLabel: TextView
    private lateinit var promptRow: LinearLayout
    private lateinit var promptBubble: TextView
    private lateinit var responseScroll: ScrollView
    private lateinit var responseView: TextView
    private lateinit var cardsRow: LinearLayout
    private lateinit var askBar: LinearLayout
    private lateinit var askInput: EditText
    private lateinit var appsSection: View
    private lateinit var appsRow: LinearLayout
    private var firstCard: View? = null

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.setBackgroundColor(Ui.BG)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.revision != builtRevision) {
            build()
            lastWeatherAt = 0L
        } else {
            refreshApps()
        }
        handler.removeCallbacks(tick)
        tick.run()
        if (System.currentTimeMillis() - lastWeatherAt > 30 * 60 * 1000L) refreshWeather()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (promptRow.visibility == View.VISIBLE && !busy) {
            resetConversation()
        } else if (!busy) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ---------------------------------------------------------------- layout

    private fun build() {
        Ui.init(this)
        builtRevision = prefs.revision
        accent = prefs.accent
        history.clear()
        busy = false

        val root = FrameLayout(this)
        root.addView(SkyView(this), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val content = Ui.col(this)
        content.setPadding(s(96), s(60), s(96), s(56))
        root.addView(content, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        content.addView(buildHeader(), Ui.lp(Ui.MATCH, s(84)))

        val main = Ui.row(this)
        main.gravity = Gravity.TOP
        content.addView(main, Ui.lp(Ui.MATCH, 0, 1f).margins(top = s(36)))
        val assistant = buildAssistant()
        if (prefs.showQuickActions) {
            val actions = buildActions()
            if (prefs.actionsLeft) {
                main.addView(actions, Ui.lp(s(460), Ui.MATCH))
                main.addView(assistant, Ui.lp(0, Ui.MATCH, 1f).margins(left = s(32)))
            } else {
                main.addView(assistant, Ui.lp(0, Ui.MATCH, 1f))
                main.addView(actions, Ui.lp(s(460), Ui.MATCH).margins(left = s(32)))
            }
        } else {
            main.addView(assistant, Ui.lp(0, Ui.MATCH, 1f))
        }

        appsSection = buildAppsSection()
        appsSection.visibility = if (prefs.showApps) View.VISIBLE else View.GONE
        content.addView(appsSection, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(36)))

        setContentView(root)
        refreshApps()
        lastWeather?.let { showWeather(it) }
        updateClock()
        resetConversation()
    }

    private fun buildHeader(): View {
        val row = Ui.row(this)
        row.addView(OrbView(this, accent), Ui.lp(s(56), s(56)))

        val names = Ui.col(this)
        greetingView = Ui.text(this, "", 40, Ui.TEXT, 600)
        greetingView.maxLines = 1
        dateView = Ui.label(this, "", 17)
        names.addView(greetingView)
        names.addView(dateView, Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = s(10)))
        row.addView(names, Ui.lp(0, Ui.WRAP, 1f).margins(left = s(22)))

        weatherIcon = IconView(this, Icon.MOON, Ui.SOFT)
        weatherIcon.visibility = View.GONE
        row.addView(weatherIcon, Ui.lp(s(30), s(30)))
        weatherText = Ui.text(this, "", 26, Ui.SOFT, 500)
        weatherText.visibility = View.GONE
        row.addView(weatherText, Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(12)))

        timeView = Ui.text(this, "", 44, Ui.TEXT, 600)
        row.addView(timeView, Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(36)))

        val settings = circleButton(Icon.SLIDERS, "Launcher settings", filled = false) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        row.addView(settings, Ui.lp(s(64), s(64)).margins(left = s(36)))
        return row
    }

    private fun buildAssistant(): View {
        val panel = Ui.col(this)
        panel.background = Ui.panel()
        panel.setPadding(s(40), s(40), s(40), s(40))

        // Name + status pill
        val head = Ui.row(this)
        head.addView(OrbView(this, accent), Ui.lp(s(40), s(40)))
        head.addView(
            Ui.text(this, prefs.assistantName, 28, Ui.TEXT, 600),
            Ui.lp(0, Ui.WRAP, 1f).margins(left = s(16))
        )
        val pill = Ui.row(this)
        pill.background = Ui.box(Ui.CHIP, 999, 1, Ui.HAIRLINE)
        pill.setPadding(s(18), s(10), s(18), s(10))
        val dot = View(this)
        dot.background = Ui.oval(accent)
        pill.addView(dot, Ui.lp(s(10), s(10)))
        stateLabel = Ui.label(this, "Ready", 16, Ui.SOFT)
        pill.addView(stateLabel, Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(10)))
        head.addView(pill)
        panel.addView(head, Ui.lp(Ui.MATCH, s(48)))

        // What you asked
        promptRow = Ui.row(this)
        promptRow.gravity = Gravity.END
        promptBubble = Ui.text(this, "", 24, Ui.TEXT, 500)
        val bubbleBg = GradientDrawable()
        val r = Ui.sf(24)
        val tight = Ui.sf(6)
        bubbleBg.cornerRadii = floatArrayOf(r, r, r, r, tight, tight, r, r)
        bubbleBg.setColor(Ui.alpha(accent, 0.14f))
        bubbleBg.setStroke(s(1), Ui.alpha(accent, 0.45f))
        promptBubble.background = bubbleBg
        promptBubble.setPadding(s(26), s(14), s(26), s(14))
        promptBubble.maxWidth = s(900)
        promptRow.addView(promptBubble)
        panel.addView(promptRow, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(22)))

        // The answer
        responseScroll = ScrollView(this)
        responseScroll.isVerticalScrollBarEnabled = false
        responseScroll.background = Ui.states(
            Ui.box(0, 16),
            Ui.box(Ui.alpha(accent, 0.06f), 16, 1, Ui.alpha(accent, 0.45f))
        )
        responseScroll.setPadding(s(6), s(6), s(6), s(6))
        responseView = Ui.text(this, "", 26, Ui.BODY, 400)
        responseView.setLineSpacing(0f, 1.3f)
        responseScroll.addView(responseView, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        panel.addView(responseScroll, Ui.lp(Ui.MATCH, 0, 1f).margins(top = s(16)))

        // Suggestion cards
        cardsRow = Ui.row(this)
        cardsRow.gravity = Gravity.TOP
        val cards = listOf(
            CardDef(
                Icon.PLAY, "Pick for me", "Tonight's best bet",
                "Pick something for me to watch tonight",
                "Pick one thing for me to watch tonight from my apps. Just one, with a one-line reason."
            ),
            CardDef(
                Icon.SMILE, "Something funny", "Comedies under 30 min",
                "Something funny, under 30 minutes",
                "I want something funny, under 30 minutes per episode. Give me three options from my apps."
            ),
            CardDef(
                Icon.FILM, "Movie night", "One great movie",
                "Plan a movie night",
                "Plan a movie night: one main movie and one backup, each under 2.5 hours, on my apps."
            ),
            CardDef(
                Icon.SHUFFLE, "Surprise me", "One pick, no scrolling",
                "Surprise me",
                "Surprise me with one thing to watch that I might not pick myself, from my apps."
            )
        )
        firstCard = null
        cards.forEachIndexed { i, def ->
            val card = suggestionCard(def)
            cardsRow.addView(card, Ui.lp(0, Ui.WRAP, 1f).margins(left = if (i == 0) 0 else s(22)))
            if (i == 0) firstCard = card
        }
        panel.addView(cardsRow, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(20)))

        // Ask bar
        askBar = Ui.row(this)
        askBar.background = Ui.box(Ui.CHIP, 42, 1, Ui.CHIP_LINE)
        askBar.setPadding(s(12), 0, s(26), 0)
        val send = circleButton(Icon.SEND, "Send", filled = true) {
            submit(askInput.text.toString())
        }
        askBar.addView(send, Ui.lp(s(62), s(62)))

        askInput = EditText(this)
        askInput.hint = "Ask ${prefs.assistantName} anything"
        askInput.setHintTextColor(Ui.MUTED)
        askInput.setTextColor(Ui.TEXT)
        askInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.tf(24))
        askInput.typeface = Ui.font(this, 400)
        askInput.background = null
        askInput.setSingleLine(true)
        askInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        askInput.imeOptions = EditorInfo.IME_ACTION_SEND
        askInput.setPadding(s(20), 0, s(20), 0)
        askInput.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId != EditorInfo.IME_NULL || enter) {
                submit(askInput.text.toString())
                true
            } else {
                false
            }
        }
        askInput.setOnFocusChangeListener { _, has ->
            askBar.background = if (has) {
                Ui.box(Ui.alpha(accent, 0.10f), 42, 2, accent)
            } else {
                Ui.box(Ui.CHIP, 42, 1, Ui.CHIP_LINE)
            }
        }
        askBar.addView(askInput, Ui.lp(0, Ui.MATCH, 1f))
        askBar.addView(Ui.label(this, "Select to type"))
        panel.addView(askBar, Ui.lp(Ui.MATCH, s(84)).margins(top = s(24)))

        return panel
    }

    private data class CardDef(
        val icon: Icon,
        val title: String,
        val subtitle: String,
        val shown: String,
        val prompt: String
    )

    private fun suggestionCard(def: CardDef): View {
        val card = Ui.col(this)
        card.background = Ui.states(
            Ui.box(Ui.CARD, 22, 2, Ui.CARD_LINE),
            Ui.box(Ui.CARD_FOCUS, 22, 2, accent)
        )
        card.setPadding(s(14), s(14), s(14), s(14))

        val art = FrameLayout(this)
        art.background = Ui.box(Ui.ART, 14)
        val icon = IconView(this, def.icon, Ui.SOFT)
        art.addView(icon, FrameLayout.LayoutParams(s(52), s(52), Gravity.CENTER))
        card.addView(art, Ui.lp(Ui.MATCH, s(120)))

        card.addView(
            Ui.text(this, def.title, 24, Ui.TEXT, 600),
            Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(6), top = s(14))
        )
        card.addView(
            Ui.text(this, def.subtitle, 18, Ui.MUTED),
            Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(6), top = s(6), bottom = s(6))
        )

        Ui.focusable(card, 1.05f) { has -> icon.color = if (has) accent else Ui.SOFT }
        card.contentDescription = def.title
        card.setOnClickListener { ask(def.prompt, def.shown) }
        return card
    }

    private fun buildActions(): View {
        val panel = Ui.col(this)
        panel.background = Ui.panel()
        panel.setPadding(s(26), s(26), s(26), s(26))
        panel.addView(Ui.label(this, "Quick actions"), Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(8), bottom = s(12)))

        actionRow(panel, Icon.PLAY, "What should I watch?") {
            ask(
                "What should I watch tonight? Give me three options from my apps.",
                "What should I watch tonight?"
            )
        }
        actionRow(panel, Icon.COMPASS, "Find something new") {
            ask(
                "Recommend one show and one movie that are well reviewed but not the obvious hits, from my apps.",
                "Find me something new"
            )
        }
        actionRow(panel, Icon.SPARK, "Ask anything") {
            askInput.requestFocus()
            showKeyboard()
        }
        actionRow(panel, Icon.GRID, "All apps") {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        actionRow(panel, Icon.TV, "Fire TV settings") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open TV settings", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow(panel, Icon.SLIDERS, "Launcher settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        return panel
    }

    private fun actionRow(parent: LinearLayout, icon: Icon, title: String, onClick: () -> Unit) {
        val row = Ui.row(this)
        row.background = Ui.states(
            Ui.box(0, 16),
            Ui.box(Ui.alpha(accent, 0.10f), 16, 2, accent)
        )
        row.setPadding(s(10), 0, s(10), 0)

        val square = FrameLayout(this)
        square.background = Ui.box(Ui.alpha(accent, 0.14f), 12)
        square.addView(IconView(this, icon, accent), FrameLayout.LayoutParams(s(24), s(24), Gravity.CENTER))
        row.addView(square, Ui.lp(s(44), s(44)))

        row.addView(Ui.text(this, title, 22, Ui.TEXT, 500), Ui.lp(0, Ui.WRAP, 1f).margins(left = s(18)))
        row.addView(IconView(this, Icon.CHEVRON, Ui.FAINT), Ui.lp(s(22), s(22)))

        Ui.focusable(row, 1.02f)
        row.contentDescription = title
        row.setOnClickListener { onClick() }
        parent.addView(row, Ui.lp(Ui.MATCH, s(60)).margins(top = s(6)))
    }

    private fun buildAppsSection(): View {
        val col = Ui.col(this)
        col.addView(Ui.label(this, "Your apps"), Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = s(4)))
        appsRow = Ui.row(this)
        col.addView(appsRow, Ui.lp(Ui.MATCH, s(108)).margins(top = s(14)))
        return col
    }

    private fun circleButton(icon: Icon, label: String, filled: Boolean, onClick: () -> Unit): View {
        val btn = FrameLayout(this)
        val normal = if (filled) {
            Ui.oval(Ui.alpha(accent, 0.14f), 1, Ui.alpha(accent, 0.45f))
        } else {
            Ui.oval(Ui.CHIP, 1, 0x24FFFFFF)
        }
        btn.background = Ui.states(normal, Ui.oval(accent, 1, accent))
        val iconColor = if (filled) accent else Ui.SOFT
        val iv = IconView(this, icon, iconColor)
        btn.addView(iv, FrameLayout.LayoutParams(s(28), s(28), Gravity.CENTER))
        Ui.focusable(btn, 1.08f) { has -> iv.color = if (has) Ui.ON_ACCENT else iconColor }
        btn.contentDescription = label
        btn.setOnClickListener { onClick() }
        return btn
    }

    // ---------------------------------------------------------------- apps

    private fun refreshApps() {
        apps = AppRepo.all(this)
        val favs = AppRepo.favorites(prefs, apps)
        appsRow.removeAllViews()
        favs.forEachIndexed { i, app ->
            val tile = Tiles.app(this, app, accent)
            tile.setOnClickListener { AppRepo.launch(this, app.pkg) }
            appsRow.addView(tile, Ui.lp(0, Ui.MATCH, 1f).margins(left = if (i == 0) 0 else s(20)))
        }
        val all = Tiles.allApps(this, accent)
        all.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        appsRow.addView(all, Ui.lp(0, Ui.MATCH, 1f).margins(left = if (favs.isEmpty()) 0 else s(20)))
    }

    // ---------------------------------------------------------------- clock + weather

    private fun updateClock() {
        val now = Date()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = prefs.greeting.ifBlank {
            when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
        }
        val name = prefs.userName
        greetingView.text = if (name.isBlank()) part else "$part, $name"
        dateView.text = SimpleDateFormat("EEE · MMM d", Locale.US).format(now).uppercase()
        val use24 = when (prefs.clockMode) {
            "24h" -> true
            "12h" -> false
            else -> android.text.format.DateFormat.is24HourFormat(this)
        }
        timeView.text = SimpleDateFormat(if (use24) "H:mm" else "h:mm a", Locale.US).format(now)
    }

    private fun refreshWeather() {
        if (!prefs.showWeather) {
            weatherIcon.visibility = View.GONE
            weatherText.visibility = View.GONE
            return
        }
        val lat = prefs.latitude
        val lon = prefs.longitude
        val f = prefs.fahrenheit
        Thread {
            val w = WeatherClient.fetch(lat, lon, f)
            runOnUiThread {
                if (isFinishing || isDestroyed || w == null) return@runOnUiThread
                lastWeather = w
                lastWeatherAt = System.currentTimeMillis()
                showWeather(w)
            }
        }.start()
    }

    private fun showWeather(w: Weather) {
        if (!prefs.showWeather) return
        val desc = WeatherClient.describe(w.code)
        weatherText.text = if (desc.isEmpty()) "${w.temp}°" else "${w.temp}° $desc"
        weatherText.visibility = View.VISIBLE
        val fresh = IconView(this, WeatherClient.icon(w), Ui.SOFT)
        val parent = weatherIcon.parent as? LinearLayout
        if (parent != null) {
            val index = parent.indexOfChild(weatherIcon)
            parent.removeViewAt(index)
            parent.addView(fresh, index, Ui.lp(s(30), s(30)))
        }
        weatherIcon = fresh
    }

    // ---------------------------------------------------------------- assistant

    private fun resetConversation() {
        history.clear()
        promptRow.visibility = View.GONE
        cardsRow.visibility = if (prefs.showSuggestions) View.VISIBLE else View.GONE
        responseView.text = if (prefs.showSuggestions) {
            "Here are a few ways to go tonight. Pick one, or just tell me a mood."
        } else {
            "What are you in the mood for tonight?"
        }
        responseScroll.isFocusable = false
        responseScroll.scrollTo(0, 0)
        setState("Ready")
        if (prefs.showSuggestions) firstCard?.requestFocus() else askInput.requestFocus()
    }

    private fun setState(label: String) {
        stateLabel.text = label.uppercase()
    }

    private fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        askInput.setText("")
        hideKeyboard()
        ask(text, text)
    }

    private fun ask(prompt: String, shown: String) {
        if (busy) return

        // "open netflix" works instantly, without the AI.
        val direct = Regex("^(open|launch|start|go to)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(prompt.trim())
        if (direct != null) {
            val app = AppRepo.findByName(apps, direct.groupValues[2])
            if (app != null) {
                showExchange(shown, "Opening ${app.label}…")
                AppRepo.launch(this, app.pkg)
                return
            }
        }

        busy = true
        history.add(AiClient.Msg("user", prompt))
        trimHistory()
        showExchange(shown, "Thinking…")
        setState("Thinking")

        val snapshot = ArrayList(history)
        val system = systemPrompt()
        val client = AiClient(prefs)
        Thread {
            val outcome = try {
                Result.success(client.ask(snapshot, system))
            } catch (e: Exception) {
                Result.failure(e)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                outcome.onSuccess { reply -> showReply(shown, reply) }
                outcome.onFailure { err ->
                    if (history.isNotEmpty() && history.last().role == "user") history.removeAt(history.size - 1)
                    setState("Offline")
                    responseView.text = err.message ?: "Something went wrong. Try again."
                    afterResponse()
                }
            }
        }.start()
    }

    private fun showReply(question: String, reply: String) {
        history.add(AiClient.Msg("assistant", reply))
        val result = Commands.apply(reply, prefs, apps)

        val parts = ArrayList<String>()
        if (result.text.isNotEmpty()) parts.add(result.text)
        parts.addAll(result.problems)
        val answer = parts.joinToString("\n\n").ifEmpty { "Done." }

        if (result.lookChanged) {
            rebuildKeepingChat(question, answer)
            if (result.weatherChanged) {
                lastWeather = null
                lastWeatherAt = 0L
                refreshWeather()
            }
        } else {
            if (result.rowChanged) refreshApps()
            if (result.weatherChanged) {
                lastWeatherAt = 0L
                refreshWeather()
            }
            responseView.text = answer
            setState("Ready")
            afterResponse()
        }

        val pkg = result.openPkg
        if (pkg != null) handler.postDelayed({ AppRepo.launch(this, pkg) }, 1500)
    }

    /** Redraws the screen with the new settings but keeps the conversation on screen. */
    private fun rebuildKeepingChat(question: String, answer: String) {
        val saved = ArrayList(history)
        build()
        history.addAll(saved)
        showExchange(question, answer)
        setState("Ready")
        afterResponse()
        updateClock()
    }

    private fun showExchange(question: String, answer: String) {
        promptBubble.text = question
        promptRow.visibility = View.VISIBLE
        cardsRow.visibility = View.GONE
        responseView.text = answer
        responseScroll.scrollTo(0, 0)
    }

    /** Lets the remote scroll long answers, then puts focus back on the ask bar. */
    private fun afterResponse() {
        responseScroll.post {
            val overflow = responseView.height > responseScroll.height - responseScroll.paddingTop - responseScroll.paddingBottom
            responseScroll.isFocusable = overflow
            if (overflow) responseScroll.requestFocus() else askInput.requestFocus()
        }
    }

    private fun trimHistory() {
        while (history.size > 12) history.removeAt(0)
        while (history.isNotEmpty() && history.first().role != "user") history.removeAt(0)
    }

    private fun systemPrompt(): String {
        val user = prefs.userName.ifBlank { "the user" }
        val names = apps.joinToString(", ") { it.label }
        val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
        return "You are ${prefs.assistantName}, the assistant built into $user's TV home screen " +
            "on an Amazon Fire TV. Installed apps: $names. Today is $today. " +
            "Your replies are read on a TV from across the room: keep them under 70 words, " +
            "plain text, no markdown. For lists, use short lines that start with '• '. " +
            "When suggesting something to watch, name the title and the installed app that " +
            "likely has it; streaming catalogs change, so if you aren't sure, say to check. " +
            "You can't see what the user has already watched.\n\n" +
            Commands.instructions(user) + "\n\n" +
            Commands.describeState(prefs, apps)
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(askInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(askInput.windowToken, 0)
    }
}
