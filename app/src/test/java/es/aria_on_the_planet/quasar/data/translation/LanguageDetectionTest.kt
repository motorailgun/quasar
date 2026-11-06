package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LanguageDetectionTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())

    private fun post(text: String, cw: String? = null, emojis: List<CustomEmoji> = emptyList()) =
        Post("1", user, text, Instant.EPOCH, 0, 0, 0, emojis, contentWarning = cw, language = null)

    /** 渡された文字を覚えて、決まった答えを返す。 */
    private class FakeDetector(private val answer: String?) : LanguageDetector {
        val asked = mutableListOf<String>()
        override suspend fun detect(text: String): String? = answer.also { asked += text }
    }

    @Test
    fun detectionTextDropsMentionsHashtagsUrlsEmojisAndCode() {
        val html = """<p><span class="h-card"><a href="https://e/@bob" class="u-url mention">@<span>bob</span></a></span> """ +
            """thanks :blob: see <a href="https://docs.example/a/b"><span class="invisible">https://</span>""" +
            """<span>docs.example/a/b</span></a> and """ +
            """<a href="https://e/tags/x" class="mention hashtag">#<span>x</span></a></p>""" +
            """<p>run <code>max_id=1</code> or <a href="https://remote/notes/1">this note</a></p><pre><code>GET /x</code></pre>"""
        assertEquals("thanks see and run or this note", languageDetectionText(html, setOf("blob")))
    }

    @Test
    fun asksDetectorAndMapsToModelLanguage() = runBlocking {
        val detector = FakeDetector("fr-FR")
        assertEquals("fr", detectPostLanguage(post("<p>Bonjour, ceci est une note</p>", cw = "Attention"), detector))
        // CW も一緒に渡す
        assertEquals(listOf("Attention Bonjour, ceci est une note"), detector.asked)
        // 日本語・韓国語も文字では決めず推定に任せる (短くても聞く)
        val ja = FakeDetector("ja")
        assertEquals("ja", detectPostLanguage(post("<p>わかる</p>"), ja))
        assertEquals(listOf("わかる"), ja.asked)
        // 中国語は簡体字 / 繁体字
        assertEquals("zh-hant", detectPostLanguage(post("<p>今天天氣很好</p>"), FakeDetector("zh-Hant-TW")))
        // 推定できない・モデルが対応していない・推定する機能が無い
        assertNull(detectPostLanguage(post("<p>Saluton al ĉiuj</p>"), FakeDetector(null)))
        assertNull(detectPostLanguage(post("<p>Saluton al ĉiuj</p>"), FakeDetector("eo")))
        assertNull(detectPostLanguage(post("<p>Bonjour tout le monde</p>"), null))
    }

    @Test
    fun textWithoutWordsIsNotDetected() = runBlocking {
        val detector = FakeDetector("en")
        // メンションと絵文字だけ・記号だけ
        assertNull(
            detectPostLanguage(
                post("""<p><a href="https://e/@bob" class="u-url mention">@<span>bob</span></a> :wave:</p>""",
                    emojis = listOf(CustomEmoji("wave", "", ""))),
                detector,
            ),
        )
        assertNull(detectPostLanguage(post("<p>!!! 🎉</p>"), detector))
        assertEquals(emptyList<String>(), detector.asked)
    }

    @Test
    fun offerUsesDetectedLanguage() {
        val p = post("<p>Bonjour</p>")
        assertNull(translationOfferFor(p, TranslationSettings(), listOf("ja")))
        assertEquals(TranslationOffer("fr", "ja", automatic = false), translationOfferFor(p, TranslationSettings(), listOf("ja"), "fr"))
    }
}
