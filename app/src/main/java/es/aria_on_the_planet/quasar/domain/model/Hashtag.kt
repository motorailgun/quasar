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
