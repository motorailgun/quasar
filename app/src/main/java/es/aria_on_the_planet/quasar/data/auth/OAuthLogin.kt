package es.aria_on_the_planet.quasar.data.auth

import android.util.Log
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.data.account.AccountStore
import es.aria_on_the_planet.quasar.data.account.ReloginResult
import es.aria_on_the_planet.quasar.data.account.StoredAccount
import es.aria_on_the_planet.quasar.data.account.StoredAccounts
import es.aria_on_the_planet.quasar.data.account.StoredProfile
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.session.SecureStorage
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

    /** [account] は文言に入れるアカウント (`@acct@host` かサーバのホスト。要らないエラーでは `null`)。 */
    data class Failed(val error: LoginError, val account: String? = null) : LoginStatus
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

    /**
     * 足すときに、同じサーバの ID の無い (以前の保存から移した) アカウントのトークンが無効 (401 / 422) で、同じアカウントか
     * 確かめられない。そのアカウントをログインし直すかログアウトしてもらう。
     */
    ExistingAccountNeedsLogin,

    /** 同上で、そのアカウントが今サーバで使えない (403: 無効・凍結・メール未確認・承認待ち)。 */
    ExistingAccountUnavailable,

    /** ログインし直したのが別のアカウントだった (保存しているものはそのまま)。 */
    DifferentAccount,

    /** ログインし直したアカウントは、既に別に保存している。 */
    AlreadyAdded,
}

/**
 * OAuth でのログイン (認可コード + PKCE) とログアウト。
 *
 * 1. [begin]: `POST /api/v1/apps` でクライアントを登録し、認可画面の URL を返す (画面がブラウザで開く)。
 *    途中の状態 ([PendingAuthorization]) は [SecureStorage] に保存する (ブラウザにいる間にプロセスが終わってもよい)。
 * 2. [handleRedirect]: 戻ってきた URI (MainActivity が受ける) のコードを `POST /oauth/token` でトークンに換え、
 *    `verify_credentials` でアカウント (ID・表示用の情報) を確かめて [AccountStore] に足し、使うアカウントにする。
 *    同じアカウント (サーバ + ID) が既にあれば足さずにトークンを差し替える。使うアカウントが変わると `AccountManager` が
 *    切り替え、画面がそのアカウントのタイムラインから作り直される。ログイン中のアカウントの追加も同じ流れ
 *    (途中の状態は 1 つだけ)。
 */
class OAuthLogin(
    private val api: MastodonApi,
    private val storage: SecureStorage,
    private val accountStore: AccountStore,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<LoginStatus>(
        loadPending()?.let { LoginStatus.InBrowser(it.serverUrl) } ?: LoginStatus.Idle,
    )
    val status: StateFlow<LoginStatus> = _status.asStateFlow()

    /**
     * クライアントを登録して認可画面の URL を返す。失敗したら `null` ([status] が [LoginStatus.Failed])。
     *
     * @param reloginKey 保存しているアカウントにログインし直すなら、そのキー (同じキーのままトークンを差し替える)。
     */
    suspend fun begin(serverUrl: String, reloginKey: AccountKey? = null): String? {
        _status.value = LoginStatus.Registering
        return try {
            val app = api.registerApp(serverUrl, OAuthConfig.ClientName, OAuthConfig.RedirectUri, OAuthConfig.Scopes)
            val pending = PendingAuthorization(
                serverUrl = serverUrl,
                client = OAuthClient(app.clientId, app.clientSecret),
                codeVerifier = randomUrlSafeString(),
                state = randomUrlSafeString(16),
                reloginKey = reloginKey,
            )
            storage.put(PendingKey, json.encodeToString(pending))
            _status.value = LoginStatus.InBrowser(serverUrl)
            // アカウントの追加・ログインし直しでは、ブラウザが (別のユーザーで) ログインしていてもログインの画面を出させる
            authorizeUrl(pending, forceLogin = reloginKey != null || accountStore.state.value.accounts.isNotEmpty())
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
            // 同じアカウントでのログインし直しを見分けるのに ID が要る。確かめられなければログインしない (ID の無いアカウントを
            // 足すと、同じアカウントが 2 つ並びうる)。得たトークンは無効にしておく
            val me = try {
                api.verifyCredentials(pending.serverUrl, token.accessToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Verifying the new account failed: ${e.failureKind()}")
                revoke(pending.serverUrl, pending.client, token.accessToken)
                _status.value = LoginStatus.Failed(e.toLoginError())
                return
            }
            val profile = StoredProfile(me.toDomain())
            if (pending.reloginKey != null) {
                val result = accountStore.relogin(
                    pending.reloginKey, pending.serverUrl, token.accessToken, pending.client, me.id, profile,
                )
                val error = when (result) {
                    is ReloginResult.Replaced -> {
                        // 古いトークンは無効にする (無効になっていてログインし直したなら失敗するだけ)
                        result.replaced.takeIf { it.accessToken != token.accessToken }?.let(::revoke)
                        _status.value = LoginStatus.Idle
                        return
                    }
                    ReloginResult.DifferentAccount -> LoginError.DifferentAccount
                    ReloginResult.AlreadyAdded -> LoginError.AlreadyAdded
                    // 認可の間にログアウトした。新しく足すのと同じに扱う
                    ReloginResult.Missing -> null
                }
                if (error != null) {
                    revoke(pending.serverUrl, pending.client, token.accessToken)
                    val target = accountStore.state.value.find(pending.reloginKey)
                    _status.value = LoginStatus.Failed(error, target?.label)
                    return
                }
            }
            // 同じサーバの ID の無いアカウント (以前の保存から移したもの) の ID を先に埋める。でないと同じアカウントか分からない
            resolveMissingIds(pending.serverUrl)?.let { failed ->
                revoke(pending.serverUrl, pending.client, token.accessToken)
                _status.value = failed
                return
            }
            val result = accountStore.addLogin(
                serverUrl = pending.serverUrl,
                accessToken = token.accessToken,
                client = pending.client,
                accountId = me.id,
                profile = profile,
            )
            // 同じアカウントのトークンを差し替えたら、古いトークンは無効にする
            result.replaced?.takeIf { it.accessToken != token.accessToken }?.let(::revoke)
            _status.value = LoginStatus.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Token exchange failed: ${e.failureKind()}")
            _status.value = LoginStatus.Failed(e.toLoginError())
        }
    }

    /**
     * [serverUrl] の ID の無いアカウント ([StoredAccounts.withoutIdOn]) の ID を、それぞれのトークンで `verify_credentials` を呼んで
     * 埋める。埋められなければログインを失敗にする状態を返す (同じアカウントか分からないまま足すと、2 つ並びうる)。
     * **保存しているアカウントは自動では除かない** (除くと設定も消える): トークンが無効 (401 / 422)・今は使えない (403) なら、
     * そのアカウントをログインし直すかログアウトしてもらう ([storedTokenError])。通信・サーバの失敗は後でもう一度。
     */
    private suspend fun resolveMissingIds(serverUrl: String): LoginStatus.Failed? {
        for (account in accountStore.state.value.withoutIdOn(serverUrl)) {
            try {
                val dto = api.verifyCredentials(account.serverUrl, account.accessToken)
                accountStore.updateProfile(account.key, dto.id, StoredProfile(dto.toDomain()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Resolving a stored account failed: ${e.failureKind()}")
                val error = if (e is MastodonApiException) storedTokenError(e.code) else LoginError.Network
                return LoginStatus.Failed(error, account.label)
            }
        }
        return null
    }

    /**
     * ログアウト: アカウント [key] を一覧から除き、サーバ側のトークンも無効にする (失敗してもログアウトはする)。
     * 使っていたアカウントなら残りの先頭に切り替わる (無ければログアウト状態)。そのアカウントの依存を閉じてキャッシュ・
     * 設定のファイルを消すのは `AccountManager` (一覧から消えたのを見て行う)。
     */
    fun logout(key: AccountKey) {
        accountStore.remove(key)?.let(::revoke)
    }

    /** [account] のトークンを無効にする (待たない。失敗はログのみ)。 */
    private fun revoke(account: StoredAccount) {
        account.client?.let { revoke(account.serverUrl, it, account.accessToken) }
    }

    private fun revoke(serverUrl: String, client: OAuthClient, accessToken: String) {
        scope.launch {
            try {
                api.revokeToken(serverUrl, client.clientId, client.clientSecret, accessToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Token revocation failed", e)
            }
        }
    }

    private fun loadPending(): PendingAuthorization? =
        storage.get(PendingKey)?.let { runCatching { json.decodeFromString<PendingAuthorization>(it) }.getOrNull() }

    /** ログに出す失敗の種類 (HTTP の状態コードか例外のクラス名。応答の本文・トークンは出さない)。 */
    private fun Exception.failureKind(): String =
        if (this is MastodonApiException) "HTTP $code" else javaClass.simpleName

    /** HTTP のエラー応答はサーバが断った、それ以外 (繋がらない・応答が読めない) は通信の問題として出す。 */
    private fun Exception.toLoginError(): LoginError =
        if (this is MastodonApiException) LoginError.Rejected else LoginError.Network

    private companion object {
        const val TAG = "OAuthLogin"
        const val PendingKey = "pending_authorization"
    }
}
