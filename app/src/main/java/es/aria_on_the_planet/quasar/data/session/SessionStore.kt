package es.aria_on_the_planet.quasar.data.session

import android.content.Context
import es.aria_on_the_planet.quasar.data.auth.OAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** ログイン中のセッション。ログインしていなければ [SessionStore.session] が `null` になる (トークンの無いセッションは無い)。 */
data class Session(
    val serverUrl: String,
    val accessToken: String,
)

/**
 * 保存する形。[client] はトークンを発行したクライアント (ログアウト時の無効化用。手入力のトークンの頃のものは無い)。
 * [accessToken] が `null` のもの (最初期の、サーバだけを指定した未ログインでの利用) はログアウト状態として扱う。
 */
@Serializable
private data class StoredSession(
    val serverUrl: String,
    val accessToken: String?,
    val client: OAuthClient? = null,
)

/** 接続先サーバとアクセストークンを [SecureStorage] (Keystore で暗号化) に保持する。 */
class SessionStore(context: Context, private val storage: SecureStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    private var stored: StoredSession? = load(context)

    private val _session = MutableStateFlow(stored?.toSession())
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** 今のセッションのトークンを発行したクライアント。 */
    val client: OAuthClient? get() = stored?.client

    fun save(session: Session, client: OAuthClient) {
        val value = StoredSession(session.serverUrl, session.accessToken, client)
        storage.put(Key, json.encodeToString(value))
        stored = value
        _session.value = session
    }

    fun clear() {
        storage.remove(Key)
        stored = null
        _session.value = null
    }

    private fun load(context: Context): StoredSession? =
        storage.get(Key)?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }
            ?: migrateLegacy(context)

    /** 以前の平文の保存 (SharedPreferences `session`) があれば暗号化して移し、元は消す。 */
    private fun migrateLegacy(context: Context): StoredSession? {
        val prefs = context.getSharedPreferences(LegacyPrefsName, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", null)
        val migrated = serverUrl?.let { StoredSession(it, prefs.getString("access_token", null)) }
        migrated?.let { storage.put(Key, json.encodeToString(it)) }
        context.deleteSharedPreferences(LegacyPrefsName)
        return migrated
    }

    private fun StoredSession.toSession() = accessToken?.let { Session(serverUrl, it) }

    private companion object {
        const val Key = "session"
        const val LegacyPrefsName = "session"
    }
}
