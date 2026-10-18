package es.aria_on_the_planet.quasar.data.auth

import es.aria_on_the_planet.quasar.BuildConfig
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/*
 * OAuth (認可コード + PKCE) のうち、通信も Android も使わない部分。https://docs.joinmastodon.org/spec/oauth/
 */

object OAuthConfig {
    /** `POST /api/v1/apps` の `client_name` (認可画面・ポストの「via」に出る)。 */
    const val ClientName = "Quasar"

    /**
     * 認可後に戻ってくる先。manifest の MainActivity の intent-filter と合わせる。スキームは他のアプリと
     * 被らないよう applicationId に由来する逆ドメイン (RFC 8252 7.1。スキームに `_` は使えないので `-`)。
     * debug / benchmark は別のアプリ (`.debug`) なのでスキームも別 (build.gradle.kts の `oauthRedirectScheme`)。
     */
    const val RedirectUri = "${BuildConfig.OAUTH_REDIRECT_SCHEME}://oauth"

    /** `push` は今は使わないが、Web Push の対応時に再ログインを求めずに済むよう含めておく。 */
    const val Scopes = "read write push"
}

/** 登録したクライアント。ログアウト時のトークンの無効化にも使うので、セッションと一緒に保存する。 */
@Serializable
data class OAuthClient(val clientId: String, val clientSecret: String)

/** ブラウザで認可している間の状態。ブラウザにいる間にプロセスが終わっても続けられるよう保存する。 */
@Serializable
data class PendingAuthorization(
    val serverUrl: String,
    val client: OAuthClient,
    val codeVerifier: String,
    val state: String,
)

private val random = SecureRandom()
private val base64Url = Base64.getUrlEncoder().withoutPadding()

/** PKCE の code_verifier / state 用のランダムな文字列 (base64url、[bytes] バイト分)。 */
fun randomUrlSafeString(bytes: Int = 32): String = base64Url.encodeToString(ByteArray(bytes).also(random::nextBytes))

/** PKCE の code_challenge (S256)。RFC 7636 4.2。 */
fun codeChallengeOf(verifier: String): String =
    base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

/** ブラウザで開く認可画面の URL。 */
fun authorizeUrl(pending: PendingAuthorization): String =
    pending.serverUrl.toHttpUrl().newBuilder()
        .addPathSegments("oauth/authorize")
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", pending.client.clientId)
        .addQueryParameter("redirect_uri", OAuthConfig.RedirectUri)
        .addQueryParameter("scope", OAuthConfig.Scopes)
        .addQueryParameter("state", pending.state)
        .addQueryParameter("code_challenge", codeChallengeOf(pending.codeVerifier))
        .addQueryParameter("code_challenge_method", "S256")
        .build()
        .toString()

/** 認可画面から戻ってきた URI の中身。 */
sealed interface AuthorizationResult {
    data class Code(val code: String) : AuthorizationResult

    /** ユーザーが拒否した (`error=access_denied`) 等、サーバが返したエラー。 */
    data class Error(val error: String) : AuthorizationResult

    /** state が合わない・コードが無い (別の認可の残り・偽の呼び出し)。 */
    data object Invalid : AuthorizationResult
}

/** [uri] が認可からの戻り先 ([OAuthConfig.RedirectUri]) か。 */
fun isAuthorizationRedirect(uri: String): Boolean {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return false
    val expected = URI(OAuthConfig.RedirectUri)
    return parsed.scheme.equals(expected.scheme, ignoreCase = true) && parsed.host == expected.host
}

/** 戻ってきた URI を読む。[expectedState] (送った state) と一致しなければ [AuthorizationResult.Invalid]。 */
fun parseAuthorizationRedirect(uri: String, expectedState: String): AuthorizationResult {
    val query = runCatching { URI(uri).rawQuery }.getOrNull().orEmpty()
    val params = query.split('&').filter { it.isNotEmpty() }.associate { pair ->
        val name = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        decode(name) to decode(value)
    }
    if (params["state"] != expectedState) return AuthorizationResult.Invalid
    params["error"]?.let { return AuthorizationResult.Error(it) }
    val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return AuthorizationResult.Invalid
    return AuthorizationResult.Code(code)
}

private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)
