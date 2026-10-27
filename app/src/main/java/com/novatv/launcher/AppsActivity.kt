package com.novatv.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.novatv.launcher.Ui.margins
import com.novatv.launcher.Ui.s

/** Every installed app. Hold Select (or press Menu) on one to pin it to the home row. */
class AppsActivity : Activity() {
    private lateinit var prefs: Prefs
    private var apps: List<AppEntry> = emptyList()
    private val badges = HashMap<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Ui.init(this)
        window.decorView.setBackgroundColor(Ui.BG)
        val accent = prefs.accent

        val root = FrameLayout(this)
        root.addView(SkyView(this), FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipChildren = false
        root.addView(scroll, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val content = Ui.col(this)
        content.setPadding(s(96), s(60), s(96), s(80))
        scroll.addView(content, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        val header = Ui.row(this)
        header.addView(Ui.text(this, "All apps", 40, Ui.TEXT, 600), Ui.lp(0, Ui.WRAP, 1f))
        header.addView(Ui.label(this, "Hold select to pin to home", 16, Ui.MUTED))
        content.addView(header, Ui.lp(Ui.MATCH, s(84)))

        apps = AppRepo.all(this)
        val perRow = 6
        var row: LinearLayout? = null
        apps.forEachIndexed { i, app ->
            if (i % perRow == 0) {
                row = Ui.row(this)
                content.addView(row, Ui.lp(Ui.MATCH, s(150)).margins(top = s(28)))
            }
            val cell = Ui.col(this)
            val tile = Tiles.app(this, app, accent)
            tile.tag = app
            tile.setOnClickListener { AppRepo.launch(this, app.pkg) }
            tile.setOnLongClickListener {
                toggle(app)
                true
            }
            val badge = View(this)
            badge.background = Ui.oval(accent, 3, Ui.BG)
            badge.visibility = if (AppRepo.isFavorite(prefs, apps, app)) View.VISIBLE else View.GONE
            badges[app.pkg] = badge
            val frame = FrameLayout(this)
            frame.clipChildren = false
            frame.addView(tile, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            frame.addView(badge, FrameLayout.LayoutParams(s(22), s(22), Gravity.TOP or Gravity.END).apply {
                topMargin = s(8)
                rightMargin = s(8)
            })
            cell.addView(frame, Ui.lp(Ui.MATCH, 0, 1f))
            val name = Ui.text(this, app.label, 18, Ui.SOFT, 500)
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            cell.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP).margins(top = s(10), left = s(4)))
            row?.addView(cell, Ui.lp(0, Ui.MATCH, 1f).margins(left = if (i % perRow == 0) 0 else s(24)))
        }
        // Keep the last row's tiles the same width as the others.
        val leftover = apps.size % perRow
        if (leftover != 0) {
            for (k in leftover until perRow) {
                row?.addView(View(this), Ui.lp(0, Ui.MATCH, 1f).margins(left = s(24)))
            }
        }
        if (apps.isEmpty()) {
            content.addView(Ui.text(this, "No apps found.", 26, Ui.MUTED), Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = s(40)))
        }

        setContentView(root)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val app = currentFocus?.tag as? AppEntry
            if (app != null) {
                toggle(app)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun toggle(app: AppEntry) {
        val message = AppRepo.toggleFavorite(prefs, apps, app)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        for ((pkg, badge) in badges) {
            val entry = apps.firstOrNull { it.pkg == pkg } ?: continue
            badge.visibility = if (AppRepo.isFavorite(prefs, apps, entry)) View.VISIBLE else View.GONE
        }
    }
}
