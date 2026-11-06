package es.aria_on_the_planet.quasar.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslatableHtmlTest {
    // Mastodon が返す形 (4.x)
    private val mention = """<span class="h-card" translate="no"><a href="https://a.invalid/@alice" class="u-url mention">@<span>alice</span></a></span>"""
    private val hashtag = """<a href="https://a.invalid/tags/%E6%9D%B1%E4%BA%AC" class="mention hashtag" rel="tag">#<span>東京</span></a>"""
    private val url = """<a href="https://docs.invalid/methods/statuses/" target="_blank" rel="nofollow noopener" translate="no">""" +
        """<span class="invisible">https://</span><span class="ellipsis">docs.invalid/methods/</span><span class="invisible">statuses/</span></a>"""
    private val emojis = setOf("blobcat", "blobheart")

    @Test
    fun simplify() {
        val source = toTranslatableHtml(
            "<p>$mention 今日は $hashtag で :blobcat: :unknown: 12:30:00<br />詳細 $url</p><p>2 段落目 :blobcat:</p>",
            emojis,
        )
        assertEquals(
            """<p><a href="https://a.invalid/@alice">@alice</a> 今日は <a href="https://a.invalid/tags/%E6%9D%B1%E4%BA%AC">#東京</a> で """ +
                """<img alt=":blobcat:"> :unknown: 12:30:00<br>詳細 <a href="https://docs.invalid/methods/statuses/">https://docs.invalid/methods/statuses/</a></p>""" +
                """<p>2 段落目 <img alt=":blobcat:"></p>""",
            source.html,
        )
        assertEquals(listOf("@alice", "#東京", "https://docs.invalid/methods/statuses/"), source.links.map { it.text })
        assertEquals(listOf(mention.removePrefix("""<span class="h-card" translate="no">""").removeSuffix("</span>"), hashtag, url), source.links.map { it.html })
        assertEquals(listOf("blobcat", "blobcat"), source.emojis)
    }

    @Test
    fun keepsFormattingWithoutAttributes() {
        val source = toTranslatableHtml(
            """<p class="x">Notes:</p><ul><li><code>max_id</code></li><li><strong>very</strong> <em>long</em></li></ul><blockquote><p>quoted</p></blockquote><pre><code>GET /</code></pre>""",
            emptySet(),
        )
        assertEquals(
            "<p>Notes:</p><ul><li><code>max_id</code></li><li><strong>very</strong> <em>long</em></li></ul><blockquote><p>quoted</p></blockquote><pre><code>GET /</code></pre>",
            source.html,
        )
    }

    @Test
    fun dropsQuoteFallback() {
        val source = toTranslatableHtml(
            """<p class="quote-inline">RE: <a href="https://a.invalid/@bob/1">https://a.invalid/@bob/1</a></p><p>本文</p>""",
            emptySet(),
        )
        assertEquals("<p>本文</p>", source.html)
        assertEquals(emptyList<OriginalLink>(), source.links)
    }

    @Test
    fun restoresLinksAndEmojis() {
        val source = toTranslatableHtml("<p>$mention 今日は $hashtag で :blobcat:<br />詳細 $url</p>", emojis)
        // モデルの出力の形 (リンクの文字はそのまま、絵文字は img、語順は変わる)
        val output = """<p><a href="https://a.invalid/@alice">@alice</a> Today in <a href="https://a.invalid/tags/%E6%9D%B1%E4%BA%AC">#東京</a> <img alt=":blobcat:"><br>""" +
            """Details at <a href="https://docs.invalid/methods/statuses/">https://docs.invalid/methods/statuses/</a></p>"""
        val mentionLink = mention.removePrefix("""<span class="h-card" translate="no">""").removeSuffix("</span>")
        assertEquals(
            "<p>$mentionLink Today in $hashtag :blobcat:<br>Details at $url</p>",
            restoreTranslatedHtml(source, output),
        )
    }

    @Test
    fun translatedLinkTextKeepsOriginalHref() {
        // 他サーバ (Misskey 等) の、文字が URL でないリンク
        val source = toTranslatableHtml("""<p>see <a href="https://b.invalid/page" rel="nofollow">this page</a></p>""", emptySet())
        assertEquals(
            """<p><a href="https://b.invalid/page" rel="nofollow">このページ</a>を参照</p>""",
            restoreTranslatedHtml(source, """<p><a href="https://b.invalid/page">このページ</a>を参照</p>"""),
        )
    }

    @Test
    fun namesAreNeverTranslated() {
        // href が %エンコードされているとモデルが訳すことがある (エミュレーターで確認)。メンション・ハッシュタグ・URL は元に戻す
        val source = toTranslatableHtml("<p>今日は $hashtag で $mention と $url</p>", emptySet())
        val output = """<p>Today in <a href="https://a.invalid/tags/%E6%9D%B1%E4%BA%AC">#Tokyo</a> with <a href="https://a.invalid/@alice">@Alice</a> """ +
            """and <a href="https://docs.invalid/methods/statuses/">the docs</a></p>"""
        val mentionLink = mention.removePrefix("""<span class="h-card" translate="no">""").removeSuffix("</span>")
        assertEquals("<p>Today in $hashtag with $mentionLink and $url</p>", restoreTranslatedHtml(source, output))
    }

    @Test
    fun rejectsTextMovedOutOfParagraphs() {
        // エミュレーターで出た形: 絵文字の後で段落を閉じ、文の続きを外に出す
        val source = toTranslatableHtml("<p>Thanks to $mention for helping me debug this :blobcat:</p>", emojis)
        assertNull(
            restoreTranslatedHtml(
                source,
                """<p>この<img alt=":blobcat:"></p>のデバッグを手伝ってくれた<a href="https://a.invalid/@alice">@alice</a>さん、ありがとう。""",
            ),
        )
        // 元から外に文字がある (段落の無い HTML) なら問わない
        val loose = toTranslatableHtml("hello <b>world</b>", emptySet())
        assertEquals("こんにちは <b>世界</b>", restoreTranslatedHtml(loose, "こんにちは <b>世界</b>"))
    }

    @Test
    fun linksMatchedInOrderWhenRepeated() {
        val a = """<a href="https://a.invalid/tags/x" class="mention hashtag" rel="tag">#<span>x</span></a>"""
        val source = toTranslatableHtml("<p>$a and $a</p>", emptySet())
        assertEquals(
            "<p>$a と $a</p>",
            restoreTranslatedHtml(source, """<p><a href="https://a.invalid/tags/x">#x</a> と <a href="https://a.invalid/tags/x">#x</a></p>"""),
        )
    }

    @Test
    fun escapesStrayAngleBrackets() {
        val source = toTranslatableHtml("<p>a &lt; b</p>", emptySet())
        assertEquals("<p>a &lt; b &gt; c</p>", restoreTranslatedHtml(source, "<p>a &lt; b > c</p>"))
    }

    @Test
    fun rejectsBrokenOutput() {
        val source = toTranslatableHtml("<p>$mention hi :blobcat:</p>", emojis)
        val link = """<a href="https://a.invalid/@alice">@alice</a>"""
        val img = """<img alt=":blobcat:">"""
        // 元のまま (恒等) なら通る
        assertEquals("<p>${source.links[0].html} hi :blobcat:</p>", restoreTranslatedHtml(source, "<p>$link hi $img</p>"))
        // リンクが無い・増えた・href が変わった
        assertNull(restoreTranslatedHtml(source, "<p>@alice やあ $img</p>"))
        assertNull(restoreTranslatedHtml(source, "<p>$link $link やあ $img</p>"))
        assertNull(restoreTranslatedHtml(source, """<p><a href="https://evil.invalid/">@alice</a> やあ $img</p>"""))
        // 絵文字が無い・増えた・知らない・文字に戻された
        assertNull(restoreTranslatedHtml(source, "<p>$link やあ</p>"))
        assertNull(restoreTranslatedHtml(source, "<p>$link やあ $img $img</p>"))
        assertNull(restoreTranslatedHtml(source, """<p>$link やあ <img alt=":blobheart:"></p>"""))
        assertNull(restoreTranslatedHtml(source, "<p>$link やあ :blobcat:</p>"))
        // 知らないタグ・開き閉じが合わない・リンクの中にタグ
        assertNull(restoreTranslatedHtml(source, "<div>$link やあ $img</div>"))
        assertNull(restoreTranslatedHtml(source, "<p>$link やあ $img"))
        assertNull(restoreTranslatedHtml(source, "<p>$link <strong>やあ $img</p></strong>"))
        assertNull(restoreTranslatedHtml(source, """<p><a href="https://a.invalid/@alice"><b>@alice</b></a> やあ $img</p>"""))
        // 空
        assertNull(restoreTranslatedHtml(toTranslatableHtml("<p></p>", emptySet()), ""))
    }

    @Test
    fun plainTextForFallback() {
        val source = toTranslatableHtml(
            "<p>$mention hi &amp; bye :blobcat:<br />next line $url</p><ul><li>one</li><li>two</li></ul><p>last</p>",
            emojis,
        )
        assertEquals(
            "@alice hi & bye :blobcat:\nnext line https://docs.invalid/methods/statuses/\n\none\ntwo\n\nlast",
            source.plainText(),
        )
    }

    @Test
    fun plainTextBackToHtml() {
        assertEquals(
            "<p>a &lt;b&gt; &amp; c<br>d</p><p>e :blobcat:</p>",
            plainTextToHtml("a <b> & c\nd\n\n\ne :blobcat:\n"),
        )
    }
}
