package com.novatv.launcher

import android.content.Context
import android.content.SharedPreferences

/** Everything the user can change in Settings, saved on the TV. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString(K_USER, "Carter") ?: "Carter"
        set(value) {
            sp.edit().putString(K_USER, value.trim()).apply()
        }

    var assistantName: String
        get() = (sp.getString(K_ASSISTANT, "Nova") ?: "Nova").ifBlank { "Nova" }
        set(value) {
            sp.edit().putString(K_ASSISTANT, value.trim()).apply()
        }

    var apiKey: String
        get() = sp.getString(K_KEY, "") ?: ""
        set(value) {
            sp.edit().putString(K_KEY, value.trim()).apply()
        }

    var model: String
        get() = (sp.getString(K_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
        set(value) {
            sp.edit().putString(K_MODEL, value.trim()).apply()
        }

    var latitude: Float
        get() = sp.getFloat(K_LAT, 34.05f)
        set(value) {
            sp.edit().putFloat(K_LAT, value).apply()
        }

    var longitude: Float
        get() = sp.getFloat(K_LON, -118.24f)
        set(value) {
            sp.edit().putFloat(K_LON, value).apply()
        }

    var fahrenheit: Boolean
        get() = sp.getBoolean(K_FAHRENHEIT, true)
        set(value) {
            sp.edit().putBoolean(K_FAHRENHEIT, value).apply()
        }

    var accent: Int
        get() = sp.getInt(K_ACCENT, Ui.ACCENTS[0])
        set(value) {
            sp.edit().putInt(K_ACCENT, value).apply()
        }

    // ---- Layout (Nova can change these when you ask, or use Settings)

    var showQuickActions: Boolean
        get() = sp.getBoolean(K_SHOW_ACTIONS, true)
        set(value) {
            sp.edit().putBoolean(K_SHOW_ACTIONS, value).apply()
        }

    var showSuggestions: Boolean
        get() = sp.getBoolean(K_SHOW_CARDS, true)
        set(value) {
            sp.edit().putBoolean(K_SHOW_CARDS, value).apply()
        }

    var showWeather: Boolean
        get() = sp.getBoolean(K_SHOW_WEATHER, true)
        set(value) {
            sp.edit().putBoolean(K_SHOW_WEATHER, value).apply()
        }

    var showApps: Boolean
        get() = sp.getBoolean(K_SHOW_APPS, true)
        set(value) {
            sp.edit().putBoolean(K_SHOW_APPS, value).apply()
        }

    /** True puts quick actions on the left of the assistant. */
    var actionsLeft: Boolean
        get() = sp.getBoolean(K_ACTIONS_LEFT, false)
        set(value) {
            sp.edit().putBoolean(K_ACTIONS_LEFT, value).apply()
        }

    /** 0.9 small, 1.0 normal, 1.15 large. */
    var textScale: Float
        get() = sp.getFloat(K_TEXT_SCALE, 1f)
        set(value) {
            sp.edit().putFloat(K_TEXT_SCALE, value).apply()
        }

    /** "auto", "12h" or "24h". */
    var clockMode: String
        get() = sp.getString(K_CLOCK, "auto") ?: "auto"
        set(value) {
            sp.edit().putString(K_CLOCK, value).apply()
        }

    /** Replaces "Good evening" when set. */
    var greeting: String
        get() = sp.getString(K_GREETING, "") ?: ""
        set(value) {
            sp.edit().putString(K_GREETING, value.trim()).apply()
        }

    /** One of Backgrounds.STYLES. */
    var background: String
        get() = sp.getString(K_BACKGROUND, "mountains") ?: "mountains"
        set(value) {
            sp.edit().putString(K_BACKGROUND, value).apply()
        }

    /** The link the photo background was downloaded from. */
    var backgroundUrl: String
        get() = sp.getString(K_BACKGROUND_URL, "") ?: ""
        set(value) {
            sp.edit().putString(K_BACKGROUND_URL, value.trim()).apply()
        }

    fun resetLook() {
        sp.edit()
            .remove(K_BACKGROUND)
            .remove(K_ACCENT)
            .remove(K_SHOW_ACTIONS)
            .remove(K_SHOW_CARDS)
            .remove(K_SHOW_WEATHER)
            .remove(K_SHOW_APPS)
            .remove(K_ACTIONS_LEFT)
            .remove(K_TEXT_SCALE)
            .remove(K_CLOCK)
            .remove(K_GREETING)
            .apply()
    }

    /** Package names pinned to the home row, in order. Null until the user edits it. */
    var favorites: List<String>?
        get() {
            val raw = sp.getString(K_FAVORITES, null) ?: return null
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) {
            if (value == null) {
                sp.edit().remove(K_FAVORITES).apply()
            } else {
                sp.edit().putString(K_FAVORITES, value.joinToString(",")).apply()
            }
        }

    /** Goes up each time Settings is saved, so the home screen knows to rebuild. */
    val revision: Int
        get() = sp.getInt(K_REVISION, 0)

    fun bumpRevision() {
        sp.edit().putInt(K_REVISION, revision + 1).apply()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
        private const val K_USER = "user_name"
        private const val K_ASSISTANT = "assistant_name"
        private const val K_KEY = "api_key"
        private const val K_MODEL = "model"
        private const val K_LAT = "lat"
        private const val K_LON = "lon"
        private const val K_FAHRENHEIT = "fahrenheit"
        private const val K_ACCENT = "accent"
        private const val K_FAVORITES = "favorites"
        private const val K_REVISION = "revision"
        private const val K_SHOW_ACTIONS = "show_actions"
        private const val K_SHOW_CARDS = "show_cards"
        private const val K_SHOW_WEATHER = "show_weather"
        private const val K_SHOW_APPS = "show_apps"
        private const val K_ACTIONS_LEFT = "actions_left"
        private const val K_TEXT_SCALE = "text_scale"
        private const val K_CLOCK = "clock"
        private const val K_GREETING = "greeting"
        private const val K_BACKGROUND = "background"
        private const val K_BACKGROUND_URL = "background_url"
    }
}
