package es.aria_on_the_planet.quasar.data.session

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val serverUrl: String,
    val accessToken: String?,
)

/** 接続先サーバとアクセストークンを保持する。OAuth 実装までの暫定。 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun save(session: Session) {
        prefs.edit {
            putString(KEY_SERVER_URL, session.serverUrl)
            putString(KEY_ACCESS_TOKEN, session.accessToken)
        }
        _session.value = session
    }

    fun clear() {
        prefs.edit { clear() }
        _session.value = null
    }

    private fun load(): Session? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        return Session(serverUrl, prefs.getString(KEY_ACCESS_TOKEN, null))
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
    }
}
