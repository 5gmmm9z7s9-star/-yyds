package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rhythm_music_prefs")

data class PlayerPreferences(
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_ALL,
    val shuffleEnabled: Boolean = false,
    val lastPlayedUri: String? = null,
    val darkTheme: Boolean = true,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val LAST_PLAYED_URI = stringPreferencesKey("last_played_uri")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferences: Flow<PlayerPreferences> = context.dataStore.data.map { prefs ->
        PlayerPreferences(
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 3.0f),
            repeatMode = prefs[Keys.REPEAT_MODE] ?: Player.REPEAT_MODE_ALL,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            lastPlayedUri = prefs[Keys.LAST_PLAYED_URI],
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
        )
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 3.0f)
        }
    }

    suspend fun setRepeatMode(repeatMode: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = repeatMode
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setLastPlayedUri(uriString: String?) {
        context.dataStore.edit { prefs ->
            if (uriString == null) {
                prefs.remove(Keys.LAST_PLAYED_URI)
            } else {
                prefs[Keys.LAST_PLAYED_URI] = uriString
            }
        }
    }

    suspend fun setDarkTheme(darkTheme: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = darkTheme
        }
    }
}
