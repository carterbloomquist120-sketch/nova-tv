package com.novatv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.novatv.launcher.Ui.margins

data class AppEntry(val pkg: String, val label: String)

/** Finds, pins and opens the other apps installed on the TV. */
object AppRepo {
    const val MAX_FAVORITES = 7

    /** Popular streaming apps, used to fill the home row until you pick your own. */
    private val PREFERRED = listOf(
        "com.netflix.ninja",
        "com.amazon.firetv.youtube",
        "com.google.android.youtube.tv",
        "com.amazon.avod",
        "com.hulu.plus",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.disney.disneyplus",
        "com.spotify.tv.android",
        "com.peacocktv.peacockandroid",
        "com.cbs.ott",
        "com.apple.atve.amazon.appletv"
    )

    fun all(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppEntry>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val results = try {
                pm.queryIntentActivities(query, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (ri in results) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                found[pkg] = AppEntry(pkg, label.ifEmpty { pkg })
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    fun favorites(prefs: Prefs, apps: List<AppEntry>): List<AppEntry> {
        val byPkg = apps.associateBy { it.pkg }
        val saved = prefs.favorites
        if (saved != null) return saved.mapNotNull { byPkg[it] }.take(MAX_FAVORITES)

        val picks = PREFERRED.mapNotNull { byPkg[it] }.toMutableList()
        for (a in apps) {
            if (picks.size >= MAX_FAVORITES) break
            if (a !in picks && !isUtility(a)) picks.add(a)
        }
        return picks.take(MAX_FAVORITES)
    }

    /** Adds or removes an app from the home row. Returns a message to show. */
    fun toggleFavorite(prefs: Prefs, apps: List<AppEntry>, app: AppEntry): String {
        val current = favorites(prefs, apps).map { it.pkg }.toMutableList()
        return if (current.remove(app.pkg)) {
            prefs.favorites = current
            "Removed ${app.label} from home"
        } else if (current.size >= MAX_FAVORITES) {
            "Home row is full ($MAX_FAVORITES apps). Remove one first."
        } else {
            current.add(app.pkg)
            prefs.favorites = current
            "Added ${app.label} to home"
        }
    }

    /** Pins an app to the end of the home row. Returns false if the row is full. */
    fun pin(prefs: Prefs, apps: List<AppEntry>, app: AppEntry): Boolean {
        val current = favorites(prefs, apps).map { it.pkg }.toMutableList()
        if (current.contains(app.pkg)) return true
        if (current.size >= MAX_FAVORITES) return false
        current.add(app.pkg)
        prefs.favorites = current
        return true
    }

    fun unpin(prefs: Prefs, apps: List<AppEntry>, app: AppEntry) {
        val current = favorites(prefs, apps).map { it.pkg }.toMutableList()
        current.remove(app.pkg)
        prefs.favorites = current
    }

    /** Replaces the whole home row with these apps, in this order. */
    fun setOrder(prefs: Prefs, ordered: List<AppEntry>) {
        prefs.favorites = ordered.map { it.pkg }.distinct().take(MAX_FAVORITES)
    }

    fun isFavorite(prefs: Prefs, apps: List<AppEntry>, app: AppEntry): Boolean =
        favorites(prefs, apps).any { it.pkg == app.pkg }

    private fun isUtility(a: AppEntry): Boolean {
        val l = a.label.lowercase()
        return l == "settings" || l.contains("downloader") || l.contains("file") ||
            a.pkg.startsWith("com.amazon.tv.settings") || a.pkg == "com.android.settings"
    }

    fun banner(context: Context, pkg: String): Drawable? {
        val pm = context.packageManager
        return try {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            val fromActivity = if (intent != null) pm.getActivityBanner(intent) else null
            fromActivity ?: pm.getApplicationBanner(pkg)
        } catch (e: Exception) {
            null
        }
    }

    fun icon(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    fun launch(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /** Matches a spoken or typed app name ("netflix", "prime video") to an installed app. */
    fun findByName(apps: List<AppEntry>, name: String): AppEntry? {
        val want = norm(name)
        if (want.isEmpty()) return null
        apps.firstOrNull { norm(it.label) == want }?.let { return it }
        apps.firstOrNull { norm(it.label).startsWith(want) }?.let { return it }
        apps.firstOrNull { norm(it.label).contains(want) }?.let { return it }
        return apps.firstOrNull { val l = norm(it.label); l.length >= 3 && want.contains(l) }
    }

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }
}

/** App tiles used on the home row and the All apps screen. */
object Tiles {
    fun app(context: Context, app: AppEntry, accent: Int): FrameLayout {
        val tile = FrameLayout(context)
        tile.background = Ui.box(Ui.TILE, 20)
        tile.foreground = Ui.states(
            Ui.box(0, 20, 1, Ui.CHIP_LINE),
            Ui.box(0, 20, 3, accent)
        )
        tile.outlineProvider = ViewOutlineProvider.BACKGROUND
        tile.clipToOutline = true
        tile.contentDescription = app.label
        Ui.focusable(tile, 1.06f)

        val banner = AppRepo.banner(context, app.pkg)
        if (banner != null) {
            val img = ImageView(context)
            img.setImageDrawable(banner)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            tile.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        } else {
            val col = Ui.col(context)
            col.gravity = Gravity.CENTER
            val icon = AppRepo.icon(context, app.pkg)
            if (icon != null) {
                val img = ImageView(context)
                img.setImageDrawable(icon)
                img.scaleType = ImageView.ScaleType.FIT_CENTER
                col.addView(img, Ui.lp(Ui.s(48), Ui.s(48)))
            }
            val t = Ui.text(context, app.label, 20, Ui.TEXT, 600)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER
            col.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP).margins(top = Ui.s(10)))
            col.setPadding(Ui.s(10), 0, Ui.s(10), 0)
            tile.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        return tile
    }

    fun allApps(context: Context, accent: Int): FrameLayout {
        val tile = FrameLayout(context)
        tile.background = Ui.states(
            Ui.box(Ui.TILE, 20, 1, Ui.CHIP_LINE),
            Ui.box(Ui.TILE, 20, 3, accent)
        )
        tile.contentDescription = "All apps"
        Ui.focusable(tile, 1.06f)
        val row = Ui.row(context)
        row.gravity = Gravity.CENTER
        row.addView(IconView(context, Icon.GRID, Ui.SOFT), Ui.lp(Ui.s(24), Ui.s(24)))
        row.addView(
            Ui.text(context, "All apps", 22, Ui.SOFT, 500),
            Ui.lp(Ui.WRAP, Ui.WRAP).margins(left = Ui.s(12))
        )
        tile.addView(row, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        return tile
    }
}
