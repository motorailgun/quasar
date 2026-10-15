package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

data class Post(
    /** Status ID。ブーストの場合は外側 (ブースト自体) の ID で、ページングに使う。 */
    val id: String,
    val author: User,
    /** 本文 (HTML)。カスタム絵文字を含みうる。 */
    val text: String,
    val createdAt: Instant,
    val repliesCount: Int,
    val boostsCount: Int,
    val reactionsCount: Int,
    val emojis: List<CustomEmoji>,
    /** 添付メディア (0〜15 件)。 */
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    /** 本文中の URL のリンクプレビュー。 */
    val card: PreviewCard? = null,
    val poll: Poll? = null,
    /** 引用しているポスト。 */
    val quote: Quote? = null,
    /** 投稿に使われたアプリ。他サーバからのポストでは通常 `null`。 */
    val application: PostApplication? = null,
    /** Content Warning (spoiler_text)。無ければ `null`。カスタム絵文字は [emojis] を使う。 */
    val contentWarning: String? = null,
    /** メディアが閲覧注意 (NSFW) か。 */
    val sensitive: Boolean = false,
    /** 公開範囲。 */
    val visibility: PostVisibility = PostVisibility.Public,
    /** 本文の言語 (ISO 639、`ko` / `zh-TW` 等)。不明なら `null`。翻訳の対象判定に使う (端末上では判定しない)。 */
    val language: String? = null,
    /** ログイン中ユーザーがこのポストに対して行った操作。 */
    val viewer: PostViewerState = PostViewerState(),
    /** ブーストされたポストの場合、ブーストしたユーザー。 */
    val boostedBy: User? = null,
    /** 本文を持つ Status の ID。ブーストの場合は中身の ID で、スレッド表示などに使う。 */
    val statusId: String = id,
    /** リプライ先の Status ID。リプライでなければ `null`。 */
    val inReplyToId: String? = null,
    /** リプライ先アカウントの acct (`username` または `username@domain`)。特定できなければ `null`。 */
    val inReplyToAcct: String? = null,
)

/** ポストの公開範囲 (visibility)。 */
enum class PostVisibility {
    Public,
    /** 未収載 (Mastodon の UI では「ひかえめな公開」)。 */
    Unlisted,
    /** フォロワー限定。 */
    Private,
    /** 言及した相手のみ。 */
    Direct;

    /**
     * ブーストできる公開範囲か。フォロワー限定は投稿者自身ならブーストできるが、
     * ログイン中のアカウントをまだ把握していないので一律に不可として扱う。
     */
    val boostable: Boolean
        get() = this == Public || this == Unlisted
}

/** 投稿に使われたアプリ (User Agent)。 */
data class PostApplication(
    val name: String,
    val website: String?,
)

/** ログイン中ユーザーから見たポストの状態。未認証時はすべて `false`。 */
data class PostViewerState(
    val bookmarked: Boolean = false,
    val reblogged: Boolean = false,
    val favourited: Boolean = false,
)
