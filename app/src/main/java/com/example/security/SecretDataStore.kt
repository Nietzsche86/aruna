package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.arunaDataStore: DataStore<Preferences> by preferencesDataStore(name = "aruna_secrets_prefs")

class SecretDataStore(
    private val context: Context,
    private val cryptoManager: CryptoManager,
) {
    private fun apiKeyPref(providerId: String) = stringPreferencesKey("api_key_$providerId")

    val dynamicColorEnabled: Flow<Boolean> = context.arunaDataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    val themeMode: Flow<ThemeMode> = context.arunaDataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val activeProviderId: Flow<String?> = context.arunaDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROVIDER_ID]
    }

    val activeConversationId: Flow<String?> = context.arunaDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_CONVERSATION_ID]
    }

    val contextWindowTokens: Flow<Int> = context.arunaDataStore.data.map { prefs ->
        prefs[KEY_CONTEXT_WINDOW_TOKENS] ?: DEFAULT_CONTEXT_WINDOW
    }

    suspend fun saveApiKey(providerId: String, plainApiKey: String) {
        val encrypted = if (plainApiKey.isBlank()) "" else cryptoManager.encrypt(plainApiKey.trim())
        context.arunaDataStore.edit { prefs ->
            if (encrypted.isEmpty()) {
                prefs.remove(apiKeyPref(providerId))
            } else {
                prefs[apiKeyPref(providerId)] = encrypted
            }
        }
    }

    suspend fun getApiKey(providerId: String): String {
        val prefs = context.arunaDataStore.data.first()
        val encrypted = prefs[apiKeyPref(providerId)] ?: return ""
        return runCatching { cryptoManager.decrypt(encrypted) }.getOrDefault("")
    }

    suspend fun hasApiKey(providerId: String): Boolean = getApiKey(providerId).isNotBlank()

    suspend fun deleteApiKey(providerId: String) {
        context.arunaDataStore.edit { prefs ->
            prefs.remove(apiKeyPref(providerId))
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.arunaDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.arunaDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setActiveProviderId(providerId: String?) {
        context.arunaDataStore.edit { prefs ->
            if (providerId == null) prefs.remove(KEY_ACTIVE_PROVIDER_ID)
            else prefs[KEY_ACTIVE_PROVIDER_ID] = providerId
        }
    }

    suspend fun setActiveConversationId(conversationId: String?) {
        context.arunaDataStore.edit { prefs ->
            if (conversationId == null) prefs.remove(KEY_ACTIVE_CONVERSATION_ID)
            else prefs[KEY_ACTIVE_CONVERSATION_ID] = conversationId
        }
    }

    suspend fun setContextWindowTokens(tokens: Int) {
        context.arunaDataStore.edit { it[KEY_CONTEXT_WINDOW_TOKENS] = tokens.coerceIn(4096, 2000000) }
    }

    companion object {
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        private val KEY_ACTIVE_CONVERSATION_ID = stringPreferencesKey("active_conversation_id")
        private val KEY_CONTEXT_WINDOW_TOKENS = intPreferencesKey("context_window_tokens")
        const val DEFAULT_CONTEXT_WINDOW = 32768
    }
}
