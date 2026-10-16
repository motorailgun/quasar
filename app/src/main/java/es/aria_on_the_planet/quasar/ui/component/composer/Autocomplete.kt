package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.ui.text.TextRange
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

/**
 * 入力中の補完の対象。[range] は置き換える範囲 (引き金の文字を含む)。
 *
 * 今は `:` (カスタム絵文字) のみ。メンション (`@`)・ハッシュタグ (`#`) もこの形に載せる想定。
 */
data class AutocompleteQuery(val trigger: Char, val query: String, val range: TextRange)

/**
 * カーソル直前の `:sho` を取り出す。条件:
 * * カーソルが 1 点 (範囲選択中は出さない)。
 * * `:` (日本語 IME の全角 `：` も可) が文の先頭か、英数字・コロン以外の直後にある (`12:30` や `a:b` で反応しない)。
 * * `:` からカーソルまでがショートコードの文字 (ASCII 英数字と `_`) で [MinQueryLength] 文字以上。
 * * カーソルの直後が単語の途中でない。
 */
fun findAutocompleteQuery(text: CharSequence, selection: TextRange): AutocompleteQuery? {
    if (!selection.collapsed) return null
    val cursor = selection.start
    if (cursor > text.length) return null
    if (cursor < text.length && text[cursor].isShortcodeChar()) return null
    var start = cursor
    while (start > 0 && text[start - 1].isShortcodeChar()) start--
    val colon = start - 1
    if (colon < 0 || text[colon] !in Colons) return null
    if (colon > 0 && (text[colon - 1].isLetterOrDigit() || text[colon - 1] in Colons)) return null
    val query = text.substring(start, cursor)
    if (query.length < MinQueryLength) return null
    return AutocompleteQuery(':', query, TextRange(colon, cursor))
}

/** 前方一致 → 部分一致の順、それぞれ短い順・辞書順。大文字小文字は区別しない。 */
fun rankEmojis(emojis: List<CustomEmoji>, query: String, limit: Int = MaxSuggestions): List<CustomEmoji> {
    val q = query.lowercase()
    return emojis
        .mapNotNull { emoji ->
            val index = emoji.shortcode.lowercase().indexOf(q)
            if (index < 0) null else Triple(emoji, if (index == 0) 0 else 1, emoji.shortcode)
        }
        .sortedWith(compareBy({ it.second }, { it.third.length }, { it.third.lowercase() }))
        .take(limit)
        .map { it.first }
}

/** [query] の範囲を `:shortcode:` に置き換えた後の本文と、カーソルを置く位置。後ろが空白でなければ空白を足す。 */
fun insertEmoji(text: CharSequence, range: TextRange, shortcode: String): Pair<String, Int> {
    val needsSpace = range.end >= text.length || !text[range.end].isWhitespace()
    val inserted = ":$shortcode:" + if (needsSpace) " " else ""
    val result = text.substring(0, range.start) + inserted + text.substring(range.end)
    // 空白を足さなかった場合も、既存の空白の後ろへ進める
    return result to range.start + inserted.length + if (needsSpace) 0 else 1
}

/**
 * カーソル位置 (範囲選択中はその範囲) に `:shortcode:` を挿入した後の本文と、カーソルを置く位置 (ピッカー用)。
 * サーバに絵文字として認識されるよう、直前が英数字・かな・コロンなら空白を前に、直後が空白でなければ後ろに足す。
 */
fun insertEmojiAtCursor(text: CharSequence, selection: TextRange, shortcode: String): Pair<String, Int> {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val before = text.getOrNull(start - 1)
    val needsLeadingSpace = before != null && (before.isLetterOrDigit() || before in Colons)
    val prefix = if (needsLeadingSpace) " " else ""
    val (inserted, cursor) = insertEmoji(text.substring(0, start) + prefix + text.substring(end), TextRange(start + prefix.length), shortcode)
    return inserted to cursor
}

/**
 * 本文中で絵文字になる `:shortcode:` の範囲 ([known] にあるもののみ)。前後の条件はサーバ
 * (Mastodon の `CustomEmoji::SCAN_RE`) と同じで、英数字やコロンに挟まれたものは絵文字にならない。
 */
fun findEmojiShortcodes(text: CharSequence, known: Set<String>): List<IntRange> =
    if (known.isEmpty()) emptyList() else ShortcodeScanRegex.findAll(text)
        .filter { it.groupValues[1] in known }
        .map { it.range }
        .toList()

private fun Char.isShortcodeChar(): Boolean = this == '_' || this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private val Colons = charArrayOf(':', '：')
private const val MinQueryLength = 2
private const val MaxSuggestions = 40

// Ruby の [[:alnum:]] は Unicode の英数字 (かな・漢字も含む) なので \p{L}\p{N} で合わせる
private val ShortcodeScanRegex = Regex("""(?<=^|[^\p{L}\p{N}:]):([A-Za-z0-9_]{2,}):(?=[^\p{L}\p{N}:]|$)""")
