package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.ui.text.TextRange
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

/**
 * 入力中の補完の対象。[range] は置き換える範囲 (引き金の文字を含む)。
 *
 * `:` (カスタム絵文字。[findAutocompleteQuery]) と `@` (メンション。[findMentionQuery])。ハッシュタグ (`#`) もこの形に載せる想定。
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

/** [range] の範囲を `:shortcode:` に置き換えた後の本文と、カーソルを置く位置。後ろが空白でなければ空白を足す。 */
fun insertEmoji(text: CharSequence, range: TextRange, shortcode: String): Pair<String, Int> =
    replaceToken(text, range, ":$shortcode:")

/**
 * カーソル直前の `@user` / `@user@dom` を取り出す (メンションの補完)。前後の条件はサーバ (Mastodon 4.x の
 * `Account::MENTION_RE`) に合わせる:
 * * カーソルが 1 点で、直後が単語の途中でない。
 * * `@` (全角 `＠` も可。挿入するときに半角にする) の直前が文字・数字・`_`・`=`・`/` でない (メールアドレス・URL の中で出さない)。
 * * ユーザー名は ASCII の英数字と `_` (途中なら `.` `-` も) で、[MinQueryLength] 文字以上。続けて `@ドメイン` を打っている途中も対象。
 *
 * [AutocompleteQuery.query] は先頭の `@` を除いたもの (`user` / `user@dom`)。
 */
fun findMentionQuery(text: CharSequence, selection: TextRange): AutocompleteQuery? {
    if (!selection.collapsed) return null
    val cursor = selection.start
    if (cursor > text.length) return null
    if (cursor < text.length && (text[cursor].isMentionWordChar() || text[cursor] in Ats)) return null
    // ドメインの文字 (ユーザー名の文字を含む) をさかのぼり、その手前の @ を探す
    var start = cursor
    while (start > 0 && text[start - 1].isMentionWordChar()) start--
    if (start == 0 || text[start - 1] !in Ats) return null
    var at = start - 1
    // その @ がドメインの区切りなら、さらに手前のユーザー名と先頭の @ を探す
    var userStart = at
    while (userStart > 0 && text[userStart - 1].isUsernameChar()) userStart--
    val separator = if (userStart < at && userStart > 0 && text[userStart - 1] in Ats) at else null
    if (separator != null) at = userStart - 1
    if (at > 0) {
        val before = text[at - 1]
        if (before.isLetterOrDigit() || before == '_' || before == '=' || before == '/' || before in Ats) return null
    }
    val username = text.substring(at + 1, separator ?: cursor)
    if (username.length < MinQueryLength || !username.all { it.isUsernameChar() }) return null
    if (username.first() == '.' || username.first() == '-') return null
    val query = text.substring(at + 1, cursor).replace('＠', '@')
    return AutocompleteQuery('@', query, TextRange(at, cursor))
}

/** [range] (`@user` 等) を `@acct` に置き換えた後の本文と、カーソルを置く位置。後ろが空白でなければ空白を足す。 */
fun insertMention(text: CharSequence, range: TextRange, acct: String): Pair<String, Int> =
    replaceToken(text, range, "@$acct")

private fun replaceToken(text: CharSequence, range: TextRange, token: String): Pair<String, Int> {
    val needsSpace = range.end >= text.length || !text[range.end].isWhitespace()
    val inserted = token + if (needsSpace) " " else ""
    val result = text.substring(0, range.start) + inserted + text.substring(range.end)
    // 空白を足さなかった場合も、既存の空白の後ろへ進める
    return result to range.start + inserted.length + if (needsSpace) 0 else 1
}

/**
 * カーソル位置 (範囲選択中はその範囲) に `:shortcode:` を挿入した後の本文と、カーソルを置く位置 (ピッカー用)。
 * サーバに絵文字として認識されるよう、直前が文字・数字 (英数字・かな・漢字等)・コロンなら空白を前に、直後が空白でなければ後ろに足す。
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

/** ユーザー名の文字 (Mastodon の `USERNAME_RE`: `[a-z0-9_]+([.-]+[a-z0-9_]+)*`、大文字小文字無視)。 */
private fun Char.isUsernameChar(): Boolean = isShortcodeChar() || this == '.' || this == '-'

/** メンションのドメインの文字 (`[[:word:]]` と `.` `-`)。Ruby の `[[:word:]]` は Unicode の文字・数字と `_`。 */
private fun Char.isMentionWordChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.' || this == '-'

private val Colons = charArrayOf(':', '：')
private val Ats = charArrayOf('@', '＠')
private const val MinQueryLength = 2
private const val MaxSuggestions = 40

// Ruby の [[:alnum:]] は Unicode の英数字 (かな・漢字も含む) なので \p{L}\p{N} で合わせる
private val ShortcodeScanRegex = Regex("""(?<=^|[^\p{L}\p{N}:]):([A-Za-z0-9_]{2,}):(?=[^\p{L}\p{N}:]|$)""")
