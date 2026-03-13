package top.khyan.glance.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** SharedPreferences-backed store for [Glimpse] instances. */
class GlimpseStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Glimpse> {
        val json = prefs.getString(KEY_GLIMPSES, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Insert or replace a Glimpse by id. */
    fun save(glimpse: Glimpse) {
        val current = getAll().toMutableList()
        val idx = current.indexOfFirst { it.id == glimpse.id }
        if (idx >= 0) current[idx] = glimpse else current.add(glimpse)
        persist(current)
    }

    fun delete(id: Int) {
        persist(getAll().filter { it.id != id })
    }

    fun clear() {
        persist(emptyList())
    }

    /** Returns the smallest integer > 1000 not already used as an id. */
    fun nextAvailableId(): Int {
        val ids = getAll().map { it.id }.toSet()
        var candidate = 1001
        while (candidate in ids) candidate++
        return candidate
    }

    private fun persist(list: List<Glimpse>) {
        prefs.edit().putString(KEY_GLIMPSES, Json.encodeToString(list)).apply()
    }

    fun registerChangeListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_GLIMPSES) {
                onChanged()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "glimpse_store"
        private const val KEY_GLIMPSES = "glimpses"
    }
}

