package es.aria_on_the_planet.quasar.ui.post

import es.aria_on_the_planet.quasar.domain.model.Post

/**
 * 一列に並べたリプライの 1 行。
 *
 * @param connectedToPrevious 直前の行 (先頭なら本体ポスト) へのリプライなら `true`。アイコン同士を縦線で繋ぐ。
 * @param connectedToNext 次の行がこの行へのリプライなら `true`。
 */
data class ReplyRow(
    val post: Post,
    val connectedToPrevious: Boolean,
    val connectedToNext: Boolean,
)

/** [descendants] を API の順 (会話の流れ順) のまま一列に並べ、隣り合う行の返信関係を付ける。 */
fun linearReplies(descendants: List<Post>): List<ReplyRow> =
    descendants.mapIndexed { index, post ->
        val previous = descendants.getOrNull(index - 1)
        val next = descendants.getOrNull(index + 1)
        ReplyRow(
            post = post,
            connectedToPrevious = previous != null && post.inReplyToId == previous.statusId,
            connectedToNext = next != null && next.inReplyToId == post.statusId,
        )
    }
