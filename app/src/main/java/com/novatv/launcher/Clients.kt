package com.novatv.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class AiException(message: String) : Exception(message)

/** Talks to the Claude API. Call from a background thread. */
class AiClient(private val prefs: Prefs) {
    data class Msg(val role: String, val text: String)

    fun ask(history: List<Msg>, system: String): String {
        val key = prefs.apiKey.trim()
        if (key.isEmpty()) {
            throw AiException("Add your Claude API key in Launcher settings to start chatting.")
        }

        val messages = JSONArray()
        for (m in history) {
            messages.put(JSONObject().put("role", m.role).put("content", m.text))
        }
        val body = JSONObject()
            .put("model", prefs.model)
            .put("max_tokens", 500)
            .put("system", system)
            .put("messages", messages)

        val conn = try {
            URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("Couldn't reach the AI. Check the TV's internet connection.")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                val detail = try {
                    JSONObject(raw).getJSONObject("error").optString("message")
                } catch (e: Exception) {
                    ""
                }
                throw AiException(
                    when (code) {
                        401 -> "That API key didn't work. Re-enter it in Launcher settings."
                        404 -> "The AI model name isn't recognized. Check it in Launcher settings."
                        429 -> "Too many requests right now. Try again in a minute."
                        529, 503 -> "The AI is busy right now. Try again in a minute."
                        else -> "AI error $code. $detail".trim()
                    }
                )
            }

            val content = JSONObject(raw).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            return sb.toString().trim()
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("Couldn't reach the AI. Check the TV's internet connection.")
        } finally {
            conn.disconnect()
        }
    }
}

data class Weather(val temp: Int, val code: Int, val isDay: Boolean)

/** Current conditions from Open-Meteo (free, no key). Call from a background thread. */
object WeatherClient {
    fun fetch(lat: Float, lon: Float, fahrenheit: Boolean): Weather? {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&temperature_unit=$unit"
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(raw).getJSONObject("current")
            Weather(
                cur.getDouble("temperature_2m").roundToInt(),
                cur.optInt("weather_code", 0),
                cur.optInt("is_day", 1) == 1
            )
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Storms"
        else -> ""
    }

    fun icon(w: Weather): Icon = when (w.code) {
        0, 1 -> if (w.isDay) Icon.SUN else Icon.MOON
        in 51..67, in 80..82, in 95..99 -> Icon.RAIN
        else -> Icon.CLOUD
    }
}
