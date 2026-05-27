package com.example.fibraconet.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fibraconet.data.model.LoginCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fibraconet_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL  = stringPreferencesKey("server_url")
        private val KEY_USERNAME    = stringPreferencesKey("username")
        private val KEY_PASSWORD    = stringPreferencesKey("password")
        private val KEY_M3U_URL     = stringPreferencesKey("m3u_url")
        private val KEY_FAVORITES   = stringSetPreferencesKey("favorites")
        private val KEY_RECENTS     = stringPreferencesKey("recents")
    }

    // ── Credenciales ──────────────────────────────────────────────────
    suspend fun saveCredentials(credentials: LoginCredentials) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = credentials.serverUrl
            prefs[KEY_USERNAME]   = credentials.username
            prefs[KEY_PASSWORD]   = credentials.password
            prefs[KEY_M3U_URL]    = ""
        }
    }

    suspend fun saveM3UUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_M3U_URL]    = url
            prefs[KEY_SERVER_URL] = ""
            prefs[KEY_USERNAME]   = ""
            prefs[KEY_PASSWORD]   = ""
        }
    }

    suspend fun getSavedCredentials(): LoginCredentials? {
        val prefs = context.dataStore.data.first()
        val url  = prefs[KEY_SERVER_URL] ?: ""
        val user = prefs[KEY_USERNAME]   ?: ""
        val pass = prefs[KEY_PASSWORD]   ?: ""
        if (url.isBlank() || user.isBlank()) return null
        return LoginCredentials(url, user, pass)
    }

    suspend fun getSavedM3UUrl(): String? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_M3U_URL] ?: ""
        return url.ifBlank { null }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { it.clear() }
    }

    // ── Favoritos ─────────────────────────────────────────────────────
    fun getFavoritesFlow(): Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_FAVORITES] ?: emptySet() }

    suspend fun toggleFavorite(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES] ?: emptySet()
            prefs[KEY_FAVORITES] =
                if (current.contains(channelId)) current - channelId else current + channelId
        }
    }

    // ── Recientes ─────────────────────────────────────────────────────
    fun getRecentsFlow(): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            (prefs[KEY_RECENTS] ?: "").split(",").filter { it.isNotBlank() }
        }

    suspend fun addRecentChannel(channelId: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_RECENTS] ?: "").split(",").filter { it.isNotBlank() }
            val updated = (listOf(channelId) + current.filter { it != channelId }).take(12)
            prefs[KEY_RECENTS] = updated.joinToString(",")
        }
    }
}
