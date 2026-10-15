package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CoroutineScope

typealias PostFeedState = FeedState<TimelineItem>

/**
 * ポスト一覧の [PagedFeed]。タイムライン・プロフィールなど、ポスト一覧を持つ ViewModel で共通に使う。
 * 一覧の要素は [TimelineItem] (ポストか gap)。ページングにはポストの ID (ブーストならブースト自体の ID) を使う。
 * ストリーム・補完で伸びたら [MaxTimelinePosts] 件を超えた古い側を捨てる。
 */
class PostFeed(
    scope: CoroutineScope,
    fetch: suspend (maxId: String?) -> List<Post>,
) : PagedFeed<Post, TimelineItem>(
    scope = scope,
    // タイムラインは新しい順なので末尾が最も古い
    nextMaxIdOf = { page -> page.lastOrNull()?.id },
    append = ::appendOlderPosts,
    trim = ::trimTimeline,
    nextMaxIdOfItems = ::oldestPostId,
    fetch = fetch,
) {
    /** 読み込み済みのポストを書き換える (投票結果の反映など)。 */
    fun updatePosts(transform: (Post) -> Post) = updateList { it.mapPosts(transform) }
}
