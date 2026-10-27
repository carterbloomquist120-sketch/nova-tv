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
    }
}
