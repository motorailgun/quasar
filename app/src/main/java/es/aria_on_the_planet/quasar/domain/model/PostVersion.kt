package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

/**
 * ポストの版 (編集履歴の 1 件、`StatusEdit`)。最初の版は投稿したときの内容で、最後の版が今の内容。
 * 投票は選択肢の文言だけ (件数・期限は返らない)。
 */
data class PostVersion(
    /** この版になった日時 (最初の版なら投稿日時)。 */
    val createdAt: Instant,
    val author: User,
    /** 本文 (HTML)。 */
    val text: String,
    val contentWarning: String?,
    val sensitive: Boolean,
    /** 投票の選択肢。投票の無い版なら `null`。 */
    val pollOptions: List<String>?,
    val mediaAttachments: List<MediaAttachment>,
    val emojis: List<CustomEmoji>,
)

/**
 * 版 [index] (古い順の 0 始まり) を `PostBody` 等の部品で描くための [Post]。[statusId] のポストの版として、本文・CW・
 * メディア・絵文字だけを持つ (件数・投票・カード・引用は無い。投票の選択肢は別に出す)。
 * 開閉の状態 (CW・長文) が版ごとに分かれるよう、`statusId` は版ごとに変える。
 */
fun PostVersion.asPost(statusId: String, index: Int): Post {
    val id = "$statusId#$index"
    return Post(
        id = id,
        author = author,
        text = text,
        createdAt = createdAt,
        repliesCount = 0,
        boostsCount = 0,
        reactionsCount = 0,
        emojis = emojis,
        mediaAttachments = mediaAttachments,
        contentWarning = contentWarning,
        sensitive = sensitive,
        statusId = id,
    )
}
