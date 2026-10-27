package es.aria_on_the_planet.quasar.data.auth

import android.util.Log
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.session.SecureStorage
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** ログインの進み具合 (ログイン画面に出す)。 */
sealed interface LoginStatus {
    data object Idle : LoginStatus

    /** サーバにクライアントを登録している。 */
    data object Registering : LoginStatus

    /** ブラウザで認可してもらっている (戻ってくるのを待つ)。 */
    data class InBrowser(val serverUrl: String) : LoginStatus

    /** 認可コードをトークンに換えている。 */
    data object Exchanging : LoginStatus

    data class Failed(val error: LoginError) : LoginStatus
}

enum class LoginError {
    /** 繋がらない・応答が読めない。 */
    Network,

    /** サーバがクライアントの登録・トークンの発行を断った。 */
    Rejected,

    /** ユーザーが認可画面で拒否した。 */
    Denied,

    /** 戻ってきた URI がおかしい (state の不一致等)。 */
    InvalidResponse,

    /** 認可画面を開けるブラウザが無い。 */
    NoBrowser,
}

/**
 * OAuth でのログイン (認可コード + PKCE) とログアウト。
 *
 * 1. [begin]: `POST /api/v1/apps` でクライアントを登録し、認可画面の URL を返す (画面がブラウザで開く)。
 *    途中の状態 ([PendingAuthorization]) は [SecureStorage] に保存する (ブラウザにいる間にプロセスが終わってもよい)。
 * 2. [handleRedirect]: 戻ってきた URI (MainActivity が受ける) のコードを `POST /oauth/token` でトークンに換え、
 *    [SessionStore] に保存する。セッションができればナビゲーションがタイムラインに切り替わる。
 */
class OAuthLogin(
    private val api: MastodonApi,
    private val storage: SecureStorage,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<LoginStatus>(
        loadPending()?.let { LoginStatus.InBrowser(it.serverUrl) } ?: LoginStatus.Idle,
    )
    val status: StateFlow<LoginStatus> = _status.asStateFlow()

    /** クライアントを登録して認可画面の URL を返す。失敗したら `null` ([status] が [LoginStatus.Failed])。 */
    suspend fun begin(serverUrl: String): String? {
        _status.value = LoginStatus.Registering
        return try {
            val app = api.registerApp(serverUrl, OAuthConfig.ClientName, OAuthConfig.RedirectUri, OAuthConfig.Scopes)
            val pending = PendingAuthorization(
                serverUrl = serverUrl,
                client = OAuthClient(app.clientId, app.clientSecret),
                codeVerifier = randomUrlSafeString(),
                state = randomUrlSafeString(16),
            )
            storage.put(PendingKey, json.encodeToString(pending))
            _status.value = LoginStatus.InBrowser(serverUrl)
            authorizeUrl(pending)
        } catch (e: CancellationException) {
            _status.value = LoginStatus.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Client registration failed", e)
            _status.value = LoginStatus.Failed(e.toLoginError())
            null
        }
    }

    /** 画面側で起きた失敗 (ブラウザが無い等)。 */
    fun fail(error: LoginError) {
        storage.remove(PendingKey)
        _status.value = LoginStatus.Failed(error)
    }

    /** 進行中のログインをやめる。 */
    fun cancel() {
        storage.remove(PendingKey)
        _status.value = LoginStatus.Idle
    }

    /**
     * [uri] が認可からの戻りなら処理して `true`。進行中のログインが無い (処理済みの intent の再配達等) なら無視する。
     */
    fun handleRedirect(uri: String): Boolean {
        if (!isAuthorizationRedirect(uri)) return false
        val pending = loadPending() ?: return true
        // 同じ認可コードは 1 回しか使えないので、受けた時点で消す
        storage.remove(PendingKey)
        when (val result = parseAuthorizationRedirect(uri, pending.state)) {
            is AuthorizationResult.Code -> scope.launch { exchange(pending, result.code) }
            is AuthorizationResult.Error -> _status.value = LoginStatus.Failed(
                if (result.error == "access_denied") LoginError.Denied else LoginError.Rejected,
            )
            AuthorizationResult.Invalid -> _status.value = LoginStatus.Failed(LoginError.InvalidResponse)
        }
        return true
    }

    private suspend fun exchange(pending: PendingAuthorization, code: String) {
        _status.value = LoginStatus.Exchanging
        try {
            val token = api.obtainToken(
                serverUrl = pending.serverUrl,
                clientId = pending.client.clientId,
                clientSecret = pending.client.clientSecret,
                redirectUri = OAuthConfig.RedirectUri,
                code = code,
                codeVerifier = pending.codeVerifier,
                scopes = OAuthConfig.Scopes,
            )
            sessionStore.save(Session(pending.serverUrl, token.accessToken), pending.client)
            _status.value = LoginStatus.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Token exchange failed", e)
            _status.value = LoginStatus.Failed(e.toLoginError())
        }
    }

    /** ログアウト: 保存したセッションを消し、サーバ側のトークンも無効にする (失敗してもログアウトはする)。 */
    fun logout() {
        val session = sessionStore.session.value
        val client = sessionStore.client
        sessionStore.clear()
        if (session == null || client == null) return
        scope.launch {
            try {
                api.revokeToken(session.serverUrl, client.clientId, client.clientSecret, session.accessToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Token revocation failed", e)
            }
        }
    }

    private fun loadPending(): PendingAuthorization? =
        storage.get(PendingKey)?.let { runCatching { json.decodeFromString<PendingAuthorization>(it) }.getOrNull() }

    /** HTTP のエラー応答はサーバが断った、それ以外 (繋がらない・応答が読めない) は通信の問題として出す。 */
    private fun Exception.toLoginError(): LoginError =
        if (this is MastodonApiException) LoginError.Rejected else LoginError.Network

    private companion object {
        const val TAG = "OAuthLogin"
        const val PendingKey = "pending_authorization"
    }
}
