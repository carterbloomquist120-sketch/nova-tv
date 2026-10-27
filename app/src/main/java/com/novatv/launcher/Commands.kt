package com.novatv.launcher

import kotlin.math.pow

/**
 * Lets Nova edit the home screen. Nova ends a reply with command lines like
 * "SET accent: amber" or "PIN: Peacock"; this applies them and strips them
 * from what's shown on screen.
 */
object Commands {
    data class Outcome(
        val text: String,
        val lookChanged: Boolean,
        val rowChanged: Boolean,
        val weatherChanged: Boolean,
        val openPkg: String?,
        val problems: List<String>
    )

    // Command words must be uppercase so normal sentences ("Show: The Bear") aren't mistaken for commands.
    private val LINE = Regex("^(SET\\s+([A-Za-z_ ]+?)|PIN|UNPIN|ORDER|SHOW|HIDE|RESET|OPEN)\\s*:\\s*(.*)$")

    private val NAMED_COLORS = mapOf(
        "cyan" to 0, "teal" to 0, "aqua" to 0, "turquoise" to 0,
        "periwinkle" to 1, "blue" to 1, "purple" to 1, "violet" to 1, "indigo" to 1, "lavender" to 1,
        "amber" to 2, "orange" to 2, "gold" to 2, "yellow" to 2,
        "mint" to 3, "green" to 3, "lime" to 3, "emerald" to 3
    )

    fun apply(reply: String, prefs: Prefs, apps: List<AppEntry>): Outcome {
        val kept = ArrayList<String>()
        val problems = ArrayList<String>()
        var look = false
        var row = false
        var weather = false
        var open: String? = null

        for (raw in reply.lines()) {
            val line = raw.trim().trimStart('-', '*', '•', '`', ' ').trimEnd('`', ' ')
            val m = LINE.matchEntire(line)
            if (m == null) {
                kept.add(raw)
                continue
            }
            val verb = m.groupValues[1]
            val key = m.groupValues[2].trim().lowercase().replace(' ', '_')
            val value = m.groupValues[3].trim().trimEnd('.')

            when {
                verb == "OPEN" -> {
                    val app = AppRepo.findByName(apps, value)
                    if (app != null) open = app.pkg else problems.add("I couldn't find an app called $value.")
                }
                verb == "PIN" -> {
                    val app = AppRepo.findByName(apps, value)
                    when {
                        app == null -> problems.add("I couldn't find an app called $value.")
                        !AppRepo.pin(prefs, apps, app) ->
                            problems.add("Your home row is full (${AppRepo.MAX_FAVORITES} apps). Unpin one first.")
                        else -> row = true
                    }
                }
                verb == "UNPIN" -> {
                    val app = AppRepo.findByName(apps, value)
                    if (app == null) {
                        problems.add("I couldn't find an app called $value.")
                    } else {
                        AppRepo.unpin(prefs, apps, app)
                        row = true
                    }
                }
                verb == "ORDER" -> {
                    val list = value.split(',').mapNotNull { AppRepo.findByName(apps, it.trim()) }
                    if (list.isEmpty()) {
                        problems.add("I couldn't match those app names.")
                    } else {
                        AppRepo.setOrder(prefs, list)
                        row = true
                    }
                }
                verb == "SHOW" || verb == "HIDE" -> {
                    val on = verb == "SHOW"
                    when (section(value)) {
                        "quick_actions" -> prefs.showQuickActions = on
                        "suggestions" -> prefs.showSuggestions = on
                        "weather" -> prefs.showWeather = on
                        "apps" -> prefs.showApps = on
                        else -> {
                            problems.add("I can't ${verb.lowercase()} \"$value\" yet.")
                            continue
                        }
                    }
                    look = true
                    if (section(value) == "weather") weather = true
                }
                verb == "RESET" -> {
                    prefs.resetLook()
                    look = true
                    weather = true
                }
                verb.startsWith("SET") -> {
                    when (set(key, value, prefs, problems)) {
                        Change.LOOK -> look = true
                        Change.WEATHER -> {
                            look = true
                            weather = true
                        }
                        Change.NONE -> {}
                    }
                }
            }
        }

        return Outcome(kept.joinToString("\n").trim(), look, row, weather, open, problems)
    }

    private enum class Change { NONE, LOOK, WEATHER }

    private fun set(key: String, value: String, prefs: Prefs, problems: MutableList<String>): Change {
        val v = value.trim()
        val low = v.lowercase()
        when (key) {
            "accent", "glow", "color", "colour", "glow_color" -> {
                val c = parseColor(low)
                if (c == null) {
                    problems.add("That color would be too dark to see on the night sky, so I left it.")
                    return Change.NONE
                }
                prefs.accent = c
                return Change.LOOK
            }
            "assistant_name", "assistant" -> {
                if (v.isEmpty()) return Change.NONE
                prefs.assistantName = v.take(24)
                return Change.LOOK
            }
            "user_name", "your_name", "name" -> {
                prefs.userName = v.take(24)
                return Change.LOOK
            }
            "greeting" -> {
                prefs.greeting = if (low == "default" || low == "time of day" || low == "reset") "" else v.take(40)
                return Change.LOOK
            }
            "text_size", "font_size", "text" -> {
                prefs.textScale = when {
                    low.startsWith("small") || low.contains("smaller") -> 0.9f
                    low.startsWith("large") || low.startsWith("big") || low.contains("larger") -> 1.15f
                    else -> 1f
                }
                return Change.LOOK
            }
            "clock", "clock_format", "time_format" -> {
                prefs.clockMode = when {
                    low.contains("24") -> "24h"
                    low.contains("12") -> "12h"
                    else -> "auto"
                }
                return Change.LOOK
            }
            "temperature", "units", "temperature_unit" -> {
                prefs.fahrenheit = !(low.startsWith("c"))
                return Change.WEATHER
            }
            "weather_location", "location" -> {
                val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(v).map { it.value.toFloat() }.toList()
                if (nums.size < 2 || nums[0] !in -90f..90f || nums[1] !in -180f..180f) {
                    problems.add("I couldn't read that location.")
                    return Change.NONE
                }
                prefs.latitude = nums[0]
                prefs.longitude = nums[1]
                return Change.WEATHER
            }
            "quick_actions_side", "actions_side", "side" -> {
                prefs.actionsLeft = low.startsWith("left")
                return Change.LOOK
            }
            else -> {
                problems.add("I can't change \"${key.replace('_', ' ')}\" from here yet.")
                return Change.NONE
            }
        }
    }

    private fun section(value: String): String? {
        val k = value.lowercase().filter { it.isLetter() }
        return when {
            k.contains("quick") || k == "actions" -> "quick_actions"
            k.contains("suggest") || k.contains("card") -> "suggestions"
            k.contains("weather") -> "weather"
            k.contains("app") || k.contains("homerow") -> "apps"
            else -> null
        }
    }

    /** Named color or #RRGGBB, rejected if too dark to read on the dark background. */
    fun parseColor(value: String): Int? {
        val v = value.trim().lowercase()
        NAMED_COLORS[v]?.let { return Ui.ACCENTS[it] }
        val hex = Regex("#?([0-9a-f]{6})").find(v)?.groupValues?.get(1) ?: return null
        val rgb = hex.toLong(16).toInt()
        val color = rgb or (0xFF shl 24)
        return if (luminance(color) >= 0.3) color else null
    }

    private fun luminance(color: Int): Double {
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = ch((color shr 16) and 0xFF)
        val g = ch((color shr 8) and 0xFF)
        val b = ch(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun colorName(color: Int): String = when (color) {
        Ui.ACCENTS[0] -> "cyan"
        Ui.ACCENTS[1] -> "periwinkle"
        Ui.ACCENTS[2] -> "amber"
        Ui.ACCENTS[3] -> "mint"
        else -> String.format("#%06X", color and 0xFFFFFF)
    }

    /** What the screen looks like right now, so Nova can make relative changes. */
    fun describeState(prefs: Prefs, apps: List<AppEntry>): String {
        val favs = AppRepo.favorites(prefs, apps).joinToString(", ") { it.label }
        val size = when {
            prefs.textScale < 0.95f -> "small"
            prefs.textScale > 1.05f -> "large"
            else -> "normal"
        }
        fun shown(b: Boolean) = if (b) "shown" else "hidden"
        return "Current home screen: accent=${colorName(prefs.accent)}; " +
            "assistant_name=${prefs.assistantName}; user_name=${prefs.userName.ifBlank { "(none)" }}; " +
            "greeting=${prefs.greeting.ifBlank { "time of day" }}; text_size=$size; clock=${prefs.clockMode}; " +
            "temperature=${if (prefs.fahrenheit) "F" else "C"}; " +
            "weather_location=${prefs.latitude}, ${prefs.longitude}; " +
            "quick_actions=${shown(prefs.showQuickActions)} on the ${if (prefs.actionsLeft) "left" else "right"}; " +
            "suggestions=${shown(prefs.showSuggestions)}; weather=${shown(prefs.showWeather)}; " +
            "apps row=${shown(prefs.showApps)}. Home row apps in order (max ${AppRepo.MAX_FAVORITES}): $favs."
    }

    fun instructions(owner: String): String =
        "You can also change this home screen. When the user asks for a change, reply in one short " +
            "sentence saying what you changed, then put each command on its own line at the very end, " +
            "written exactly like these (command words in capitals):\n" +
            "SET accent: cyan | periwinkle | amber | mint | a bright hex color like #FF7AB6\n" +
            "SET assistant_name: <name>\n" +
            "SET user_name: <name>\n" +
            "SET greeting: <short greeting, or default>\n" +
            "SET text_size: small | normal | large\n" +
            "SET clock: auto | 12h | 24h\n" +
            "SET temperature: F | C\n" +
            "SET weather_location: <latitude>, <longitude> (use the coordinates of the place they name)\n" +
            "SET quick_actions_side: left | right\n" +
            "SHOW: quick_actions | suggestions | weather | apps\n" +
            "HIDE: quick_actions | suggestions | weather | apps\n" +
            "PIN: <installed app name>\n" +
            "UNPIN: <installed app name>\n" +
            "ORDER: <app>, <app>, ... (the full home row in the new order)\n" +
            "RESET: look (back to the original design)\n" +
            "OPEN: <installed app name>\n" +
            "Only use commands when the user asks for that change or to open an app. " +
            "If they ask for something not on this list, like a new layout, a new feature or a different " +
            "background, say you can't do that from the TV yet and that $owner can ask Claude to add it to the app."
}
