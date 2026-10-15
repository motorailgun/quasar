package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.data.translation.TranslationSegment.Keep
import es.aria_on_the_planet.quasar.data.translation.TranslationSegment.Text
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslatableHtmlTest {
    private val mention =
        """<span class="h-card"><a href="https://e/@bob" class="u-url mention">@<span>bob</span></a></span>"""
    private val link =
        """<a href="https://example.com/a" rel="nofollow"><span class="invisible">https://</span><span class="">example.com/a</span></a>"""

    private fun translate(html: String, f: (String) -> String = { it.uppercase() }) =
        runBlocking { translateHtml(html) { f(it) } }

    @Test
    fun splitsTextLinksAndTags() {
        val segments = splitHtmlForTranslation("<p>$mention hello <b>world</b></p><p>see $link</p>")
        assertEquals(
            listOf(
                Keep("<p>"), Keep("<span class=\"h-card\">"), Keep(mention.removePrefix("<span class=\"h-card\">").removeSuffix("</span>")),
                Keep("</span>"), Keep(" "), Text("hello"), Keep(" "), Keep("<b>"), Text("world"), Keep("</b>"), Keep("</p>"),
                Keep("<p>"), Text("see"), Keep(" "), Keep(link), Keep("</p>"),
            ),
            segments,
        )
    }

    @Test
    fun identityTranslationKeepsHtml() {
        val html = "<p>$mention 안녕하세요 :blobcat: 반가워요</p><p>줄 1<br />줄 2 $link</p>"
        assertEquals(html, translate(html) { it })
    }

    @Test
    fun linksAndShortcodesAreNotTranslated() {
        assertEquals(
            "<p>$mention HI :blobcat: THERE</p>",
            translate("<p>$mention hi :blobcat: there</p>"),
        )
    }

    @Test
    fun entitiesAreDecodedAndOutputIsEscaped() {
        val received = mutableListOf<String>()
        val result = translate("<p>a &amp; b &lt;c&gt; &#39;d&#x27;</p>") { received += it; "x < y & \"z\"" }
        assertEquals(listOf("a & b <c> 'd'"), received)
        assertEquals("<p>x &lt; y &amp; &quot;z&quot;</p>", result)
    }

    @Test
    fun whitespaceOnlyTextIsKept() {
        val received = mutableListOf<String>()
        translate("<p>\n</p><p>  hi  </p>") { received += it; it }
        assertEquals(listOf("hi"), received)
        assertEquals("<p>\n</p><p>  HI  </p>", translate("<p>\n</p><p>  hi  </p>"))
    }

    @Test
    fun unclosedAnchorIsKeptToEnd() {
        assertEquals("<p>HI <a href=\"x\">rest", translate("<p>hi <a href=\"x\">rest"))
    }

    @Test
    fun plainTextKeepsShortcodesWithoutEscaping() {
        val result = runBlocking { translatePlainText("nsfw :blobcat: <spoiler> & co") { it.uppercase() } }
        assertEquals("NSFW :blobcat: <SPOILER> & CO", result)
    }
}
