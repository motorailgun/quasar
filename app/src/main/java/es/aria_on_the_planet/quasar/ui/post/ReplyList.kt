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

/**
 * 投稿したリプライ [reply] をスレッドの [descendants] に入れる。API と同じ深さ優先の順になるよう、返信先の
 * 子孫の並びの末尾に置く。返信先が本体 ([rootId]) でも子孫でもなければ (祖先への返信等) `null`。
 * 既に含まれていればそのまま返す (再読み込み済み等)。
 */
fun insertReply(descendants: List<Post>, rootId: String, reply: Post): List<Post>? {
    if (descendants.any { it.statusId == reply.statusId }) return descendants
    val parentId = reply.inReplyToId ?: return null
    val parentIndex = when (parentId) {
        rootId -> -1
        else -> descendants.indexOfFirst { it.statusId == parentId }.takeIf { it >= 0 } ?: return null
    }
    // 返信先の子孫 (返信先へ辿り着く行) が続く間は進める
    val subtree = HashSet<String>().apply { add(parentId) }
    var insertAt = parentIndex + 1
    while (insertAt < descendants.size && descendants[insertAt].inReplyToId in subtree) {
        subtree += descendants[insertAt].statusId
        insertAt++
    }
    return descendants.toMutableList().apply { add(insertAt, reply) }
}
