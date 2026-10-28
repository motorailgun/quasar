package es.aria_on_the_planet.quasar.domain.model

import java.text.BreakIterator

/**
 * Mastodon の数え方での文字数 (`StatusLengthValidator` に合わせる)。
 *
 * * URL (`http://` / `https://` 始まり) は長さに関わらず [ComposeLimits.charactersPerUrl] 文字。
 * * メンション `@user@domain` は `@user` の分だけ数える。
 * * 文字は書記素 (見た目の 1 文字) 単位。絵文字の結合や異体字セレクタは 1 文字。
 *
 * URL の判定はサーバ (twitter-text) より簡略化しているため、末尾の記号の扱いなどで数文字ずれることがある。
 */
fun countPostCharacters(text: String, limits: ComposeLimits = ComposeLimits()): Int {
    if (text.isEmpty()) return 0
    var placeholders = 0
    val rewritten = text
        .replace(UrlRegex) {
            placeholders++
            ""
        }
        .replace(MentionRegex) { "@" + it.groupValues[1] }
    return graphemeCount(rewritten) + placeholders * limits.charactersPerUrl
}

/** 書記素 (見た目の 1 文字) の数。 */
internal fun graphemeCount(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

/** 末尾の句読点・閉じ括弧は URL に含めない。 */
private val UrlRegex = Regex("""(?<![\w/])https?://[^\s]*[^\s.,:;!?'"()\[\]<>]""", RegexOption.IGNORE_CASE)

/** Mastodon の `Account::MENTION_RE` 相当。グループ 1 がユーザー名。 */
private val MentionRegex = Regex(
    """(?<![=/\w])@([a-z0-9_]+(?:[a-z0-9_.-]+[a-z0-9_]+)?)(?:@[\w]+(?:[.-]+[\w]+)*)?""",
    RegexOption.IGNORE_CASE,
)
