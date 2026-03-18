package edu.singaporetech.inf2007quiz01.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Top-level delegate so there's only one DataStore instance across the app. */
private val Context.dataStore by preferencesDataStore(name = "calbot_preferences")

/**
 * Wraps Preferences DataStore for per-CalBot settings.
 * Right now it just handles the API toggle, but easy to extend later.
 */
class PreferencesManager(private val context: Context) {

    /** Returns a Flow that emits the current toggle state — defaults to off. */
    fun getApiToggle(calBotId: Int): Flow<Boolean> {
        val key = booleanPreferencesKey("api_toggle_$calBotId")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    /** Persist the toggle state so it survives app restarts. */
    suspend fun setApiToggle(calBotId: Int, enabled: Boolean) {
        val key = booleanPreferencesKey("api_toggle_$calBotId")
        context.dataStore.edit { preferences ->
            preferences[key] = enabled
        }
    }
}
