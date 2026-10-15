package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

data class Notification(
    val id: String,
    val type: NotificationType,
    val createdAt: Instant,
    /** 通知のきっかけとなった操作をしたアカウント。 */
    val account: User,
    /** 対象のポスト (mention / reblog / favourite など)。種別によっては `null`。 */
    val post: Post?,
    /** v2 のグループキー (Mastodon 4.3+)。ストリーミングで届いた通知を既存のグループへ合流させるのに使う。 */
    val groupKey: String? = null,
)

/** https://docs.joinmastodon.org/entities/Notification/#type */
enum class NotificationType {
    Mention,
    Status,
    Reblog,
    Follow,
    FollowRequest,
    Favourite,
    Poll,
    Update,
    Quote,
    QuotedUpdate,
    /** 未対応の種別 (admin.* やコレクション関連、将来追加されるもの)。 */
    Unknown,
}
