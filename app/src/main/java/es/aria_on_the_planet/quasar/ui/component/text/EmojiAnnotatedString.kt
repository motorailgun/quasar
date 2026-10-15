package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

private val ShortcodeRegex = Regex(""":([A-Za-z0-9_]+):""")

internal const val EmojiInlineIdPrefix = "emoji:"

/**
 * [source] 中の `:shortcode:` のうち [emojis] に存在するものを inline content に置き換える。
 * 元のスタイルやリンクは保持される。
 */
internal fun AnnotatedString.replaceEmojiShortcodes(emojis: List<CustomEmoji>): AnnotatedString {
    if (emojis.isEmpty()) return this
    val known = emojis.mapTo(HashSet()) { it.shortcode }
    val matches = ShortcodeRegex.findAll(text).filter { it.groupValues[1] in known }.toList()
    if (matches.isEmpty()) return this
    val source = this
    return buildAnnotatedString {
        var last = 0
        for (match in matches) {
            append(source.subSequence(last, match.range.first))
            appendInlineContent(EmojiInlineIdPrefix + match.groupValues[1], match.value)
            last = match.range.last + 1
        }
        append(source.subSequence(last, source.length))
    }
}

/**
 * 引用に対応していないクライアント向けの「RE: <リンク>」。引用は別途表示するので取り除く。
 * Mastodon は `<p class="quote-inline">`、Misskey 由来は `<br><br><span class="quote-inline">` で来る。
 */
private val QuoteInlineRegex = Regex(
    """(?:<br\s*/?>\s*)*<(p|span) class="quote-inline">.*?</\1>""",
    RegexOption.DOT_MATCHES_ALL,
)
private val InvisibleSpanRegex = Regex("""<span class="invisible">[^<]*</span>""")
private val EllipsisSpanRegex = Regex("""<span class="ellipsis">([^<]*)</span>""")

/**
 * Mastodon の HTML を表示用に整形する。
 * URL 短縮表示用の invisible / ellipsis span の処理と、引用のフォールバック段落の除去を行う。
 */
internal fun simplifyMastodonHtml(html: String): String = html
    .replace(QuoteInlineRegex, "")
    .replace(InvisibleSpanRegex, "")
    .replace(EllipsisSpanRegex, "$1…")
