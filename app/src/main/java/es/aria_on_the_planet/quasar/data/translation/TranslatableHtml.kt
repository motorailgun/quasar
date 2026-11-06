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
                out.append(escapeText(token.raw))
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
                        out.append(link.html.substringBefore('>')).append('>').append(escapeText(text)).append("</a>")
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

/** 本文を訳す単位に分けたもの ([split])。全部の [html] をつなげると元の [TranslatableHtml.html] になる。 */
sealed interface TranslatablePart {
    val html: String

    /** 訳さずにそのまま出すタグ・空白 (分けた段落の `<p>` / `</p>`、区切りの `<br>` 等)。 */
    data class Markup(override val html: String) : TranslatablePart

    /**
     * 1 回で訳す分 ([restoreTranslatedHtml] にはこの [source] を渡す)。[inline] は段落等の中の一部 (段落を分けたもの・
     * 段落の外の文字) で、平文で訳し直したときに `<p>` で包まない ([plainTextToInlineHtml])。
     */
    data class Text(val source: TranslatableHtml, val inline: Boolean) : TranslatablePart {
        override val html: String get() = source.html
    }
}

/** 段落と同じく、丸ごと 1 つの単位にする要素。 */
private val BlockLevelTags = BlockTags + setOf("ul", "ol")

/**
 * 長い本文を、1 回の訳 (モデルの出力の上限・コンテキスト) に収まるよう [maxLength] 文字 (HTML の文字数) 以下ずつに分ける。
 * 収まれば分けない。分けるのは段落等の切れ目で、それでも長い段落はその中の `<br>` の切れ目で分ける (中の分は [inline])。
 * 文の途中では分けないので、`<br>` の無い長い段落は [maxLength] を超えたまま 1 つになる。
 */
fun TranslatableHtml.split(maxLength: Int): List<TranslatablePart> {
    if (html.isBlank()) return emptyList()
    val parts = mutableListOf<TranslatablePart>()
    var nextLink = 0
    var nextEmoji = 0

    fun markup(token: HtmlToken) {
        val last = parts.lastOrNull()
        if (last is TranslatablePart.Markup) parts[parts.lastIndex] = TranslatablePart.Markup(last.html + token.raw) else parts += TranslatablePart.Markup(token.raw)
    }

    fun text(tokens: List<HtmlToken>) {
        var start = 0
        var end = tokens.size
        while (start < end && tokens[start].isSeparator()) start++
        while (end > start && tokens[end - 1].isSeparator()) end--
        tokens.subList(0, start).forEach(::markup)
        if (start < end) {
            val body = tokens.subList(start, end)
            val links = body.count { it is HtmlToken.Start && it.name == "a" }
            val emojis = body.count { it is HtmlToken.Start && it.name == "img" }
            val source = TranslatableHtml(
                html = body.joinToString("") { it.raw },
                links = this.links.subList(nextLink, nextLink + links),
                emojis = this.emojis.subList(nextEmoji, nextEmoji + emojis),
            )
            parts += TranslatablePart.Text(source, inline = hasLooseText(body))
            nextLink += links
            nextEmoji += emojis
        }
        tokens.subList(end, tokens.size).forEach(::markup)
    }

    fun splitTokens(tokens: List<HtmlToken>) {
        val current = mutableListOf<HtmlToken>()
        var length = 0
        for (unit in splitUnits(tokens)) {
            val unitLength = unit.sumOf { it.raw.length }
            if (length + unitLength > maxLength && current.isNotEmpty()) {
                text(current.toList())
                current.clear()
                length = 0
            }
            val open = unit.first() as? HtmlToken.Start
            val close = unit.last() as? HtmlToken.End
            val inner = if (unit.size > 2 && open != null && open.name in KeptTags && close?.name == open.name) unit.subList(1, unit.size - 1) else null
            if (unitLength > maxLength && inner != null && splitUnits(inner).size > 1) {
                // 長い段落等で、中に切れ目がある: 中を分ける
                markup(unit.first())
                splitTokens(inner)
                markup(unit.last())
            } else {
                current += unit
                length += unitLength
            }
        }
        if (current.isNotEmpty()) text(current.toList())
    }

    splitTokens(lexHtml(html))
    return parts
}

/**
 * [tokens] (同じ深さの並び) を分けてよい単位にする: 段落等の要素 1 つ / `<br>` 1 つ / その間の文字・リンク・絵文字・
 * `<strong>` 等のひと続き (文の途中では分けない)。
 */
private fun splitUnits(tokens: List<HtmlToken>): List<List<HtmlToken>> {
    val units = mutableListOf<List<HtmlToken>>()
    val inline = mutableListOf<HtmlToken>()
    fun flushInline() {
        if (inline.isNotEmpty()) units += inline.toList()
        inline.clear()
    }
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        val end = if (token is HtmlToken.Start && token.name !in VoidTags && !token.selfClosing) closingIndex(tokens, i) else i
        val atom = tokens.subList(i, end + 1)
        when {
            token is HtmlToken.Start && (token.name in BlockLevelTags || token.name == "br") -> {
                flushInline()
                units += atom
            }
            else -> inline += atom
        }
        i = end + 1
    }
    flushInline()
    return units
}

/** [start] の開きタグに対応する閉じタグの位置 (無ければ最後)。 */
private fun closingIndex(tokens: List<HtmlToken>, start: Int): Int {
    var depth = 0
    for (i in start until tokens.size) {
        when (val token = tokens[i]) {
            is HtmlToken.Start -> if (token.name !in VoidTags && !token.selfClosing) depth++
            is HtmlToken.End -> if (token.name !in VoidTags && --depth == 0) return i
            is HtmlToken.Text -> Unit
        }
    }
    return tokens.lastIndex
}

/** 分けた単位の端に残して訳さないもの (`<br>`・空白)。 */
private fun HtmlToken.isSeparator(): Boolean =
    (this is HtmlToken.Start && name == "br") || (this is HtmlToken.Text && raw.isBlank())

/** 正しい形の文字参照 (名前付き・10 進・16 進。セミコロンまで)。 */
private val CharacterReferenceRegex = Regex("""&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#[xX][0-9A-Fa-f]+);""")

/**
 * モデルの出力の文字 (タグでない部分。リンクの中の文字も) を表示用の HTML に入れられる形にする。タグに見えない `<` / `>` と、
 * 正しい文字参照になっていない `&` (`AT&T`、セミコロンの無い `&copy` 等) をエスケープする。表示側 (`fromHtml`) が `<b c` を
 * タグと読んで続きを飲み込んだり、`&copy` を文字参照と読んだりしないように。正しい文字参照 (`&amp;` 等) はそのまま。
 */
internal fun escapeText(raw: String): String {
    val out = StringBuilder()
    var last = 0
    for (match in CharacterReferenceRegex.findAll(raw)) {
        out.appendEscaped(raw, last, match.range.first).append(match.value)
        last = match.range.last + 1
    }
    return out.appendEscaped(raw, last, raw.length).toString()
}

private fun StringBuilder.appendEscaped(text: String, start: Int, end: Int): StringBuilder {
    for (i in start until end) {
        when (val c = text[i]) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }
    return this
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
    text.trim().split(BlankLineRegex).filter { it.isNotBlank() }.joinToString("") { paragraph ->
        "<p>" + plainTextToInlineHtml(paragraph) + "</p>"
    }

/** 平文を段落の中に置く HTML にする (段落を分けて訳した一部 ([TranslatablePart.Text.inline]) 用)。改行は `<br>`。 */
fun plainTextToInlineHtml(text: String): String =
    text.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

private val BlankLineRegex = Regex("""\n\s*\n""")

private fun decodeEntities(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&amp;", "&")
