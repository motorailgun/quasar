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
