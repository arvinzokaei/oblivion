package org.bepass.oblivion.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the settings that were used the *last* time the user pressed
 * "Connect" inside the app. The Quick Settings tile lives outside the
 * Flutter UI, so when the user taps the tile with the app fully closed,
 * this is where it gets the connection settings from instead.
 *
 * Nothing here is new user data — it is exactly the same "settings" map
 * and "arguments" list that OblivionPlugin.handleConnect() already
 * receives from the Flutter side on every connect call. We just also
 * write it to a small private SharedPreferences file so it survives
 * after the app is killed.
 */
object TunnelPrefs {
    private const val PREFS_NAME = "oblivion_tunnel_prefs"
    private const val KEY_SETTINGS = "last_settings_json"
    private const val KEY_ARGUMENTS = "last_arguments_json"

    fun save(context: Context, settings: Map<String, Any?>, arguments: List<String>) {
        val settingsJson = JSONObject()
        for ((key, value) in settings) {
            when (value) {
                null -> Unit
                is List<*> -> settingsJson.put(key, JSONArray(value))
                else -> settingsJson.put(key, value)
            }
        }

        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SETTINGS, settingsJson.toString())
                .putString(KEY_ARGUMENTS, JSONArray(arguments).toString())
                .apply()
        }
    }

    /** Returns the last-used config, or null if the user never connected yet. */
    fun load(context: Context): TunnelConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsRaw = prefs.getString(KEY_SETTINGS, null) ?: return null
        val argumentsRaw = prefs.getString(KEY_ARGUMENTS, null)

        val settings = runCatching { toMap(JSONObject(settingsRaw)) }.getOrNull() ?: return null
        val arguments = runCatching {
            argumentsRaw?.let { toStringList(JSONArray(it)) } ?: emptyList()
        }.getOrDefault(emptyList())

        return runCatching { TunnelConfig.fromMap(settings, arguments) }.getOrNull()
    }

    fun hasSavedConfig(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_SETTINGS)

    private fun toMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = if (value is JSONArray) toStringList(value) else value
        }
        return map
    }

    private fun toStringList(array: JSONArray): List<String> =
        (0 until array.length()).map { array.getString(it) }
}
