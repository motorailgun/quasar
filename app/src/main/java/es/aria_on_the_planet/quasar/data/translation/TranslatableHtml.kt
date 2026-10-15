package es.aria_on_the_planet.quasar.data.translation

/**
 * プレーンテキストしか受け付けない翻訳 (ML Kit) のために、本文を「訳す部分」と「原文のまま残す部分」に分ける。
 * 訳したら [joinTranslatedHtml] で組み立て直すと、リンク・カスタム絵文字・段落を保った HTML になる。
 */
sealed interface TranslationSegment {
    /** 訳すテキスト (HTML のエンティティは解決済み)。 */
    data class Text(val text: String) : TranslationSegment

    /** そのまま残す部分 (タグ、`<a>` 要素全体、`:shortcode:`、空白のみのテキスト)。HTML なら HTML のまま。 */
    data class Keep(val raw: String) : TranslationSegment
}

private val TagRegex = Regex("""<[^>]*>""")
private val AnchorOpenRegex = Regex("""<a(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
private val AnchorCloseRegex = Regex("""</a\s*>""", RegexOption.IGNORE_CASE)
private val ShortcodeRegex = Regex(""":[A-Za-z0-9_]+:""")

/**
 * Mastodon の本文 HTML を分ける。`<a>` 要素 (メンション・ハッシュタグ・URL) はリンク文字列ごと残し、
 * それ以外のタグ (`<p>`, `<br>` 等) は区切りとして残す。タグの間のテキストは `:shortcode:` でさらに分ける。
 *
 * 文中にリンクがあると前後を別々に訳すことになるが、プレースホルダを入れて訳すと記号が崩れることがあるため分割にしている。
 */
fun splitHtmlForTranslation(html: String): List<TranslationSegment> {
    val segments = mutableListOf<TranslationSegment>()
    var index = 0
    while (index < html.length) {
        val tag = TagRegex.find(html, index)
        val textEnd = tag?.range?.first ?: html.length
        if (textEnd > index) splitText(decodeHtmlEntities(html.substring(index, textEnd)), segments)
        if (tag == null) break
        if (AnchorOpenRegex.matches(tag.value)) {
            // <a>…</a> はリンク文字列ごと残す (閉じタグが無ければ末尾まで)
            val close = AnchorCloseRegex.find(html, tag.range.last + 1)
            val end = close?.range?.last?.plus(1) ?: html.length
            segments += TranslationSegment.Keep(html.substring(tag.range.first, end))
            index = end
        } else {
            segments += TranslationSegment.Keep(tag.value)
            index = tag.range.last + 1
        }
    }
    return segments
}

/** プレーンテキスト (CW 等) を分ける。`:shortcode:` だけを残す。 */
fun splitPlainTextForTranslation(text: String): List<TranslationSegment> =
    mutableListOf<TranslationSegment>().also { splitText(text, it) }

private fun splitText(text: String, into: MutableList<TranslationSegment>) {
    var last = 0
    for (match in ShortcodeRegex.findAll(text)) {
        addText(text.substring(last, match.range.first), into)
        into += TranslationSegment.Keep(match.value)
        last = match.range.last + 1
    }
    addText(text.substring(last), into)
}

/** 前後の空白は訳さずに残す (翻訳で消えると、隣のリンク・絵文字とくっつくため)。 */
private fun addText(text: String, into: MutableList<TranslationSegment>) {
    if (text.isEmpty()) return
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) {
        into += TranslationSegment.Keep(text)
        return
    }
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    if (start > 0) into += TranslationSegment.Keep(text.substring(0, start))
    into += TranslationSegment.Text(text.substring(start, end))
    if (end < text.length) into += TranslationSegment.Keep(text.substring(end))
}

/**
 * [segments] の [TranslationSegment.Text] を [translate] で訳して組み立て直す。
 *
 * @param html `true` なら HTML として組み立てる (訳文をエスケープする)。`false` ならプレーンテキスト。
 */
suspend fun translateSegments(
    segments: List<TranslationSegment>,
    html: Boolean,
    translate: suspend (String) -> String,
): String = buildString {
    for (segment in segments) {
        when (segment) {
            is TranslationSegment.Text -> {
                val translated = translate(segment.text)
                append(if (html) escapeHtml(translated) else translated)
            }
            is TranslationSegment.Keep -> append(segment.raw)
        }
    }
}

/** 本文 HTML を訳す。 */
suspend fun translateHtml(html: String, translate: suspend (String) -> String): String =
    translateSegments(splitHtmlForTranslation(html), html = true, translate)

/** プレーンテキスト (CW) を訳す。 */
suspend fun translatePlainText(text: String, translate: suspend (String) -> String): String =
    translateSegments(splitPlainTextForTranslation(text), html = false, translate)

internal fun escapeHtml(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

private val EntityRegex = Regex("""&(#[0-9]+|#[xX][0-9A-Fa-f]+|[A-Za-z]+);""")
private val NamedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
)

internal fun decodeHtmlEntities(text: String): String = EntityRegex.replace(text) { match ->
    val name = match.groupValues[1]
    val codePoint = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
        name.startsWith("#") -> name.substring(1).toIntOrNull()
        else -> return@replace NamedEntities[name] ?: match.value
    }
    if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else match.value
}
