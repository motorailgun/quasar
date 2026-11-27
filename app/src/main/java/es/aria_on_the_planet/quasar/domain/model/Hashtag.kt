package es.aria_on_the_planet.quasar.domain.model

/** ポスト本文のハッシュタグ (Status.tags)。 */
data class Hashtag(
    /** `#` を除いた名前。 */
    val name: String,
    /** 接続中サーバのハッシュタグのページ (`https://<server>/tags/<name>`)。 */
    val url: String,
)

/** ポスト本文のリンクの行き先。 */
sealed interface PostLinkTarget {
    data class Account(val mention: Mention) : PostLinkTarget
    data class Tag(val hashtag: Hashtag) : PostLinkTarget
    data class External(val url: String) : PostLinkTarget
}

/**
 * 本文のリンク ([url] = href、[text] = リンクの文字列) の行き先を決める。
 *
 * * メンション: href が [Post.mentions] の `url` と一致するもの ([findByUrl])。
 * * ハッシュタグ: href が [Post.tags] の `url` と一致するか、文字列が `#名前` で名前が [Post.tags] にあるもの
 *   (大文字小文字は区別しない)。`tags` の `url` は接続中サーバのページなので、他サーバのポストでは href
 *   (元のサーバのページ) と一致しない。そのため Mastodon Web と同じく文字列でも照合する。
 * * それ以外は外部リンク。
 */
fun Post.linkTarget(url: String, text: String?): PostLinkTarget {
    mentions.findByUrl(url)?.let { return PostLinkTarget.Account(it) }
    val href = url.trimEnd('/')
    tags.firstOrNull { it.url.isNotEmpty() && it.url.trimEnd('/') == href }?.let { return PostLinkTarget.Tag(it) }
    val name = text?.trim()?.takeIf { it.startsWith('#') }?.drop(1)
    if (!name.isNullOrEmpty()) {
        tags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return PostLinkTarget.Tag(it) }
    }
    return PostLinkTarget.External(url)
}

/**
 * 投稿の本文のうち、サーバ (Mastodon 4.x) がハッシュタグとして扱う `#名前` の範囲 (`#` を含む)。
 *
 * サーバの `Tag::HASHTAG_RE` (`(?<=^|[[:space:]])[#＃](名前)`) と `Extractor.extract_hashtags_with_indices` に合わせる:
 * * `#` (全角 `＃` も可) は本文の先頭か空白の直後のみ (`a#b`・`(#b` 等はタグにならない。3.5 は `(` の後もタグにするが 4.x に揃える)。
 * * 名前は単語の文字 (文字・結合文字・数字・`_` 等) の並びで、文字 (Alphabetic) を 1 つ以上含む (`#123` はタグにならない)。
 *   単語の文字の間には区切り (`·` `・` ZWNJ) を挟める (`#ハッシュ・タグ`)。末尾の区切りは含めない。
 * * 直後が `://` なら末尾の `http` / `https` を除く (`#tag https://...` の空白が抜けた `#taghttps://...`)。
 *
 * Ruby (Onigmo) の文字クラスは JVM / Android の正規表現と意味が揃わないので、コードポイントごとに判定している。
 */
fun findHashtags(text: CharSequence): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if ((c == '#' || c == '＃') && (i == 0 || text.isHashtagSpaceBefore(i))) {
            val end = hashtagNameEnd(text, i + 1)
            if (end != null) {
                val trimmed = trimUrlScheme(text, i + 1, end)
                result += i until trimmed
                i = end
                continue
            }
        }
        i++
    }
    return result
}

/**
 * `#` の直後 [start] からの名前の終わり (排他)。名前にならなければ `null`。
 *
 * サーバの名前のパターンは `FIRST|LAST` で、Ruby の正規表現の (欲張りな) 読み方では次と同じになる:
 * * FIRST (`単語 [単語 区切り]* [文字 区切り] [単語 区切り]* 単語`): 単語の文字か区切りの並びのうち、最後の単語の文字まで。
 *   ただし 2 つ目以降・最後の単語の文字より前に、文字か区切りが要る。
 * * LAST (`単語* 文字 単語*`): 単語の文字だけの先頭部分。文字を 1 つ以上含むこと。
 */
private fun hashtagNameEnd(text: CharSequence, start: Int): Int? {
    var pos = start
    var count = 0
    var lastWordStart = -1
    var lastWordEnd = -1
    var firstAlphaOrSeparator = -1 // 2 つ目以降で最初の文字か区切りの位置
    var wordPrefixEnd = start // 単語の文字だけの先頭部分の終わり
    var wordPrefixHasAlpha = false
    var inWordPrefix = true
    while (pos < text.length) {
        val cp = Character.codePointAt(text, pos)
        val word = cp.isRubyWordChar()
        val separator = cp.isHashtagSeparator()
        if (!word && !separator) break
        if (count == 0 && !word) return null
        val alpha = cp.isHashtagAlpha()
        val next = pos + Character.charCount(cp)
        if (count >= 1 && firstAlphaOrSeparator < 0 && (alpha || separator)) firstAlphaOrSeparator = pos
        if (word) {
            lastWordStart = pos
            lastWordEnd = next
        }
        if (inWordPrefix && word) {
            wordPrefixEnd = next
            if (alpha) wordPrefixHasAlpha = true
        } else {
            inWordPrefix = false
        }
        pos = next
        count++
    }
    if (firstAlphaOrSeparator in 0 until lastWordStart) return lastWordEnd
    if (wordPrefixHasAlpha) return wordPrefixEnd
    return null
}

/** サーバと同じく、直後が `://` なら名前の末尾の `http` / `https` を除いた終わり (名前は 1 文字以上残す)。 */
private fun trimUrlScheme(text: CharSequence, nameStart: Int, end: Int): Int {
    if (!text.startsWith("://", end)) return end
    val name = text.subSequence(nameStart, end)
    for (scheme in arrayOf("https", "http")) {
        if (name.length > scheme.length && name.endsWith(scheme)) return end - scheme.length
    }
    return end
}

private fun CharSequence.startsWith(prefix: String, at: Int): Boolean =
    at + prefix.length <= length && regionMatches(at, prefix, 0, prefix.length)

/** [index] の直前のコードポイントが Ruby の `[[:space:]]` (Zs・Zl・Zp・U+0009〜000D・U+0085) か。 */
private fun CharSequence.isHashtagSpaceBefore(index: Int): Boolean {
    val cp = Character.codePointBefore(this, index)
    return Character.isSpaceChar(cp) || cp in 0x09..0x0D || cp == 0x85
}

/** Ruby の `[[:alpha:]]` (Alphabetic)。 */
private fun Int.isHashtagAlpha(): Boolean = Character.isAlphabetic(this)

/** Ruby の `[[:word:]]` (Alphabetic・結合文字・10 進数字・連結符・ZWJ / ZWNJ)。メンションのドメインと前の文字の判定 ([findMentions]) にも使う。 */
internal fun Int.isRubyWordChar(): Boolean {
    if (Character.isAlphabetic(this)) return true
    return when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
        Character.DECIMAL_DIGIT_NUMBER, Character.CONNECTOR_PUNCTUATION -> true
        else -> this == 0x200C || this == 0x200D
    }
}

/** `HASHTAG_SEPARATORS` (`_` `·` `・` ZWNJ)。 */
private fun Int.isHashtagSeparator(): Boolean = this == '_'.code || this == 0x00B7 || this == 0x30FB || this == 0x200C
