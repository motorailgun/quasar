package es.aria_on_the_planet.quasar.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun splitKeepsShortBodyWhole() {
        val source = toTranslatableHtml("<p>$mention hi :blobcat:</p><p>two</p>", emojis)
        assertEquals(listOf(TranslatablePart.Text(source, inline = false)), source.split(maxLength = 1000))
        assertEquals(emptyList<TranslatablePart>(), toTranslatableHtml("", emptySet()).split(maxLength = 1000))
    }

    @Test
    fun splitAtParagraphs() {
        val source = toTranslatableHtml(
            "<p>${"a".repeat(30)} $mention</p><p>${"b".repeat(30)} :blobcat:</p><ul><li>c</li></ul><p>$hashtag d</p>",
            emojis,
        )
        val parts = source.split(maxLength = 90)
        assertEquals(source.html, parts.joinToString("") { it.html })
        val texts = parts.filterIsInstance<TranslatablePart.Text>()
        assertEquals(parts.size, texts.size)
        // 段落の途中では分けない
        assertTrue(texts.all { it.html.startsWith("<p>") || it.html.startsWith("<ul>") })
        assertTrue(texts.size > 1)
        assertTrue(texts.none { it.inline })
        // リンク・絵文字はそれぞれの分へ順に配られる
        assertEquals(source.links, texts.flatMap { it.source.links })
        assertEquals(source.emojis, texts.flatMap { it.source.emojis })
        texts.forEach { text ->
            assertEquals(Regex("<a ").findAll(text.html).count(), text.source.links.size)
            assertEquals(Regex("<img ").findAll(text.html).count(), text.source.emojis.size)
        }
    }

    @Test
    fun splitLongParagraphAtLineBreaks() {
        val line = "x".repeat(40)
        val source = toTranslatableHtml("<p>$line $mention<br />$line :blobcat:<br /><br />$line</p><p>short</p>", emojis)
        val parts = source.split(maxLength = 100)
        assertEquals(source.html, parts.joinToString("") { it.html })
        assertEquals(TranslatablePart.Markup("<p>"), parts.first())
        val inner = parts.takeWhile { it.html != "</p>" }.filterIsInstance<TranslatablePart.Text>()
        // 段落の中の分は段落の外に文字があるものとして扱い、区切りの <br> は訳さない
        assertTrue(inner.size >= 2)
        assertTrue(inner.all { it.inline && !it.html.startsWith("<br>") && !it.html.endsWith("<br>") })
        assertTrue(TranslatablePart.Markup("<br><br>") in parts)
        // 分けた一部の訳もそのまま戻せる
        val first = inner.first()
        assertEquals(first.html.replace(line, "y"), restoreTranslatedHtml(first.source, first.html.replace(line, "y"))?.replace(mention.removePrefix("""<span class="h-card" translate="no">""").removeSuffix("</span>"), """<a href="https://a.invalid/@alice">@alice</a>"""))
    }

    @Test
    fun splitDoesNotBreakSentences() {
        // <br> の無い長い段落は分けられないので 1 つのまま
        val source = toTranslatableHtml("<p>${"z".repeat(200)} $hashtag ${"z".repeat(200)}</p>", emptySet())
        assertEquals(listOf(TranslatablePart.Text(source, inline = false)), source.split(maxLength = 100))
    }

    @Test
    fun plainTextBackToInlineHtml() {
        assertEquals("a &lt;b&gt;<br>c", plainTextToInlineHtml("a <b>\nc\n"))
    }

    @Test
    fun escapesTranslatedLinkText() {
        val source = toTranslatableHtml("""<p>see <a href="https://b.invalid/page">this page</a></p>""", emptySet())
        // タグに見えない < (表示側ではタグと読まれうる) も、裸の & も、リンクの中で訳された文字でエスケープする
        assertEquals(
            """<p>見て <a href="https://b.invalid/page">a &lt;b c &amp; d</a></p>""",
            restoreTranslatedHtml(source, """<p>見て <a href="https://b.invalid/page">a <b c & d</a></p>"""),
        )
    }

    @Test
    fun escapesBareAmpersandsButKeepsCharacterReferences() {
        assertEquals("AT&amp;T &amp;copy right", escapeText("AT&T &copy right"))
        assertEquals("&amp; &lt; &#39; &#x1F600; &copy; x &gt; y", escapeText("&amp; &lt; &#39; &#x1F600; &copy; x > y"))
        assertEquals("&amp;#; &amp;#xZZ; &amp;;", escapeText("&#; &#xZZ; &;"))
        val source = toTranslatableHtml("<p>a &amp; b</p>", emptySet())
        assertEquals("<p>AT&amp;T &amp; b</p>", restoreTranslatedHtml(source, "<p>AT&T &amp; b</p>"))
    }
}
