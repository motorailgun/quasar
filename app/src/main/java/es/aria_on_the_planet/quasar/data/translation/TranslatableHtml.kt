package es.aria_on_the_planet.quasar.data.translation

/**
 * ポストの本文 (Mastodon の HTML) を、PLaMo 3 Translate に訳させる形との間で変換する。
 * 実験の結果と経緯は docs/translation-ondevice.md。
 *
 * * 平文で渡すとハッシュタグが訳され (`#東京` → `#Tokyo`)、カスタム絵文字の位置が崩れる。HTML で渡すとタグ・属性はそのまま
 *   返ってくるので、HTML のまま訳させる。
 * * ただし Mastodon の HTML そのままでは、リンクの中の `<span>` で区切られた文字 (`#<span>東京</span>`) が訳され、トークンも
 *   増える。なので訳す前に簡略化する ([toTranslatableHtml]):
 *   * 残すタグ ([KeptTags]) は属性を外して残し、`<span>` 等それ以外のタグは外して中身だけ残す。
 *   * リンクは `<a href="…">中の文字をつなげたもの</a>` (短縮表示の URL も全体)。元の `<a>` は [OriginalLink] に覚えておく。
 *   * 既知のカスタム絵文字の `:shortcode:` は `<img alt=":shortcode:">` (文字のままだと動かされたり崩されたりする)。
 *   * 引用のフォールバック「RE: <リンク>」(`quote-inline`) は除く (表示でも除いている)。
 * * 訳文は検査してから戻す ([restoreTranslatedHtml])。崩れていたら null (呼び出し側は平文での訳等に切り替える)。
 */
data class TranslatableHtml(
    /** モデルに渡す HTML。 */
    val html: String,
    /** 元の本文のリンク (出てきた順)。 */
    val links: List<OriginalLink>,
    /** `<img alt>` にした絵文字の shortcode (出てきた順、重複あり)。 */
    val emojis: List<String>,
)

/**
 * 元のリンク。[text] はモデルに渡した文字 (中の文字をつなげたもの)、[html] は元の `<a>…</a>` そのもの。
 * 訳文では [html] に戻す (短縮表示・class も元どおり)。ただし文字が普通の言葉のリンク (他サーバの「this page」等) で、
 * 訳されていたら、href だけ元のものにして訳した文字を使う ([translatable])。
 */
data class OriginalLink(val href: String, val text: String, val html: String) {
    /**
     * リンクの文字を訳してよいか。メンション・ハッシュタグ・URL は名前なので訳さない (`#東京` を `#Tokyo` にすると、
     * 東京のタグを指したまま別の名前に見える。モデルは href が %エンコードされていると `#東京` を訳すことがある)。
     */
    val translatable: Boolean
        get() = text.trim().let { !it.startsWith("#") && !it.startsWith("@") && !it.contains("://") && !it.startsWith("www.") }
}

/** 属性を外して残すタグ (Mastodon が他サーバの HTML に許すもののうち、表示に関わるもの)。 */
private val KeptTags = setOf(
    "p", "br", "ul", "ol", "li", "blockquote", "pre", "code", "strong", "b", "em", "i", "u", "s", "del", "sub", "sup",
)

private val VoidTags = setOf("br", "img")

/** 文字を入れる箱 (この外に文字・リンク・絵文字があるかを見る)。 */
private val BlockTags = setOf("p", "li", "blockquote", "pre")

/** 段落等の外に文字・リンク・絵文字があるか。 */
private fun hasLooseText(tokens: List<HtmlToken>): Boolean {
    var depth = 0
    for (token in tokens) {
        when (token) {
            is HtmlToken.Start -> {
                if (token.name in BlockTags) depth++
                if (depth == 0 && (token.name == "a" || token.name == "img")) return true
            }
            is HtmlToken.End -> if (token.name in BlockTags) depth--
            is HtmlToken.Text -> if (depth == 0 && token.raw.isNotBlank()) return true
        }
    }
    return false
}

private val ShortcodeRegex = Regex(""":([A-Za-z0-9_]+):""")

/** 表示側 (`simplifyMastodonHtml`) の QuoteInlineRegex と同じ。 */
private val QuoteInlineRegex = Regex(
    """(?:<br\s*/?>\s*)*<(p|span) class="quote-inline">.*?</\1>""",
    RegexOption.DOT_MATCHES_ALL,
)

/** [html] (Mastodon の本文) を訳させる形にする。[emojiShortcodes] はポストのカスタム絵文字 (`Post.emojis`)。 */
fun toTranslatableHtml(html: String, emojiShortcodes: Set<String>): TranslatableHtml {
    val tokens = lexHtml(html.replace(QuoteInlineRegex, ""))
    val out = StringBuilder()
    val links = mutableListOf<OriginalLink>()
    val emojis = mutableListOf<String>()
    var i = 0
    while (i < tokens.size) {
        when (val token = tokens[i]) {
            is HtmlToken.Text -> out.appendTextWithEmojis(token.raw, emojiShortcodes, emojis)
            is HtmlToken.Start -> when {
                token.name == "a" -> {
                    // 対応する </a> まで (中は span と文字だけのはず)
                    val end = (i + 1 until tokens.size).firstOrNull { (tokens[it] as? HtmlToken.End)?.name == "a" } ?: tokens.size
                    val inner = tokens.subList(i + 1, end)
                    val text = inner.filterIsInstance<HtmlToken.Text>().joinToString("") { it.raw }
                    val href = token.attributes["href"]
                    if (href != null) {
                        val original = (tokens.subList(i, minOf(end + 1, tokens.size))).joinToString("") { it.raw }
                        links += OriginalLink(href, text, original)
                        out.append("<a href=\"").append(href).append("\">").append(text).append("</a>")
                    } else {
                        out.appendTextWithEmojis(text, emojiShortcodes, emojis)
                    }
                    i = end
                }
                token.name in KeptTags -> out.append('<').append(token.name).append('>')
                else -> Unit
            }
            is HtmlToken.End -> if (token.name in KeptTags && token.name !in VoidTags) out.append("</").append(token.name).append('>')
        }
        i++
    }
    return TranslatableHtml(out.toString(), links, emojis)
}

/**
 * 訳文 ([output]、モデルの出力) を表示用の HTML に戻す。次のどれかなら崩れたとして null:
 * 残すタグ・`<a href>`・`<img alt=":既知の絵文字:">` 以外のタグがある / タグの開き閉じが合わない / リンクの中にタグがある /
 * リンク (href) や絵文字が元と同じ数だけ無い / 元は段落等の中にしか文字が無かったのに、外に文字がある
 * (`<p>この<img></p>のデバッグを…` のように、段落を閉じて文の続きを外に出すことがある)。
 * リンクは元のリンクに対応付け、文字が変わっていなければ元の `<a>` に戻す (変わっていれば href だけ元のもの)。
 * 絵文字は `:shortcode:` に戻す (表示は今までどおり `EmojiText`)。
 */
fun restoreTranslatedHtml(source: TranslatableHtml, output: String): String? {
    val tokens = lexHtml(output)
    val unusedLinks = source.links.toMutableList()
    val usedEmojis = mutableListOf<String>()
    val open = ArrayDeque<String>()
    val out = StringBuilder()
    val sourceHasLooseText = hasLooseText(lexHtml(source.html))
    var i = 0
    while (i < tokens.size) {
        when (val token = tokens[i]) {
            is HtmlToken.Text -> {
                if (!sourceHasLooseText && open.none { it in BlockTags } && token.raw.isNotBlank()) return null
                out.append(token.raw.replace("<", "&lt;").replace(">", "&gt;"))
            }
            is HtmlToken.Start -> when (token.name) {
                "a" -> {
                    if (!sourceHasLooseText && open.none { it in BlockTags }) return null
                    val close = tokens.getOrNull(i + 2) as? HtmlToken.End
                    val text = when (val next = tokens.getOrNull(i + 1)) {
                        is HtmlToken.Text -> if (close?.name == "a") next.raw else return null
                        is HtmlToken.End -> if (next.name == "a") "" else return null
                        else -> return null
                    }
                    val href = token.attributes["href"] ?: return null
                    val link = unusedLinks.firstOrNull { it.href == href } ?: return null
                    unusedLinks.remove(link)
                    if (!link.translatable || text.trim() == link.text.trim()) {
                        out.append(link.html)
                    } else {
                        out.append(link.html.substringBefore('>')).append('>').append(text).append("</a>")
                    }
                    i += if (text.isEmpty()) 1 else 2
                }
                "img" -> {
                    if (!sourceHasLooseText && open.none { it in BlockTags }) return null
                    val code = token.attributes["alt"]?.let { ShortcodeRegex.matchEntire(it) }?.groupValues?.get(1) ?: return null
                    usedEmojis += code
                    out.append(':').append(code).append(':')
                }
                in KeptTags -> {
                    if (token.name !in VoidTags && !token.selfClosing) open.addLast(token.name)
                    out.append('<').append(token.name).append('>')
                }
                else -> return null
            }
            is HtmlToken.End -> {
                if (token.name in VoidTags) {
                    i++
                    continue
                }
                if (open.removeLastOrNull() != token.name) return null
                out.append("</").append(token.name).append('>')
            }
        }
        i++
    }
    if (open.isNotEmpty() || unusedLinks.isNotEmpty()) return null
    if (usedEmojis.sorted() != source.emojis.sorted()) return null
    return out.toString().takeIf { it.isNotBlank() }
}

private fun StringBuilder.appendTextWithEmojis(text: String, known: Set<String>, emojis: MutableList<String>) {
    var last = 0
    for (match in ShortcodeRegex.findAll(text)) {
        val code = match.groupValues[1]
        if (code !in known) continue
        append(text, last, match.range.first)
        append("<img alt=\":").append(code).append(":\">")
        emojis += code
        last = match.range.last + 1
    }
    append(text, last, text.length)
}

private sealed interface HtmlToken {
    val raw: String

    data class Text(override val raw: String) : HtmlToken
    data class Start(
        override val raw: String,
        val name: String,
        val attributes: Map<String, String>,
        val selfClosing: Boolean,
    ) : HtmlToken
    data class End(override val raw: String, val name: String) : HtmlToken
}

private val TagRegex = Regex("""<(/?)([A-Za-z][A-Za-z0-9]*)((?:\s+[^\s=>/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'>]+))?)*)\s*(/?)>""")
private val AttributeRegex = Regex("""([^\s=>/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
private val CommentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

/**
 * 小さな HTML の字句解析 (Mastodon がサニタイズした本文と、それを元にしたモデルの出力だけが相手)。
 * タグに見えない `<` は文字として扱う。文字・属性の値は文字参照 (`&amp;` 等) のまま。
 */
private fun lexHtml(html: String): List<HtmlToken> {
    val source = html.replace(CommentRegex, "")
    val tokens = mutableListOf<HtmlToken>()
    var last = 0
    for (match in TagRegex.findAll(source)) {
        if (match.range.first > last) tokens += HtmlToken.Text(source.substring(last, match.range.first))
        val name = match.groupValues[2].lowercase()
        tokens += if (match.groupValues[1] == "/") {
            HtmlToken.End(match.value, name)
        } else {
            val attributes = AttributeRegex.findAll(match.groupValues[3]).associate { attribute ->
                val (key, double, single, bare) = attribute.destructured
                key.lowercase() to double.ifEmpty { single.ifEmpty { bare } }
            }
            HtmlToken.Start(match.value, name, attributes, selfClosing = match.groupValues[4] == "/")
        }
        last = match.range.last + 1
    }
    if (last < source.length) tokens += HtmlToken.Text(source.substring(last))
    // 隣り合う文字をまとめる (コメントを除いた跡等)
    return tokens.fold(mutableListOf()) { merged, token ->
        val previous = merged.lastOrNull()
        if (token is HtmlToken.Text && previous is HtmlToken.Text) {
            merged[merged.lastIndex] = HtmlToken.Text(previous.raw + token.raw)
        } else {
            merged += token
        }
        merged
    }
}

/**
 * 本文の言語を推定するための文字 ([detectPostLanguage])。言葉でないもの (メンション・ハッシュタグ・URL のリンク、絵文字、
 * `<code>` / `<pre>`) は除き、空白はまとめる。普通の言葉のリンク (「this note」等) の文字は残す。
 */
fun languageDetectionText(html: String, emojiShortcodes: Set<String>): String {
    val out = StringBuilder()
    var code = 0
    var link: StringBuilder? = null
    for (token in lexHtml(toTranslatableHtml(html, emojiShortcodes).html)) {
        when (token) {
            is HtmlToken.Text -> if (code == 0) (link ?: out).append(decodeEntities(token.raw))
            is HtmlToken.Start -> when (token.name) {
                "code", "pre" -> code++
                "a" -> link = StringBuilder()
                "br", "p", "li" -> out.append(' ')
            }
            is HtmlToken.End -> when (token.name) {
                "code", "pre" -> code = maxOf(0, code - 1)
                "a" -> link?.let { text ->
                    if (!text.isLinkLike()) out.append(text)
                    link = null
                }
            }
        }
    }
    return out.toString().replace(WhitespaceRegex, " ").trim()
}

/** メンション・ハッシュタグ・URL (空白が無く `.` か `/` を含む) の文字。 */
private fun CharSequence.isLinkLike(): Boolean {
    val text = trim()
    return text.startsWith('@') || text.startsWith('#') || (text.none { it.isWhitespace() } && (text.contains('.') || text.contains('/')))
}

private val WhitespaceRegex = Regex("""\s+""")

/**
 * 訳させる形の HTML ([TranslatableHtml.html]) の文字だけ。HTML で訳した結果を戻せなかったとき ([restoreTranslatedHtml] が null)
 * に平文で訳し直すため。段落・リストの項目の区切りは空行 / 改行、`<br>` は改行、絵文字は `:shortcode:`、リンクは中の文字。
 */
fun TranslatableHtml.plainText(): String {
    val out = StringBuilder()
    for (token in lexHtml(html)) {
        when (token) {
            is HtmlToken.Text -> out.append(decodeEntities(token.raw))
            is HtmlToken.Start -> when (token.name) {
                "br" -> out.append('\n')
                "li" -> if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
                "img" -> token.attributes["alt"]?.let(out::append)
            }
            is HtmlToken.End -> when (token.name) {
                "li" -> if (!out.endsWith("\n")) out.append('\n')
                "p", "blockquote", "pre", "ul", "ol" ->
                    if (!out.endsWith("\n\n")) out.append(if (out.endsWith("\n")) "\n" else "\n\n")
            }
        }
    }
    return out.toString().trim()
}

/** 平文 (平文で訳した訳文) を表示用の HTML にする。空行で段落を分け、改行は `<br>`。 */
fun plainTextToHtml(text: String): String =
    text.trim().split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }.joinToString("") { paragraph ->
        "<p>" + paragraph.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
    }

private fun decodeEntities(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&amp;", "&")
