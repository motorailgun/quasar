package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.ui.text.AnnotatedString
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EmojiAnnotatedStringTest {
    private val blobcat = CustomEmoji("blobcat", "https://e/blobcat.png", "https://e/blobcat_s.png")

    @Test
    fun replacesKnownShortcodeWithInlineContent() {
        val result = AnnotatedString("hi :blobcat: there").replaceEmojiShortcodes(listOf(blobcat))
        // alternateText として元の shortcode は残る
        assertEquals("hi :blobcat: there", result.text)
        val inline = result.getStringAnnotations(0, result.length)
            .filter { it.tag == "androidx.compose.foundation.text.inlineContent" }
        assertEquals(listOf("emoji:blobcat"), inline.map { it.item })
        assertEquals(3, inline.single().start)
        assertEquals(12, inline.single().end)
    }

    @Test
    fun leavesUnknownShortcodeUntouched() {
        val source = AnnotatedString("time 12:30:00 :unknown:")
        assertSame(source, source.replaceEmojiShortcodes(listOf(blobcat)))
    }

    @Test
    fun simplifiesMastodonLinkSpans() {
        val html = """<a href="https://example.com/very/long"><span class="invisible">https://</span><span class="ellipsis">example.com/ve</span><span class="invisible">ry/long</span></a>"""
        assertEquals(
            """<a href="https://example.com/very/long">example.com/ve…</a>""",
            simplifyMastodonHtml(html),
        )
    }

    @Test
    fun removesQuoteInlineFallback() {
        val html = """<p class="quote-inline">RE: <a href="https://e/@a/1"><span class="invisible">https://</span><span class="ellipsis">e/@a/1</span></a></p><p>body</p>"""
        assertEquals("<p>body</p>", simplifyMastodonHtml(html))

        // Misskey 由来
        val misskey = """本文<br><br><span class="quote-inline">RE: <a href="https://m/notes/1">https://m/notes/1</a></span>"""
        assertEquals("本文", simplifyMastodonHtml(misskey))
    }
}
