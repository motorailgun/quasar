package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType.OnDevice
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType.Server
import es.aria_on_the_planet.quasar.domain.model.TranslationSettings
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.primaryLanguageOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TranslationRequestTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(
        language: String? = "ko",
        text: String = "<p>안녕</p>",
        visibility: PostVisibility = PostVisibility.Public,
        cw: String? = null,
    ) = Post("1", user, text, Instant.EPOCH, 0, 0, 0, emptyList(), language = language, visibility = visibility, contentWarning = cw)

    private val settings = TranslationSettings(onDeviceEnabled = true)
    private val both = TranslationEnvironment(
        loggedIn = true,
        server = ServerTranslationCapabilities(enabled = true, languages = mapOf("ko" to listOf("en", "ja"))),
        onDeviceLanguages = setOf("ko", "en", "ja"),
    )

    private fun providers(post: Post, settings: TranslationSettings = this.settings, env: TranslationEnvironment = both) =
        translationRequestFor(post, settings, env)?.providers

    @Test
    fun serverThenOnDevice() {
        val request = translationRequestFor(post(), settings, both)!!
        assertEquals("ko", request.source)
        assertEquals("en", request.target)
        assertEquals(listOf(Server, OnDevice), request.providers)
    }

    @Test
    fun languageTagIsNormalized() {
        assertEquals(listOf(Server, OnDevice), providers(post(language = "ko-KR")))
        assertEquals("zh", primaryLanguageOf("zh_TW"))
        assertNull(primaryLanguageOf("und"))
        assertNull(primaryLanguageOf(" "))
    }

    @Test
    fun noTranslationForUnknownOrUntargetedLanguage() {
        assertNull(providers(post(language = null)))
        assertNull(providers(post(language = "und")))
        // 翻訳する言語に入っていない
        assertNull(providers(post(language = "ja")))
        // 翻訳先と同じ
        assertNull(providers(post(language = "en"), settings.copy(sourceLanguages = setOf("en"))))
    }

    @Test
    fun noTranslationForEmptyPost() {
        assertNull(providers(post(text = "")))
        assertEquals(listOf(Server, OnDevice), providers(post(text = "", cw = "주의")))
    }

    @Test
    fun serverOnlyForPublicPostsWhenLoggedIn() {
        assertEquals(listOf(Server, OnDevice), providers(post(visibility = PostVisibility.Unlisted)))
        assertEquals(listOf(OnDevice), providers(post(visibility = PostVisibility.Private)))
        assertEquals(listOf(OnDevice), providers(post(visibility = PostVisibility.Direct)))
        assertEquals(listOf(OnDevice), providers(post(), env = both.copy(loggedIn = false)))
    }

    @Test
    fun serverLanguagePairs() {
        // サーバーもオンデバイスも対応していない組
        assertNull(providers(post(), settings.copy(targetLanguage = "de")))
        val deOnDevice = both.copy(onDeviceLanguages = setOf("ko", "de"))
        assertEquals(listOf(OnDevice), providers(post(), settings.copy(targetLanguage = "de"), deOnDevice))
        // 一覧が取れないサーバーは組を確かめない
        val noList = both.copy(server = ServerTranslationCapabilities(enabled = true, languages = null))
        assertEquals(listOf(Server, OnDevice), providers(post(), env = noList))
        // サーバー翻訳が無効・未取得
        assertEquals(listOf(OnDevice), providers(post(), env = both.copy(server = ServerTranslationCapabilities.None)))
        assertEquals(listOf(OnDevice), providers(post(), env = both.copy(server = null)))
    }

    @Test
    fun onDeviceIsOptIn() {
        assertEquals(listOf(Server), providers(post(), settings.copy(onDeviceEnabled = false)))
        // GMS が無い (対応言語が空)
        assertEquals(listOf(Server), providers(post(), env = both.copy(onDeviceLanguages = emptySet())))
        assertNull(providers(post(), settings.copy(onDeviceEnabled = false), both.copy(server = null)))
    }
}
