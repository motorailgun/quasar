package es.aria_on_the_planet.quasar.domain.model

import java.net.URI

/** ポスト本文でメンションしているアカウント (Status.mentions)。 */
data class Mention(
    /** 閲覧中サーバ内でのアカウント ID。 */
    val id: String,
    val username: String,
    /** `username` (ローカル) または `username@domain` (リモート)。 */
    val acct: String,
    /** プロフィールの URL。本文の `<a href>` と同じ値が入る。 */
    val url: String,
) {
    /**
     * プロフィールを開くための [User]。ID と名前・サーバしか分からないので、アイコン・表示名・bio は空。
     * 一覧に表示する用途には使わない。
     */
    fun asUser(): User = User(
        inServerId = id,
        screenName = acct,
        displayName = "",
        serverUrl = runCatching { URI(url).let { "${it.scheme}://${it.authority}" } }.getOrDefault(""),
        iconUrl = "",
        note = "",
        emojis = emptyList(),
    )
}

/**
 * 本文のリンク先 [href] がメンションならそのアカウント。Mastodon Web と同じく `url` との一致で判定する
 * (`class="mention"` はハッシュタグにも付き、他実装からのポストでは付き方が揃っていないため見ない)。
 */
fun List<Mention>.findByUrl(href: String): Mention? {
    val target = href.trimEnd('/')
    return firstOrNull { it.url.isNotEmpty() && it.url.trimEnd('/') == target }
}

/**
 * 投稿の本文のうち、サーバ (Mastodon 4.x) がメンションとして扱う `@user` / `@user@domain` の範囲 (`@` を含む)。
 * アカウントが実在するかは見ない (入力欄の強調用)。
 *
 * サーバの `Account::MENTION_RE` と `Extractor.extract_mentions_or_lists_with_indices` に合わせる:
 * * `@` (半角のみ。全角 `＠` はメンションにならない) の直前が単語の文字 (`[[:word:]]`)・`=`・`/` でない。
 * * ユーザー名は `[a-z0-9_]+([.-]+[a-z0-9_]+)*` (大文字小文字無視)。続けて `@` + `[[:word:]]+([.-]+[[:word:]]+)*` があればドメイン。
 * * 直後が `@` / `＠`・ラテン文字のアクセント付きの文字 (twitter-text の `LATIN_ACCENTS`)・`://` ならメンションにしない
 *   (`@user@`・`@usér` は打ちかけでも強調しない)。ドメインが 253 文字を超えるものも。
 *
 * サーバは URL と重なるものを除くが、URL の中の `@` はたいてい直前が `/` か単語の文字なので見ていない。
 */
fun findMentions(text: CharSequence): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        if (text[i] != '@' || (i > 0 && text.isMentionBlockedBefore(i))) {
            i++
            continue
        }
        val usernameEnd = mentionPartEnd(text, i + 1) { it.isUsernameChar() }
        if (usernameEnd == null) {
            i++
            continue
        }
        var end: Int = usernameEnd
        var domainLength = 0
        if (end < text.length && text[end] == '@') {
            mentionPartEnd(text, end + 1) { it.isRubyWordChar() }?.let { domainEnd ->
                domainLength = Character.codePointCount(text, end + 1, domainEnd)
                end = domainEnd
            }
        }
        if (!text.isEndMentionMatch(end) && domainLength <= MaxMentionDomainLength) result += i until end
        // サーバの scan と同じく、弾いたものも含めて一致の後ろから探し直す
        i = end
    }
    return result
}

/** [start] からの `C+([.-]+C+)*` (C は [isChar]) の終わり (排他)。1 文字も無ければ `null`。 */
private fun mentionPartEnd(text: CharSequence, start: Int, isChar: (Int) -> Boolean): Int? {
    var end = consumeWhile(text, start, isChar)
    if (end == start) return null
    while (true) {
        var separatorEnd = end
        while (separatorEnd < text.length && (text[separatorEnd] == '.' || text[separatorEnd] == '-')) separatorEnd++
        if (separatorEnd == end) break
        val next = consumeWhile(text, separatorEnd, isChar)
        if (next == separatorEnd) break
        end = next
    }
    return end
}

private fun consumeWhile(text: CharSequence, from: Int, isChar: (Int) -> Boolean): Int {
    var pos = from
    while (pos < text.length) {
        val cp = Character.codePointAt(text, pos)
        if (!isChar(cp)) break
        pos += Character.charCount(cp)
    }
    return pos
}

/** `MENTION_RE` の否定の後読み `(?<![=/[:word:]])`。 */
private fun CharSequence.isMentionBlockedBefore(index: Int): Boolean {
    val cp = Character.codePointBefore(this, index)
    return cp == '='.code || cp == '/'.code || cp.isRubyWordChar()
}

/** twitter-text の `end_mention_match` (`\A(?:[@＠]|[LATIN_ACCENTS]|://)`)。 */
private fun CharSequence.isEndMentionMatch(index: Int): Boolean {
    if (index >= length) return false
    val c = this[index]
    if (c == '@' || c == '＠') return true
    if (index + 3 <= length && regionMatches(index, "://", 0, 3)) return true
    return c.code.isLatinAccent()
}

private fun Int.isUsernameChar(): Boolean =
    this == '_'.code || this in 'a'.code..'z'.code || this in 'A'.code..'Z'.code || this in '0'.code..'9'.code

/** twitter-text の `LATIN_ACCENTS`。 */
private fun Int.isLatinAccent(): Boolean =
    this in 0xC0..0xD6 || this in 0xD8..0xF6 || this in 0xF8..0x24F || this in 0x253..0x254 || this in 0x256..0x257 ||
        this == 0x259 || this == 0x25B || this == 0x263 || this == 0x268 || this == 0x26F || this == 0x272 ||
        this == 0x289 || this == 0x28B || this == 0x2BB || this in 0x300..0x36F || this in 0x1E00..0x1EFF

/** Mastodon の `Extractor::MAX_DOMAIN_LENGTH`。 */
private const val MaxMentionDomainLength = 253
