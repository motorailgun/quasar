package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationMode
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TranslationOfferTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())

    private fun post(language: String?, text: String = "<p>hola</p>", cw: String? = null) =
        Post("1", user, text, Instant.EPOCH, 0, 0, 0, emptyList(), contentWarning = cw, language = language)

    @Test
    fun targetIsDeviceLanguageByDefault() {
        assertEquals(
            TranslationOffer(source = "es", target = "ja", automatic = false),
            translationOfferFor(post("es"), TranslationSettings(), listOf("ja-JP")),
        )
        // 端末の言語に対応していなければ英語
        assertEquals("en", translationOfferFor(post("es"), TranslationSettings(), listOf("eo"))?.target)
    }

    @Test
    fun explicitTargetAndLanguageModes() {
        val settings = TranslationSettings(
            languageModes = mapOf("es" to TranslationMode.Automatic, "ko" to TranslationMode.Automatic, "fr" to TranslationMode.Hidden),
            targetLanguage = "en",
        )
        val device = listOf("ja")
        assertEquals(TranslationOffer("es", "en", automatic = true), translationOfferFor(post("es"), settings, device))
        // 一覧に無い言語は「その他の言語」(既定は翻訳を表示)
        assertEquals(TranslationOffer("de", "en", automatic = false), translationOfferFor(post("de"), settings, device))
        assertNull(translationOfferFor(post("fr"), settings, device))
        // 一覧を変えていれば、端末の言語も他と同じ
        assertEquals(TranslationOffer("ja", "en", automatic = false), translationOfferFor(post("ja"), settings, device))
        // 中国語は簡体字 / 繁体字を分ける
        assertEquals("zh-hant", translationOfferFor(post("zh-TW"), settings, device)?.source)
    }

    @Test
    fun defaultModeAppliesToUnlistedLanguages() {
        val settings = TranslationSettings(defaultMode = TranslationMode.Hidden, languageModes = mapOf("es" to TranslationMode.Offer))
        assertNull(translationOfferFor(post("de"), settings, listOf("ja")))
        assertEquals(TranslationOffer("es", "ja", automatic = false), translationOfferFor(post("es"), settings, listOf("ja")))
    }

    @Test
    fun deviceLanguagesAreHiddenUntilListIsChanged() {
        // 端末の言語が日本語・英語・エスペラント (対応していない) で、訳す先は先頭の日本語
        val device = listOf("ja-JP", "en-US", "eo")
        assertNull(translationOfferFor(post("en"), TranslationSettings(), device))
        assertEquals(TranslationOffer("es", "ja", automatic = false), translationOfferFor(post("es"), TranslationSettings(), device))
        // 一覧を変えると、そのときの端末の言語が一覧に入る
        val changed = TranslationSettings().withLanguageMode("es", TranslationMode.Automatic, plamoLanguagesOf(device))
        assertEquals(mapOf("ja" to TranslationMode.Hidden, "en" to TranslationMode.Hidden, "es" to TranslationMode.Automatic), changed.languageModes)
        assertEquals(mapOf("ja" to TranslationMode.Hidden), changed.withoutLanguage("en", listOf("ko")).withoutLanguage("es", listOf("ko")).languageModes)
    }

    @Test
    fun nothingToOffer() {
        val settings = TranslationSettings(targetLanguage = "ja")
        // 訳す先と同じ・言語が無い・対応していない・本文も CW も空
        assertNull(translationOfferFor(post("ja"), settings, listOf("en")))
        assertNull(translationOfferFor(post(null), settings, listOf("en")))
        assertNull(translationOfferFor(post("eo"), settings, listOf("en")))
        assertNull(translationOfferFor(post("es", text = ""), settings, listOf("en")))
        // CW だけでも訳す
        assertEquals("es", translationOfferFor(post("es", text = "", cw = "spoiler"), settings, listOf("en"))?.source)
    }
}
