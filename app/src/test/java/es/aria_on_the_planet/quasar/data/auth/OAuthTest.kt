package es.aria_on_the_planet.quasar.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthTest {
    @Test
    fun codeChallengeIsBase64UrlSha256() {
        // base64url(SHA-256(verifier)) をパディング無しで (Python の hashlib で求めた値)
        assertEquals(
            "oudnMYE-9jVh4rs8sYKk-ln_ZK7JYDWkIkYfmwnwrVY",
            codeChallengeOf("dBjftJeZ4CVP-mJ92K1ahHmXWj6ZsqYWDl4wTn9N0Ks"),
        )
    }

    @Test
    fun randomStringsAreUrlSafeAndDistinct() {
        val a = randomUrlSafeString()
        val b = randomUrlSafeString()
        assertNotEquals(a, b)
        // 32 バイト → パディング無しの base64url で 43 文字 (code_verifier は 43〜128 文字)
        assertEquals(43, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun authorizeUrlCarriesAllParameters() {
        val pending = PendingAuthorization(
            serverUrl = "https://example.invalid",
            client = OAuthClient("id", "secret"),
            codeVerifier = "dBjftJeZ4CVP-mJ92K1ahHmXWj6ZsqYWDl4wTn9N0Ks",
            state = "st",
        )
        val url = authorizeUrl(pending).toHttpUrl()
        assertEquals("/oauth/authorize", url.encodedPath)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("id", url.queryParameter("client_id"))
        assertEquals(OAuthConfig.RedirectUri, url.queryParameter("redirect_uri"))
        assertEquals(OAuthConfig.Scopes, url.queryParameter("scope"))
        assertEquals("st", url.queryParameter("state"))
        assertEquals("oudnMYE-9jVh4rs8sYKk-ln_ZK7JYDWkIkYfmwnwrVY", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        // client_secret はブラウザに渡さない
        assertFalse("secret" in url.toString())
        // 最初のログインではブラウザのログインをそのまま使う
        assertEquals(null, url.queryParameter("force_login"))
        // アカウントの追加・ログインし直しではログインの画面を出させる
        assertEquals("true", authorizeUrl(pending, forceLogin = true).toHttpUrl().queryParameter("force_login"))
    }

    @Test
    fun recognizesRedirect() {
        assertTrue(isAuthorizationRedirect("${OAuthConfig.RedirectUri}?code=abc&state=s"))
        assertFalse(isAuthorizationRedirect("es.aria-on-the-planet.quasar://other?code=abc"))
        assertFalse(isAuthorizationRedirect("https://oauth/?code=abc"))
        assertFalse(isAuthorizationRedirect("not a uri"))
    }

    @Test
    fun parsesCode() {
        assertEquals(
            AuthorizationResult.Code("a b/c"),
            parseAuthorizationRedirect("${OAuthConfig.RedirectUri}?code=a+b%2Fc&state=s1", "s1"),
        )
    }

    @Test
    fun rejectsMismatchedOrMissingState() {
        assertEquals(
            AuthorizationResult.Invalid,
            parseAuthorizationRedirect("${OAuthConfig.RedirectUri}?code=abc&state=other", "s1"),
        )
        assertEquals(AuthorizationResult.Invalid, parseAuthorizationRedirect("${OAuthConfig.RedirectUri}?code=abc", "s1"))
        assertEquals(AuthorizationResult.Invalid, parseAuthorizationRedirect("${OAuthConfig.RedirectUri}?state=s1", "s1"))
    }

    @Test
    fun parsesError() {
        assertEquals(
            AuthorizationResult.Error("access_denied"),
            parseAuthorizationRedirect("${OAuthConfig.RedirectUri}?error=access_denied&error_description=x&state=s1", "s1"),
        )
    }

    @Test
    fun storedTokenFailuresAreToldApart() {
        // トークンが無効 (401)・ユーザーが無い (422) はログインし直し、403 (無効・凍結・承認待ち等) は今は使えない
        assertEquals(LoginError.ExistingAccountNeedsLogin, storedTokenError(401))
        assertEquals(LoginError.ExistingAccountNeedsLogin, storedTokenError(422))
        assertEquals(LoginError.ExistingAccountUnavailable, storedTokenError(403))
        listOf(404, 429, 500, 502, 503).forEach { assertEquals(LoginError.Rejected, storedTokenError(it)) }
    }
}
