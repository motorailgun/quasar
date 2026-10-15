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
    /** ログイン中ユーザーがこのポストに対して行った操作。 */
    val viewer: PostViewerState = PostViewerState(),
    /** ブーストされたポストの場合、ブーストしたユーザー。 */
    val boostedBy: User? = null,
    /** 本文を持つ Status の ID。ブーストの場合は中身の ID で、スレッド表示などに使う。 */
    val statusId: String = id,
    /** リプライ先の Status ID。リプライでなければ `null`。 */
    val inReplyToId: String? = null,
)

/** ログイン中ユーザーから見たポストの状態。未認証時はすべて `false`。 */
data class PostViewerState(
    val bookmarked: Boolean = false,
    val reblogged: Boolean = false,
    val favourited: Boolean = false,
)
