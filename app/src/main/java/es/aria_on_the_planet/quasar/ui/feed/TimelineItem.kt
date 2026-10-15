package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.data.streaming.StreamEvent
import es.aria_on_the_planet.quasar.domain.model.Post

/** タイムラインの 1 行。ポストか、未取得の区間 (gap)。 */
sealed interface TimelineItem {
    /** LazyColumn のキー。一覧内で一意。 */
    val key: String

    data class PostItem(val post: Post) : TimelineItem {
        override val key: String get() = post.id
    }

    /**
     * 未取得の区間。上側 (新しい側) の最古の ID [maxId] と、下側 (古い側) の最新の ID [sinceId] の間を
     * 読み込みボタンで下方向へ順に埋める。
     */
    data class Gap(val maxId: String, val sinceId: String, val isLoading: Boolean = false) : TimelineItem {
        override val key: String get() = "gap:$maxId:$sinceId"
    }
}

/** タイムラインの最大件数 (ポスト数)。ストリーム・補完で伸びたら古い側から捨てる。 */
const val MaxTimelinePosts = 400

/*
 * タイムライン (ID の降順 = 新しい順) を書き換える純粋関数。ブーストは外側 (ブースト自体) の ID で並ぶ。
 * gap は位置を保ったまま素通しする。
 */

val List<TimelineItem>.posts: List<Post> get() = mapNotNull { (it as? TimelineItem.PostItem)?.post }

/** 一覧中の各ポストを書き換える (投票結果の反映など)。 */
fun List<TimelineItem>.mapPosts(transform: (Post) -> Post): List<TimelineItem> =
    map { if (it is TimelineItem.PostItem) TimelineItem.PostItem(transform(it.post)) else it }

/** 古い側のページを末尾に足す。既にある ID は捨てる。 */
fun appendOlderPosts(current: List<TimelineItem>, page: List<Post>): List<TimelineItem> {
    val known = current.posts.mapTo(HashSet()) { it.id }
    return current + page.filter { known.add(it.id) }.map(TimelineItem::PostItem)
}

/**
 * 新しいポストを ID 順の正しい位置へ入れる。既にある ID は無視する。
 * ストリームの新着は先頭に入り、補完で取得した分が後から届いても順序が崩れない。
 */
fun mergeNewerPosts(current: List<TimelineItem>, incoming: List<Post>): List<TimelineItem> {
    if (incoming.isEmpty()) return current
    val known = current.posts.mapTo(HashSet()) { it.id }
    val result = current.toMutableList()
    for (post in incoming.sortedWith(compareByDescending(MastodonIdComparator) { it.id })) {
        if (!known.add(post.id)) continue
        val index = result.indexOfFirst { it is TimelineItem.PostItem && MastodonIdComparator.compare(it.post.id, post.id) < 0 }
        if (index == -1) result += TimelineItem.PostItem(post) else result.add(index, TimelineItem.PostItem(post))
    }
    return result
}

/**
 * 再接続時の補完で 1 ページ分埋まって返ってきた (= 間に未取得の区間がありうる) とき用。
 * [newerPage] (`since_id = [sinceId]` で取得した、新しい順) を入れ、その最古のポストと [sinceId] の間に gap を置く。
 *
 * [sinceId] は補完前の最新 ID を渡す (一覧の今の最新ではない)。補完の取得中にストリームの新着が先頭へ入っていても、
 * gap の位置は補完前の一覧の上端になる。
 */
fun mergeNewerPostsWithGap(current: List<TimelineItem>, newerPage: List<Post>, sinceId: String): List<TimelineItem> {
    val oldestOfPage = newerPage.minWithOrNull(compareBy(MastodonIdComparator) { it.id }) ?: return current
    val merged = mergeNewerPosts(current, newerPage)
    // ページが補完前の上端まで届いていれば区間は無い
    if (MastodonIdComparator.compare(oldestOfPage.id, sinceId) <= 0) return merged
    // 補完前の上端 (sinceId 以下の最初のポスト) の直前に gap を入れる
    val index = merged.indexOfFirst { it is TimelineItem.PostItem && MastodonIdComparator.compare(it.post.id, sinceId) <= 0 }
    if (index < 0) return merged
    return merged.toMutableList().apply { add(index, TimelineItem.Gap(maxId = oldestOfPage.id, sinceId = sinceId)) }
}

/** gap の読み込み中表示を切り替える。 */
fun setGapLoading(current: List<TimelineItem>, gap: TimelineItem.Gap, loading: Boolean): List<TimelineItem> =
    current.map { if (it is TimelineItem.Gap && it.key == gap.key) it.copy(isLoading = loading) else it }

/**
 * gap を埋める。[page] は `max_id = gap.maxId`・`since_id = gap.sinceId` で取得した分 (区間の上側から、新しい順)。
 * [pageLimit] 件埋まっていればまだ続きがあるので、取得分の下へ gap を移す。足りなければ gap を消す。
 */
fun fillGap(current: List<TimelineItem>, gap: TimelineItem.Gap, page: List<Post>, pageLimit: Int): List<TimelineItem> {
    val index = current.indexOfFirst { it is TimelineItem.Gap && it.key == gap.key }
    if (index < 0) return current
    val known = current.posts.mapTo(HashSet()) { it.id }
    val inserted = page
        .sortedWith(compareByDescending(MastodonIdComparator) { it.id })
        .filter { known.add(it.id) }
        .map(TimelineItem::PostItem)
    val oldest = page.minWithOrNull(compareBy(MastodonIdComparator) { it.id })
    val remaining = if (page.size >= pageLimit && oldest != null && MastodonIdComparator.compare(oldest.id, gap.sinceId) > 0) {
        listOf(TimelineItem.Gap(maxId = oldest.id, sinceId = gap.sinceId))
    } else {
        emptyList()
    }
    return current.subList(0, index) + inserted + remaining + current.subList(index + 1, current.size)
}

/** 削除されたポストを除く。[statusId] はポスト自体の ID か、ブーストの ID のどちらでもよい。 */
fun removePost(current: List<TimelineItem>, statusId: String): List<TimelineItem> {
    val result = current.filterNot { it is TimelineItem.PostItem && (it.post.id == statusId || it.post.statusId == statusId) }
    return if (result.size == current.size) current else result
}

/** 編集されたポストに差し替える。ブーストとして載っている場合は、外側 (ID・ブーストした人) を保つ。 */
fun replaceEditedPost(current: List<TimelineItem>, edited: Post): List<TimelineItem> {
    if (current.posts.none { it.statusId == edited.statusId }) return current
    return current.mapPosts { post ->
        if (post.statusId == edited.statusId) edited.copy(id = post.id, boostedBy = post.boostedBy) else post
    }
}

/**
 * ポスト数が [maxPosts] を超えたら古い側 (下) から捨てる。末尾に残った gap も捨てる (下に続きが無いので無意味)。
 */
fun trimTimeline(current: List<TimelineItem>, maxPosts: Int = MaxTimelinePosts): List<TimelineItem> {
    var count = 0
    val cut = current.indexOfFirst { it is TimelineItem.PostItem && ++count > maxPosts }
    val kept = if (cut < 0) current else current.subList(0, cut)
    return kept.dropLastWhile { it is TimelineItem.Gap }.takeIf { it.size != current.size } ?: current
}

/** 一覧の中で最も新しいポストの ID (補完の `since_id` に使う)。 */
fun newestPostId(items: List<TimelineItem>): String? = items.posts.maxOfWithOrNull(MastodonIdComparator) { it.id }

/** 一覧の中で最も古いポストの ID (末尾を捨てた後の次ページの `max_id` に使う)。 */
fun oldestPostId(items: List<TimelineItem>): String? = items.posts.minOfWithOrNull(MastodonIdComparator) { it.id }

// --- ストリーミング・補完の適用 ------------------------------------------------------------------

/**
 * ストリームのイベントを、[isTarget] のタイムラインの一覧に当てる。
 *
 * @param isTarget [StreamEvent.Update] がこの一覧宛てか (ストリームとカラムの対応)。削除・編集はどの一覧にも当てる。
 */
fun applyStreamEvent(current: List<TimelineItem>, event: StreamEvent, isTarget: Boolean): List<TimelineItem> =
    when (event) {
        is StreamEvent.Update -> if (isTarget) mergeNewerPosts(current, listOf(event.post)) else current
        is StreamEvent.Delete -> removePost(current, event.statusId)
        is StreamEvent.StatusUpdate -> replaceEditedPost(current, event.post)
        StreamEvent.Connected, is StreamEvent.NotificationReceived -> current
    }

/**
 * 再接続時の補完で `since_id = [sinceId]` で取得した [page] (新しい順) を当てる。
 * [pageLimit] 件埋まっていれば間に未取得の区間がありうるので gap を挟み、そうでなければそのまま合流させる。
 */
fun applyNewerPage(current: List<TimelineItem>, sinceId: String, page: List<Post>, pageLimit: Int): List<TimelineItem> =
    if (page.size >= pageLimit) mergeNewerPostsWithGap(current, page, sinceId) else mergeNewerPosts(current, page)
