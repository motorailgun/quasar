package es.aria_on_the_planet.quasar.domain.model

/** `inServerId` + `serverUrl` で一意に識別されるユーザー。 */
data class User(
    /** 閲覧中サーバ内でのアカウント ID。 */
    val inServerId: String,
    /** `username` (ローカル) または `username@domain` (リモート)。 */
    val screenName: String,
    /** 表示名。カスタム絵文字を含みうる。空の場合は [screenName] を使う。 */
    val displayName: String,
    /** ユーザーが所属するサーバの URL (例: `https://mastodon.social`)。 */
    val serverUrl: String,
    val iconUrl: String,
    /** プロフィール文 (HTML)。カスタム絵文字を含みうる。 */
    val note: String,
    val emojis: List<CustomEmoji>,
    /** アイコンのアニメーションしない版 (`avatar_static`)。 */
    val iconStaticUrl: String = iconUrl,
    /** 所属サーバでのプロフィールのページ (`Account.url`)。 */
    val url: String? = null,
) {
    /** 接続中サーバ以外のユーザーか (`acct` にドメインが付く)。 */
    val isRemote: Boolean get() = '@' in screenName

    /** ドメインを含む `username@domain` 形式。ローカルユーザーにも所属サーバのドメインを付ける。 */
    val fullScreenName: String
        get() = if ('@' in screenName) screenName else "$screenName@${serverUrl.substringAfter("://")}"
}
